CREATE TABLE patients (
    id UUID PRIMARY KEY,
    mrn VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_patients_mrn UNIQUE (mrn),
    CONSTRAINT chk_patients_mrn_not_blank CHECK (btrim(mrn) <> ''),
    CONSTRAINT chk_patients_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT chk_patients_last_name_not_blank CHECK (btrim(last_name) <> ''),
    CONSTRAINT chk_patients_date_of_birth CHECK (date_of_birth <= CURRENT_DATE),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT chk_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_patients_last_name ON patients (last_name);
CREATE INDEX idx_patients_status ON patients (status);
