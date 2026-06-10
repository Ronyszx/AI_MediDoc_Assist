package com.mediassist.platform.audit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuditEventResponse(
    Long id,
    AuditEntityType entityType,
    UUID entityId,
    AuditAction action,
    String actor,
    JsonNode details,
    LocalDateTime occurredAt
) {
}
