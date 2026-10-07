package com.mediassist.platform.documentqa.domain;

import java.util.List;

public record DocumentEvidenceAssessment(String modelName, List<DocumentEvidenceRating> ratings) {
    public DocumentEvidenceAssessment {
        ratings = List.copyOf(ratings);
    }
}
