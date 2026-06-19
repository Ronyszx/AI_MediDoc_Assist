package com.mediassist.platform.documentqa.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionResponse;
import com.mediassist.platform.documentqa.domain.DocumentQuestionAnswer;
import com.mediassist.platform.documentqa.domain.DocumentQuestionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class DocumentQaApplicationService {

    private static final String SYSTEM_PROMPT = """
        You are MediAssist, a document question answering assistant.
        Answer only from the provided document context chunks.
        Cite chunk numbers in the answer when possible, for example [Chunk 1].
        Do not invent facts or use outside knowledge.
        If the answer is not present in the context, say you cannot determine it from the provided document context.
        Do not provide medical advice, treatment recommendations, or a diagnosis beyond summarizing what the document says.
        """;

    private final DocumentEmbeddingApplicationService documentEmbeddingApplicationService;
    private final LlmClient llmClient;
    private final DocumentQaMapper documentQaMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;
    private final LlmSettings llmSettings;

    public DocumentQaApplicationService(
        DocumentEmbeddingApplicationService documentEmbeddingApplicationService,
        LlmClient llmClient,
        DocumentQaMapper documentQaMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper,
        LlmSettings llmSettings
    ) {
        this.documentEmbeddingApplicationService = documentEmbeddingApplicationService;
        this.llmClient = llmClient;
        this.documentQaMapper = documentQaMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
        this.llmSettings = llmSettings;
    }

    @Transactional
    public DocumentQuestionResponse answerQuestion(
        @NotNull UUID documentId,
        @NotNull @Valid DocumentQuestionRequest request,
        @NotBlank String performedBy
    ) {
        String question = request.question().trim();
        List<SemanticSearchMatch> matches = documentEmbeddingApplicationService.searchSimilarChunks(
            documentId,
            question,
            request.topK()
        );

        if (matches.isEmpty()) {
            throw new NoRelevantDocumentContextException(documentId);
        }

        try {
            LlmCompletionResponse completion = llmClient.complete(new LlmCompletionRequest(
                llmSettings.getModelName(),
                List.of(
                    new LlmMessage("system", SYSTEM_PROMPT),
                    new LlmMessage("user", buildUserPrompt(question, matches))
                ),
                llmSettings.getTemperature(),
                llmSettings.getMaxOutputTokens()
            ));
            DocumentQuestionAnswer answer = buildAnswer(documentId, question, completion, matches);

            recordQuestionAudit(documentId, performedBy, request.topK(), answer.sources().size(), completion.modelName());
            return documentQaMapper.toResponse(answer);
        } catch (LlmServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RagAnswerGenerationException(documentId, exception);
        }
    }

    private DocumentQuestionAnswer buildAnswer(
        UUID documentId,
        String question,
        LlmCompletionResponse completion,
        List<SemanticSearchMatch> matches
    ) {
        List<DocumentQuestionSource> sources = matches.stream()
            .map(documentQaMapper::toSource)
            .toList();

        return new DocumentQuestionAnswer(
            documentId,
            question,
            completion.content(),
            completion.modelName(),
            sources,
            LocalDateTime.now()
        );
    }

    private String buildUserPrompt(String question, List<SemanticSearchMatch> matches) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Question:\n")
            .append(question)
            .append("\n\nDocument context:\n");

        for (int index = 0; index < matches.size(); index++) {
            SemanticSearchMatch match = matches.get(index);
            prompt.append("[Chunk ")
                .append(index + 1)
                .append(" | sourceChunkIndex=")
                .append(match.chunkIndex())
                .append(" | similarity=")
                .append(String.format(Locale.ROOT, "%.4f", match.similarityScore()))
                .append("]\n")
                .append(match.chunkText())
                .append("\n\n");
        }

        prompt.append("""
            Instructions:
            Answer the question using only the document context above.
            Cite chunks as [Chunk 1], [Chunk 2], etc. when possible.
            If the context does not contain the answer, say that it cannot be determined from the provided document context.
            Do not provide medical advice.
            """);

        return prompt.toString();
    }

    private void recordQuestionAudit(
        UUID documentId,
        String performedBy,
        int topK,
        int sourceCount,
        String modelName
    ) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("topK", topK);
        details.put("sourceCount", sourceCount);
        details.put("modelName", modelName);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            documentId,
            AuditAction.DOCUMENT_QUESTION_ASKED,
            performedBy,
            details
        ));
    }
}
