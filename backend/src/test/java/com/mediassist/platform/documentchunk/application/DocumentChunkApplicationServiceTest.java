package com.mediassist.platform.documentchunk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.documentchunk.api.dto.DocumentChunkResponse;
import com.mediassist.platform.documentchunk.domain.ChunkStatus;
import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentextraction.domain.DocumentExtraction;
import com.mediassist.platform.documentextraction.domain.DocumentExtractionRepository;
import com.mediassist.platform.documentextraction.domain.ExtractionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentChunkApplicationServiceTest {

    @Mock
    private MedicalDocumentRepository documentRepository;
    @Mock
    private DocumentExtractionRepository extractionRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private TextChunkingService textChunkingService;
    @Mock
    private AuditApplicationService auditApplicationService;

    @Test
    void shouldReturnExistingChunksWithoutApplyingANewChunkingStrategy() {
        UUID documentId = UUID.randomUUID();
        UUID extractionId = UUID.randomUUID();
        MedicalDocument document = new MedicalDocument();
        document.setId(documentId);
        DocumentExtraction extraction = new DocumentExtraction();
        extraction.setId(extractionId);
        extraction.setDocument(document);
        extraction.setExtractionStatus(ExtractionStatus.COMPLETED);
        extraction.setExtractedText("Previously extracted text.");

        DocumentChunk existing = new DocumentChunk();
        existing.setId(UUID.randomUUID());
        existing.setDocumentExtraction(extraction);
        existing.setChunkIndex(0);
        existing.setChunkText("Existing persisted chunk.");
        existing.setChunkStatus(ChunkStatus.CREATED);
        existing.setCreatedAt(LocalDateTime.of(2026, 10, 3, 12, 0));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionRepository.findByDocumentId(documentId)).thenReturn(Optional.of(extraction));
        when(chunkRepository.existsByDocumentExtractionId(extractionId)).thenReturn(true);
        when(chunkRepository.findAllByDocumentExtractionIdOrderByChunkIndexAsc(extractionId)).thenReturn(List.of(existing));
        DocumentChunkApplicationService service = new DocumentChunkApplicationService(
            documentRepository, extractionRepository, chunkRepository, textChunkingService,
            new DocumentChunkMapper(), auditApplicationService, new ObjectMapper()
        );

        List<DocumentChunkResponse> response = service.chunkDocument(documentId, "synthetic-test-user");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().chunkId()).isEqualTo(existing.getId());
        assertThat(response.getFirst().chunkText()).isEqualTo(existing.getChunkText());
        assertThat(response.getFirst().createdAt()).isEqualTo(existing.getCreatedAt());
        verify(chunkRepository).existsByDocumentExtractionId(extractionId);
        verify(chunkRepository).findAllByDocumentExtractionIdOrderByChunkIndexAsc(extractionId);
        verifyNoMoreInteractions(chunkRepository);
        verifyNoInteractions(textChunkingService, auditApplicationService);
    }
}
