package com.mediassist.platform.audit.domain;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository {

    AuditEvent save(AuditEvent auditEvent);

    List<AuditEvent> findAllByEntityTypeAndEntityIdOrderByOccurredAtDesc(AuditEntityType entityType, UUID entityId);
}
