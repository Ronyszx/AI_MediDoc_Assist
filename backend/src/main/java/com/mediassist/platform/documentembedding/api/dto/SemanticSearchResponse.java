package com.mediassist.platform.documentembedding.api.dto;

import java.util.List;
import java.util.UUID;

public record SemanticSearchResponse(
    UUID documentId,
    String modelName,
    String query,
    List<SemanticSearchMatchResponse> matches
) {
}
