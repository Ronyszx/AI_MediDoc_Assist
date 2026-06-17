package com.mediassist.platform.documentembedding.domain;

import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkEmbeddingRepository {

    List<DocumentChunkEmbedding> findAllByDocumentIdAndModelName(UUID documentId, String modelName);

    List<DocumentChunkEmbedding> findAllByChunkIdInAndModelName(Collection<UUID> chunkIds, String modelName);

    void saveEmbedding(DocumentChunk chunk, String modelName, int embeddingDimension, List<Double> embedding);

    List<SemanticSearchMatch> searchSimilarChunks(
        UUID documentId,
        String modelName,
        List<Double> queryEmbedding,
        int topK
    );
}
