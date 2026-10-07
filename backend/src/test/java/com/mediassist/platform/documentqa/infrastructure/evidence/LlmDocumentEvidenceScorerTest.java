package com.mediassist.platform.documentqa.infrastructure.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentqa.application.DocumentEvidenceException;
import com.mediassist.platform.documentqa.application.DocumentEvidenceScoringPromptBuilder;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceRating;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class LlmDocumentEvidenceScorerTest {
    private final LlmClient client = mock(LlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmDocumentEvidenceScorer scorer = new LlmDocumentEvidenceScorer(client, new DocumentQaProperties(),
        new DocumentEvidenceScoringPromptBuilder(mapper), mapper);
    private final List<DocumentEvidenceItem> passages = List.of(new DocumentEvidenceItem("P9", UUID.randomUUID(),
        4, 2, 0, 12, "Not confirmed."));

    @Test
    void shouldRequestEveryGradeUsingConfiguredJsonModeWithoutSendingSourceMetadata() {
        reply("{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3}]}");
        var result = scorer.assessEvidence("Question", passages);
        assertThat(result.modelName()).isEqualTo("returned-model");
        assertThat(result.ratings()).containsExactly(new DocumentEvidenceRating("P9", 3));
        ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(client).complete(request.capture());
        assertThat(request.getValue().responseFormat()).isEqualTo(LlmResponseFormat.JSON);
        assertThat(request.getValue().temperature()).isEqualTo(0.2);
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(800);
        assertThat(request.getValue().messages().getFirst().content()).contains("Include every supplied ID exactly once", "attributed uncertain opinions");
        assertThat(request.getValue().messages().getLast().content()).contains("Not confirmed.", "\"id\":\"P9\"")
            .doesNotContain("chunkId", "citationNumber", "startOffset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"ratings\":[]}", "{\"ratings\":[{\"passageId\":\"P1\",\"relevance\":3}]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":4}]}", "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":-1}]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":2.5}]}", "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":\"3\"}]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":2147483648}]}", "{\"ratings\":[{\"passageId\":\"P9\"}]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3,\"answer\":\"private phrase\"}]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3}],\"answer\":\"private phrase\"}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3}]} {}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3}],\"ratings\":[]}",
        "{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3,\"relevance\":0}]}"})
    void shouldRejectMissingForeignAmbiguousOrInvalidRatings(String content) {
        reply(content);
        assertThatThrownBy(() -> scorer.assessEvidence("Question", passages))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageNotContaining("private phrase");
    }

    @Test
    void shouldRejectDuplicateIdsEvenWhenTheArrayLengthMatches() {
        reply("{\"ratings\":[{\"passageId\":\"P9\",\"relevance\":3},{\"passageId\":\"P9\",\"relevance\":0}]}");
        var second = new DocumentEvidenceItem("P10", UUID.randomUUID(), 5, 3, 0, 4, "Text");
        assertThatThrownBy(() -> scorer.assessEvidence("Question", List.of(passages.getFirst(), second)))
            .isInstanceOf(DocumentEvidenceException.class);
    }

    @Test
    void shouldBoundTheResponseAndPreserveProviderErrors() {
        reply("x".repeat(8193));
        assertThatThrownBy(() -> scorer.assessEvidence("Question", passages)).isInstanceOf(DocumentEvidenceException.class);
        var failure = new LlmServiceUnavailableException("Unavailable");
        when(client.complete(any())).thenThrow(failure);
        assertThatThrownBy(() -> scorer.assessEvidence("Question", passages)).isSameAs(failure);
    }

    private void reply(String content) {
        when(client.complete(any())).thenReturn(new LlmCompletionResponse("returned-model", content));
    }
}
