package com.mediassist.platform.documentextraction.api.dto;

import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentExtractionTextResponse(
    UUID extractionId,
    UUID documentId,
    ExtractionStatus extractionStatus,
    Integer pageCount,
    String extractedText,
    LocalDateTime updatedAt
) {
}
