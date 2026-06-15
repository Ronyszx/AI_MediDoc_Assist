package com.mediassist.platform.documentextraction.domain;

import java.util.Optional;
import java.util.UUID;

public interface DocumentExtractionRepository {

    DocumentExtraction save(DocumentExtraction extraction);

    Optional<DocumentExtraction> findById(UUID extractionId);

    Optional<DocumentExtraction> findByDocumentId(UUID documentId);
}
