package com.mediassist.platform.documentchunk.api.dto;

import com.mediassist.platform.documentchunk.domain.ChunkStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentChunkResponse(
    UUID chunkId,
    UUID documentId,
    UUID extractionId,
    Integer chunkIndex,
    ChunkStatus chunkStatus,
    String chunkText,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
