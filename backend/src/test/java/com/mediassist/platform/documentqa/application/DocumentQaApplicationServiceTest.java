package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.audit.api.dto.AuditEventCreateRequest;
import com.mediassist.platform.audit.application.AuditApplicationService;
import com.mediassist.platform.audit.domain.AuditAction;
import com.mediassist.platform.audit.domain.AuditEntityType;
import com.mediassist.platform.documentembedding.application.DocumentChunksNotFoundException;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionResponse;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentQaApplicationServiceTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("bdf48e1a-c6f2-43d9-ae42-7329a497ac0d");
    private static final String QUESTION = "Which conditions are recorded or only investigated?";
    private static final String ACTOR = "synthetic-test-user";

    @Mock
    private DocumentQaContextRetrievalService contextRetrievalService;
    @Mock
    private DocumentAnswerGenerationService generator;
    @Mock
    private AuditApplicationService auditApplicationService;
    private DocumentQaApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new DocumentQaApplicationService(
            contextRetrievalService,
            generator,
            new DocumentQaMapper(),
            auditApplicationService,
            new ObjectMapper()
        );
    }

    @Test
    void shouldDelegateGenerationAndPreserveSourceOrderAndResponseFields() {
        List<SemanticSearchMatch> matches = retrievedMatches();
        prepareCompletion(matches);
        LocalDateTime before = LocalDateTime.now();

        DocumentQuestionResponse response = applicationService.answerQuestion(
            DOCUMENT_ID, new DocumentQuestionRequest("  " + QUESTION + "  ", 5), ACTOR
        );

        verify(generator).generateAnswer(QUESTION, matches);
        assertThat(response.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(response.question()).isEqualTo(QUESTION);
        assertThat(response.answer()).isEqualTo("A condition was recorded. [Chunk 1]");
        assertThat(response.modelName()).isEqualTo("returned-answer-model");
        assertThat(response.sources()).extracting(source -> source.chunkId())
            .containsExactly(matches.get(0).chunkId(), matches.get(1).chunkId());
        assertThat(response.sources()).extracting(source -> source.chunkIndex()).containsExactly(64, 4);
        assertThat(response.sources().getFirst().preview()).isEqualTo(matches.getFirst().chunkText());
        assertThat(response.answeredAt()).isBetween(before, LocalDateTime.now());
        verify(contextRetrievalService).retrieveContext(DOCUMENT_ID, QUESTION, 5);
        verifyNoMoreInteractions(contextRetrievalService, generator);
    }

    @Test
    void shouldRecordTheExistingQuestionAuditWithoutPersistingQuestionText() {
        prepareCompletion(retrievedMatches());

        applicationService.answerQuestion(DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR);

        ArgumentCaptor<AuditEventCreateRequest> audit = ArgumentCaptor.forClass(AuditEventCreateRequest.class);
        verify(auditApplicationService).recordAuditEvent(audit.capture());
        AuditEventCreateRequest event = audit.getValue();
        assertThat(event.entityType()).isEqualTo(AuditEntityType.MEDICAL_DOCUMENT);
        assertThat(event.entityId()).isEqualTo(DOCUMENT_ID);
        assertThat(event.action()).isEqualTo(AuditAction.DOCUMENT_QUESTION_ASKED);
        assertThat(event.actor()).isEqualTo(ACTOR);
        assertThat(event.details().get("topK").asInt()).isEqualTo(5);
        assertThat(event.details().get("sourceCount").asInt()).isEqualTo(2);
        assertThat(event.details().get("modelName").asText()).isEqualTo("returned-answer-model");
        assertThat(event.details().has("question")).isFalse();
        verifyNoMoreInteractions(auditApplicationService);
    }

    @Test
    void shouldNotCallTheModelWhenNoContextIsRetrieved() {
        when(contextRetrievalService.retrieveContext(DOCUMENT_ID, QUESTION, 5)).thenReturn(List.of());

        assertThatThrownBy(() -> applicationService.answerQuestion(
            DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR
        )).isInstanceOf(NoRelevantDocumentContextException.class);

        verifyNoInteractions(generator, auditApplicationService);
    }

    @Test
    void shouldPreserveRetrievalErrorsWithoutCallingTheModel() {
        DocumentChunksNotFoundException failure = new DocumentChunksNotFoundException(DOCUMENT_ID);
        when(contextRetrievalService.retrieveContext(DOCUMENT_ID, QUESTION, 5)).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.answerQuestion(
            DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR
        )).isSameAs(failure);

        verifyNoInteractions(generator, auditApplicationService);
    }

    @Test
    void shouldPreserveUnavailableOrTruncatedCompletionErrorsWithoutRecordingSuccess() {
        prepareContext(retrievedMatches());
        LlmServiceUnavailableException failure = new LlmServiceUnavailableException("Answer exceeded the output limit");
        when(generator.generateAnswer(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.answerQuestion(
            DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR
        )).isSameAs(failure);

        verifyNoInteractions(auditApplicationService);
    }

    @Test
    void shouldWrapUnexpectedAnswerGenerationFailuresWithoutRecordingSuccess() {
        prepareContext(retrievedMatches());
        IllegalStateException failure = new IllegalStateException("Unexpected completion failure");
        when(generator.generateAnswer(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.answerQuestion(
            DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR
        )).isInstanceOf(RagAnswerGenerationException.class).hasCause(failure);

        verifyNoInteractions(auditApplicationService);
    }

    private void prepareCompletion(List<SemanticSearchMatch> matches) {
        prepareContext(matches);
        when(generator.generateAnswer(any(), any())).thenReturn(
            new DocumentAnswerDraft("A condition was recorded. [Chunk 1]", "returned-answer-model", DocumentAnswerMode.FREE_TEXT, 0, false)
        );
    }

    private void prepareContext(List<SemanticSearchMatch> matches) {
        when(contextRetrievalService.retrieveContext(DOCUMENT_ID, QUESTION, 5)).thenReturn(matches);
    }

    private List<SemanticSearchMatch> retrievedMatches() {
        return List.of(
            new SemanticSearchMatch(UUID.randomUUID(), 64, "A clinician recorded a condition.", 0.63, "embedding-model"),
            new SemanticSearchMatch(UUID.randomUUID(), 4, "Another condition was only investigated.", 0.59, "embedding-model")
        );
    }

    @Test
    void shouldRejectInvalidEvidenceWithoutRecordingSuccess() {
        prepareContext(retrievedMatches());
        var failure = new DocumentEvidenceException("Unknown evidence passage");
        when(generator.generateAnswer(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> applicationService.answerQuestion(DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR))
            .isInstanceOf(RagAnswerGenerationException.class).hasCause(failure);
        verifyNoInteractions(auditApplicationService);
    }

    @Test
    void shouldAuditEvidenceModeAndLimitsWithoutStoringText() {
        prepareContext(retrievedMatches());
        when(generator.generateAnswer(any(), any())).thenReturn(new DocumentAnswerDraft("Source excerpts", "model",
            DocumentAnswerMode.EVIDENCE_EXCERPTS, 2, true));
        applicationService.answerQuestion(DOCUMENT_ID, new DocumentQuestionRequest(QUESTION, 5), ACTOR);

        ArgumentCaptor<AuditEventCreateRequest> event = ArgumentCaptor.forClass(AuditEventCreateRequest.class);
        verify(auditApplicationService).recordAuditEvent(event.capture());
        assertThat(event.getValue().details().path("answerMode").asText()).isEqualTo("EVIDENCE_EXCERPTS");
        assertThat(event.getValue().details().path("evidenceCount").asInt()).isEqualTo(2);
        assertThat(event.getValue().details().path("contextLimited").asBoolean()).isTrue();
        assertThat(event.getValue().details().has("question")).isFalse();
        assertThat(event.getValue().details().has("answer")).isFalse();
        assertThat(event.getValue().details().has("evidence")).isFalse();
    }
}
