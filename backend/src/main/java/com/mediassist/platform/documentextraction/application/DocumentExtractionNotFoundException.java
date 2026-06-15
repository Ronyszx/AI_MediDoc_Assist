package com.mediassist.platform.documentextraction.application;

import java.util.UUID;

public class DocumentExtractionNotFoundException extends RuntimeException {

    public DocumentExtractionNotFoundException(UUID documentId) {
        super("Document extraction not found for document id: " + documentId);
    }
}
