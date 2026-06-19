package com.mediassist.platform.documentqa.application;

public record LlmMessage(
    String role,
    String content
) {
}
