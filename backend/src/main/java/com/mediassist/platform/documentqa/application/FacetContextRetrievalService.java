package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQuery;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQueryResult;
import com.mediassist.platform.documentqa.domain.QuestionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FacetContextRetrievalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacetContextRetrievalService.class);
    private final QuestionPlanner questionPlanner;
    private final DocumentEmbeddingApplicationService embeddingApplicationService;
    private final ReciprocalRankFusion rankFusion;
    private final FacetContextSelector selector;
    private final FacetRetrievalSettings settings;

    public FacetContextRetrievalService(QuestionPlanner questionPlanner,
                                      DocumentEmbeddingApplicationService embeddingApplicationService,
                                      ReciprocalRankFusion rankFusion, FacetContextSelector selector,
                                      FacetRetrievalSettings settings) {
        this.questionPlanner = questionPlanner;
        this.embeddingApplicationService = embeddingApplicationService;
        this.rankFusion = rankFusion;
        this.selector = selector;
        this.settings = settings;
    }

    public List<SemanticSearchMatch> retrieve(UUID documentId, String question, int topK) {
        QuestionPlan plan;
        try {
            plan = questionPlanner.plan(question);
        } catch (QuestionPlanningException exception) {
            LOGGER.warn("QA facet planning degraded; using original-query retrieval");
            return embeddingApplicationService.searchSimilarChunks(documentId, question, topK);
        }
        if (plan.facets().size() < 2) {
            return embeddingApplicationService.searchSimilarChunks(documentId, question, topK);
        }
        List<SemanticSearchQuery> queries = new ArrayList<>();
        queries.add(new SemanticSearchQuery(question, settings.getOriginalCandidateCount()));
        plan.facets().forEach(facet -> queries.add(new SemanticSearchQuery(facet.query(), settings.getFacetCandidateCount())));
        List<SemanticSearchQueryResult> batches = embeddingApplicationService.searchSimilarChunkBatches(documentId, queries);
        List<SemanticSearchMatch> matches = selector.select(question, rankFusion.fuse(batches, settings.getFusionConstant()),
            plan.facets().size(), topK);
        LOGGER.info("QA facet retrieval: facets={}, candidateEntries={}, sources={}", plan.facets().size(),
            batches.stream().mapToInt(batch -> batch.candidates().size()).sum(), matches.size());
        return matches;
    }
}
