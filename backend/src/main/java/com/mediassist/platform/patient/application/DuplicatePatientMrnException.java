package com.mediassist.platform.patient.application;

public class DuplicatePatientMrnException extends RuntimeException {

    public DuplicatePatientMrnException(String mrn) {
        super("Patient already exists with MRN: " + mrn);
    }
}
