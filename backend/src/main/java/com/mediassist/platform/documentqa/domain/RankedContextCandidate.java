package com.mediassist.platform.documentqa.domain;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import java.util.Map;

public record RankedContextCandidate(
    SemanticSearchMatch match,
    double fusionScore,
    Map<Integer, Integer> queryRanks
) {
    public RankedContextCandidate {
        queryRanks = Map.copyOf(queryRanks);
    }
}
