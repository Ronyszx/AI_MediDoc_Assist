package com.mediassist.platform.documentembedding.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.api.dto.DocumentChunkEmbeddingMetadataResponse;
import com.mediassist.platform.documentembedding.api.dto.DocumentEmbeddingSummaryResponse;
import com.mediassist.platform.documentembedding.api.dto.SemanticSearchRequest;
import com.mediassist.platform.documentembedding.api.dto.SemanticSearchResponse;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbedding;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbeddingRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class DocumentEmbeddingApplicationService {

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final DocumentEmbeddingMapper documentEmbeddingMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;

    public DocumentEmbeddingApplicationService(
        MedicalDocumentRepository medicalDocumentRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository,
        EmbeddingService embeddingService,
        DocumentEmbeddingMapper documentEmbeddingMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper
    ) {
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentChunkEmbeddingRepository = documentChunkEmbeddingRepository;
        this.embeddingService = embeddingService;
        this.documentEmbeddingMapper = documentEmbeddingMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
    }

    public DocumentEmbeddingSummaryResponse generateEmbeddings(
        @NotNull UUID documentId,
        @NotBlank String performedBy
    ) {
        validateDocumentExists(documentId);
        List<DocumentChunk> chunks = findChunksForDocument(documentId);
        List<DocumentChunk> chunksToEmbed = findChunksWithoutEmbedding(chunks);

        if (chunksToEmbed.isEmpty()) {
            return buildSummary(documentId, chunks.size(), 0, chunks.size());
        }

        recordAuditEvent(
            documentId,
            AuditAction.EMBEDDING_STARTED,
            performedBy,
            buildEmbeddingStartedDetails(chunks.size(), chunksToEmbed.size())
        );

        try {
            EmbeddingResult embeddingResult = embeddingService.embedTexts(chunksToEmbed.stream()
                .map(DocumentChunk::getChunkText)
                .toList());

            for (int index = 0; index < chunksToEmbed.size(); index++) {
                documentChunkEmbeddingRepository.saveEmbedding(
                    chunksToEmbed.get(index),
                    embeddingResult.modelName(),
                    embeddingResult.dimensions(),
                    embeddingResult.embeddings().get(index)
                );
            }

            recordAuditEvent(
                documentId,
                AuditAction.EMBEDDING_COMPLETED,
                performedBy,
                buildEmbeddingCompletedDetails(chunks.size(), chunksToEmbed.size())
            );

            return buildSummary(documentId, chunks.size(), chunksToEmbed.size(), chunks.size() - chunksToEmbed.size());
        } catch (RuntimeException exception) {
            recordAuditEvent(
                documentId,
                AuditAction.EMBEDDING_FAILED,
                performedBy,
                buildEmbeddingFailedDetails(chunks.size(), chunksToEmbed.size(), exception)
            );
            throw new DocumentEmbeddingProcessingException(documentId, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkEmbeddingMetadataResponse> getEmbeddingMetadata(@NotNull UUID documentId) {
        validateDocumentExists(documentId);
        findChunksForDocument(documentId);

        List<DocumentChunkEmbedding> embeddings = documentChunkEmbeddingRepository.findAllByDocumentIdAndModelName(
            documentId,
            embeddingService.modelName()
        );
        if (embeddings.isEmpty()) {
            throw new DocumentEmbeddingsNotFoundException(documentId, embeddingService.modelName());
        }

        return embeddings.stream()
            .map(documentEmbeddingMapper::toMetadataResponse)
            .toList();
    }

    public SemanticSearchResponse semanticSearch(
        @NotNull UUID documentId,
        @NotNull @Valid SemanticSearchRequest request,
        @NotBlank String performedBy
    ) {
        validateDocumentExists(documentId);
        findChunksForDocument(documentId);
        ensureDocumentHasEmbeddings(documentId);

        EmbeddingResult queryEmbedding = embeddingService.embedQuery(request.query());
        List<SemanticSearchMatch> matches = documentChunkEmbeddingRepository.searchSimilarChunks(
            documentId,
            queryEmbedding.modelName(),
            queryEmbedding.embeddings().getFirst(),
            request.topK()
        );

        recordAuditEvent(
            documentId,
            AuditAction.SEMANTIC_SEARCH_PERFORMED,
            performedBy,
            buildSemanticSearchDetails(request, matches.size())
        );

        return new SemanticSearchResponse(
            documentId,
            queryEmbedding.modelName(),
            request.query(),
            matches.stream()
                .map(documentEmbeddingMapper::toSearchMatchResponse)
                .toList()
        );
    }

    private void validateDocumentExists(UUID documentId) {
        if (medicalDocumentRepository.findById(documentId).isEmpty()) {
            throw new MedicalDocumentNotFoundException(documentId);
        }
    }

    private List<DocumentChunk> findChunksForDocument(UUID documentId) {
        List<DocumentChunk> chunks = documentChunkRepository.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            throw new DocumentChunksNotFoundException(documentId);
        }

        return chunks;
    }

    private List<DocumentChunk> findChunksWithoutEmbedding(List<DocumentChunk> chunks) {
        Set<UUID> chunkIds = chunks.stream()
            .map(DocumentChunk::getId)
            .collect(Collectors.toSet());
        Set<UUID> embeddedChunkIds = documentChunkEmbeddingRepository
            .findAllByChunkIdInAndModelName(chunkIds, embeddingService.modelName())
            .stream()
            .map(embedding -> embedding.getChunk().getId())
            .collect(Collectors.toSet());

        return chunks.stream()
            .filter(chunk -> !embeddedChunkIds.contains(chunk.getId()))
            .toList();
    }

    private void ensureDocumentHasEmbeddings(UUID documentId) {
        if (documentChunkEmbeddingRepository.findAllByDocumentIdAndModelName(documentId, embeddingService.modelName()).isEmpty()) {
            throw new DocumentEmbeddingsNotFoundException(documentId, embeddingService.modelName());
        }
    }

    private DocumentEmbeddingSummaryResponse buildSummary(
        UUID documentId,
        int totalChunks,
        int embeddedChunks,
        int skippedChunks
    ) {
        return new DocumentEmbeddingSummaryResponse(
            documentId,
            embeddingService.modelName(),
            embeddingService.dimensions(),
            totalChunks,
            embeddedChunks,
            skippedChunks,
            LocalDateTime.now()
        );
    }

    private void recordAuditEvent(UUID documentId, AuditAction action, String performedBy, ObjectNode details) {
        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            documentId,
            action,
            performedBy,
            details
        ));
    }

    private ObjectNode buildEmbeddingStartedDetails(int totalChunks, int chunksToEmbed) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("modelName", embeddingService.modelName());
        details.put("embeddingDimension", embeddingService.dimensions());
        details.put("totalChunks", totalChunks);
        details.put("chunksToEmbed", chunksToEmbed);
        return details;
    }

    private ObjectNode buildEmbeddingCompletedDetails(int totalChunks, int embeddedChunks) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("modelName", embeddingService.modelName());
        details.put("embeddingDimension", embeddingService.dimensions());
        details.put("totalChunks", totalChunks);
        details.put("embeddedChunks", embeddedChunks);
        return details;
    }

    private ObjectNode buildEmbeddingFailedDetails(int totalChunks, int chunksToEmbed, RuntimeException exception) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("modelName", embeddingService.modelName());
        details.put("embeddingDimension", embeddingService.dimensions());
        details.put("totalChunks", totalChunks);
        details.put("chunksToEmbed", chunksToEmbed);
        details.put("reason", exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        return details;
    }

    private ObjectNode buildSemanticSearchDetails(SemanticSearchRequest request, int resultCount) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("modelName", embeddingService.modelName());
        details.put("topK", request.topK());
        details.put("resultCount", resultCount);
        return details;
    }
}
