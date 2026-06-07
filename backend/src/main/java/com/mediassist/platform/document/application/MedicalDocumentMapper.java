package com.mediassist.platform.document.application;

import com.mediassist.platform.document.api.dto.MedicalDocumentResponse;
import com.mediassist.platform.document.api.dto.MedicalDocumentUploadRequest;
import com.mediassist.platform.document.domain.DocumentStatus;
import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.patient.domain.Patient;
import org.springframework.stereotype.Component;

@Component
public class MedicalDocumentMapper {

    public MedicalDocument toNewMedicalDocument(
        Patient patient,
        MedicalDocumentUploadRequest request,
        DocumentStorageDetails storageDetails
    ) {
        MedicalDocument medicalDocument = new MedicalDocument();
        medicalDocument.setPatient(patient);
        medicalDocument.setOriginalFileName(normalizeRequired(storageDetails.originalFileName()));
        medicalDocument.setStoredFileName(normalizeRequired(storageDetails.storedFileName()));
        medicalDocument.setMimeType(normalizeRequired(storageDetails.mimeType()));
        medicalDocument.setFileSizeBytes(storageDetails.fileSizeBytes());
        medicalDocument.setChecksumSha256(normalizeRequired(storageDetails.checksumSha256()));
        medicalDocument.setStoragePath(normalizeRequired(storageDetails.storagePath()));
        medicalDocument.setDocumentType(request.documentType());
        medicalDocument.setStatus(DocumentStatus.UPLOADED);
        medicalDocument.setDocumentDate(request.documentDate());
        medicalDocument.setDescription(normalizeOptional(request.description()));
        return medicalDocument;
    }

    public MedicalDocumentResponse toResponse(MedicalDocument medicalDocument) {
        return new MedicalDocumentResponse(
            medicalDocument.getId(),
            medicalDocument.getPatient().getId(),
            medicalDocument.getOriginalFileName(),
            medicalDocument.getMimeType(),
            medicalDocument.getFileSizeBytes(),
            medicalDocument.getDocumentType(),
            medicalDocument.getStatus(),
            medicalDocument.getDocumentDate(),
            medicalDocument.getDescription(),
            medicalDocument.getUploadedAt(),
            medicalDocument.getCreatedAt(),
            medicalDocument.getUpdatedAt()
        );
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
