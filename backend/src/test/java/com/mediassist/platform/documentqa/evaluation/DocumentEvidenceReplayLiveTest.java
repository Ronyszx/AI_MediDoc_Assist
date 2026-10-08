package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.config.DocumentQaConfiguration;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.application.DocumentEvidenceApplicationService;
import com.mediassist.platform.documentqa.application.DocumentEvidenceAnswerRenderer;
import com.mediassist.platform.documentqa.application.DocumentEvidenceContextBuilder;
import com.mediassist.platform.documentqa.application.DocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.application.DocumentEvidencePromptBuilder;
import com.mediassist.platform.documentqa.application.DocumentEvidenceValidator;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.LlmCompletionResponse;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.client.OllamaLlmClient;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.LlmDocumentEvidenceExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

@EnabledIfEnvironmentVariable(named = "MEDIASSIST_QA_EVIDENCE_REPLAY", matches = "true")
class DocumentEvidenceReplayLiveTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReplayTheSavedFailureAndSelectionControlsWithoutRetrievingOrWritingData() throws Exception {
        Path root = Path.of("..").toRealPath();
        Path folder = root.resolve("docs/evaluation");
        String name = System.getenv("MEDIASSIST_QA_EVIDENCE_REPLAY_OUTPUT");
        assertThat(name).isNotBlank();
        Path output = Path.of(name).toAbsolutePath().normalize();
        assertThat(output.getParent()).isEqualTo(folder);
        assertThat(output.toString()).endsWith(".json");
        JsonNode saved = read(folder, "qa_retrieval_evidence_budget_packing.json");
        assertThat(saved.path("scope").asText()).startsWith("Fictional");

        var llmSettings = new DocumentQaProperties();
        llmSettings.setModelName(saved.path("settings").path("llmModel").asText());
        llmSettings.setTemperature(saved.path("settings").path("temperature").asDouble());
        llmSettings.setMaxOutputTokens(saved.path("settings").path("maxOutputTokens").asInt());
        llmSettings.setContextWindowTokens(saved.path("settings").path("contextWindowTokens").asInt());
        var settings = new DocumentEvidenceProperties();
        var prompt = new DocumentEvidencePromptBuilder(mapper, settings);
        var rest = new DocumentQaConfiguration().documentQaRestClient(RestClient.builder(), llmSettings);
        var client = new RecordingClient(new OllamaLlmClient(rest, llmSettings));
        var extractor = new RecordingExtractor(new LlmDocumentEvidenceExtractor(client, llmSettings, settings, prompt, mapper));
        var service = new DocumentEvidenceApplicationService(new DocumentEvidenceContextBuilder(prompt, settings, llmSettings),
            extractor,
            new DocumentEvidenceValidator(), new DocumentEvidenceAnswerRenderer(settings));

