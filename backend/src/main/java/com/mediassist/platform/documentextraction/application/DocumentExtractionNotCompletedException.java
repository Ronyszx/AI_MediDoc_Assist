package com.mediassist.platform.documentextraction.application;

import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import java.util.UUID;

public class DocumentExtractionNotCompletedException extends RuntimeException {

    public DocumentExtractionNotCompletedException(UUID documentId, ExtractionStatus extractionStatus) {
        super("Document extraction is not completed for document id: " + documentId + ". Current status: " + extractionStatus);
    }
}
