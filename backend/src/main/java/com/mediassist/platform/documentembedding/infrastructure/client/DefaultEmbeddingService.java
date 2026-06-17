package com.mediassist.platform.documentembedding.infrastructure.client;

import com.mediassist.platform.documentembedding.application.EmbeddingClient;
import com.mediassist.platform.documentembedding.application.EmbeddingResult;
import com.mediassist.platform.documentembedding.application.EmbeddingService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultEmbeddingService implements EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final DocumentEmbeddingProperties properties;

    public DefaultEmbeddingService(
        EmbeddingClient embeddingClient,
        DocumentEmbeddingProperties properties
    ) {
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embedTexts(List<String> texts) {
        if (texts.isEmpty()) {
            return new EmbeddingResult(properties.getModelName(), properties.getDimensions(), List.of());
        }

        List<List<Double>> embeddings = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += properties.getBatchSize()) {
            int end = Math.min(start + properties.getBatchSize(), texts.size());
            EmbeddingResult batchResult = embeddingClient.embed(texts.subList(start, end), properties.getModelName());
            validateEmbeddingResult(batchResult, end - start);
            embeddings.addAll(batchResult.embeddings());
        }

        return new EmbeddingResult(properties.getModelName(), properties.getDimensions(), embeddings);
    }

    @Override
    public EmbeddingResult embedQuery(String query) {
        EmbeddingResult result = embeddingClient.embed(List.of(query), properties.getModelName());
        validateEmbeddingResult(result, 1);
        return result;
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    private void validateEmbeddingResult(EmbeddingResult result, int expectedEmbeddingCount) {
        if (!properties.getModelName().equals(result.modelName())) {
            throw new EmbeddingClientException("Embedding service returned an unexpected model: " + result.modelName());
        }
        if (result.dimensions() != properties.getDimensions()) {
            throw new EmbeddingClientException("Embedding service returned an unexpected dimension: " + result.dimensions());
        }
        if (result.embeddings().size() != expectedEmbeddingCount) {
            throw new EmbeddingClientException("Embedding service returned an unexpected embedding count");
        }

        result.embeddings().forEach(this::validateVector);
    }

    private void validateVector(List<Double> embedding) {
        if (embedding.size() != properties.getDimensions()) {
            throw new EmbeddingClientException("Embedding vector has unexpected dimension: " + embedding.size());
        }
        if (embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new EmbeddingClientException("Embedding vector contains non-finite values");
        }
    }
}
