package com.mediassist.platform.patient.api.dto;

import com.mediassist.platform.patient.domain.PatientStatus;
import jakarta.validation.constraints.NotNull;

public record PatientStatusUpdateRequest(
    @NotNull
    PatientStatus status
) {
}
