package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentembedding.domain.SemanticSearchQueryResult;
import com.mediassist.platform.documentqa.application.DocumentAnswerGenerationService;
import com.mediassist.platform.documentqa.application.DocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.application.DocumentQaContextRetrievalService;
import com.mediassist.platform.documentqa.application.QuestionPlanner;
import com.mediassist.platform.documentqa.application.QuestionPlanningException;
import com.mediassist.platform.documentqa.application.ReciprocalRankFusion;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.domain.QuestionPlan;
import com.mediassist.platform.documentqa.domain.RankedContextCandidate;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.DocumentEvidenceProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.LlmDocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.infrastructure.retrieval.FacetRetrievalProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.LlmQuestionPlanner;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "MEDIASSIST_QA_INTEGRATION_EVALUATION", matches = "true")
@SpringBootTest(properties = {"mediassist.qa.evidence.enabled=true", "mediassist.qa.evidence.batch-selection-enabled=false",
    "mediassist.qa.retrieval.diversity-enabled=false", "mediassist.qa.facets.enabled=false"})
@Import(DocumentQaRetrievalIntegrationLiveTest.RecordingConfiguration.class)
class DocumentQaRetrievalIntegrationLiveTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private DocumentQaContextRetrievalService retrieval;
    @Autowired private DocumentAnswerGenerationService answers;
    @Autowired private DocumentChunkRepository chunks;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentQaProperties llmSettings;
    @Autowired private DocumentEvidenceProperties evidenceSettings;
    @Autowired private FacetRetrievalProperties facetSettings;
    @Autowired private RecordingPlanner planner;
    @Autowired private RecordingFusion fusion;
    @Autowired private RecordingExtractor extractor;
    private JsonNode reference;

    @Test
    void shouldCompareRetrievalAndRenderedEvidenceWithGradingDisabled() throws Exception {
        Path root = Path.of("..").toRealPath();
        Path folder = root.resolve("docs/evaluation");
        String outputName = System.getenv("MEDIASSIST_QA_INTEGRATION_OUTPUT");
        assertThat(outputName).isNotBlank();
        Path output = Path.of(outputName).toAbsolutePath().normalize();
        assertThat(output.getParent()).isEqualTo(folder);
        assertThat(Files.exists(output)).isFalse();
        JsonNode frozen = read(folder, "qa_source_evidence_evaluation.json");
        JsonNode saved = read(folder, "rag_chunk_boundary_comparison_v2.json");
        JsonNode isolation = read(folder, "rag_prompt_evaluation_results_v3.json").path("documents").path("isolation");
        UUID mainId = UUID.fromString(saved.path("main_document_id").asText());
        UUID isolationId = UUID.fromString(isolation.path("document_id").asText());
        verifyChunks(mainId, saved.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        String vectorHash = vectorHash(mainId, isolationId);
        verifySettings();

        ObjectNode result = mapper.createObjectNode();
        result.put("startedAt", LocalDateTime.now().toString());
        result.put("scope", "Fictional development integration: real scoped retrieval/query embeddings and original ID selection, mock servlet context, no HTTP replay, audits or chunk/vector writes. Controls test selection only, not persisted control-document retrieval. No clinical accuracy claim.");
        for (String name : List.of("qa_source_evidence_evaluation.json", "qa_evidence_labels.json",
            "qa_evidence_adversarial_controls.json", "qa_selection_development_controls.json", "qa_retrieval_integration_protocol.json")) {
            result.put(name + "Sha256", hash(folder.resolve(name)));
        }
        result.set("acceptance", read(folder, "qa_retrieval_integration_protocol.json").path("acceptance"));
        result.set("settings", mapper.valueToTree(Map.of("llmModel", llmSettings.getModelName(), "temperature", llmSettings.getTemperature(),
            "maxOutputTokens", llmSettings.getMaxOutputTokens(), "contextWindowTokens", llmSettings.getContextWindowTokens(),
            "evidence", evidenceSettings, "facets", facetSettings)));
        ObjectNode hashes = result.putObject("sourceSha256");
        for (String file : List.of("application/FacetContextSelector.java", "application/FacetContextRetrievalService.java",
            "application/ReciprocalRankFusion.java", "application/DocumentQaContextRetrievalService.java",
            "infrastructure/retrieval/LlmQuestionPlanner.java", "application/DocumentEvidencePromptBuilder.java",
            "application/DocumentEvidenceContextBuilder.java", "application/DocumentEvidenceValidator.java",
            "application/DocumentEvidenceAnswerRenderer.java", "infrastructure/evidence/LlmDocumentEvidenceExtractor.java")) {
            String currentHash = hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/" + file));
            hashes.put(file, currentHash);
            if (frozen.path("sourceSha256").has(file)) {
                assertThat(currentHash).isEqualTo(frozen.path("sourceSha256").path(file).asText());
            }
        }
        String referenceName = System.getenv("MEDIASSIST_QA_INTEGRATION_REFERENCE");
        result.put("referenceReplayed", referenceName != null);
        if (referenceName != null) {
            Path path = Path.of(referenceName).toAbsolutePath().normalize();
            assertThat(path.getParent()).isEqualTo(folder);
            reference = mapper.readTree(path.toFile());
            assertThat(reference.path("pairs").size()).isEqualTo(21);
            for (String name : List.of("qa_source_evidence_evaluation.json", "qa_evidence_labels.json",
                "qa_evidence_adversarial_controls.json", "qa_selection_development_controls.json", "qa_retrieval_integration_protocol.json")) {
                assertThat(reference.path(name + "Sha256")).isEqualTo(result.path(name + "Sha256"));
            }
            assertThat(reference.path("settings")).isEqualTo(result.path("settings"));
            result.put("referenceSha256", hash(path));
        }
        ArrayNode pairs = result.putArray("pairs");
        ArrayNode oracleRuns = result.putArray("oracleRuns");
        ArrayNode controls = result.putArray("controls");
        ArrayNode orderProbes = result.putArray("orderProbes");
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);

        int sequence = 0;
        for (JsonNode old : frozen.path("retrievalRuns")) {
            ObjectNode pair = pairs.addObject();
            pair.put("caseId", old.path("caseId").asText());
            pair.put("question", old.path("question").asText());
            pair.put("topK", old.path("topK").asInt());
            UUID documentId = pair.path("caseId").asText().equals("ISO") ? isolationId : mainId;
            pair.put("documentId", documentId.toString());
            JsonNode previous = reference == null ? null : referencePair(pair);
            if (previous != null) {
                pair.set("ordinary", previous.path("ordinary").deepCopy());
            }
            List<Boolean> modes = previous != null ? List.of(true) : sequence++ % 2 == 0 ? List.of(false, true) : List.of(true, false);
            for (boolean facets : modes) {
                facetSettings.setEnabled(facets);
                planner.reset(previous);
                fusion.reset();
                ObjectNode trial = pair.putObject(facets ? "facet" : "ordinary");
                System.out.println("QA integration: " + pair.path("caseId") + " topK=" + pair.path("topK") + " facets=" + facets);
                long start = System.nanoTime();
                try {
                    List<SemanticSearchMatch> sources = retrieval.retrieveContext(documentId,
                        pair.path("question").asText(), pair.path("topK").asInt());
                    trial.put("retrievalSeconds", (System.nanoTime() - start) / 1e9);
                    assertThat(sources).hasSizeLessThanOrEqualTo(pair.path("topK").asInt());
                    assertSourcesBelongToDocument(sources, documentId);
                    if (!facets) {
                        List<String> frozenIds = new ArrayList<>();
                        old.path("sources").forEach(source -> frozenIds.add(source.path("chunkId").asText()));
                        assertThat(sources.stream().map(source -> source.chunkId().toString()).toList()).isEqualTo(frozenIds);
                    }
                    if (previous != null && previous.path("facet").path("trace").path("candidates").isArray()) {
                        JsonNode currentCandidates = mapper.valueToTree(fusion.candidates);
                        assertThat(currentCandidates).isEqualTo(previous.path("facet").path("trace").path("candidates"));
                    }
                    evaluate(trial, pair.path("question").asText(), sources);
                } catch (RuntimeException exception) {
                    trial.put("success", false);
                    trial.put("errorType", exception.getClass().getSimpleName());
                    trial.put("errorMessage", exception.getMessage());
                }
                ObjectNode trace = trial.putObject("trace");
                trace.set("plan", mapper.valueToTree(planner.plan));
                trace.put("planningFallback", planner.failed);
                trace.put("planReplayed", previous != null && facets);
                trace.set("queries", mapper.valueToTree(fusion.queries));
                trace.set("candidates", mapper.valueToTree(fusion.candidates));
                trace.put("candidateStageObserved", !fusion.queries.isEmpty());
                trial.put("totalSeconds", (System.nanoTime() - start) / 1e9);
                save(output, result);
            }
        }
        for (JsonNode old : frozen.path("oracleRuns")) {
            ObjectNode run = oracleRuns.addObject();
            run.put("caseId", old.path("caseId").asText());
            evaluate(run, old.path("question").asText(), mapper.convertValue(old.path("sources"), new TypeReference<>() {}));
            save(output, result);
        }
        for (String controlFile : List.of("qa_evidence_adversarial_controls.json", "qa_selection_development_controls.json")) {
            for (JsonNode control : read(folder, controlFile).path("cases")) {
                ObjectNode run = controls.addObject();
                run.put("caseId", control.path("id").asText());
                List<SemanticSearchMatch> sources = new ArrayList<>();
                for (int index = 0; index < control.path("chunks").size(); index++) {
                    UUID id = UUID.nameUUIDFromBytes((control.path("id").asText() + index).getBytes(StandardCharsets.UTF_8));
                    sources.add(new SemanticSearchMatch(id, index, control.path("chunks").get(index).asText(), 0.7, "fixture"));
                }
                evaluate(run, control.path("question").asText(), sources);
                save(output, result);
            }
        }
        JsonNode broad = java.util.stream.StreamSupport.stream(pairs.spliterator(), false)
            .filter(pair -> pair.path("caseId").asText().equals("Q08") && pair.path("topK").asInt() == 10).findFirst().orElseThrow();
        for (String order : List.of("reversed", "rotated")) {
            List<SemanticSearchMatch> sources = new ArrayList<>(mapper.convertValue(broad.path("facet").path("sources"), new TypeReference<List<SemanticSearchMatch>>() {}));
            if (order.equals("reversed")) {
                Collections.reverse(sources);
            } else {
                Collections.rotate(sources, sources.size() / 2);
            }
            ObjectNode run = orderProbes.addObject();
            run.put("caseId", "Q08");
            run.put("order", order);
            evaluate(run, broad.path("question").asText(), sources);
            save(output, result);
        }
        verifyChunks(mainId, saved.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        assertThat(vectorHash(mainId, isolationId)).isEqualTo(vectorHash);
        result.put("storedChunksAndVectorsUnchanged", true);
        result.put("finishedAt", LocalDateTime.now().toString());
        save(output, result);
        assertThat(pairs).hasSize(21);
        for (JsonNode pair : pairs) {
            for (String mode : List.of("ordinary", "facet")) {
                assertThat(pair.path(mode).path("success").asBoolean())
                    .as("%s topK=%s %s: %s", pair.path("caseId").asText(), pair.path("topK").asInt(), mode,
                        pair.path(mode).path("errorType").asText())
                    .isTrue();
            }
        }
        for (ArrayNode runs : List.of(oracleRuns, controls, orderProbes)) {
            assertThat(runs).allSatisfy(run -> assertThat(run.path("success").asBoolean()).isTrue());
        }
    }

    private void evaluate(ObjectNode trial, String question, List<SemanticSearchMatch> sources) {
        extractor.reset();
        long start = System.nanoTime();
        trial.put("question", question);
        trial.set("sources", mapper.valueToTree(sources));
        try {
            var draft = answers.generateAnswer(question, sources);
            List<String> quotes = EvidenceQuoteInspection.inspect(draft, sources, extractor.passages, extractor.selection);
            trial.set("draft", mapper.valueToTree(draft));
            trial.set("catalog", mapper.valueToTree(extractor.passages));
            trial.set("selection", mapper.valueToTree(extractor.selection));
            trial.put("renderedEvidenceText", String.join("\n", quotes));
            trial.put("provenancePassed", true);
            trial.put("success", true);
        } catch (RuntimeException exception) {
            trial.put("success", false);
            trial.put("errorType", exception.getClass().getSimpleName());
            trial.put("errorMessage", exception.getMessage());
        } finally {
            trial.set("catalog", mapper.valueToTree(extractor.passages));
            if (extractor.selection != null) {
                trial.set("selection", mapper.valueToTree(extractor.selection));
            }
        }
        trial.put("answerSeconds", (System.nanoTime() - start) / 1e9);
    }

    private JsonNode referencePair(JsonNode pair) {
        JsonNode old = java.util.stream.StreamSupport.stream(reference.path("pairs").spliterator(), false)
            .filter(item -> item.path("caseId").equals(pair.path("caseId")) && item.path("topK").equals(pair.path("topK")))
            .findFirst().orElseThrow();
        assertThat(old.path("question")).isEqualTo(pair.path("question"));
        assertThat(old.path("documentId")).isEqualTo(pair.path("documentId"));
        return old;
    }

    private void verifySettings() {
        assertThat(llmSettings.getProvider()).isEqualTo("ollama");
        assertThat(llmSettings.getModelName()).isEqualTo("qwen3.5:4b");
        assertThat(llmSettings.getTemperature()).isEqualTo(0.2);
        assertThat(llmSettings.getMaxOutputTokens()).isEqualTo(800);
        assertThat(llmSettings.getContextWindowTokens()).isEqualTo(8192);
        assertThat(evidenceSettings.isEnabled()).isTrue();
        assertThat(evidenceSettings.isBatchSelectionEnabled()).isFalse();
    }

    private void assertSourcesBelongToDocument(List<SemanticSearchMatch> sources, UUID documentId) {
        var ids = chunks.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(documentId).stream().map(chunk -> chunk.getId()).toList();
        assertThat(sources.stream().map(SemanticSearchMatch::chunkId).toList()).doesNotHaveDuplicates().isSubsetOf(ids);
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
            from document_chunk_embeddings embedding join document_chunks chunk on chunk.id = embedding.chunk_id
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
        @Bean @Primary RecordingPlanner recordingPlanner(LlmQuestionPlanner delegate) { return new RecordingPlanner(delegate); }
        @Bean @Primary RecordingExtractor recordingExtractor(LlmDocumentEvidenceExtractor delegate) { return new RecordingExtractor(delegate); }
        @Bean @Primary RecordingFusion recordingFusion() { return new RecordingFusion(); }
    }

    static class RecordingPlanner implements QuestionPlanner {
        private final LlmQuestionPlanner delegate;
        private QuestionPlan plan;
        private boolean failed;
        private JsonNode previous;

        RecordingPlanner(LlmQuestionPlanner delegate) { this.delegate = delegate; }

        void reset(JsonNode previous) { this.previous = previous; plan = null; failed = false; }

        @Override
        public QuestionPlan plan(String question) {
            try {
                if (previous == null) {
                    plan = delegate.plan(question);
                } else if (previous.path("facet").path("trace").path("planningFallback").asBoolean()) {
                    throw new QuestionPlanningException("Replayed planning fallback");
                } else {
                    JsonNode saved = previous.path("facet").path("trace").path("plan");
                    var facets = new ArrayList<com.mediassist.platform.documentqa.domain.QueryFacet>();
                    saved.path("facets").forEach(item -> facets.add(new com.mediassist.platform.documentqa.domain.QueryFacet(
                        item.path("name").asText(), item.path("query").asText())));
                    plan = new QuestionPlan(question, facets);
                }
                return plan;
            } catch (QuestionPlanningException exception) {
                failed = true;
                throw exception;
            }
        }
    }

    static class RecordingFusion extends ReciprocalRankFusion {
        private List<String> queries = List.of();
        private List<RankedContextCandidate> candidates = List.of();

        void reset() { queries = List.of(); candidates = List.of(); }

        @Override
        public List<RankedContextCandidate> fuse(List<SemanticSearchQueryResult> batches, int constant) {
            queries = batches.stream().map(SemanticSearchQueryResult::query).toList();
            candidates = super.fuse(batches, constant);
            return candidates;
        }
    }

    static class RecordingExtractor implements DocumentEvidenceExtractor {
        private final LlmDocumentEvidenceExtractor delegate;
        private List<DocumentEvidenceItem> passages = List.of();
        private DocumentEvidenceSelection selection;

        RecordingExtractor(LlmDocumentEvidenceExtractor delegate) { this.delegate = delegate; }
        void reset() { passages = List.of(); selection = null; }

        @Override
        public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> supplied) {
            passages = List.copyOf(supplied);
            selection = delegate.selectEvidence(question, supplied);
            return selection;
        }
    }
}
