package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.application.DocumentEmbeddingsNotFoundException;
import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.infrastructure.retrieval.DocumentQaRetrievalProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentQaContextRetrievalServiceTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String QUESTION = "Which conditions were recorded?";

    @Mock
    private DocumentEmbeddingApplicationService embeddingApplicationService;
    @Mock
    private DocumentQaContextSelector selector;
    @Mock
    private FacetContextRetrievalService facetService;

    private final DocumentQaRetrievalProperties properties = new DocumentQaRetrievalProperties();
    private final FacetRetrievalProperties facetProperties = new FacetRetrievalProperties();
    private DocumentQaContextRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new DocumentQaContextRetrievalService(embeddingApplicationService, selector, properties, facetService, facetProperties);
    }

    @Test
    void shouldUseExistingRetrievalWhenDiversityIsDisabled() {
        List<SemanticSearchMatch> matches = List.of(match());
        when(embeddingApplicationService.searchSimilarChunks(DOCUMENT_ID, QUESTION, 5)).thenReturn(matches);

        assertThat(service.retrieveContext(DOCUMENT_ID, QUESTION, 5)).isSameAs(matches);
        verifyNoInteractions(selector);
    }

    @Test
    void shouldRetrieveMoreCandidatesButSelectOnlyTheRequestedContextCount() {
        properties.setDiversityEnabled(true);
        properties.setCandidateCount(20);
        properties.setRelevanceWeight(0.7);
        SemanticSearchMatch match = match();
        List<SemanticSearchCandidate> candidates = List.of(new SemanticSearchCandidate(match, List.of(1.0)));
        when(embeddingApplicationService.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20)).thenReturn(candidates);
        when(selector.select(candidates, 5, 0.7)).thenReturn(List.of(match));

        assertThat(service.retrieveContext(DOCUMENT_ID, QUESTION, 5)).containsExactly(match);
        verify(embeddingApplicationService).searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20);
        verify(selector).select(candidates, 5, 0.7);
    }

    @Test
    void shouldPreserveMissingEmbeddingErrors() {
        properties.setDiversityEnabled(true);
        DocumentEmbeddingsNotFoundException failure = new DocumentEmbeddingsNotFoundException(DOCUMENT_ID, "test-model");
        when(embeddingApplicationService.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20)).thenThrow(failure);

        assertThatThrownBy(() -> service.retrieveContext(DOCUMENT_ID, QUESTION, 5)).isSameAs(failure);
        verifyNoInteractions(selector);
    }

    private SemanticSearchMatch match() {
        return new SemanticSearchMatch(UUID.randomUUID(), 0, "Recorded evidence", 0.8, "test-model");
    }

    @Test
    void shouldUseFacetsOnlyWhenExplicitlyEnabledAndNotCombineWithMmr() {
        facetProperties.setEnabled(true);
        properties.setDiversityEnabled(true);
        SemanticSearchMatch match = match();
        when(facetService.retrieve(DOCUMENT_ID, QUESTION, 5)).thenReturn(List.of(match));

        assertThat(service.retrieveContext(DOCUMENT_ID, QUESTION, 5)).containsExactly(match);
        verifyNoInteractions(embeddingApplicationService, selector);
    }
}
