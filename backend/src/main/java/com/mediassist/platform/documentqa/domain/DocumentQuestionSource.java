package com.mediassist.platform.documentqa.domain;

import java.util.UUID;

public record DocumentQuestionSource(
    UUID chunkId,
    Integer chunkIndex,
    Double similarityScore,
    String preview
) {
}
