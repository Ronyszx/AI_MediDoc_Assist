package com.mediassist.platform.documentembedding.application;

import java.util.UUID;

public class DocumentEmbeddingProcessingException extends RuntimeException {

    public DocumentEmbeddingProcessingException(UUID documentId, Throwable cause) {
        super("Unable to generate embeddings for document id: " + documentId, cause);
    }
}
