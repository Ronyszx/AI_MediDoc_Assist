package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class DocumentQaContextRetrievalService {

    private final DocumentEmbeddingApplicationService embeddingApplicationService;
    private final DocumentQaContextSelector contextSelector;
    private final DocumentQaRetrievalSettings retrievalSettings;
    private final FacetContextRetrievalService facetRetrievalService;
    private final FacetRetrievalSettings facetSettings;

    public DocumentQaContextRetrievalService(
        DocumentEmbeddingApplicationService embeddingApplicationService,
        DocumentQaContextSelector contextSelector,
        DocumentQaRetrievalSettings retrievalSettings,
        FacetContextRetrievalService facetRetrievalService,
        FacetRetrievalSettings facetSettings
    ) {
        this.embeddingApplicationService = embeddingApplicationService;
        this.contextSelector = contextSelector;
        this.retrievalSettings = retrievalSettings;
        this.facetRetrievalService = facetRetrievalService;
        this.facetSettings = facetSettings;
    }

    @Transactional(readOnly = true)
    public List<SemanticSearchMatch> retrieveContext(
        @NotNull UUID documentId,
        @NotBlank String question,
        @Min(1) @Max(10) int topK
    ) {
        if (facetSettings.isEnabled()) {
            return facetRetrievalService.retrieve(documentId, question, topK);
        }
        if (!retrievalSettings.isDiversityEnabled()) {
            return embeddingApplicationService.searchSimilarChunks(documentId, question, topK);
        }

        List<SemanticSearchCandidate> candidates = embeddingApplicationService.searchSimilarChunkCandidates(
            documentId, question, Math.max(topK, retrievalSettings.getCandidateCount())
        );
        return contextSelector.select(candidates, topK, retrievalSettings.getRelevanceWeight());
    }
}
