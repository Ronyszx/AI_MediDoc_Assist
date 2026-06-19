package com.mediassist.platform.documentembedding.infrastructure.client;

import com.mediassist.platform.documentembedding.application.EmbeddingClient;
import com.mediassist.platform.documentembedding.application.EmbeddingResult;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;


@Component
public class HttpEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final DocumentEmbeddingProperties properties;

    private static final Logger log = LoggerFactory.getLogger(HttpEmbeddingClient.class);

    public HttpEmbeddingClient(RestClient.Builder restClientBuilder, DocumentEmbeddingProperties properties) {
        this.restClient = restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        this.properties = properties;
    }
    @Override
    public EmbeddingResult embed(List<String> texts, String modelName) {
        try {
            log.info(
                    "Calling embedding service. endpoint={}, model={}, textCount={}",
                    properties.getEndpointUrl(),
                    modelName,
                    texts.size()
            );

            EmbeddingResponse response = restClient.post()
                    .uri(properties.getEndpointUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(texts, modelName))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null) {
                throw new EmbeddingClientException("Embedding service returned an empty response");
            }

            log.info(
                    "Embedding service response received. model={}, dimensions={}, embeddingCount={}",
                    response.model(),
                    response.dimensions(),
                    response.embeddings() == null ? 0 : response.embeddings().size()
            );

            return new EmbeddingResult(response.model(), response.dimensions(), response.embeddings());

        } catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();

            log.error(
                    "Embedding service request failed. status={}, body={}",
                    exception.getStatusCode(),
                    responseBody,
                    exception
            );

            throw new EmbeddingClientException(
                    "Embedding service request failed with status "
                            + exception.getStatusCode()
                            + " and body: "
                            + responseBody,
                    exception
            );
        }
    }

//    @Override
//    public EmbeddingResult embed(List<String> texts, String modelName) {
//        EmbeddingResponse response = restClient.post()
//            .uri(properties.getEndpointUrl())
//            .body(new EmbeddingRequest(texts, modelName))
//            .retrieve()
//            .body(EmbeddingResponse.class);
//
//        if (response == null) {
//            throw new EmbeddingClientException("Embedding service returned an empty response");
//        }
//
//        return new EmbeddingResult(response.model(), response.dimensions(), response.embeddings());
//    }

//    @Override
//    public EmbeddingResult embed(List<String> texts, String modelName) {
//        try {
//            EmbeddingResponse response = restClient.post()
//                    .uri(properties.getEndpointUrl())
//                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
//                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
//                    .body(new EmbeddingRequest(texts, modelName))
//                    .retrieve()
//                    .body(EmbeddingResponse.class);
//
//            if (response == null) {
//                throw new EmbeddingClientException("Embedding service returned an empty response");
//            }
//
//            return new EmbeddingResult(response.model(), response.dimensions(), response.embeddings());
//        } catch (RestClientResponseException exception) {
//            throw new EmbeddingClientException(
//                    "Embedding service request failed with status "
//                            + exception.getStatusCode()
//                            + " and body: "
//                            + exception.getResponseBodyAsString(),
//                    exception
//            );
//        }
//    }

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
