package com.mediassist.platform.document.api.dto;

import com.mediassist.platform.document.domain.DocumentStatus;
import com.mediassist.platform.document.domain.DocumentType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicalDocumentResponse(
    UUID id,
    UUID patientId,
    String originalFileName,
    String mimeType,
    long fileSizeBytes,
    DocumentType documentType,
    DocumentStatus status,
    LocalDate documentDate,
    String description,
    OffsetDateTime uploadedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
