package com.mediassist.platform.documentembedding.application;

import com.mediassist.platform.documentembedding.api.dto.DocumentChunkEmbeddingMetadataResponse;
import com.mediassist.platform.documentembedding.api.dto.SemanticSearchMatchResponse;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbedding;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import org.springframework.stereotype.Component;

@Component
public class DocumentEmbeddingMapper {

    public DocumentChunkEmbeddingMetadataResponse toMetadataResponse(DocumentChunkEmbedding embedding) {
        return new DocumentChunkEmbeddingMetadataResponse(
            embedding.getId(),
            embedding.getChunk().getId(),
            embedding.getChunk().getChunkIndex(),
            embedding.getModelName(),
            embedding.getEmbeddingDimension(),
            embedding.getCreatedAt(),
            embedding.getUpdatedAt()
        );
    }

    public SemanticSearchMatchResponse toSearchMatchResponse(SemanticSearchMatch match) {
        return new SemanticSearchMatchResponse(
            match.chunkId(),
            match.chunkIndex(),
            match.chunkText(),
            match.similarityScore(),
            match.modelName()
        );
    }
}
