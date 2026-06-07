package com.mediassist.platform.audit.application;

import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.api.dto.AuditEventResponse;
import com.mediassist.platform.audit.domain.AuditEvent;
import org.springframework.stereotype.Component;

@Component
public class AuditEventMapper {

    public AuditEvent toEntity(AuditEventCreateRequest request) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setEntityType(request.entityType());
        auditEvent.setEntityId(request.entityId());
        auditEvent.setAction(request.action());
        auditEvent.setActor(request.actor().trim());
        auditEvent.setDetails(request.details());
        return auditEvent;
    }

    public AuditEventResponse toResponse(AuditEvent auditEvent) {
        return new AuditEventResponse(
            auditEvent.getId(),
            auditEvent.getEntityType(),
            auditEvent.getEntityId(),
            auditEvent.getAction(),
            auditEvent.getActor(),
            auditEvent.getDetails(),
            auditEvent.getOccurredAt()
        );
    }
}
