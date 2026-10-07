package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.application.DocumentAnswerGenerationService;
import com.mediassist.platform.documentqa.application.DocumentEvidenceContextBuilder;
import com.mediassist.platform.documentqa.application.DocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.application.DocumentEvidenceScorer;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceAssessment;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceRating;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.LlmDocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.infrastructure.evidence.LlmDocumentEvidenceScorer;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "MEDIASSIST_EVIDENCE_SELECTION_EVALUATION", matches = "true")
@SpringBootTest(properties = {"mediassist.qa.evidence.enabled=true", "mediassist.qa.evidence.selection-batch-size=8",
    "mediassist.qa.facets.enabled=false", "mediassist.qa.retrieval.diversity-enabled=false"})
@Import(EvidenceSelectionComparisonLiveTest.RecordingConfiguration.class)
class EvidenceSelectionComparisonLiveTest {
    private static final Pattern QUOTE = Pattern.compile("(?m)^- \"(.*)\" \\[Chunk ([0-9]+)]$");
    @Autowired private ObjectMapper mapper;
    @Autowired private DocumentAnswerGenerationService answers;
    @Autowired private DocumentEvidenceContextBuilder contextBuilder;
    @Autowired private DocumentEvidenceProperties evidenceSettings;
    @Autowired private DocumentQaProperties llmSettings;
    @Autowired private RecordingExtractor extractor;
    @Autowired private RecordingScorer scorer;
    @Autowired private DocumentChunkRepository chunks;
    @Autowired private JdbcTemplate jdbc;
    private JsonNode replayBaseline;

