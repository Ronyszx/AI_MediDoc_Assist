CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk_embeddings (
    id UUID PRIMARY KEY,
    chunk_id UUID NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_document_chunk_embeddings_chunk
        FOREIGN KEY (chunk_id)
            REFERENCES document_chunks(id)
            ON DELETE CASCADE,
    CONSTRAINT uq_document_chunk_embeddings_chunk_model
        UNIQUE (chunk_id, model_name),
    CONSTRAINT chk_document_chunk_embeddings_dimension
        CHECK (embedding_dimension = 1024),
    CONSTRAINT chk_document_chunk_embeddings_model_not_blank
        CHECK (btrim(model_name) <> '')
);

CREATE INDEX idx_document_chunk_embeddings_chunk_id
    ON document_chunk_embeddings(chunk_id);

CREATE INDEX idx_document_chunk_embeddings_embedding_hnsw
    ON document_chunk_embeddings USING hnsw (embedding vector_cosine_ops);

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
            'CHUNKING_FAILED',
            'EMBEDDING_STARTED',
            'EMBEDDING_COMPLETED',
            'EMBEDDING_FAILED',
            'SEMANTIC_SEARCH_PERFORMED'
        )
    );
