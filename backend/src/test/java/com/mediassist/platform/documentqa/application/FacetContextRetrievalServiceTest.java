package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.application.DocumentEmbeddingsNotFoundException;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQuery;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.QueryFacet;
import com.mediassist.platform.documentqa.domain.QuestionPlan;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacetContextRetrievalServiceTest {
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String QUESTION = "Compare diagnoses and investigations";
    @Mock private QuestionPlanner planner;
    @Mock private DocumentEmbeddingApplicationService embeddings;
    @Mock private ReciprocalRankFusion fusion;
    @Mock private FacetContextSelector selector;
    private FacetContextRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new FacetContextRetrievalService(planner, embeddings, fusion, selector, new FacetRetrievalProperties());
    }

    @Test
    void shouldKeepFocusedQuestionsOnOriginalRetrieval() {
        when(planner.plan(QUESTION)).thenReturn(new QuestionPlan(QUESTION, List.of()));
        var match = match();
        when(embeddings.searchSimilarChunks(DOCUMENT_ID, QUESTION, 5)).thenReturn(List.of(match));
        assertThat(service.retrieve(DOCUMENT_ID, QUESTION, 5)).containsExactly(match);
        verifyNoInteractions(fusion, selector);
    }

    @Test
    void shouldFallBackOnlyForPlanningFailures() {
        when(planner.plan(QUESTION)).thenThrow(new QuestionPlanningException("Invalid plan"));
        service.retrieve(DOCUMENT_ID, QUESTION, 5);
        verify(embeddings).searchSimilarChunks(DOCUMENT_ID, QUESTION, 5);
        verifyNoInteractions(fusion, selector);
    }

    @Test
    void shouldBatchOriginalAndFacetQueriesInOrderAndRetainDocumentScope() {
        when(planner.plan(QUESTION)).thenReturn(new QuestionPlan(QUESTION, List.of(
            new QueryFacet("Diagnosis", "Recorded diagnoses"), new QueryFacet("Investigation", "Pending investigations"))));
        when(embeddings.searchSimilarChunkBatches(eq(DOCUMENT_ID), any())).thenReturn(List.of());
        when(fusion.fuse(List.of(), 60)).thenReturn(List.of());
        when(selector.select(QUESTION, List.of(), 2, 5)).thenReturn(List.of(match()));

        assertThat(service.retrieve(DOCUMENT_ID, QUESTION, 5)).hasSize(1);
        verify(embeddings).searchSimilarChunkBatches(DOCUMENT_ID, List.of(
            new SemanticSearchQuery(QUESTION, 20), new SemanticSearchQuery("Recorded diagnoses", 10),
            new SemanticSearchQuery("Pending investigations", 10)));
    }

    @Test
    void shouldPropagateEmbeddingFailuresRatherThanMislabelAsPlanningDegradation() {
        when(planner.plan(QUESTION)).thenReturn(new QuestionPlan(QUESTION, List.of(
            new QueryFacet("Diagnosis", "Recorded diagnoses"), new QueryFacet("Investigation", "Pending investigations"))));
        var failure = new DocumentEmbeddingsNotFoundException(DOCUMENT_ID, "model");
        when(embeddings.searchSimilarChunkBatches(eq(DOCUMENT_ID), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.retrieve(DOCUMENT_ID, QUESTION, 5)).isSameAs(failure);
        verifyNoInteractions(fusion, selector);
    }

    private SemanticSearchMatch match() {
        return new SemanticSearchMatch(UUID.randomUUID(), 0, "Evidence", 0.7, "model");
    }

    @Test
    void shouldNotRewriteFocusedQuestionsWhenPlannerReturnsOneFacet() {
        when(planner.plan(QUESTION)).thenReturn(new QuestionPlan(QUESTION, List.of(new QueryFacet("Diagnosis", "Recorded diagnoses"))));
        service.retrieve(DOCUMENT_ID, QUESTION, 5);
        verify(embeddings).searchSimilarChunks(DOCUMENT_ID, QUESTION, 5);
        verifyNoInteractions(fusion, selector);
    }
}
