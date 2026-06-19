package com.mediassist.platform.documentqa.infrastructure.client;

import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmMessage;
import com.mediassist.platform.documentqa.application.LlmServiceUnavailableException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpLlmClient implements LlmClient {

    private final RestClient restClient;
    private final DocumentQaProperties properties;

    public HttpLlmClient(RestClient.Builder restClientBuilder, DocumentQaProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public LlmCompletionResponse complete(LlmCompletionRequest request) {
        try {
            ChatCompletionResponse response = restClient.post()
                .uri(properties.getEndpointUrl())
                .body(new ChatCompletionRequest(
                    request.modelName(),
                    request.messages(),
                    request.temperature(),
                    request.maxOutputTokens()
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new LlmServiceUnavailableException("LLM service returned an empty response");
            }

            String responseModel = response.model() != null && !response.model().isBlank()
                ? response.model()
                : request.modelName();

            return new LlmCompletionResponse(responseModel, response.content().trim());
        } catch (RestClientException exception) {
            throw new LlmServiceUnavailableException("LLM service is unavailable", exception);
        }
    }

    private record ChatCompletionRequest(
        String model,
        List<LlmMessage> messages,
        double temperature,
        int maxTokens
    ) {
    }

    private record ChatCompletionResponse(
        String model,
        String content
    ) {
    }
}
