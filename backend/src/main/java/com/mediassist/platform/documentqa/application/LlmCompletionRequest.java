package com.mediassist.platform.documentqa.application;

import java.util.List;
import java.util.Map;

public record LlmCompletionRequest(
    String modelName,
    List<LlmMessage> messages,
    double temperature,
    int maxOutputTokens,
    LlmResponseFormat responseFormat,
    Map<String, Object> responseSchema
) {
    public LlmCompletionRequest {
        messages = List.copyOf(messages);
        if (responseSchema != null && responseFormat != LlmResponseFormat.JSON) {
            throw new IllegalArgumentException("A response schema requires JSON output");
        }
        responseSchema = responseSchema == null ? null : Map.copyOf(responseSchema);
    }

    public LlmCompletionRequest(String modelName, List<LlmMessage> messages, double temperature, int maxOutputTokens,
                                LlmResponseFormat responseFormat) {
        this(modelName, messages, temperature, maxOutputTokens, responseFormat, null);
    }

    public LlmCompletionRequest(String modelName, List<LlmMessage> messages, double temperature, int maxOutputTokens) {
        this(modelName, messages, temperature, maxOutputTokens, LlmResponseFormat.TEXT, null);
    }
}
