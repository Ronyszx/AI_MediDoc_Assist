CREATE TABLE medical_documents (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    document_date DATE,
    description VARCHAR(500),
    uploaded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_medical_documents_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT uk_medical_documents_storage_path UNIQUE (storage_path),
    CONSTRAINT uk_medical_documents_stored_file_name UNIQUE (stored_file_name),
    CONSTRAINT chk_medical_documents_original_file_name_not_blank CHECK (btrim(original_file_name) <> ''),
    CONSTRAINT chk_medical_documents_stored_file_name_not_blank CHECK (btrim(stored_file_name) <> ''),
    CONSTRAINT chk_medical_documents_mime_type_not_blank CHECK (btrim(mime_type) <> ''),
    CONSTRAINT chk_medical_documents_storage_path_not_blank CHECK (btrim(storage_path) <> ''),
    CONSTRAINT chk_medical_documents_file_size_positive CHECK (file_size_bytes > 0),
    CONSTRAINT chk_medical_documents_checksum_format CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_medical_documents_document_type CHECK (
        document_type IN (
            'LAB_REPORT',
            'PRESCRIPTION',
            'DISCHARGE_SUMMARY',
            'REFERRAL',
            'INSURANCE',
            'CONSENT_FORM',
            'IDENTIFICATION',
            'OTHER'
        )
    ),
    CONSTRAINT chk_medical_documents_status CHECK (status IN ('UPLOADED', 'ARCHIVED'))
);

CREATE INDEX idx_medical_documents_patient_uploaded
    ON medical_documents (patient_id, uploaded_at DESC);
CREATE INDEX idx_medical_documents_document_type
    ON medical_documents (document_type);
