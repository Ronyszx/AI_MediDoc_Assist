package com.mediassist.platform.document.api.dto;

import com.mediassist.platform.document.domain.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record DocumentStatusUpdateRequest(
    @NotNull
    DocumentStatus status
) {
}
