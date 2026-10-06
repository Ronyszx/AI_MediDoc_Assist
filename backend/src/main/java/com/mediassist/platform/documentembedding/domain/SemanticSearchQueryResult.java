package com.mediassist.platform.documentembedding.domain;

import java.util.List;

public record SemanticSearchQueryResult(
    String query,
    List<Double> queryEmbedding,
    List<SemanticSearchCandidate> candidates
) {
    public SemanticSearchQueryResult {
        queryEmbedding = List.copyOf(queryEmbedding);
        candidates = List.copyOf(candidates);
    }
}
