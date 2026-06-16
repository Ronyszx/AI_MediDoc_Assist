package com.mediassist.platform.documentchunk.domain;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository {

    <S extends DocumentChunk> List<S> saveAll(Iterable<S> chunks);

    boolean existsByDocumentExtractionId(UUID documentExtractionId);

    List<DocumentChunk> findAllByDocumentExtractionIdOrderByChunkIndexAsc(UUID documentExtractionId);

    List<DocumentChunk> findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(UUID documentId);
}
