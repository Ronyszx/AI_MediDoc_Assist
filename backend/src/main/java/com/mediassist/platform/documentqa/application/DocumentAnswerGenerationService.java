package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.domain.DocumentAnswerDraft;
import com.mediassist.platform.documentqa.domain.DocumentAnswerMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentAnswerGenerationService {
    private final DocumentEvidenceSettings evidenceSettings;
    private final DocumentEvidenceApplicationService evidenceService;
    private final LlmClient llmClient;
    private final LlmSettings llmSettings;
    private final DocumentQaPromptBuilder promptBuilder;

    public DocumentAnswerGenerationService(DocumentEvidenceSettings evidenceSettings, DocumentEvidenceApplicationService evidenceService,
                                         LlmClient llmClient, LlmSettings llmSettings, DocumentQaPromptBuilder promptBuilder) {
        this.evidenceSettings = evidenceSettings;
        this.evidenceService = evidenceService;
        this.llmClient = llmClient;
        this.llmSettings = llmSettings;
        this.promptBuilder = promptBuilder;
    }

    public DocumentAnswerDraft generateAnswer(String question, List<SemanticSearchMatch> matches) {
        if (evidenceSettings.isEnabled()) return evidenceService.generateAnswer(question, matches);
        LlmCompletionResponse completion = llmClient.complete(new LlmCompletionRequest(llmSettings.getModelName(),
            promptBuilder.buildMessages(question, matches), llmSettings.getTemperature(), llmSettings.getMaxOutputTokens()));
        return new DocumentAnswerDraft(completion.content(), completion.modelName(), DocumentAnswerMode.FREE_TEXT, 0, false);
    }
}
