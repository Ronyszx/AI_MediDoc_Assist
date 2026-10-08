package com.mediassist.platform.documentqa.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAnswerGenerationServiceTest {
    @Mock private LlmClient llm;
    @Mock private DocumentEvidenceApplicationService evidence;
    private final DocumentEvidenceProperties settings = new DocumentEvidenceProperties();
    private final DocumentQaProperties llmSettings = new DocumentQaProperties();
    private final DocumentQaPromptBuilder prompt = new DocumentQaPromptBuilder();
    private final List<SemanticSearchMatch> matches = List.of(new SemanticSearchMatch(UUID.randomUUID(), 64, "Document text", 0.7, "embedding"));
    private DocumentAnswerGenerationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentAnswerGenerationService(settings, evidence, llm, llmSettings, prompt);
    }

    @Test
    void shouldPreserveLegacyPromptModelSettingsAndTextModeWhenDisabled() {
        when(llm.complete(any())).thenReturn(new LlmCompletionResponse("returned-model", "Answer [Chunk 1]"));
        var answer = service.generateAnswer("Question", matches);
        ArgumentCaptor<LlmCompletionRequest> request = ArgumentCaptor.forClass(LlmCompletionRequest.class);
        verify(llm).complete(request.capture());
        assertThat(request.getValue().messages()).isEqualTo(prompt.buildMessages("Question", matches));
        assertThat(request.getValue().modelName()).isEqualTo(llmSettings.getModelName());
        assertThat(request.getValue().temperature()).isEqualTo(0.2);
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(800);
        assertThat(request.getValue().responseFormat()).isEqualTo(LlmResponseFormat.TEXT);
        assertThat(answer.mode()).isEqualTo(DocumentAnswerMode.FREE_TEXT);
        assertThat(answer.modelName()).isEqualTo("returned-model");
        assertThat(answer.content()).isEqualTo("Answer [Chunk 1]");
        verifyNoInteractions(evidence);
    }

    @Test
    void shouldDelegateOnlyToEvidenceWhenEnabled() {
        settings.setEnabled(true);
        var expected = new DocumentAnswerDraft("Verified excerpts", "model", DocumentAnswerMode.EVIDENCE_EXCERPTS, 2, false);
        when(evidence.generateAnswer("Question", matches)).thenReturn(expected);
        assertThat(service.generateAnswer("Question", matches)).isSameAs(expected);
        verifyNoInteractions(llm);
    }

    @Test
    void shouldNeverFallBackToProseWhenEvidenceValidationFails() {
        settings.setEnabled(true);
        var failure = new DocumentEvidenceException("Unknown passage");
        when(evidence.generateAnswer("Question", matches)).thenThrow(failure);
        assertThatThrownBy(() -> service.generateAnswer("Question", matches)).isSameAs(failure);
        verifyNoInteractions(llm);
    }
}
