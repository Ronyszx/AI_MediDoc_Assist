package com.mediassist.platform.documentqa.application;

import java.util.List;

public record LlmCompletionRequest(
    String modelName,
    List<LlmMessage> messages,
    double temperature,
    int maxOutputTokens
) {
}
