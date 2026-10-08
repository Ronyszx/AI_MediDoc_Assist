package com.mediassist.platform.documentembedding.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SemanticSearchCandidateTest {

    @Test
    void shouldKeepAnImmutableCopyOfTheInternalVector() {
        List<Double> vector = new ArrayList<>(List.of(1.0, 0.0));
        SemanticSearchCandidate candidate = new SemanticSearchCandidate(
            new SemanticSearchMatch(UUID.randomUUID(), 0, "Evidence", 0.8, "test-model"), vector
        );
        vector.set(0, 5.0);

        assertThat(candidate.embedding()).containsExactly(1.0, 0.0);
        assertThatThrownBy(() -> candidate.embedding().set(0, 2.0)).isInstanceOf(UnsupportedOperationException.class);
    }
}
