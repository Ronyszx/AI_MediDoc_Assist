package com.mediassist.platform.documentembedding.application;

import java.util.List;

public record EmbeddingResult(
    String modelName,
    int dimensions,
    List<List<Double>> embeddings
) {
}
