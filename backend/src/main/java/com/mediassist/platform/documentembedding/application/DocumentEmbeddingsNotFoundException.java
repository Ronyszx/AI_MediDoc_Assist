package com.mediassist.platform.documentembedding.application;

import java.util.UUID;

public class DocumentEmbeddingsNotFoundException extends RuntimeException {

    public DocumentEmbeddingsNotFoundException(UUID documentId, String modelName) {
        super("No embeddings found for document id: " + documentId + " and model: " + modelName);
    }
}
