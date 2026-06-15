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
            'EXTRACTION_FAILED'
        )
    );
