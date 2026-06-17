package com.mediassist.platform.documentembedding.infrastructure.client;

import com.mediassist.platform.documentembedding.application.EmbeddingClient;
import com.mediassist.platform.documentembedding.application.EmbeddingResult;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final DocumentEmbeddingProperties properties;

    public HttpEmbeddingClient(RestClient.Builder restClientBuilder, DocumentEmbeddingProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embed(List<String> texts, String modelName) {
        EmbeddingResponse response = restClient.post()
            .uri(properties.getEndpointUrl())
            .body(new EmbeddingRequest(texts, modelName))
            .retrieve()
            .body(EmbeddingResponse.class);

        if (response == null) {
            throw new EmbeddingClientException("Embedding service returned an empty response");
        }

        return new EmbeddingResult(response.model(), response.dimensions(), response.embeddings());
    }

    private record EmbeddingRequest(
        List<String> texts,
        String model
    ) {
    }

    private record EmbeddingResponse(
        String model,
        int dimensions,
        List<List<Double>> embeddings
    ) {
    }
}
