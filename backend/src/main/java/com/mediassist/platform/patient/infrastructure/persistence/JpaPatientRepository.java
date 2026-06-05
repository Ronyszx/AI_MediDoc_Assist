package com.mediassist.platform.patient.infrastructure.persistence;

import com.mediassist.platform.patient.domain.Patient;
import com.mediassist.platform.patient.domain.PatientRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPatientRepository extends JpaRepository<Patient, UUID>, PatientRepository {
}
