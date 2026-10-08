package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.RankedContextCandidate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FacetContextSelector {
    private final DocumentQaPromptBuilder promptBuilder;
    private final LlmSettings llmSettings;
    private final FacetRetrievalSettings settings;

    public FacetContextSelector(DocumentQaPromptBuilder promptBuilder, LlmSettings llmSettings, FacetRetrievalSettings settings) {
        this.promptBuilder = promptBuilder;
        this.llmSettings = llmSettings;
        this.settings = settings;
    }

    public List<SemanticSearchMatch> select(String question, List<RankedContextCandidate> candidates, int facetCount, int topK) {
        if (topK < 1 || topK > 10) {
            throw new IllegalArgumentException("Context count must be between 1 and 10");
        }
        List<SemanticSearchMatch> selected = new ArrayList<>();
        for (int facet = 1; facet <= facetCount && selected.size() < topK; facet++) {
            int queryIndex = facet;
            List<RankedContextCandidate> nominees = candidates.stream()
                .filter(candidate -> candidate.queryRanks().getOrDefault(queryIndex, Integer.MAX_VALUE) <= settings.getNominationRankLimit())
                .sorted(Comparator.comparingInt(candidate -> candidate.queryRanks().get(queryIndex)))
                .toList();
            if (nominees.stream().anyMatch(candidate -> selected.contains(candidate.match()))) {
                continue;
            }
            for (RankedContextCandidate nominee : nominees) {
                if (fits(question, selected, nominee.match())) {
                    selected.add(nominee.match());
                    break;
                }
            }
        }
        List<SemanticSearchMatch> nominations = List.copyOf(selected);
        fill(question, candidates, selected, topK);
        if (selected.size() < Math.min(topK, candidates.size())) {
            List<SemanticSearchMatch> packed = new ArrayList<>(nominations);
            var compactCandidates = candidates.stream()
                .sorted(Comparator.comparingDouble(this::fusionScorePerToken).reversed()
                    .thenComparing(Comparator.comparingDouble(RankedContextCandidate::fusionScore).reversed())
                    .thenComparing(candidate -> candidate.match().chunkIndex())
                    .thenComparing(candidate -> candidate.match().chunkId()))
                .toList();
            fill(question, compactCandidates, packed, topK);
            if (packed.size() >= selected.size()) {
                return List.copyOf(packed);
            }
        }
        return List.copyOf(selected);
    }

    private void fill(String question, List<RankedContextCandidate> candidates, List<SemanticSearchMatch> selected, int topK) {
        for (RankedContextCandidate candidate : candidates) {
            if (selected.size() >= topK) {
                break;
            }
            if (!selected.contains(candidate.match()) && fits(question, selected, candidate.match())) {
                selected.add(candidate.match());
            }
        }
    }

    private double fusionScorePerToken(RankedContextCandidate candidate) {
        // A packing heuristic for ranked context, not a measure of clinical importance.
        return candidate.fusionScore() / Math.max(1, estimateTokens(candidate.match().chunkText()));
    }

    private boolean fits(String question, List<SemanticSearchMatch> selected, SemanticSearchMatch next) {
        List<SemanticSearchMatch> proposed = new ArrayList<>(selected);
        proposed.add(next);
        int evidenceTokens = proposed.stream().mapToInt(match -> estimateTokens(match.chunkText())).sum();
        int promptTokens = promptBuilder.buildMessages(question, proposed).stream()
            .mapToInt(message -> estimateTokens(message.content()) + 64).sum();
        return evidenceTokens <= settings.getMaxEvidenceTokens()
            && promptTokens + llmSettings.getMaxOutputTokens() + 256 <= llmSettings.getContextWindowTokens();
    }

    // A conservative local estimate, not the provider's tokenizer. Calibrate before enabling by default.
    private int estimateTokens(String text) {
        return (text.getBytes(StandardCharsets.UTF_8).length + 1) / 2;
    }
}
