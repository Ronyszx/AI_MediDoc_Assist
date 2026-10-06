package com.mediassist.platform.documentqa.infrastructure.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.QuestionPlanningException;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class LlmQuestionPlannerTest {
    @Mock
    private LlmClient client;
    private LlmQuestionPlanner planner;
    private final FacetRetrievalProperties settings = new FacetRetrievalProperties();

    @BeforeEach
    void setUp() {
        planner = new LlmQuestionPlanner(client, new DocumentQaProperties(), settings, new ObjectMapper());
    }

    @Test
    void shouldPreserveOriginalQuestionAndReturnOnlyRequestedFacets() {
        reply("""
            {"facets":[{"name":"Investigations","query":"Which conditions were investigated?"},
            {"name":"Opinions","query":"Which assessments are attributed opinions?"}]}
            """);
        var plan = planner.plan("Which conditions were investigated and which assessments are opinions?");

        assertThat(plan.facets()).hasSize(2);
        assertThat(plan.originalQuestion()).isEqualTo("Which conditions were investigated and which assessments are opinions?");
        ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(client).complete(request.capture());
        assertThat(request.getValue().temperature()).isZero();
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(512);
        assertThat(request.getValue().responseFormat()).isEqualTo(LlmResponseFormat.JSON);
        assertThat(request.getValue().messages().getLast().content()).isEqualTo(plan.originalQuestion());
    }

    @Test
    void shouldAllowFocusedQuestionWithoutDecomposition() {
        reply("{\"facets\":[]}");
        assertThat(planner.plan("What dose is recorded?").facets()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "{\"facets\":\"bad\"}",
        "{\"facets\":[],\"answer\":\"invented\"}", "{\"facets\":[]} {}",
        "```json\n{\"facets\":[]}\n```", "{\"facets\":[{\"name\":\"x\",\"query\":\" \"}]}",
        "{\"facets\":[{\"name\":\"x\",\"query\":\"one\"},{\"name\":\"X\",\"query\":\"two\"}]}",
        "{\"facets\":[{\"name\":\"x\",\"query\":\"What dose is recorded?\"}]}"})
    void shouldRejectInvalidOrDuplicatePlans(String content) {
        reply(content);
        assertThatThrownBy(() -> planner.plan("What dose is recorded?")).isInstanceOf(QuestionPlanningException.class);
    }

    @Test
    void shouldRejectDroppedDatesAndIdentifiers() {
        reply("{\"facets\":[{\"name\":\"Diagnosis\",\"query\":\"What diagnosis is recorded?\"}]}");
        assertThatThrownBy(() -> planner.plan("What was recorded for SYNTHETIC-001 on 11 June 2026?"))
            .isInstanceOf(QuestionPlanningException.class);
    }

    @Test
    void shouldPreserveSuppliedQualifiers() {
        reply("{\"facets\":[{\"name\":\"Diagnosis\",\"query\":\"What diagnosis is recorded for SYNTHETIC-001 on 11 June 2026?\"}]}");
        assertThat(planner.plan("Which diagnoses and symptoms were recorded for SYNTHETIC-001 on 11 June 2026?").facets()).hasSize(1);
    }

    @Test
    void shouldEnforceFacetAndTextLimits() {
        settings.setMaxFacets(1);
        reply("{\"facets\":[{\"name\":\"a\",\"query\":\"first\"},{\"name\":\"b\",\"query\":\"second\"}]}");
        assertThatThrownBy(() -> planner.plan("Question")).isInstanceOf(QuestionPlanningException.class);
        reply("{\"facets\":[{\"name\":\"a\",\"query\":\"" + "a".repeat(1001) + "\"}]}");
        assertThatThrownBy(() -> planner.plan("Question")).isInstanceOf(QuestionPlanningException.class);
    }

    @Test
    void shouldWrapProviderFailureForExplicitFallback() {
        when(client.complete(any())).thenThrow(new LlmServiceUnavailableException("Unavailable"));
        assertThatThrownBy(() -> planner.plan("Question")).isInstanceOf(QuestionPlanningException.class);
    }

    private void reply(String content) {
        when(client.complete(any())).thenReturn(new LlmCompletionResponse("test-model", content));
    }
}
