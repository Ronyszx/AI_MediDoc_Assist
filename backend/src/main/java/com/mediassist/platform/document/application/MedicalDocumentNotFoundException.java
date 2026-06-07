package com.mediassist.platform.document.application;

import java.util.UUID;

public class MedicalDocumentNotFoundException extends RuntimeException {

    public MedicalDocumentNotFoundException(UUID documentId) {
        super("Medical document not found for id: " + documentId);
    }
}
