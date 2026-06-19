package com.mediassist.platform.documentqa.application;

public record LlmCompletionResponse(
    String modelName,
    String content
) {
}
