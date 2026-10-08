package com.mediassist.platform.documentqa.domain;

public record DocumentAnswerDraft(
    String content,
    String modelName,
    DocumentAnswerMode mode,
    int evidenceCount,
    boolean contextLimited
) {
}
