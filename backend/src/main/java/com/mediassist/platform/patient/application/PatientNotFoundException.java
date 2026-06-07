package com.mediassist.platform.patient.application;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(UUID patientId) {
        super("Patient not found for id: " + patientId);
    }
}
