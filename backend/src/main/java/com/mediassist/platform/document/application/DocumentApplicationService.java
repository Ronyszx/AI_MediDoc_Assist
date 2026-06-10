package com.mediassist.platform.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.document.api.dto.MedicalDocumentResponse;
import com.mediassist.platform.document.api.dto.MedicalDocumentUploadRequest;
import com.mediassist.platform.document.application.storage.DocumentStorageException;
import com.mediassist.platform.document.application.storage.DocumentStorageService;
import com.mediassist.platform.document.application.storage.StoredDocumentResource;
import com.mediassist.platform.document.domain.DocumentStatus;
import com.mediassist.platform.document.domain.DocumentType;
import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.patient.application.PatientNotFoundException;
import com.mediassist.platform.patient.domain.Patient;
import com.mediassist.platform.patient.domain.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@Transactional
public class DocumentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApplicationService.class);

    private final MedicalDocumentRepository medicalDocumentRepository;
    private final PatientRepository patientRepository;
    private final MedicalDocumentMapper medicalDocumentMapper;
    private final AuditApplicationService auditApplicationService;
    private final DocumentStorageService documentStorageService;
    private final ObjectMapper objectMapper;

    public DocumentApplicationService(
        MedicalDocumentRepository medicalDocumentRepository,
        PatientRepository patientRepository,
        MedicalDocumentMapper medicalDocumentMapper,
        AuditApplicationService auditApplicationService,
        DocumentStorageService documentStorageService,
        ObjectMapper objectMapper
    ) {
        this.medicalDocumentRepository = medicalDocumentRepository;
        this.patientRepository = patientRepository;
        this.medicalDocumentMapper = medicalDocumentMapper;
        this.auditApplicationService = auditApplicationService;
        this.documentStorageService = documentStorageService;
        this.objectMapper = objectMapper;
    }

    public MedicalDocumentResponse uploadDocument(
        @NotNull UUID patientId,
        @NotNull DocumentType documentType,
        @NotNull MultipartFile file,
        @NotBlank String performedBy
    ) {
        DocumentStorageDetails storageDetails = documentStorageService.store(file);

        try {
            return createDocumentMetadata(
                patientId,
                new MedicalDocumentUploadRequest(documentType, null, null),
                storageDetails,
                performedBy
            );
        } catch (RuntimeException exception) {
            cleanupStoredDocument(storageDetails.storagePath(), exception);
            throw exception;
        }
    }

    public MedicalDocumentResponse createDocumentMetadata(
        @NotNull UUID patientId,
        @NotNull @Valid MedicalDocumentUploadRequest request,
        @NotNull @Valid DocumentStorageDetails storageDetails,
        @NotBlank String performedBy
    ) {
        Patient patient = findPatientById(patientId);
        validateStoragePathIsAvailable(storageDetails.storagePath());

        MedicalDocument medicalDocument = medicalDocumentMapper.toNewMedicalDocument(
            patient,
            request,
            storageDetails
        );
        medicalDocument.setUploadedAt(LocalDateTime.now());

        MedicalDocument savedDocument = medicalDocumentRepository.save(medicalDocument);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            savedDocument.getId(),
            AuditAction.UPLOADED,
            performedBy,
            buildDocumentCreatedDetails(savedDocument)
        ));

        return medicalDocumentMapper.toResponse(savedDocument);
    }

    @Transactional(readOnly = true)
    public MedicalDocumentResponse getDocumentById(@NotNull UUID documentId) {
        return medicalDocumentMapper.toResponse(findDocumentById(documentId));
    }

    @Transactional(readOnly = true)
    public DownloadedDocument downloadDocument(@NotNull UUID documentId) {
        MedicalDocument medicalDocument = findDocumentById(documentId);
        StoredDocumentResource storedDocumentResource = documentStorageService.load(medicalDocument.getStoragePath());

        return new DownloadedDocument(
            medicalDocument.getOriginalFileName(),
            medicalDocument.getMimeType(),
            storedDocumentResource.contentLength(),
            storedDocumentResource.resource()
        );
    }

    @Transactional(readOnly = true)
    public List<MedicalDocumentResponse> listDocumentsForPatient(@NotNull UUID patientId) {
        findPatientById(patientId);

        return medicalDocumentRepository.findAllByPatientIdOrderByUploadedAtDesc(patientId).stream()
            .map(medicalDocumentMapper::toResponse)
            .toList();
    }

    public MedicalDocumentResponse changeDocumentStatus(
        @NotNull UUID documentId,
        @NotNull DocumentStatus status,
        @NotBlank String performedBy
    ) {
        MedicalDocument medicalDocument = findDocumentById(documentId);
        DocumentStatus previousStatus = medicalDocument.getStatus();

        if (previousStatus == status) {
            return medicalDocumentMapper.toResponse(medicalDocument);
        }

        medicalDocument.setStatus(status);
        MedicalDocument savedDocument = medicalDocumentRepository.save(medicalDocument);

        auditApplicationService.recordAuditEvent(new AuditEventCreateRequest(
            AuditEntityType.MEDICAL_DOCUMENT,
            savedDocument.getId(),
            resolveStatusChangeAction(status),
            performedBy,
            buildDocumentStatusChangedDetails(savedDocument, previousStatus)
        ));

        return medicalDocumentMapper.toResponse(savedDocument);
    }

    private Patient findPatientById(UUID patientId) {
        return patientRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    private MedicalDocument findDocumentById(UUID documentId) {
        return medicalDocumentRepository.findById(documentId)
            .orElseThrow(() -> new MedicalDocumentNotFoundException(documentId));
    }

    private void validateStoragePathIsAvailable(String storagePath) {
        if (medicalDocumentRepository.existsByStoragePath(storagePath.trim())) {
            throw new DuplicateDocumentStoragePathException(storagePath);
        }
    }

    private AuditAction resolveStatusChangeAction(DocumentStatus status) {
        if (status == DocumentStatus.ARCHIVED) {
            return AuditAction.ARCHIVED;
        }

        return AuditAction.STATUS_CHANGED;
    }

    private void cleanupStoredDocument(String storagePath, RuntimeException originalException) {
        try {
            documentStorageService.delete(storagePath);
        } catch (DocumentStorageException cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.warn("Failed to clean up stored document at path {}", storagePath, cleanupException);
        }
    }

    private ObjectNode buildDocumentCreatedDetails(MedicalDocument medicalDocument) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("patientId", medicalDocument.getPatient().getId().toString());
        details.put("documentType", medicalDocument.getDocumentType().name());
        details.put("originalFileName", medicalDocument.getOriginalFileName());
        return details;
    }

    private ObjectNode buildDocumentStatusChangedDetails(
        MedicalDocument medicalDocument,
        DocumentStatus previousStatus
    ) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("patientId", medicalDocument.getPatient().getId().toString());
        details.put("fromStatus", previousStatus.name());
        details.put("toStatus", medicalDocument.getStatus().name());
        return details;
    }
}
