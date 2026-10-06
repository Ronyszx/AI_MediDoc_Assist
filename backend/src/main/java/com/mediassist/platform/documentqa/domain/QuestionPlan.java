package com.mediassist.platform.documentqa.domain;

import java.util.List;

public record QuestionPlan(String originalQuestion, List<QueryFacet> facets) {
    public QuestionPlan {
        facets = List.copyOf(facets);
    }
}
