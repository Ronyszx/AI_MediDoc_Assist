package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentQaContextSelector {

    public List<SemanticSearchMatch> select(
        List<SemanticSearchCandidate> candidates,
        int topK,
        double relevanceWeight
    ) {
        if (topK < 1 || !Double.isFinite(relevanceWeight) || relevanceWeight < 0 || relevanceWeight > 1) {
            throw new IllegalArgumentException("Invalid context selection settings");
        }
        List<NormalizedCandidate> remaining = normalizeCandidates(candidates);
        List<NormalizedCandidate> selected = new ArrayList<>();

        while (!remaining.isEmpty() && selected.size() < topK) {
            NormalizedCandidate next = remaining.getFirst();
            double bestScore = selectionScore(next, selected, relevanceWeight);
            for (int index = 1; index < remaining.size(); index++) {
                NormalizedCandidate candidate = remaining.get(index);
                double score = selectionScore(candidate, selected, relevanceWeight);
                if (score > bestScore) {
                    next = candidate;
                    bestScore = score;
                }
            }
            selected.add(next);
            remaining.remove(next);
        }

        return selected.stream().map(NormalizedCandidate::match).toList();
    }

    private List<NormalizedCandidate> normalizeCandidates(List<SemanticSearchCandidate> candidates) {
        Map<UUID, NormalizedCandidate> unique = new LinkedHashMap<>();
        int dimensions = candidates.isEmpty() ? 0 : candidates.getFirst().embedding().size();
        for (SemanticSearchCandidate candidate : candidates) {
            if (candidate.embedding().size() != dimensions) {
                throw new IllegalArgumentException("Candidate embeddings have inconsistent dimensions");
            }
            if (!Double.isFinite(candidate.match().similarityScore())) {
                throw new IllegalArgumentException("Candidate similarity must be finite");
            }
            unique.putIfAbsent(candidate.match().chunkId(), new NormalizedCandidate(
                candidate.match(), normalize(candidate.embedding())
            ));
        }
        List<NormalizedCandidate> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingDouble((NormalizedCandidate candidate) -> candidate.match().similarityScore())
            .reversed()
            .thenComparing(candidate -> candidate.match().chunkIndex())
            .thenComparing(candidate -> candidate.match().chunkId()));
        return result;
    }

    private double[] normalize(List<Double> embedding) {
        double squaredLength = 0;
        for (double value : embedding) {
            squaredLength += value * value;
        }
        if (!Double.isFinite(squaredLength) || squaredLength <= 0) {
            throw new IllegalArgumentException("Candidate embedding must be finite and nonzero");
        }
        double length = Math.sqrt(squaredLength);
        double[] normalized = new double[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            normalized[index] = embedding.get(index) / length;
        }
        return normalized;
    }

    private double selectionScore(
        NormalizedCandidate candidate,
        List<NormalizedCandidate> selected,
        double relevanceWeight
    ) {
        if (selected.isEmpty()) {
            return candidate.match().similarityScore();
        }
        double redundancy = selected.stream()
            .mapToDouble(previous -> cosineSimilarity(candidate.embedding(), previous.embedding()))
            .max()
            .orElse(0);
        return relevanceWeight * candidate.match().similarityScore() - (1 - relevanceWeight) * redundancy;
    }

    private double cosineSimilarity(double[] left, double[] right) {
        double similarity = 0;
        for (int index = 0; index < left.length; index++) {
            similarity += left[index] * right[index];
        }
        return Math.max(-1, Math.min(1, similarity));
    }

    private record NormalizedCandidate(SemanticSearchMatch match, double[] embedding) {
    }
}
