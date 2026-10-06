package com.mediassist.platform.documentqa.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmMessage;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaLlmClientTest {

    private final DocumentQaProperties properties = new DocumentQaProperties();
    private MockRestServiceServer server;
    private OllamaLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaLlmClient(builder.build(), properties);
    }

    @Test
    void shouldSendOllamaRequestAndReturnOnlyAnswerContent() {
        properties.setContextWindowTokens(4096);
        server.expect(requestTo(properties.getEndpointUrl()))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("configured-model"))
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].content").value("Summarize the supplied context."))
            .andExpect(jsonPath("$.stream").value(false))
            .andExpect(jsonPath("$.think").value(false))
            .andExpect(jsonPath("$.format").doesNotExist())
            .andExpect(jsonPath("$.options.temperature").value(0.2))
            .andExpect(jsonPath("$.options.num_predict").value(800))
            .andExpect(jsonPath("$.options.num_ctx").value(4096))
            .andRespond(withSuccess("""
                {
                  "model": "configured-model",
                  "message": {
                    "role": "assistant",
                    "content": "  The document mentions hypertension. [Chunk 1]  ",
                    "thinking": "This must not be returned."
                  },
                  "done": true,
                  "done_reason": "stop",
                  "eval_count": 20
                }
                """, MediaType.APPLICATION_JSON));

        LlmCompletionResponse response = client.complete(completionRequest());

        assertThat(response.modelName()).isEqualTo("configured-model");
        assertThat(response.content()).isEqualTo("The document mentions hypertension. [Chunk 1]");
        server.verify();
    }

    @Test
    void shouldUseRequestedModelWhenResponseOmitsModelName() {
        server.expect(requestTo(properties.getEndpointUrl()))
            .andRespond(withSuccess("""
                {"message":{"content":"Context is insufficient."},"done":true}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.complete(completionRequest()).modelName()).isEqualTo("configured-model");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{}",
        "{\"message\":{\"content\":\" \"},\"done\":true}",
        "{\"message\":{\"thinking\":\"Internal reasoning only.\"},\"done\":true}",
        "{\"message\":{\"content\":\"Partial answer\"},\"done\":false}",
        "{\"message\":{\"content\":\"Partial answer\"},\"done\":true,\"done_reason\":\"length\"}",
        "not valid json"
    })
    void shouldRejectEmptyIncompleteOrInvalidResponses(String responseBody) {
        server.expect(requestTo(properties.getEndpointUrl()))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(completionRequest()))
            .isInstanceOf(LlmServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void shouldTranslateOllamaHttpErrorsToServiceUnavailable() {
        server.expect(requestTo(properties.getEndpointUrl()))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"model not found\"}"));

        assertThatThrownBy(() -> client.complete(completionRequest()))
            .isInstanceOf(LlmServiceUnavailableException.class)
            .hasMessageContaining("Ollama service is unavailable");
        server.verify();
    }

    @Test
    void shouldTranslateConnectionFailuresToServiceUnavailable() {
        server.expect(requestTo(properties.getEndpointUrl()))
            .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.complete(completionRequest()))
            .isInstanceOf(LlmServiceUnavailableException.class);
        server.verify();
    }

    private LlmCompletionRequest completionRequest() {
        return new LlmCompletionRequest(
            "configured-model",
            List.of(
                new LlmMessage("system", "Answer only from the document context."),
                new LlmMessage("user", "Summarize the supplied context.")
            ),
            0.2,
            800
        );
    }

    @Test
    void shouldRequestJsonOnlyForStructuredCompletions() {
        server.expect(requestTo(properties.getEndpointUrl()))
            .andExpect(jsonPath("$.format").value("json"))
            .andExpect(jsonPath("$.options.num_predict").value(512))
            .andRespond(withSuccess("""
                {"message":{"content":"{\\"facets\\":[]}"},"done":true}
                """, MediaType.APPLICATION_JSON));
        var request = completionRequest();
        var completion = client.complete(new LlmCompletionRequest(request.modelName(), request.messages(),
            0, 512, LlmResponseFormat.JSON));
        assertThat(completion.content()).isEqualTo("{\"facets\":[]}");
        server.verify();
    }
}
