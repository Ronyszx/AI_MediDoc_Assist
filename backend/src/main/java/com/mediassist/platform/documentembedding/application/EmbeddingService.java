package com.mediassist.platform.documentembedding.application;

import java.util.List;

public interface EmbeddingService {

    EmbeddingResult embedTexts(List<String> texts);

    EmbeddingResult embedQuery(String query);

    String modelName();

    int dimensions();
}
