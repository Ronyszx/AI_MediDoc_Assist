package com.mediassist.platform.documentextraction.Infrastructure.persistence;

import com.mediassist.platform.documentextraction.domain.DocumentExtractionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentExtractionRepository
        extends JpaRepository<DocumentExtraction, UUID>, DocumentExtractionRepository {

    Optional<DocumentExtraction> findByDocumentId(UUID documentId);

    boolean existsByDocumentId(UUID documentId);
}