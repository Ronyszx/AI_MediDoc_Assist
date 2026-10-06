package com.mediassist.platform.documentqa.infrastructure.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentqa.application.DocumentEvidenceException;
import com.mediassist.platform.documentqa.application.DocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.application.DocumentEvidencePromptBuilder;
import com.mediassist.platform.documentqa.application.DocumentEvidenceSettings;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmSettings;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LlmDocumentEvidenceExtractor implements DocumentEvidenceExtractor {
    private final LlmClient llmClient;
    private final LlmSettings llmSettings;
    private final DocumentEvidenceSettings settings;
    private final DocumentEvidencePromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public LlmDocumentEvidenceExtractor(LlmClient llmClient, LlmSettings llmSettings, DocumentEvidenceSettings settings,
                                       DocumentEvidencePromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.llmSettings = llmSettings;
        this.settings = settings;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages) {
        LlmCompletionResponse completion = llmClient.complete(new LlmCompletionRequest(llmSettings.getModelName(),
            promptBuilder.buildMessages(question, passages), llmSettings.getTemperature(),
            llmSettings.getMaxOutputTokens(), LlmResponseFormat.JSON));
        return new DocumentEvidenceSelection(completion.modelName(), parseIds(completion.content()));
    }

    private List<String> parseIds(String content) {
        if (content == null || content.length() > 8192) {
            throw new DocumentEvidenceException("Invalid evidence response size");
        }
        try {
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(content);
            if (root == null || !root.isObject() || root.size() != 1 || !root.path("passageIds").isArray()
                || root.path("passageIds").size() > settings.getMaxItems()) {
                throw new DocumentEvidenceException("Invalid evidence selection schema");
            }
            List<String> ids = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (JsonNode id : root.path("passageIds")) {
                if (!id.isTextual() || !id.textValue().matches("P[1-9][0-9]{0,2}") || !unique.add(id.textValue())) {
                    throw new DocumentEvidenceException("Invalid or repeated evidence passage ID");
                }
                ids.add(id.textValue());
            }
            return List.copyOf(ids);
        } catch (JsonProcessingException exception) {
            throw new DocumentEvidenceException("Malformed evidence selection JSON");
        }
    }
}
