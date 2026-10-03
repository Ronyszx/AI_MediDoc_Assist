package com.mediassist.platform.documentqa.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmMessage;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "mediassist.llm", name = "provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final RestClient restClient;
    private final DocumentQaProperties properties;

    public OllamaLlmClient(
        @Qualifier("documentQaRestClient") RestClient restClient,
        DocumentQaProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public LlmCompletionResponse complete(LlmCompletionRequest request) {
        try {
            OllamaChatResponse response = restClient.post()
                .uri(properties.getEndpointUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new OllamaChatRequest(
                    request.modelName(),
                    request.messages(),
                    false,
                    false,
                    new OllamaOptions(
                        request.temperature(),
                        request.maxOutputTokens(),
                        properties.getContextWindowTokens()
                    )
                ))
                .retrieve()
                .body(OllamaChatResponse.class);

            return toCompletion(response, request.modelName());
        } catch (RestClientException exception) {
            throw new LlmServiceUnavailableException("Ollama service is unavailable or returned an invalid response", exception);
        }
    }

    private LlmCompletionResponse toCompletion(OllamaChatResponse response, String requestedModel) {
        if (response == null || response.message() == null || response.message().content() == null
            || response.message().content().isBlank()) {
            throw new LlmServiceUnavailableException("Ollama returned an empty answer");
        }
        if (!Boolean.TRUE.equals(response.done())) {
            throw new LlmServiceUnavailableException("Ollama returned an incomplete response");
        }
        if ("length".equals(response.doneReason())) {
            throw new LlmServiceUnavailableException("Ollama answer exceeded the configured output token limit");
        }

        String modelName = response.model() == null || response.model().isBlank() ? requestedModel : response.model();
        return new LlmCompletionResponse(modelName, response.message().content().trim());
    }

    private record OllamaChatRequest(
        String model,
        List<LlmMessage> messages,
        boolean stream,
        boolean think,
        OllamaOptions options
    ) {
    }

    private record OllamaOptions(
        double temperature,
        @JsonProperty("num_predict") int maxOutputTokens,
        @JsonProperty("num_ctx") int contextWindowTokens
    ) {
    }

    private record OllamaChatResponse(
        String model,
        OllamaMessage message,
        Boolean done,
        @JsonProperty("done_reason") String doneReason
    ) {
    }

    private record OllamaMessage(String content) {
    }
}
