package com.mediassist.platform.documentqa.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionResponse;
import com.mediassist.platform.documentqa.domain.DocumentQuestionAnswer;
import com.mediassist.platform.documentqa.domain.DocumentQuestionSource;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class DocumentQaApplicationService {

    private final DocumentQaContextRetrievalService contextRetrievalService;
    private final DocumentAnswerGenerationService answerGenerationService;
    private final DocumentQaMapper documentQaMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;

    public DocumentQaApplicationService(
        DocumentQaContextRetrievalService contextRetrievalService,
        DocumentAnswerGenerationService answerGenerationService,
        DocumentQaMapper documentQaMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper
    ) {
        this.contextRetrievalService = contextRetrievalService;
        this.answerGenerationService = answerGenerationService;
        this.documentQaMapper = documentQaMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentQuestionResponse answerQuestion(
        @NotNull UUID documentId,
        @NotNull @Valid DocumentQuestionRequest request,
        @NotBlank String performedBy
    ) {
        String question = request.question().trim();
        List<SemanticSearchMatch> matches = contextRetrievalService.retrieveContext(
            documentId,
            question,
            request.topK()
        );

        if (matches.isEmpty()) {
            throw new NoRelevantDocumentContextException(documentId);
        }

        try {
            DocumentAnswerDraft completion = answerGenerationService.generateAnswer(question, matches);
            DocumentQuestionAnswer answer = buildAnswer(documentId, question, completion, matches);

            recordQuestionAudit(documentId, performedBy, request.topK(), answer.sources().size(), completion);
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
        DocumentAnswerDraft completion,
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

    private void recordQuestionAudit(
        UUID documentId,
        String performedBy,
        int topK,
        int sourceCount,
        DocumentAnswerDraft draft
    ) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("topK", topK);
        details.put("sourceCount", sourceCount);
        details.put("modelName", draft.modelName());
        details.put("answerMode", draft.mode().name());
        details.put("evidenceCount", draft.evidenceCount());
        details.put("contextLimited", draft.contextLimited());

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            documentId,
            AuditAction.DOCUMENT_QUESTION_ASKED,
            performedBy,
            details
        ));
    }
}
