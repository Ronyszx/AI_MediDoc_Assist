package com.mediassist.platform.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {

    Patient save(Patient patient);

    Optional<Patient> findById(UUID patientId);

    Optional<Patient> findByMrn(String mrn);

    boolean existsByMrn(String mrn);

    List<Patient> findAll();
}
