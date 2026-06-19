package com.mediassist.platform.documentqa.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentQuestionAnswer(
    UUID documentId,
    String question,
    String answer,
    String modelName,
    List<DocumentQuestionSource> sources,
    LocalDateTime answeredAt
) {
}
