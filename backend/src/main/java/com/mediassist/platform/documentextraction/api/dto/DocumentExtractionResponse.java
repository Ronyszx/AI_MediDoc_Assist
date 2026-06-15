package com.mediassist.platform.documentextraction.api.dto;

import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentExtractionResponse(
    UUID extractionId,
    UUID documentId,
    ExtractionStatus extractionStatus,
    Integer pageCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
