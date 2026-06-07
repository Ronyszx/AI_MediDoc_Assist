package com.mediassist.platform.document.api.dto;

import com.mediassist.platform.document.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateMedicalDocumentRequest(
    @NotNull
    DocumentType documentType,

    LocalDate documentDate,

    @Size(max = 500)
    String description,

    @NotBlank
    @Size(max = 255)
    String originalFileName,

    @NotBlank
    @Size(max = 255)
    String storedFileName,

    @NotBlank
    @Size(max = 120)
    String mimeType,

    @Positive
    long fileSizeBytes,

    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{64}$")
    String checksumSha256,

    @NotBlank
    @Size(max = 500)
    String storagePath
) {
}
