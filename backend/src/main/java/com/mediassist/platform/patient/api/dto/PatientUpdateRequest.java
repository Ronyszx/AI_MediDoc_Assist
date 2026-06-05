package com.mediassist.platform.patient.api.dto;

import com.mediassist.platform.patient.domain.Gender;
import com.mediassist.platform.patient.domain.PatientStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientUpdateRequest(
    @NotBlank
    @Size(max = 100)
    String firstName,

    @NotBlank
    @Size(max = 100)
    String lastName,

    @NotNull
    @PastOrPresent
    LocalDate dateOfBirth,

    @NotNull
    Gender gender,

    @Size(max = 20)
    String phone,

    @Email
    @Size(max = 150)
    String email,

    @NotNull
    PatientStatus status
) {
}
