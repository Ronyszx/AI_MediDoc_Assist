package com.mediassist.platform.documentembedding.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentEmbeddingSummaryResponse(
    UUID documentId,
    String modelName,
    Integer embeddingDimension,
    Integer totalChunks,
    Integer embeddedChunks,
    Integer skippedChunks,
    LocalDateTime processedAt
) {
}
