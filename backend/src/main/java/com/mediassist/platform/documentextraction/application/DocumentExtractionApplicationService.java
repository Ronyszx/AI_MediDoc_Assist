package com.mediassist.platform.documentextraction.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.document.application.storage.DocumentStorageOperationException;
import com.mediassist.platform.document.application.storage.DocumentStorageService;
import com.mediassist.platform.document.application.storage.StoredDocumentResource;
import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionResponse;
import com.mediassist.platform.documentextraction.api.dto.DocumentExtractionTextResponse;
import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import com.mediassist.platform.documentextraction.domain.DocumentExtractionRepository;
import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class DocumentExtractionApplicationService {

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final DocumentExtractionRepository documentExtractionRepository;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final DocumentStorageService documentStorageService;
    private final DocumentExtractionMapper documentExtractionMapper;
    private final AuditApplicationService auditApplicationService;
    private final ObjectMapper objectMapper;

    public DocumentExtractionApplicationService(
        MedicalDocumentRepository medicalDocumentRepository,
        DocumentExtractionRepository documentExtractionRepository,
        PdfTextExtractionService pdfTextExtractionService,
        DocumentStorageService documentStorageService,
        DocumentExtractionMapper documentExtractionMapper,
        AuditApplicationService auditApplicationService,
        ObjectMapper objectMapper
    ) {
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.documentExtractionRepository = documentExtractionRepository;
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.documentStorageService = documentStorageService;
        this.documentExtractionMapper = documentExtractionMapper;
        this.auditApplicationService = auditApplicationService;
        this.objectMapper = objectMapper;
    }

    public DocumentExtractionResponse extractDocument(
        @NotNull UUID documentId,
        @NotBlank String performedBy
    ) {
        MedicalDocument document = findDocumentById(documentId);
        DocumentExtraction extraction = documentExtractionRepository.findByDocumentId(documentId)
            .orElseGet(() -> documentExtractionMapper.createPendingExtraction(document));

        if (extraction.getId() != null && extraction.getExtractionStatus() == ExtractionStatus.COMPLETED) {
            return documentExtractionMapper.toResponse(extraction);
        }

        markExtractionPending(extraction);
        DocumentExtraction pendingExtraction = documentExtractionRepository.save(extraction);
        recordExtractionAudit(documentId, AuditAction.EXTRACTION_STARTED, performedBy, buildStartedDetails(pendingExtraction));

        try {
            Path pdfPath = resolveStoredPdfPath(document.getStoragePath());
            ExtractedPdfContent extractedPdfContent = pdfTextExtractionService.extract(pdfPath);

            pendingExtraction.setExtractedText(extractedPdfContent.extractedText());
            pendingExtraction.setPageCount(extractedPdfContent.pageCount());
            pendingExtraction.setExtractionStatus(ExtractionStatus.COMPLETED);

            DocumentExtraction completedExtraction = documentExtractionRepository.save(pendingExtraction);
            recordExtractionAudit(
                documentId,
                AuditAction.EXTRACTION_COMPLETED,
                performedBy,
                buildCompletedDetails(completedExtraction)
            );

            return documentExtractionMapper.toResponse(completedExtraction);
        } catch (RuntimeException exception) {
            pendingExtraction.setExtractionStatus(ExtractionStatus.FAILED);
            pendingExtraction.setExtractedText(null);
            pendingExtraction.setPageCount(null);
            DocumentExtraction failedExtraction = documentExtractionRepository.save(pendingExtraction);

            recordExtractionAudit(
                documentId,
                AuditAction.EXTRACTION_FAILED,
                performedBy,
                buildFailedDetails(failedExtraction, exception)
            );

            throw new DocumentExtractionProcessingException(documentId, exception);
        }
    }

    @Transactional(readOnly = true)
    public DocumentExtractionResponse getExtraction(@NotNull UUID documentId) {
        findDocumentById(documentId);
        return documentExtractionMapper.toResponse(findExtractionByDocumentId(documentId));
    }

    @Transactional(readOnly = true)
    public DocumentExtractionTextResponse getExtractedText(@NotNull UUID documentId) {
        findDocumentById(documentId);
        DocumentExtraction extraction = findExtractionByDocumentId(documentId);
        if (extraction.getExtractionStatus() != ExtractionStatus.COMPLETED || extraction.getExtractedText() == null) {
            throw new DocumentExtractionNotCompletedException(documentId, extraction.getExtractionStatus());
        }

        return documentExtractionMapper.toTextResponse(extraction);
    }

    private MedicalDocument findDocumentById(UUID documentId) {
        return medicalDocumentRepository.findById(documentId)
            .orElseThrow(() -> new MedicalDocumentNotFoundException(documentId));
    }

    private DocumentExtraction findExtractionByDocumentId(UUID documentId) {
        return documentExtractionRepository.findByDocumentId(documentId)
            .orElseThrow(() -> new DocumentExtractionNotFoundException(documentId));
    }

    private void markExtractionPending(DocumentExtraction extraction) {
        extraction.setExtractionStatus(ExtractionStatus.PENDING);
        extraction.setExtractedText(null);
        extraction.setPageCount(null);
    }

    private Path resolveStoredPdfPath(String storagePath) {
        StoredDocumentResource storedDocumentResource = documentStorageService.load(storagePath);
        try {
            return storedDocumentResource.resource().getFile().toPath();
        } catch (IOException exception) {
            throw new DocumentStorageOperationException("Unable to resolve stored document as a local file", exception);
        }
    }

    private void recordExtractionAudit(UUID documentId, AuditAction action, String performedBy, ObjectNode details) {
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
        details.put("status", extraction.getExtractionStatus().name());
        return details;
    }

    private ObjectNode buildCompletedDetails(DocumentExtraction extraction) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("extractionId", extraction.getId().toString());
        details.put("status", extraction.getExtractionStatus().name());
        if (extraction.getPageCount() != null) {
            details.put("pageCount", extraction.getPageCount());
        }
        return details;
    }

    private ObjectNode buildFailedDetails(DocumentExtraction extraction, RuntimeException exception) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("extractionId", extraction.getId().toString());
        details.put("status", extraction.getExtractionStatus().name());
        details.put("reason", exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        return details;
    }
}