        var result = mapper.createObjectNode();
        result.put("scope", "Fictional saved-context replay: local Ollama only, no Spring context, database, retrieval, embedding calls or audit writes. Protocol checks are not semantic or clinical validation.");
        result.put("startedAt", LocalDateTime.now().toString());
        result.set("settings", mapper.valueToTree(llmSettings));
        result.set("evidenceSettings", mapper.valueToTree(settings));
        var hashes = result.putObject("sourceSha256");
        for (String file : List.of("application/DocumentEvidenceApplicationService.java", "application/DocumentEvidencePromptBuilder.java",
            "application/DocumentEvidenceContextBuilder.java", "application/LlmCompletionRequest.java",
            "infrastructure/client/OllamaLlmClient.java", "infrastructure/evidence/LlmDocumentEvidenceExtractor.java")) {
            hashes.put(file, hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/" + file)));
        }
        var inputs = result.putObject("inputSha256");
        for (String file : List.of("qa_retrieval_evidence_budget_packing.json", "qa_evidence_labels.json",
            "qa_evidence_adversarial_controls.json", "qa_selection_development_controls.json")) {
            inputs.put(file, hash(folder.resolve(file)));
        }
        var runs = result.putArray("runs");
        Files.writeString(output, mapper.writeValueAsString(result), StandardOpenOption.CREATE_NEW);

        JsonNode broad = java.util.stream.StreamSupport.stream(saved.path("pairs").spliterator(), false)
            .filter(pair -> pair.path("caseId").asText().equals("Q08") && pair.path("topK").asInt() == 10).findFirst().orElseThrow();
        List<SemanticSearchMatch> sources = mapper.convertValue(broad.path("facet").path("sources"), new TypeReference<>() {});
        List<ReplayCase> cases = new ArrayList<>();
        for (int repeat = 1; repeat <= 3; repeat++) {
            cases.add(new ReplayCase("Q08-normal-" + repeat, broad.path("question").asText(), sources));
        }
        for (String order : List.of("reversed", "rotated")) {
            var reordered = new ArrayList<>(sources);
            if (order.equals("reversed")) {
                Collections.reverse(reordered);
            } else {
                Collections.rotate(reordered, reordered.size() / 2);
            }
            cases.add(new ReplayCase("Q08-" + order, broad.path("question").asText(), reordered));
        }
        for (String file : List.of("qa_evidence_adversarial_controls.json", "qa_selection_development_controls.json")) {
            for (JsonNode control : read(folder, file).path("cases")) {
                var matches = new ArrayList<SemanticSearchMatch>();
                for (int index = 0; index < control.path("chunks").size(); index++) {
                    UUID id = UUID.nameUUIDFromBytes((control.path("id").asText() + index).getBytes(StandardCharsets.UTF_8));
                    matches.add(new SemanticSearchMatch(id, index, control.path("chunks").get(index).asText(), 0.7, "fixture"));
                }
                cases.add(new ReplayCase(control.path("id").asText(), control.path("question").asText(), matches));
            }
        }
        List<String> failures = new ArrayList<>();
        for (ReplayCase replay : cases) {
            extractor.reset();
            client.reset();
            ObjectNode run = runs.addObject();
            run.put("caseId", replay.id());
            run.put("question", replay.question());
            run.set("sources", mapper.valueToTree(replay.sources()));
            long start = System.nanoTime();
            try {
                var draft = service.generateAnswer(replay.question(), replay.sources());
                var quotes = EvidenceQuoteInspection.inspect(draft, replay.sources(), extractor.passages, extractor.selection);
                run.set("draft", mapper.valueToTree(draft));
                run.put("renderedEvidenceText", String.join("\n", quotes));
                run.put("provenancePassed", true);
                run.put("success", true);
            } catch (RuntimeException exception) {
                run.put("success", false);
                run.put("errorType", exception.getClass().getSimpleName());
                run.put("errorMessage", exception.getMessage());
                failures.add(replay.id() + ": " + exception.getMessage());
            }
            run.set("catalog", mapper.valueToTree(extractor.passages));
            run.set("selection", mapper.valueToTree(extractor.selection));
            run.set("completion", mapper.valueToTree(client.completion));
            run.set("request", mapper.valueToTree(client.request));
            run.put("seconds", (System.nanoTime() - start) / 1e9);
            System.out.println("Evidence replay: " + replay.id() + " success=" + run.path("success") + " " + run.path("errorMessage").asText());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
        result.put("finishedAt", LocalDateTime.now().toString());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        assertThat(failures).as("Generation failures; usefulness is evaluated separately").isEmpty();
    }

    private JsonNode read(Path folder, String file) throws Exception {
        return mapper.readTree(folder.resolve(file).toFile());
    }

    private String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record ReplayCase(String id, String question, List<SemanticSearchMatch> sources) { }

    private static final class RecordingClient implements LlmClient {
        private final LlmClient delegate;
        private LlmCompletionRequest request;
        private LlmCompletionResponse completion;
        private RecordingClient(LlmClient delegate) { this.delegate = delegate; }
        private void reset() { request = null; completion = null; }
        public LlmCompletionResponse complete(LlmCompletionRequest request) {
            this.request = request;
            completion = delegate.complete(request);
            return completion;
        }
    }

    private static final class RecordingExtractor implements DocumentEvidenceExtractor {
        private final DocumentEvidenceExtractor delegate;
        private List<DocumentEvidenceItem> passages = List.of();
        private DocumentEvidenceSelection selection;
        private RecordingExtractor(DocumentEvidenceExtractor delegate) { this.delegate = delegate; }
        private void reset() { passages = List.of(); selection = null; }
        public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages) {
            this.passages = List.copyOf(passages);
            selection = delegate.selectEvidence(question, passages);
            return selection;
        }
    }
}
