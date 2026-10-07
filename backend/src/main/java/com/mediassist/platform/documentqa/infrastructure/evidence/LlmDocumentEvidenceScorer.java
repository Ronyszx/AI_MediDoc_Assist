package com.mediassist.platform.documentqa.infrastructure.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassist.platform.documentqa.application.DocumentEvidenceException;
import com.mediassist.platform.documentqa.application.DocumentEvidenceScorer;
import com.mediassist.platform.documentqa.application.DocumentEvidenceScoringPromptBuilder;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmResponseFormat;
import com.mediassist.platform.documentqa.application.LlmSettings;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceAssessment;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceRating;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LlmDocumentEvidenceScorer implements DocumentEvidenceScorer {
    private final LlmClient llmClient;
    private final LlmSettings llmSettings;
    private final DocumentEvidenceScoringPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public LlmDocumentEvidenceScorer(LlmClient llmClient, LlmSettings llmSettings,
                                    DocumentEvidenceScoringPromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.llmSettings = llmSettings;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentEvidenceAssessment assessEvidence(String question, List<DocumentEvidenceItem> passages) {
        var completion = llmClient.complete(new LlmCompletionRequest(llmSettings.getModelName(),
            promptBuilder.buildMessages(question, passages), llmSettings.getTemperature(),
            llmSettings.getMaxOutputTokens(), LlmResponseFormat.JSON));
        return new DocumentEvidenceAssessment(completion.modelName(), parseRatings(completion.content(), passages));
    }

    private List<DocumentEvidenceRating> parseRatings(String content, List<DocumentEvidenceItem> passages) {
        if (content == null || content.length() > 8192) {
            throw new DocumentEvidenceException("Invalid evidence rating response size");
        }
        try {
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(content);
            if (root == null || !root.isObject() || root.size() != 1 || !root.path("ratings").isArray()
                || root.path("ratings").size() != passages.size()) {
                throw new DocumentEvidenceException("Evidence ratings must cover every supplied passage");
            }
            Set<String> allowed = passages.stream().map(DocumentEvidenceItem::passageId).collect(Collectors.toSet());
            Set<String> rated = new HashSet<>();
            List<DocumentEvidenceRating> ratings = new ArrayList<>();
            for (JsonNode item : root.path("ratings")) {
                JsonNode id = item.path("passageId");
                JsonNode relevance = item.path("relevance");
                if (!item.isObject() || item.size() != 2 || !id.isTextual() || !allowed.contains(id.textValue())
                    || !rated.add(id.textValue()) || !relevance.isIntegralNumber() || !relevance.canConvertToInt()
                    || relevance.intValue() < 0 || relevance.intValue() > 3) {
                    throw new DocumentEvidenceException("Invalid evidence passage rating");
                }
                ratings.add(new DocumentEvidenceRating(id.textValue(), relevance.intValue()));
            }
            return List.copyOf(ratings);
        } catch (JsonProcessingException exception) {
            throw new DocumentEvidenceException("Malformed evidence rating JSON");
        }
    }
}
