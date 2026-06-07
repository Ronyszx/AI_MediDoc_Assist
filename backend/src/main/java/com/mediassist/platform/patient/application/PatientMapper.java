package com.mediassist.platform.patient.application;

import com.mediassist.platform.patient.api.dto.PatientCreateRequest;
import com.mediassist.platform.patient.api.dto.PatientResponse;
import com.mediassist.platform.patient.api.dto.PatientUpdateRequest;
import com.mediassist.platform.patient.domain.Patient;
import com.mediassist.platform.patient.domain.PatientStatus;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    public Patient toNewPatient(PatientCreateRequest request) {
        Patient patient = new Patient();
        patient.setMrn(normalizeRequired(request.mrn()));
        patient.setFirstName(normalizeRequired(request.firstName()));
        patient.setLastName(normalizeRequired(request.lastName()));
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setPhone(normalizeOptional(request.phone()));
        patient.setEmail(normalizeOptional(request.email()));
        patient.setStatus(PatientStatus.ACTIVE);
        return patient;
    }

    public void updatePatient(Patient patient, PatientUpdateRequest request) {
        patient.setFirstName(normalizeRequired(request.firstName()));
        patient.setLastName(normalizeRequired(request.lastName()));
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setPhone(normalizeOptional(request.phone()));
        patient.setEmail(normalizeOptional(request.email()));
        patient.setStatus(request.status());
    }

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
            patient.getId(),
            patient.getMrn(),
            patient.getFirstName(),
            patient.getLastName(),
            patient.getDateOfBirth(),
            patient.getGender(),
            patient.getPhone(),
            patient.getEmail(),
            patient.getStatus(),
            patient.getCreatedAt(),
            patient.getUpdatedAt()
        );
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
