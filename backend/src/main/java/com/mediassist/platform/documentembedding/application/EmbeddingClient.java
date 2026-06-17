package com.mediassist.platform.documentembedding.application;

import java.util.List;

public interface EmbeddingClient {

    EmbeddingResult embed(List<String> texts, String modelName);
}
