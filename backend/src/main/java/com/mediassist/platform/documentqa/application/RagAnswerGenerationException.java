package com.mediassist.platform.documentqa.application;

import java.util.UUID;

public class RagAnswerGenerationException extends RuntimeException {

    public RagAnswerGenerationException(UUID documentId, Throwable cause) {
        super("Unable to generate an answer for document id: " + documentId, cause);
    }
}
