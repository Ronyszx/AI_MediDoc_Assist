package com.mediassist.platform.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "ApiErrorResponse", description = "Standard API error response")
public record ApiErrorResponse(
    @Schema(example = "2026-06-07T10:15:30")
    LocalDateTime timestamp,

    @Schema(example = "404")
    int status,

    @Schema(example = "Patient Not Found")
    String error,

    @Schema(example = "Patient not found for id: 8f17d8ae-1e28-494a-9e01-f9c88a5bd100")
    String message,

    @Schema(example = "/api/v1/patients/8f17d8ae-1e28-494a-9e01-f9c88a5bd100")
    String path
) {
}
