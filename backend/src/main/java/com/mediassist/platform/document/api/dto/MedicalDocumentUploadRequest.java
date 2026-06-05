package com.mediassist.platform.document.api.dto;

import com.mediassist.platform.document.domain.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MedicalDocumentUploadRequest(
    @NotNull
    DocumentType documentType,

    LocalDate documentDate,

    @Size(max = 500)
    String description
) {
}
