CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_extraction_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    chunk_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_document_chunks_document_extraction
        FOREIGN KEY (document_extraction_id)
            REFERENCES document_extractions(id)
            ON DELETE CASCADE,
    CONSTRAINT uq_document_chunks_extraction_index
        UNIQUE (document_extraction_id, chunk_index),
    CONSTRAINT chk_document_chunks_chunk_index_non_negative
        CHECK (chunk_index >= 0),
    CONSTRAINT chk_document_chunks_chunk_text_not_blank
        CHECK (btrim(chunk_text) <> ''),
    CONSTRAINT chk_document_chunks_status
        CHECK (chunk_status IN ('CREATED'))
);

CREATE INDEX idx_document_chunks_extraction_id
    ON document_chunks(document_extraction_id);

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action CHECK (
        action IN (
            'CREATED',
            'UPDATED',
            'STATUS_CHANGED',
            'UPLOADED',
            'DOWNLOADED',
            'ARCHIVED',
            'EXTRACTION_STARTED',
            'EXTRACTION_COMPLETED',
            'EXTRACTION_FAILED',
            'CHUNKING_STARTED',
            'CHUNKING_COMPLETED',
            'CHUNKING_FAILED'
        )
    );
