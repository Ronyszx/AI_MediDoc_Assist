CREATE TABLE document_extractions (
                                      id UUID PRIMARY KEY,
                                      document_id UUID NOT NULL UNIQUE,
                                      extraction_status VARCHAR(30) NOT NULL,
                                      extracted_text TEXT,
                                      page_count INTEGER,
                                      created_at TIMESTAMP NOT NULL,
                                      updated_at TIMESTAMP,

                                      CONSTRAINT fk_document_extractions_document
                                          FOREIGN KEY (document_id)
                                              REFERENCES medical_documents(id)
                                              ON DELETE CASCADE
);

CREATE INDEX idx_document_extractions_document_id
    ON document_extractions(document_id);