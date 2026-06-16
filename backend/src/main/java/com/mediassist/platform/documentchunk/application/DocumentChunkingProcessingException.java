package com.mediassist.platform.documentchunk.application;

import java.util.UUID;

public class DocumentChunkingProcessingException extends RuntimeException {

    public DocumentChunkingProcessingException(UUID documentId, Throwable cause) {
        super("Unable to chunk extracted text for document id: " + documentId, cause);
    }
}
