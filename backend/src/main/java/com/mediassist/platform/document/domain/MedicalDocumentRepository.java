package com.mediassist.platform.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalDocumentRepository {

    MedicalDocument save(MedicalDocument medicalDocument);

    Optional<MedicalDocument> findById(UUID documentId);

    List<MedicalDocument> findAllByPatientIdOrderByUploadedAtDesc(UUID patientId);

    boolean existsByStoragePath(String storagePath);
}
