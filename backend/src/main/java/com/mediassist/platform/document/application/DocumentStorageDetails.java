package com.mediassist.platform.document.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DocumentStorageDetails(
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
