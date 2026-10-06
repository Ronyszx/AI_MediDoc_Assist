package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentEvidenceApplicationServiceTest {
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private final DocumentQaProperties llmSettings = new DocumentQaProperties();
    private final DocumentEvidenceExtractor extractor = mock(DocumentEvidenceExtractor.class);
    private final DocumentEvidenceApplicationService service = new DocumentEvidenceApplicationService(
        new DocumentEvidenceContextBuilder(new DocumentEvidencePromptBuilder(new ObjectMapper(), settings), settings, llmSettings),
        extractor, new DocumentEvidenceValidator(), new DocumentEvidenceAnswerRenderer(settings));

    @Test
    void shouldRenderOnlyTheSelectedCompleteParagraphWithItsSourcePosition() {
        when(extractor.selectEvidence(eq("What did the examiner record?"), anyList()))
            .thenReturn(new DocumentEvidenceSelection("actual-model", List.of("P2")));
        var answer = service.generateAnswer("What did the examiner record?", List.of(source("Administrative entry."),
            source("Examiner B: I did not confirm the condition at this examination.")));
        assertThat(answer.mode()).isEqualTo(DocumentAnswerMode.EVIDENCE_EXCERPTS);
        assertThat(answer.modelName()).isEqualTo("actual-model");
        assertThat(answer.evidenceCount()).isEqualTo(1);
        assertThat(answer.content()).contains("I did not confirm the condition", "[Chunk 2]")
            .doesNotContain("Administrative entry", "condition was confirmed");
    }

    @Test
    void shouldRejectAnUnknownSelectionRatherThanRenderingAnUnverifiedAnswer() {
        when(extractor.selectEvidence(eq("Question"), anyList()))
            .thenReturn(new DocumentEvidenceSelection("model", List.of("P9")));
        assertThatThrownBy(() -> service.generateAnswer("Question", List.of(source("Evidence."))))
            .isInstanceOf(DocumentEvidenceException.class).hasMessage("Unknown or repeated selected passage");
    }

    @Test
    void shouldReturnAScopedAbstentionForAnExplicitEmptySelection() {
        when(extractor.selectEvidence(eq("Dose?"), anyList()))
            .thenReturn(new DocumentEvidenceSelection("model", List.of()));
        var answer = service.generateAnswer("Dose?", List.of(source("No dose is recorded in this extract.")));
        assertThat(answer.evidenceCount()).isZero();
        assertThat(answer.content()).contains("retrieved document context", "does not establish absence from the whole document");
    }

    @Test
    void shouldNotCallTheModelWhenNoCompletePassageFits() {
        settings.setMaxPassageCharacters(128);
        assertThatThrownBy(() -> service.generateAnswer("Question", List.of(source("x".repeat(129)))))
            .isInstanceOf(DocumentEvidenceException.class).hasMessageContaining("context budget");
        verifyNoInteractions(extractor);
    }

    @Test
    void shouldPropagateProviderUnavailabilityWithoutChangingToFreeText() {
        when(extractor.selectEvidence(eq("Question"), anyList()))
            .thenThrow(new LlmServiceUnavailableException("Unavailable"));
        assertThatThrownBy(() -> service.generateAnswer("Question", List.of(source("Evidence."))))
            .isInstanceOf(LlmServiceUnavailableException.class);
    }

    private SemanticSearchMatch source(String text) {
        return new SemanticSearchMatch(UUID.randomUUID(), 64, text, 0.7, "model");
    }
}
