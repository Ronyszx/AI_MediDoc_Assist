package com.mediassist.platform.document.application.storage;

public class DocumentStorageOperationException extends DocumentStorageException {

    public DocumentStorageOperationException(String message) {
        super(message);
    }

    public DocumentStorageOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
