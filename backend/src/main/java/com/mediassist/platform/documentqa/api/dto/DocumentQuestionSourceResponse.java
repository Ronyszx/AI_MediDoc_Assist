package com.mediassist.platform.documentqa.api.dto;

import java.util.UUID;

public record DocumentQuestionSourceResponse(
    UUID chunkId,
    Integer chunkIndex,
    Double similarityScore,
    String preview
) {
}
