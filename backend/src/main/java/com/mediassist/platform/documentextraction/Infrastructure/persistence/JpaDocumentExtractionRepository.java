package com.mediassist.platform.documentextraction.infrastructure.persistence;

import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import com.mediassist.platform.documentextraction.domain.DocumentExtractionRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentExtractionRepository
    extends JpaRepository<DocumentExtraction, UUID>, DocumentExtractionRepository {
}
