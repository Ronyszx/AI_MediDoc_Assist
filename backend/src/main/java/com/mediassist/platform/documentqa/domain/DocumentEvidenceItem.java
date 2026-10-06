package com.mediassist.platform.documentqa.domain;

import java.util.UUID;

public record DocumentEvidenceItem(
    String passageId,
    UUID chunkId,
    int chunkIndex,
    int citationNumber,
    int startOffset,
    int endOffset,
    String quote
) {
}
