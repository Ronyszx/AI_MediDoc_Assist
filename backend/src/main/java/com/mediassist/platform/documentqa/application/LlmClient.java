package com.mediassist.platform.documentqa.application;

public interface LlmClient {

    LlmCompletionResponse complete(LlmCompletionRequest request);
}
