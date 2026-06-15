package com.mediassist.platform.documentextraction.application;

import java.util.UUID;

public class DocumentExtractionProcessingException extends RuntimeException {

    public DocumentExtractionProcessingException(UUID documentId, Throwable cause) {
        super("Failed to extract text for document id: " + documentId, cause);
    }
}
