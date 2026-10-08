package com.mediassist.platform.documentqa.infrastructure.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassist.platform.documentqa.application.DocumentEvidenceException;
import com.mediassist.platform.documentqa.application.DocumentEvidencePromptBuilder;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmDocumentEvidenceExtractorTest {
    @Mock private LlmClient client;
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private LlmDocumentEvidenceExtractor extractor;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        extractor = new LlmDocumentEvidenceExtractor(client, new DocumentQaProperties(), settings,
            new DocumentEvidencePromptBuilder(mapper, settings), mapper);
    }

    @Test
    void shouldConstrainAllowedIdsAndPreserveConfiguredModelTemperatureAndBudget() {
        reply("{\"passageIds\":[\"P2\",\"P1\"]}");
        var selection = extractor.selectEvidence("Question", passages(2));
        assertThat(selection.passageIds()).containsExactly("P2", "P1");
        assertThat(selection.modelName()).isEqualTo("returned-model");
        ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(client).complete(request.capture());
        assertThat(request.getValue().responseFormat()).isEqualTo(LlmResponseFormat.JSON);
        JsonNode schema = new ObjectMapper().valueToTree(request.getValue().responseSchema());
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required").get(0).asText()).isEqualTo("passageIds");
        var array = schema.path("properties").path("passageIds");
        assertThat(array.path("maxItems").asInt()).isEqualTo(2);
        assertThat(array.path("minItems").asInt()).isZero();
        assertThat(array.path("uniqueItems").asBoolean()).isTrue();
        assertThat(array.path("items").path("enum").size()).isEqualTo(2);
        assertThat(array.path("items").path("enum").get(0).asText()).isEqualTo("P1");
        assertThat(array.path("items").path("enum").get(1).asText()).isEqualTo("P2");
        assertThat(request.getValue().temperature()).isEqualTo(0.2);
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(800);
        assertThat(request.getValue().messages().getFirst().content()).contains("Do not write an answer", "not automatically contradictory");
        assertThat(request.getValue().messages().getLast().content()).contains("\"question\":\"Question\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"passageIds\":\"P1\"}", "{\"passageIds\":[]} {}",
        "{\"passageIds\":[\"P1\"],\"answer\":\"invented diagnosis\"}", "{\"passageIds\":[\"P1\",\"P1\"]}",
        "{\"passageIds\":[1]}", "{\"passageIds\":[\"P0\"]}", "{\"passageIds\":[\"p1\"]}",
        "{\"passageIds\":[\"foreign-chunk\"]}", "{\"passageIds\":[\"P1\"],\"passageIds\":[]}",
        "```json\n{\"passageIds\":[]}\n```"})
    void shouldRejectMalformedOrOverSpecifiedResponses(String content) {
        reply(content);
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(2))).isInstanceOf(DocumentEvidenceException.class);
    }

    @Test
    void shouldAllowEmptySelectionAndEnforceCountAndResponseSize() {
        reply("{\"passageIds\":[]}");
        assertThat(extractor.selectEvidence("Question", passages(2)).passageIds()).isEmpty();
        settings.setMaxItems(1);
        reply("{\"passageIds\":[\"P1\",\"P2\"]}");
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(2)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("limit of 1 passages (received 2)");
        reply("x".repeat(8193));
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(2))).isInstanceOf(DocumentEvidenceException.class);
    }

    @Test
    void shouldPreserveProviderFailureRatherThanAbstainOrRetryWithProse() {
        var failure = new LlmServiceUnavailableException("Unavailable");
        when(client.complete(any())).thenThrow(failure);
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(2))).isSameAs(failure);
    }

    @Test
    void shouldRejectUnknownIdsEvenIfTheyMatchTheIdPattern() {
        reply("{\"passageIds\":[\"P3\"]}");
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(2)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("unavailable passage ID");
    }

    @Test
    void shouldUseTheConfiguredItemCapWithoutSilentlyTrimmingExcessIds() {
        reply("{\"passageIds\":[\"P1\",\"P2\",\"P3\",\"P4\",\"P5\",\"P6\",\"P7\",\"P8\",\"P9\",\"P10\",\"P11\",\"P12\",\"P13\"]}");
        assertThatThrownBy(() -> extractor.selectEvidence("Question", passages(13)))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("limit of 12 passages (received 13)");
        ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(client).complete(request.capture());
        JsonNode schema = new ObjectMapper().valueToTree(request.getValue().responseSchema());
        assertThat(schema.path("properties").path("passageIds").path("maxItems").asInt()).isEqualTo(12);
    }

    @Test
    void shouldNotCallTheProviderWithoutPassages() {
        assertThatThrownBy(() -> extractor.selectEvidence("Question", List.of()))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("No evidence passages");
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    private List<DocumentEvidenceItem> passages(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> new DocumentEvidenceItem("P" + index, UUID.randomUUID(), index, index, 0, 9, "Evidence."))
            .toList();
    }

    private void reply(String content) {
        when(client.complete(any())).thenReturn(new LlmCompletionResponse("returned-model", content));
    }
}
