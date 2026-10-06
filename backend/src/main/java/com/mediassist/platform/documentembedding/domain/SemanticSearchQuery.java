package com.mediassist.platform.documentembedding.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SemanticSearchQuery(
    @NotBlank @Size(max = 1000) String query,
    @Min(1) @Max(100) int candidateCount
) {
}
