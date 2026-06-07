package com.mediassist.platform.document.application;

public class DuplicateDocumentStoragePathException extends RuntimeException {

    public DuplicateDocumentStoragePathException(String storagePath) {
        super("Medical document already exists for storage path: " + storagePath);
    }
}
