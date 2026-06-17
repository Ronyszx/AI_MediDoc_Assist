package com.mediassist.platform.documentembedding.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SemanticSearchRequest(
    @NotBlank
    String query,

    @NotNull
    @Min(1)
    @Max(20)
    Integer topK
) {
}
