package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentQaContextSelectorTest {

    private final DocumentQaContextSelector selector = new DocumentQaContextSelector();

    @Test
    void shouldPreferRelevantDistinctEvidenceOverARepeatedPassage() {
        SemanticSearchCandidate diagnosis = candidate(0, 0.9, List.of(1.0, 0.0));
        SemanticSearchCandidate repetition = candidate(1, 0.89, List.of(1.0, 0.0));
        SemanticSearchCandidate investigation = candidate(2, 0.75, List.of(0.0, 1.0));

        assertThat(selector.select(List.of(diagnosis, repetition, investigation), 2, 0.7))
            .containsExactly(diagnosis.match(), investigation.match());
    }

    @Test
    void shouldPreservePureRelevanceOrderWhenWeightIsOne() {
        SemanticSearchCandidate first = candidate(0, 0.9, List.of(1.0, 0.0));
        SemanticSearchCandidate second = candidate(1, 0.8, List.of(1.0, 0.0));
        SemanticSearchCandidate third = candidate(2, 0.7, List.of(0.0, 1.0));

        assertThat(selector.select(List.of(third, first, second), 2, 1.0))
            .containsExactly(first.match(), second.match());
    }

    @Test
    void shouldKeepBothSidesOfAConflictWhenTheyAreTheRelevantAvailableEvidence() {
        SemanticSearchCandidate first = candidate(0, 0.9, List.of(1.0, 0.0));
        SemanticSearchCandidate conflicting = candidate(1, 0.85, List.of(1.0, 0.0));

        assertThat(selector.select(List.of(first, conflicting), 5, 0.7))
            .containsExactly(first.match(), conflicting.match());
    }

    @Test
    void shouldLeaveOriginalMatchTextAndSimilarityScoresUnchanged() {
        SemanticSearchCandidate first = candidate(0, 0.9, List.of(10.0, 0.0));
        SemanticSearchCandidate distinct = candidate(1, 0.7, List.of(0.0, 20.0));

        assertThat(selector.select(List.of(distinct, first), 2, 0.7))
            .containsExactly(first.match(), distinct.match());
        assertThat(first.embedding()).containsExactly(10.0, 0.0);
    }

    @Test
    void shouldBreakTiesDeterministicallyUsingChunkOrder() {
        SemanticSearchCandidate first = candidate(0, 0.8, List.of(1.0, 0.0));
        SemanticSearchCandidate second = candidate(1, 0.8, List.of(0.0, 1.0));

        assertThat(selector.select(List.of(second, first), 1, 0.7)).containsExactly(first.match());
    }

    @Test
    void shouldNeverSelectTheSameChunkTwice() {
        SemanticSearchCandidate first = candidate(0, 0.8, List.of(1.0, 0.0));

        assertThat(selector.select(List.of(first, first), 5, 0.7)).containsExactly(first.match());
    }

    @Test
    void shouldHandleAnEmptyCandidatePool() {
        assertThat(selector.select(List.of(), 5, 0.7)).isEmpty();
    }

    @Test
    void shouldRejectInvalidWeightsAndLimits() {
        assertThatThrownBy(() -> selector.select(List.of(), 0, 0.7)).isInstanceOf(IllegalArgumentException.class);
        for (double weight : List.of(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThatThrownBy(() -> selector.select(List.of(), 5, weight)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldRejectInvalidOrInconsistentVectors() {
        SemanticSearchCandidate valid = candidate(0, 0.8, List.of(1.0, 0.0));
        for (List<Double> vector : List.of(List.<Double>of(), List.of(0.0, 0.0),
            List.of(Double.NaN, 1.0), List.of(Double.POSITIVE_INFINITY, 1.0))) {
            SemanticSearchCandidate invalid = candidate(1, 0.7, vector);
            assertThatThrownBy(() -> selector.select(List.of(invalid), 5, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> selector.select(List.of(valid, candidate(1, 0.7, List.of(1.0))), 5, 0.7))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selector.select(List.of(candidate(0, Double.NaN, List.of(1.0))), 5, 0.7))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private SemanticSearchCandidate candidate(int index, double similarity, List<Double> vector) {
        return new SemanticSearchCandidate(
            new SemanticSearchMatch(new UUID(0, index + 1), index, "Evidence " + index, similarity, "test-model"), vector
        );
    }
}
