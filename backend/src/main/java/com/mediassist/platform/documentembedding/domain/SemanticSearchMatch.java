package com.mediassist.platform.documentembedding.domain;

import java.util.UUID;

public record SemanticSearchMatch(
    UUID chunkId,
    Integer chunkIndex,
    String chunkText,
    Double similarityScore,
    String modelName
) {
}