    @Test
    void shouldCompareBothSelectionModesOnIdenticalFrozenContexts() throws Exception {
        Path root = Path.of("..").toRealPath();
        Path folder = root.resolve("docs/evaluation");
        String outputName = System.getenv("MEDIASSIST_EVIDENCE_SELECTION_OUTPUT");
        assertThat(outputName).isNotBlank();
        Path output = Path.of(outputName).toAbsolutePath().normalize();
        assertThat(output.getParent()).isEqualTo(folder);
        assertThat(Files.exists(output)).isFalse();
        JsonNode frozen = read(folder, "qa_source_evidence_evaluation.json");
        JsonNode baseline = read(folder, "rag_chunk_boundary_comparison_v2.json");
        JsonNode isolation = read(folder, "rag_prompt_evaluation_results_v3.json").path("documents").path("isolation");
        JsonNode controls = read(folder, "qa_selection_development_controls.json");
        String baselineName = System.getenv("MEDIASSIST_EVIDENCE_SELECTION_BASELINE");
        if (baselineName != null) {
            Path reference = Path.of(baselineName).toAbsolutePath().normalize();
            assertThat(reference.getParent()).isEqualTo(folder);
            replayBaseline = mapper.readTree(reference.toFile());
            assertThat(replayBaseline.path("pairs").size()).isEqualTo(46);
            assertThat(replayBaseline.path("frozenInputSha256").asText()).isEqualTo(hash(folder.resolve("qa_source_evidence_evaluation.json")));
            assertThat(replayBaseline.path("newControlsSha256").asText()).isEqualTo(hash(folder.resolve("qa_selection_development_controls.json")));
        }
        UUID mainId = UUID.fromString(baseline.path("main_document_id").asText());
        UUID isolationId = UUID.fromString(isolation.path("document_id").asText());
        verifyChunks(mainId, baseline.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        String vectorHash = vectorHash(mainId, isolationId);
        assertThat(llmSettings.getProvider()).isEqualTo("ollama");
        assertThat(llmSettings.getModelName()).isEqualTo("qwen3.5:4b");
        assertThat(llmSettings.getTemperature()).isEqualTo(0.2);
        assertThat(llmSettings.getMaxOutputTokens()).isEqualTo(800);
        assertThat(llmSettings.getContextWindowTokens()).isEqualTo(8192);
        if (replayBaseline != null) {
            JsonNode settings = replayBaseline.path("settings");
            assertThat(settings.path("model").asText()).isEqualTo(llmSettings.getModelName());
            assertThat(settings.path("temperature").asDouble()).isEqualTo(llmSettings.getTemperature());
            assertThat(settings.path("maxOutputTokens").asInt()).isEqualTo(llmSettings.getMaxOutputTokens());
            assertThat(settings.path("contextWindowTokens").asInt()).isEqualTo(llmSettings.getContextWindowTokens());
            assertThat(settings.path("batchSize").asInt()).isEqualTo(evidenceSettings.getSelectionBatchSize());
            assertThat(settings.path("maxSelectedPassages").asInt()).isEqualTo(evidenceSettings.getMaxItems());
            assertThat(settings.path("maxAnswerCharacters").asInt()).isEqualTo(evidenceSettings.getMaxAnswerCharacters());
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("startedAt", LocalDateTime.now().toString());
        result.put("scope", "Fixed-context fictional development comparison. Same model, sources and limits. Single mode uses the unchanged ID-selection prompt; batched mode grades every passage. No retrieval, embedding, audit or data writes; not HTTP replay or clinical validation.");
        result.put("baselineReplayed", replayBaseline != null);
        if (baselineName != null) {
            result.put("baselineSha256", hash(Path.of(baselineName)));
        }
        result.put("frozenInputSha256", hash(folder.resolve("qa_source_evidence_evaluation.json")));
        result.put("newControlsSha256", hash(folder.resolve("qa_selection_development_controls.json")));
        ObjectNode hashes = result.putObject("sourceSha256");
        for (String file : List.of("application/DocumentEvidenceSelectionService.java", "application/DocumentEvidenceApplicationService.java",
            "application/DocumentEvidencePromptBuilder.java", "application/DocumentEvidenceContextBuilder.java",
            "application/DocumentEvidenceValidator.java", "application/DocumentEvidenceAnswerRenderer.java",
            "infrastructure/evidence/LlmDocumentEvidenceExtractor.java", "application/DocumentEvidenceScoringPromptBuilder.java",
            "infrastructure/evidence/LlmDocumentEvidenceScorer.java")) {
            String currentHash = hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/" + file));
            hashes.put(file, currentHash);
            if (frozen.path("sourceSha256").has(file)) {
                if (!file.equals("application/DocumentEvidenceApplicationService.java")) {
                    assertThat(currentHash).isEqualTo(frozen.path("sourceSha256").path(file).asText());
                }
            }
        }
        result.set("settings", mapper.valueToTree(Map.of("model", llmSettings.getModelName(), "temperature", llmSettings.getTemperature(),
            "maxOutputTokens", llmSettings.getMaxOutputTokens(), "contextWindowTokens", llmSettings.getContextWindowTokens(),
            "batchSize", evidenceSettings.getSelectionBatchSize(), "maxSelectedPassages", evidenceSettings.getMaxItems(),
            "maxAnswerCharacters", evidenceSettings.getMaxAnswerCharacters(), "selectionStrategy", "complete_ordinal_ratings")));
        result.set("predeclaredAcceptance", controls.path("acceptanceDeclaredBeforeRun"));
        ArrayNode pairs = result.putArray("pairs");
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);

        int sequence = 0;
        for (String group : List.of("oracleRuns", "retrievalRuns", "adversarialRuns")) {
            for (JsonNode old : frozen.path(group)) {
                List<SemanticSearchMatch> sources = mapper.convertValue(old.path("sources"), new TypeReference<>() {});
                ObjectNode pair = pair(pairs, group, old.path("caseId").asText(), old.path("question").asText(), sources);
                if (old.has("topK")) {
                    pair.put("topK", old.path("topK").asInt());
                }
                evaluatePair(pair, sources, sequence++, output, result);
            }
        }
        for (JsonNode control : controls.path("cases")) {
            List<SemanticSearchMatch> sources = new ArrayList<>();
            for (int index = 0; index < control.path("chunks").size(); index++) {
                UUID id = UUID.nameUUIDFromBytes((control.path("id").asText() + index).getBytes(StandardCharsets.UTF_8));
                sources.add(new SemanticSearchMatch(id, index, control.path("chunks").get(index).asText(), 0.7, "fixture"));
            }
            ObjectNode pair = pair(pairs, "newControls", control.path("id").asText(), control.path("question").asText(), sources);
            evaluatePair(pair, sources, sequence++, output, result);
        }
        JsonNode broad = java.util.stream.StreamSupport.stream(frozen.path("retrievalRuns").spliterator(), false)
            .filter(run -> run.path("caseId").asText().equals("Q08") && run.path("topK").asInt() == 10).findFirst().orElseThrow();
        for (String order : List.of("reversed", "rotated")) {
            List<SemanticSearchMatch> sources = new ArrayList<>(mapper.convertValue(broad.path("sources"), new TypeReference<List<SemanticSearchMatch>>() {}));
            if (order.equals("reversed")) {
                Collections.reverse(sources);
            } else {
                Collections.rotate(sources, sources.size() / 2);
            }
            ObjectNode pair = pair(pairs, "orderProbes", "Q08", broad.path("question").asText(), sources);
            pair.put("topK", 10);
            pair.put("order", order);
            evaluatePair(pair, sources, sequence++, output, result);
        }
        verifyChunks(mainId, baseline.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        assertThat(vectorHash(mainId, isolationId)).isEqualTo(vectorHash);
        result.put("storedChunksAndVectorsUnchanged", true);
        result.put("finishedAt", LocalDateTime.now().toString());
        save(output, result);
        assertThat(pairs).hasSize(46).allSatisfy(pair -> {
            assertThat(pair.path("single").path("success").asBoolean()).isTrue();
            assertThat(pair.path("batched").path("success").asBoolean()).isTrue();
        });
    }

    private ObjectNode pair(ArrayNode pairs, String group, String id, String question, List<SemanticSearchMatch> sources) {
        ObjectNode pair = pairs.addObject();
        pair.put("group", group);
        pair.put("caseId", id);
        pair.put("question", question);
        pair.set("sources", mapper.valueToTree(sources));
        var context = contextBuilder.build(question, sources);
        pair.set("catalog", mapper.valueToTree(context.passages()));
        pair.put("catalogLimited", context.limited());
        return pair;
    }

    private void evaluatePair(ObjectNode pair, List<SemanticSearchMatch> sources, int sequence, Path output, ObjectNode result) throws Exception {
        if (replayBaseline != null) {
            JsonNode reference = java.util.stream.StreamSupport.stream(replayBaseline.path("pairs").spliterator(), false)
                .filter(old -> old.path("group").equals(pair.path("group")) && old.path("caseId").equals(pair.path("caseId"))
                    && old.path("topK").equals(pair.path("topK")) && old.path("order").equals(pair.path("order")))
                .findFirst().orElseThrow();
            assertThat(reference.path("sources")).isEqualTo(pair.path("sources"));
            assertThat(reference.path("catalog")).isEqualTo(pair.path("catalog"));
            assertThat(reference.path("question")).isEqualTo(pair.path("question"));
            assertThat(reference.path("single").path("success").asBoolean()).isTrue();
            pair.set("single", reference.path("single").deepCopy());
        }
        List<Boolean> modes = replayBaseline != null ? List.of(true) : sequence % 2 == 0 ? List.of(false, true) : List.of(true, false);
        for (boolean batched : modes) {
            String mode = batched ? "batched" : "single";
            System.out.println("Selection comparison: " + pair.path("group") + " " + pair.path("caseId") + " " + pair.path("topK") + " " + mode);
            evidenceSettings.setBatchSelectionEnabled(batched);
            extractor.calls.clear();
            scorer.calls.clear();
            ObjectNode trial = pair.putObject(mode);
            long start = System.nanoTime();
            try {
                var draft = answers.generateAnswer(pair.path("question").asText(), sources);
                trial.set("draft", mapper.valueToTree(draft));
                var catalog = contextBuilder.build(pair.path("question").asText(), sources).passages();
                List<String> rendered = validateQuotes(draft.content(), catalog, sources, batched ? scorer.calls : extractor.calls);
                assertThat(rendered).hasSize(draft.evidenceCount());
                trial.put("renderedEvidenceText", String.join("\n", rendered));
                trial.put("exactQuoteAndCitationChecksPassed", true);
                trial.put("success", true);
            } catch (RuntimeException exception) {
                trial.put("success", false);
                trial.put("errorType", exception.getClass().getSimpleName());
            }
            List<SelectionCall> calls = batched ? scorer.calls : extractor.calls;
            trial.set("calls", mapper.valueToTree(calls));
            trial.put("seconds", (System.nanoTime() - start) / 1e9);
            if (batched) {
                assertThat(calls.size()).isLessThanOrEqualTo((pair.path("catalog").size() + 7) / 8);
            }
            save(output, result);
        }
    }

    private List<String> validateQuotes(String answer, List<DocumentEvidenceItem> catalog, List<SemanticSearchMatch> sources, List<SelectionCall> calls) {
        List<String> selected = calls.stream().flatMap(call -> call.selection().passageIds().stream()).toList();
        var quotes = QUOTE.matcher(answer);
        List<String> rendered = new ArrayList<>();
        while (quotes.find()) {
            String quote = unescape(quotes.group(1));
            int citation = Integer.parseInt(quotes.group(2));
            assertThat(citation).isBetween(1, sources.size());
            var source = sources.get(citation - 1);
            var passage = catalog.stream().filter(item -> selected.contains(item.passageId()) && item.citationNumber() == citation
                && item.quote().replaceAll("\\s+", " ").equals(quote)).findFirst().orElseThrow();
            assertThat(passage.chunkId()).isEqualTo(source.chunkId());
            assertThat(passage.chunkIndex()).isEqualTo(source.chunkIndex());
            assertThat(passage.quote()).isEqualTo(source.chunkText().substring(passage.startOffset(), passage.endOffset()));
            rendered.add(quote);
        }
        if (rendered.isEmpty()) {
            assertThat(selected).isEmpty();
            assertThat(answer).contains("does not establish absence from the whole document");
        }
        return rendered;
    }

    private String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\\' && index + 1 < text.length() && "\\`*_{}[]()<>#+-!.|~".indexOf(text.charAt(index + 1)) >= 0) {
                character = text.charAt(++index);
            }
            result.append(character);
        }
        return result.toString();
    }

    private void verifyChunks(UUID documentId, JsonNode saved) {
        var actual = chunks.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(documentId);
        assertThat(actual).hasSize(saved.size());
        for (int index = 0; index < actual.size(); index++) {
            assertThat(actual.get(index).getId().toString()).isEqualTo(saved.get(index).path("chunkId").asText());
            assertThat(actual.get(index).getChunkIndex()).isEqualTo(saved.get(index).path("chunkIndex").asInt());
            assertThat(actual.get(index).getChunkText()).isEqualTo(saved.get(index).path("chunkText").asText());
        }
    }

    private String vectorHash(UUID mainId, UUID isolationId) {
        return jdbc.queryForObject("""
            select md5(string_agg(embedding.id::text || embedding.embedding::text || embedding.model_name, ',' order by embedding.id))
            from document_chunk_embeddings embedding
            join document_chunks chunk on chunk.id = embedding.chunk_id
            join document_extractions extraction on extraction.id = chunk.document_extraction_id
            where extraction.document_id in (?, ?)
            """, String.class, mainId, isolationId);
    }

    private JsonNode read(Path folder, String name) throws Exception {
        return mapper.readTree(folder.resolve(name).toFile());
    }

    private String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private void save(Path output, ObjectNode result) throws Exception {
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
    }

    @TestConfiguration
    static class RecordingConfiguration {
        @Bean
        @Primary
        RecordingExtractor recordingExtractor(LlmDocumentEvidenceExtractor delegate) {
            return new RecordingExtractor(delegate);
        }

        @Bean
        @Primary
        RecordingScorer recordingScorer(LlmDocumentEvidenceScorer delegate) {
            return new RecordingScorer(delegate);
        }
    }

    record SelectionCall(List<String> requestedPassageIds, DocumentEvidenceSelection selection,
                         List<DocumentEvidenceRating> ratings, double seconds) {
    }

    static class RecordingExtractor implements DocumentEvidenceExtractor {
        private final LlmDocumentEvidenceExtractor delegate;
        private final List<SelectionCall> calls = new ArrayList<>();

        RecordingExtractor(LlmDocumentEvidenceExtractor delegate) {
            this.delegate = delegate;
        }

        @Override
        public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages) {
            long start = System.nanoTime();
            DocumentEvidenceSelection selection = delegate.selectEvidence(question, passages);
            calls.add(new SelectionCall(passages.stream().map(DocumentEvidenceItem::passageId).toList(), selection, List.of(),
                (System.nanoTime() - start) / 1e9));
            return selection;
        }
    }

    static class RecordingScorer implements DocumentEvidenceScorer {
        private final LlmDocumentEvidenceScorer delegate;
        private final List<SelectionCall> calls = new ArrayList<>();

        RecordingScorer(LlmDocumentEvidenceScorer delegate) {
            this.delegate = delegate;
        }

        @Override
        public DocumentEvidenceAssessment assessEvidence(String question, List<DocumentEvidenceItem> passages) {
            long start = System.nanoTime();
            DocumentEvidenceAssessment assessment = delegate.assessEvidence(question, passages);
            var ids = assessment.ratings().stream().filter(rating -> rating.relevance() >= 2).map(DocumentEvidenceRating::passageId).toList();
            calls.add(new SelectionCall(passages.stream().map(DocumentEvidenceItem::passageId).toList(),
                new DocumentEvidenceSelection(assessment.modelName(), ids), assessment.ratings(), (System.nanoTime() - start) / 1e9));
            return assessment;
        }
    }
}
