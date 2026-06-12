package com.mediassist.platform.documentextraction.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

public class DocumentExtraction {
    @Entity
    @Table(
            name = "DocumentExtraction",
            uniqueConstraints = {
                    @UniqueConstraint(name = "uk_patients_mrn", columnNames = "mrn")
            },
            indexes = {
                    @Index(name = "idx_patients_last_name", columnList = "last_name"),
                    @Index(name = "idx_patients_status", columnList = "status")
            }
    )

}

