package com.mediassist.platform.documentchunk.infrastructure.persistence;

import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentChunkRepository extends JpaRepository<DocumentChunk, UUID>, DocumentChunkRepository {
}
