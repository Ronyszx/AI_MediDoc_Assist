package com.mediassist.platform.documentchunk.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.documentchunk.api.dto.DocumentChunkResponse;
import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentextraction.application.DocumentExtractionNotCompletedException;
import com.mediassist.platform.documentextraction.application.DocumentExtractionNotFoundException;
import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import com.mediassist.platform.documentextraction.domain.DocumentExtractionRepository;
import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class DocumentChunkApplicationService {

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final DocumentExtractionRepository documentExtractionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextChunkingService textChunkingService;
    private final DocumentChunkMapper documentChunkMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;

    public DocumentChunkApplicationService(
        MedicalDocumentRepository medicalDocumentRepository,
        DocumentExtractionRepository documentExtractionRepository,
        DocumentChunkRepository documentChunkRepository,
        TextChunkingService textChunkingService,
        DocumentChunkMapper documentChunkMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper
    ) {
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.documentExtractionRepository = documentExtractionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.textChunkingService = textChunkingService;
        this.documentChunkMapper = documentChunkMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
    }

    public List<DocumentChunkResponse> chunkDocument(
        @NotNull UUID documentId,
        @NotBlank String performedBy
    ) {
        validateDocumentExists(documentId);
        DocumentExtraction extraction = findCompletedExtraction(documentId);

        if (documentChunkRepository.existsByDocumentExtractionId(extraction.getId())) {
            return findChunksForExtraction(extraction.getId());
        }

        recordChunkingAudit(documentId, AuditAction.CHUNKING_STARTED, performedBy, buildStartedDetails(extraction));

        try {
            List<String> chunkTexts = textChunkingService.splitIntoChunks(extraction.getExtractedText());
            List<DocumentChunk> chunks = createChunks(extraction, chunkTexts);
            List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);

            recordChunkingAudit(
                documentId,
                AuditAction.CHUNKING_COMPLETED,
                performedBy,
                buildCompletedDetails(extraction, savedChunks.size())
            );

            return savedChunks.stream()
                .map(documentChunkMapper::toResponse)
                .toList();
        } catch (RuntimeException exception) {
            recordChunkingAudit(
                documentId,
                AuditAction.CHUNKING_FAILED,
                performedBy,
                buildFailedDetails(extraction, exception)
            );
            throw new DocumentChunkingProcessingException(documentId, exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> listChunksForDocument(@NotNull UUID documentId) {
        validateDocumentExists(documentId);
        findExtractionByDocumentId(documentId);

        return documentChunkRepository.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(documentId).stream()
            .map(documentChunkMapper::toResponse)
            .toList();
    }

    private void validateDocumentExists(UUID documentId) {
        if (!medicalDocumentRepository.findById(documentId).isPresent()) {
            throw new MedicalDocumentNotFoundException(documentId);
        }
    }

    private DocumentExtraction findCompletedExtraction(UUID documentId) {
        DocumentExtraction extraction = findExtractionByDocumentId(documentId);
        if (extraction.getExtractionStatus() != ExtractionStatus.COMPLETED || extraction.getExtractedText() == null) {
            throw new DocumentExtractionNotCompletedException(documentId, extraction.getExtractionStatus());
        }

        return extraction;
    }

    private DocumentExtraction findExtractionByDocumentId(UUID documentId) {
        return documentExtractionRepository.findByDocumentId(documentId)
            .orElseThrow(() -> new DocumentExtractionNotFoundException(documentId));
    }

    private List<DocumentChunkResponse> findChunksForExtraction(UUID extractionId) {
        return documentChunkRepository.findAllByDocumentExtractionIdOrderByChunkIndexAsc(extractionId).stream()
            .map(documentChunkMapper::toResponse)
            .toList();
    }

    private List<DocumentChunk> createChunks(DocumentExtraction extraction, List<String> chunkTexts) {
        return java.util.stream.IntStream.range(0, chunkTexts.size())
            .mapToObj(index -> documentChunkMapper.toNewChunk(extraction, index, chunkTexts.get(index)))
            .toList();
    }

    private void recordChunkingAudit(UUID documentId, AuditAction action, String performedBy, ObjectNode details) {
        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            documentId,
            action,
            performedBy,
            details
        ));
    }

    private ObjectNode buildStartedDetails(DocumentExtraction extraction) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("extractionId", extraction.getId().toString());
        details.put("status", "STARTED");
        return details;
    }

    private ObjectNode buildCompletedDetails(DocumentExtraction extraction, int chunkCount) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("extractionId", extraction.getId().toString());
        details.put("chunkCount", chunkCount);
        details.put("status", "COMPLETED");
        return details;
    }

    private ObjectNode buildFailedDetails(DocumentExtraction extraction, RuntimeException exception) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("extractionId", extraction.getId().toString());
        details.put("status", "FAILED");
        details.put("reason", exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        return details;
    }
}
