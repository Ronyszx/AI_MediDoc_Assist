package com.mediassist.platform.documentembedding.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentChunkEmbeddingMetadataResponse(
    UUID embeddingId,
    UUID chunkId,
    Integer chunkIndex,
    String modelName,
    Integer embeddingDimension,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
