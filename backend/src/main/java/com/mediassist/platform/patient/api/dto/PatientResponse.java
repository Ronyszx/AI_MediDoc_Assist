package com.mediassist.platform.patient.api.dto;

import com.mediassist.platform.patient.domain.Gender;
import com.mediassist.platform.patient.domain.PatientStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PatientResponse(
    UUID id,
    String mrn,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    Gender gender,
    String phone,
    String email,
    PatientStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
