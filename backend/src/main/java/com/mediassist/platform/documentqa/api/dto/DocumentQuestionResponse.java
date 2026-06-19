package com.mediassist.platform.documentqa.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentQuestionResponse(
    UUID documentId,
    String question,
    String answer,
    String modelName,
    List<DocumentQuestionSourceResponse> sources,
    LocalDateTime answeredAt
) {
}
