package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQueryResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {
    private final ReciprocalRankFusion fusion = new ReciprocalRankFusion();

    @Test
    void shouldDeduplicateByIdAndKeepQueryRanksWithoutSemanticDeduplication() {
        var first = candidate(0, List.of(1.0, 0.0));
        var second = candidate(1, List.of(1.0, 0.0));
        var result = fusion.fuse(List.of(batch(List.of(first, second)), batch(List.of(second, first))), 60);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().queryRanks()).containsEntry(0, 1).containsEntry(1, 2);
        assertThat(result.getFirst().fusionScore()).isCloseTo(1.0 / 61 + 1.0 / 62, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void shouldRecomputeOriginalSimilarityForCandidatesFoundOnlyByAFacet() {
        var original = candidate(0, List.of(1.0, 0.0));
        var facetOnly = candidate(1, List.of(0.0, 1.0));
        var result = fusion.fuse(List.of(batch(List.of(original)),
            new SemanticSearchQueryResult("facet", List.of(0.0, 1.0), List.of(facetOnly))), 60);

        assertThat(result.get(1).match().similarityScore()).isZero();
        assertThat(result.get(1).match().chunkText()).isEqualTo(facetOnly.match().chunkText());
        assertThat(result.get(1).queryRanks()).containsOnlyKeys(1);
    }

    @Test
    void shouldNotCountRepeatedIdWithinOneQueryTwice() {
        var candidate = candidate(0, List.of(1.0, 0.0));
        assertThat(fusion.fuse(List.of(batch(List.of(candidate, candidate))), 60).getFirst().fusionScore()).isEqualTo(1.0 / 61);
    }

    @Test
    void shouldRejectInvalidVectorsAndFusionSettings() {
        assertThatThrownBy(() -> fusion.fuse(List.of(), 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fusion.fuse(List.of(batch(List.of())), 0)).isInstanceOf(IllegalArgumentException.class);
        for (List<Double> vector : List.of(List.of(0.0, 0.0), List.of(Double.NaN, 0.0), List.of(1.0))) {
            assertThatThrownBy(() -> fusion.fuse(List.of(batch(List.of(candidate(0, vector)))), 60))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private SemanticSearchQueryResult batch(List<SemanticSearchCandidate> candidates) {
        return new SemanticSearchQueryResult("query", List.of(1.0, 0.0), candidates);
    }

    private SemanticSearchCandidate candidate(int index, List<Double> vector) {
        return new SemanticSearchCandidate(new SemanticSearchMatch(UUID.randomUUID(), index, "Evidence", 0.99, "model"), vector);
    }
}
