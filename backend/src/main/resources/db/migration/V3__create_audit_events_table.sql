CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    details JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_events_entity_type CHECK (
        entity_type IN ('PATIENT', 'MEDICAL_DOCUMENT')
    ),
    CONSTRAINT chk_audit_events_action CHECK (
        action IN ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'UPLOADED', 'DOWNLOADED', 'ARCHIVED')
    ),
    CONSTRAINT chk_audit_events_actor_not_blank CHECK (btrim(actor) <> '')
);

CREATE INDEX idx_audit_events_entity_reference
    ON audit_events (entity_type, entity_id, occurred_at DESC);
