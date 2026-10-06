package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.RankedContextCandidate;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FacetContextSelectorTest {
    private final FacetRetrievalProperties settings = new FacetRetrievalProperties();
    private final DocumentQaProperties llmSettings = new DocumentQaProperties();
    private final FacetContextSelector selector = new FacetContextSelector(new DocumentQaPromptBuilder(), llmSettings, settings);

    @Test
    void shouldGiveFacetsAnOpportunityBeforeFillingByFusionRank() {
        var general = candidate(0, "General", Map.of(0, 1));
        var diagnosis = candidate(1, "Recorded assessment", Map.of(1, 1));
        var investigation = candidate(2, "Pending investigation", Map.of(2, 1));

        assertThat(selector.select("Compare", List.of(general, diagnosis, investigation), 2, 2))
            .containsExactly(diagnosis.match(), investigation.match());
    }

    @Test
    void shouldAllowOneChunkToNominateMultipleFacetsAndKeepDistinctIds() {
        var shared = candidate(0, "Same text", Map.of(1, 1, 2, 1));
        var other = candidate(1, "Same text", Map.of(0, 1));
        assertThat(selector.select("Compare", List.of(shared, other), 2, 5)).containsExactly(shared.match(), other.match());
    }

    @Test
    void shouldNotExceedTopKOrInventChunksForMissingFacets() {
        var only = candidate(0, "Evidence", Map.of(1, 1));
        assertThat(selector.select("Compare", List.of(only), 6, 1)).containsExactly(only.match());
        assertThat(selector.select("Compare", List.of(), 6, 5)).isEmpty();
    }

    @Test
    void shouldSkipOversizedChunksWithoutTruncatingAndContinueWithSmallerEvidence() {
        settings.setMaxEvidenceTokens(256);
        var large = candidate(0, "x".repeat(600), Map.of(1, 1));
        var small = candidate(1, "Short evidence", Map.of(1, 2));
        assertThat(selector.select("Compare", List.of(large, small), 1, 5)).containsExactly(small.match());
    }

    @Test
    void shouldReservePromptAndOutputSpaceAndRejectInvalidTopK() {
        llmSettings.setContextWindowTokens(1024);
        assertThat(selector.select("Compare", List.of(candidate(0, "Evidence", Map.of(1, 1))), 1, 5)).isEmpty();
        assertThatThrownBy(() -> selector.select("Compare", List.of(), 0, 11)).isInstanceOf(IllegalArgumentException.class);
    }

    private RankedContextCandidate candidate(int index, String text, Map<Integer, Integer> ranks) {
        return new RankedContextCandidate(new SemanticSearchMatch(UUID.randomUUID(), index, text, 0.7, "model"), 0.1, ranks);
    }
}
