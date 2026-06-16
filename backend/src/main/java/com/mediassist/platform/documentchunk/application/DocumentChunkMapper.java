package com.mediassist.platform.documentchunk.application;

import com.mediassist.platform.documentchunk.api.dto.DocumentChunkResponse;
import com.mediassist.platform.documentchunk.domain.ChunkStatus;
import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkMapper {

    public DocumentChunk toNewChunk(DocumentExtraction extraction, int chunkIndex, String chunkText) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentExtraction(extraction);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(chunkText);
        chunk.setChunkStatus(ChunkStatus.CREATED);
        return chunk;
    }

    public DocumentChunkResponse toResponse(DocumentChunk chunk) {
        DocumentExtraction extraction = chunk.getDocumentExtraction();

        return new DocumentChunkResponse(
            chunk.getId(),
            extraction.getDocument().getId(),
            extraction.getId(),
            chunk.getChunkIndex(),
            chunk.getChunkStatus(),
            chunk.getChunkText(),
            chunk.getCreatedAt(),
            chunk.getUpdatedAt()
        );
    }
}
