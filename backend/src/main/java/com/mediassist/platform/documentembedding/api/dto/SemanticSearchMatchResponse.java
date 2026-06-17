package com.mediassist.platform.documentembedding.api.dto;

import java.util.UUID;

public record SemanticSearchMatchResponse(
    UUID chunkId,
    Integer chunkIndex,
    String chunkText,
    Double similarityScore,
    String modelName
) {
}
