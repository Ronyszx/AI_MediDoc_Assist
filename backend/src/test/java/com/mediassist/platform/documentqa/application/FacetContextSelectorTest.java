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

    @Test
    void shouldRepackOnlyWhenTheEvidenceBudgetPreventsFillingTopK() {
        settings.setMaxEvidenceTokens(256);
        var nomination = candidate(0, "n".repeat(80), Map.of(1, 1));
        var longFiller = scoredCandidate(1, "l".repeat(350), 0.2);
        var compactOne = scoredCandidate(2, "a".repeat(160), 0.15);
        var compactTwo = scoredCandidate(3, "b".repeat(160), 0.14);

        assertThat(selector.select("Compare", List.of(nomination, longFiller, compactOne, compactTwo), 1, 3))
            .containsExactly(nomination.match(), compactOne.match(), compactTwo.match());
    }

    @Test
    void shouldPreserveFusionOrderWhenTheRequestedContextAlreadyFits() {
        var high = scoredCandidate(0, "x".repeat(350), 0.2);
        var shortLower = scoredCandidate(1, "Short", 0.1);
        assertThat(selector.select("Question", List.of(high, shortLower), 0, 2))
            .containsExactly(high.match(), shortLower.match());
    }

    @Test
    void shouldKeepFacetNominationsEvenWhenAnotherChunkHasHigherPackingDensity() {
        settings.setMaxEvidenceTokens(256);
        var nomination = candidate(0, "n".repeat(400), Map.of(1, 1));
        var compact = scoredCandidate(1, "a".repeat(160), 1.0);
        assertThat(selector.select("Compare", List.of(nomination, compact), 1, 2))
            .containsExactly(nomination.match());
    }

    private RankedContextCandidate scoredCandidate(int index, String text, double score) {
        var match = new SemanticSearchMatch(UUID.randomUUID(), index, text, 0.7, "model");
        return new RankedContextCandidate(match, score, Map.of(0, index + 1));
    }

    private RankedContextCandidate candidate(int index, String text, Map<Integer, Integer> ranks) {
        return new RankedContextCandidate(new SemanticSearchMatch(UUID.randomUUID(), index, text, 0.7, "model"), 0.1, ranks);
    }
}
