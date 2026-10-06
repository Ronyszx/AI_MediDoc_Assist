package com.mediassist.platform.documentembedding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.document.application.MedicalDocumentNotFoundException;
import com.mediassist.platform.document.domain.MedicalDocument;
import com.mediassist.platform.document.domain.MedicalDocumentRepository;
import com.mediassist.platform.documentchunk.domain.DocumentChunk;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbedding;
import com.mediassist.platform.documentembedding.domain.DocumentChunkEmbeddingRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchCandidate;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentEmbeddingRetrievalTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String QUESTION = "What is recorded?";

    @Mock
    private MedicalDocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private DocumentChunkEmbeddingRepository embeddingRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private AuditApplicationService auditService;

    private DocumentEmbeddingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentEmbeddingApplicationService(documentRepository, chunkRepository, embeddingRepository,
            embeddingService, new DocumentEmbeddingMapper(), auditService, new ObjectMapper());
    }

    @Test
    void shouldReuseStoredVectorsAndEmbedTheQuestionOnlyOnce() {
        prepareDocumentAndChunks();
        when(embeddingService.modelName()).thenReturn("configured-model");
        when(embeddingRepository.findAllByDocumentIdAndModelName(DOCUMENT_ID, "configured-model"))
            .thenReturn(List.of(mock(DocumentChunkEmbedding.class)));
        List<Double> queryVector = List.of(1.0, 0.0);
        when(embeddingService.embedQuery(QUESTION)).thenReturn(new EmbeddingResult("configured-model", 2, List.of(queryVector)));
        SemanticSearchCandidate candidate = new SemanticSearchCandidate(
            new SemanticSearchMatch(UUID.randomUUID(), 0, "Evidence", 0.8, "configured-model"), queryVector
        );
        when(embeddingRepository.searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", queryVector, 20))
            .thenReturn(List.of(candidate));

        assertThat(service.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20)).containsExactly(candidate);
        verify(embeddingService).modelName();
        verify(embeddingService).embedQuery(QUESTION);
        verifyNoMoreInteractions(embeddingService);
        verify(embeddingRepository).findAllByDocumentIdAndModelName(DOCUMENT_ID, "configured-model");
        verify(embeddingRepository).searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", queryVector, 20);
        verifyNoMoreInteractions(embeddingRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectMissingDocumentsBeforeEmbedding() {
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20))
            .isInstanceOf(MedicalDocumentNotFoundException.class);
        verifyNoInteractions(chunkRepository, embeddingRepository, embeddingService, auditService);
    }

    @Test
    void shouldRejectMissingChunksBeforeEmbedding() {
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(mock(MedicalDocument.class)));
        when(chunkRepository.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(DOCUMENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20))
            .isInstanceOf(DocumentChunksNotFoundException.class);
        verifyNoInteractions(embeddingRepository, embeddingService, auditService);
    }

    @Test
    void shouldRejectMissingModelEmbeddingsBeforeEmbeddingTheQuestion() {
        prepareDocumentAndChunks();
        when(embeddingService.modelName()).thenReturn("configured-model");
        when(embeddingRepository.findAllByDocumentIdAndModelName(DOCUMENT_ID, "configured-model")).thenReturn(List.of());

        assertThatThrownBy(() -> service.searchSimilarChunkCandidates(DOCUMENT_ID, QUESTION, 20))
            .isInstanceOf(DocumentEmbeddingsNotFoundException.class);
        verify(embeddingService, org.mockito.Mockito.never()).embedQuery(any());
        verifyNoInteractions(auditService);
    }

    private void prepareDocumentAndChunks() {
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(mock(MedicalDocument.class)));
        when(chunkRepository.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(DOCUMENT_ID))
            .thenReturn(List.of(mock(DocumentChunk.class)));
    }

    @Test
    void shouldValidateOnceAndEmbedMultipleQueriesInOneBatchWithoutWriting() {
        prepareDocumentAndChunks();
        when(embeddingService.modelName()).thenReturn("configured-model");
        when(embeddingRepository.findAllByDocumentIdAndModelName(DOCUMENT_ID, "configured-model"))
            .thenReturn(List.of(mock(DocumentChunkEmbedding.class)));
        var first = List.of(1.0, 0.0);
        var second = List.of(0.0, 1.0);
        when(embeddingService.embedTexts(List.of("Original", "Facet")))
            .thenReturn(new EmbeddingResult("configured-model", 2, List.of(first, second)));
        when(embeddingRepository.searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", first, 20)).thenReturn(List.of());
        when(embeddingRepository.searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", second, 10)).thenReturn(List.of());

        var result = service.searchSimilarChunkBatches(DOCUMENT_ID, List.of(
            new SemanticSearchQuery("Original", 20), new SemanticSearchQuery("Facet", 10)));
        assertThat(result).hasSize(2);
        assertThat(result.get(1).queryEmbedding()).isEqualTo(second);
        verify(documentRepository).findById(DOCUMENT_ID);
        verify(chunkRepository).findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(DOCUMENT_ID);
        verify(embeddingRepository).findAllByDocumentIdAndModelName(DOCUMENT_ID, "configured-model");
        verify(embeddingRepository).searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", first, 20);
        verify(embeddingRepository).searchSimilarChunkCandidates(DOCUMENT_ID, "configured-model", second, 10);
        verifyNoMoreInteractions(embeddingRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectMissingDocumentBeforeBatchEmbedding() {
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.searchSimilarChunkBatches(DOCUMENT_ID, List.of(new SemanticSearchQuery(QUESTION, 20))))
            .isInstanceOf(MedicalDocumentNotFoundException.class);
        verifyNoInteractions(chunkRepository, embeddingRepository, embeddingService, auditService);
    }
}
