package com.mediassist.platform.documentqa.application;

import java.util.UUID;

public class NoRelevantDocumentContextException extends RuntimeException {

    public NoRelevantDocumentContextException(UUID documentId) {
        super("No relevant document context was found for document id: " + documentId);
    }
}
