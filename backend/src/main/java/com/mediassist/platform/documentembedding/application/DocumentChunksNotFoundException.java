package com.mediassist.platform.documentembedding.application;

import java.util.UUID;

public class DocumentChunksNotFoundException extends RuntimeException {

    public DocumentChunksNotFoundException(UUID documentId) {
        super("No chunks found for document id: " + documentId);
    }
}
