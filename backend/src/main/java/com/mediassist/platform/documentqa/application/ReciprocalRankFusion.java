package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQueryResult;
import com.mediassist.platform.documentqa.domain.RankedContextCandidate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReciprocalRankFusion {
    public List<RankedContextCandidate> fuse(List<SemanticSearchQueryResult> batches, int fusionConstant) {
        if (batches.isEmpty() || fusionConstant < 1) {
            throw new IllegalArgumentException("Rank fusion requires an original query and a positive constant");
        }
        Map<UUID, SemanticSearchCandidate> candidates = new LinkedHashMap<>();
        Map<UUID, Map<Integer, Integer>> ranks = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < batches.size(); queryIndex++) {
            List<SemanticSearchCandidate> matches = batches.get(queryIndex).candidates();
            for (int index = 0; index < matches.size(); index++) {
                SemanticSearchCandidate candidate = matches.get(index);
                UUID id = candidate.match().chunkId();
                candidates.putIfAbsent(id, candidate);
                ranks.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).putIfAbsent(queryIndex, index + 1);
            }
        }
        List<Double> originalVector = batches.getFirst().queryEmbedding();
        return candidates.entrySet().stream().map(entry -> {
            SemanticSearchMatch source = entry.getValue().match();
            SemanticSearchMatch originalMatch = new SemanticSearchMatch(source.chunkId(), source.chunkIndex(), source.chunkText(),
                cosine(originalVector, entry.getValue().embedding()), source.modelName());
            Map<Integer, Integer> queryRanks = ranks.get(entry.getKey());
            double score = queryRanks.values().stream().mapToDouble(rank -> 1.0 / (fusionConstant + rank)).sum();
            return new RankedContextCandidate(originalMatch, score, queryRanks);
        }).sorted(Comparator.comparingDouble(RankedContextCandidate::fusionScore).reversed()
            .thenComparing(candidate -> candidate.match().chunkIndex())
            .thenComparing(candidate -> candidate.match().chunkId())).toList();
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || left.size() != right.size()) {
            throw new IllegalArgumentException("Query and candidate vector dimensions must match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (!Double.isFinite(dot) || !Double.isFinite(leftNorm) || !Double.isFinite(rightNorm)
            || leftNorm <= 0 || rightNorm <= 0) {
            throw new IllegalArgumentException("Vectors must be finite and nonzero");
        }
        return Math.clamp(dot / Math.sqrt(leftNorm * rightNorm), -1, 1);
    }
}
