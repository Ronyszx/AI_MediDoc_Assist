package com.mediassist.platform.audit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AuditEventCreateRequest(
    @NotNull
    AuditEntityType entityType,

    @NotNull
    UUID entityId,

    @NotNull
    AuditAction action,

    @NotBlank
    @Size(max = 100)
    String actor,

    JsonNode details
) {
}
