package com.mediassist.platform.audit.application;

import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.api.dto.AuditEventResponse;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.audit.domain.AuditEvent;
import com.mediassist.platform.audit.domain.AuditEventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class AuditApplicationService {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventMapper auditEventMapper;

    public AuditApplicationService(
        AuditEventRepository auditEventRepository,
        AuditEventMapper auditEventMapper
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditEventMapper = auditEventMapper;
    }

    public AuditEventResponse recordAuditEvent(@NotNull @Valid AuditEventCreateRequest request) {
        AuditEvent auditEvent = auditEventMapper.toEntity(request);
        AuditEvent savedAuditEvent = auditEventRepository.save(auditEvent);
        return auditEventMapper.toResponse(savedAuditEvent);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditHistory(
        @NotNull AuditEntityType entityType,
        @NotNull UUID entityId
    ) {
        return auditEventRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId)
            .stream()
            .map(auditEventMapper::toResponse)
            .toList();
    }
}
