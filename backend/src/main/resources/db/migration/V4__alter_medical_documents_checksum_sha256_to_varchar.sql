ALTER TABLE medical_documents
    ALTER COLUMN checksum_sha256 TYPE VARCHAR(64)
    USING btrim(checksum_sha256);
