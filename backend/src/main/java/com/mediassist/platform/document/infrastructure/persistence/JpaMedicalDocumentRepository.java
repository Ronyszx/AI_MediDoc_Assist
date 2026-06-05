package com.mediassist.platform.document.infrastructure.persistence;

import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID>, MedicalDocumentRepository {
}
