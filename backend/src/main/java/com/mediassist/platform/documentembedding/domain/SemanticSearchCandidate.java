package com.mediassist.platform.documentembedding.domain;

import java.util.List;

public record SemanticSearchCandidate(SemanticSearchMatch match, List<Double> embedding) {

    public SemanticSearchCandidate {
        embedding = List.copyOf(embedding);
    }
}
