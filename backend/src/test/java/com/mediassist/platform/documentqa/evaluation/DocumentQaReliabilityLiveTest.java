package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.application.DocumentEmbeddingApplicationService;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.application.DocumentQaApplicationService;
import com.mediassist.platform.documentqa.application.DocumentQaPromptBuilder;
import com.mediassist.platform.documentqa.application.LlmClient;
import com.mediassist.platform.documentqa.application.LlmCompletionRequest;
import com.mediassist.platform.documentqa.application.QuestionPlanner;
import com.mediassist.platform.documentqa.domain.QuestionPlan;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.retrieval.LlmQuestionPlanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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

@EnabledIfEnvironmentVariable(named = "MEDIASSIST_QA_LIVE_EVALUATION", matches = "true")
@SpringBootTest(properties = {"mediassist.qa.facets.enabled=true", "mediassist.qa.retrieval.diversity-enabled=false"})
@Import(DocumentQaReliabilityLiveTest.PlannerRecordingConfiguration.class)
class DocumentQaReliabilityLiveTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private DocumentChunkRepository chunks;
    @Autowired private DocumentEmbeddingApplicationService embeddings;
    @Autowired private DocumentQaApplicationService qa;
    @Autowired private DocumentQaPromptBuilder prompt;
    @Autowired private LlmClient llm;
    @Autowired private DocumentQaProperties settings;
    @Autowired private RecordingPlanner planner;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldEvaluateOnlyTheUnchangedFictionalFixtures() throws Exception {
        Path root = Path.of("..").toRealPath();
        Path folder = root.resolve("docs/evaluation");
        String outputName = System.getenv("MEDIASSIST_QA_EVALUATION_OUTPUT");
        assertThat(outputName).isNotBlank();
        Path output = Path.of(outputName).toAbsolutePath().normalize();
        assertThat(output.getParent()).isEqualTo(folder);
        assertThat(Files.exists(output)).isFalse();
        JsonNode baseline = mapper.readTree(folder.resolve("rag_chunk_boundary_comparison_v2.json").toFile());
        JsonNode labels = mapper.readTree(folder.resolve("qa_evidence_labels.json").toFile());
        JsonNode fixture = mapper.readTree(folder.resolve("synthetic_records.json").toFile());
        JsonNode isolation = mapper.readTree(folder.resolve("rag_prompt_evaluation_results_v3.json").toFile()).path("documents").path("isolation");
        UUID mainId = UUID.fromString(labels.path("main_document_id").asText());
        assertThat(mainId.toString()).isEqualTo(baseline.path("main_document_id").asText());
        UUID isolationId = UUID.fromString(isolation.path("document_id").asText());
        verifyStoredChunks(mainId, baseline.path("main_chunks"));
        verifyStoredChunks(isolationId, isolation.path("chunks"));
        String storedVectorHash = vectorHash(mainId, isolationId);
        String promptHash = hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaPromptBuilder.java"));
        assertThat(promptHash).isEqualTo(baseline.path("source_sha256").path("prompt_builder").asText());
        assertThat(settings.getProvider()).isEqualTo("ollama");
        assertThat(settings.getModelName()).isEqualTo("qwen3.5:4b");
        assertThat(settings.getTemperature()).isEqualTo(0.2);
        assertThat(settings.getMaxOutputTokens()).isEqualTo(800);
        assertThat(settings.getContextWindowTokens()).isEqualTo(8192);

        Map<String, JsonNode> caseLabels = new HashMap<>();
        labels.path("cases").forEach(node -> caseLabels.put(node.path("id").asText(), node));
        Map<String, String> questions = new HashMap<>();
        fixture.path("questions").forEach(node -> questions.put(node.path("id").asText(), node.path("question").asText()));
        ObjectNode result = mapper.createObjectNode();
        result.put("startedAt", LocalDateTime.now().toString());
        result.put("scope", "Real Spring application services and providers, mock servlet context; not HTTP endpoint replay. Existing fictional fixtures only. Normal QA success audits are written; no chunk/vector writes.");
        result.put("promptSha256", promptHash);
        result.put("labelSha256", hash(folder.resolve("qa_evidence_labels.json")));
        ObjectNode sourceHashes = result.putObject("sourceSha256");
        for (String file : List.of("documentqa/infrastructure/retrieval/LlmQuestionPlanner.java",
            "documentqa/infrastructure/client/OllamaLlmClient.java", "documentqa/application/LlmCompletionRequest.java",
            "documentqa/application/FacetContextRetrievalService.java", "documentqa/application/ReciprocalRankFusion.java",
            "documentqa/application/FacetContextSelector.java", "documentqa/application/DocumentQaContextRetrievalService.java",
            "documentembedding/application/DocumentEmbeddingApplicationService.java")) {
            sourceHashes.put(file, hash(root.resolve("backend/src/main/java/com/mediassist/platform/" + file)));
        }
        result.set("llmSettings", mapper.valueToTree(Map.of("model", settings.getModelName(), "temperature", settings.getTemperature(),
            "maxOutputTokens", settings.getMaxOutputTokens(), "contextWindowTokens", settings.getContextWindowTokens())));
        ArrayNode oracleRuns = result.putArray("oracleRuns");
        ArrayNode retrievalRuns = result.putArray("retrievalRuns");
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);

        for (JsonNode caseId : labels.path("oracle_cases")) {
            String id = caseId.asText();
            String question = questions.get(id);
            List<SemanticSearchMatch> candidates = embeddings.searchSimilarChunkCandidates(mainId, question, 100)
                .stream().map(candidate -> candidate.match()).toList();
            List<SemanticSearchMatch> selected = new ArrayList<>();
            for (JsonNode index : caseLabels.get(id).path("oracle_chunk_indices")) {
                selected.add(candidates.stream().filter(candidate -> candidate.chunkIndex() == index.asInt()).findFirst().orElseThrow());
            }
            ObjectNode run = oracleRuns.addObject();
            run.put("caseId", id);
            run.put("question", question);
            run.set("sources", mapper.valueToTree(selected));
            run.set("evidenceCoverage", evidenceCoverage(selected, caseLabels.get(id)));
            if (!caseLabels.get(id).path("required_evidence").isEmpty()) {
                assertThat(run.path("evidenceCoverage").path("allNeedsCovered").asBoolean()).isTrue();
            }
            System.out.println("Oracle context: " + id);
            long start = System.nanoTime();
            try {
                run.set("completion", mapper.valueToTree(llm.complete(new LlmCompletionRequest(settings.getModelName(),
                    prompt.buildMessages(question, selected), settings.getTemperature(), settings.getMaxOutputTokens()))));
                run.put("success", true);
            } catch (RuntimeException exception) {
                run.put("success", false);
                run.put("errorType", exception.getClass().getSimpleName());
            }
            run.put("seconds", (System.nanoTime() - start) / 1e9);
            save(output, result);
        }

        String actor = "facet-evaluation-" + LocalDateTime.now();
        for (JsonNode previous : baseline.path("rag_runs")) {
            String id = previous.path("case_id").asText();
            boolean isIsolation = "ISO".equals(id);
            UUID documentId = isIsolation ? isolationId : mainId;
            String question = previous.path("question").asText();
            int topK = previous.path("top_k").asInt();
            Map<UUID, SemanticSearchMatch> sourceMap = sources(isIsolation ? isolation.path("chunks") : baseline.path("main_chunks"));
            ObjectNode run = retrievalRuns.addObject();
            run.put("caseId", id);
            run.put("topK", topK);
            run.put("question", question);
            List<SemanticSearchMatch> baselineMatches = new ArrayList<>();
            previous.path("response").path("sources").forEach(source -> baselineMatches.add(sourceMap.get(UUID.fromString(source.path("chunkId").asText()))));
            run.set("baselineEvidenceCoverage", evidenceCoverage(baselineMatches, caseLabels.get(id)));
            run.set("baselineResponse", previous.path("response"));
            planner.lastPlan = null;
            planner.failed = false;
            planner.failureReason = null;
            System.out.println("Facet retrieval: " + id + " topK=" + topK);
            long start = System.nanoTime();
            try {
                var response = qa.answerQuestion(documentId, new DocumentQuestionRequest(question, topK), actor);
                run.set("response", mapper.valueToTree(response));
                run.put("success", true);
                List<SemanticSearchMatch> selected = response.sources().stream().map(source -> sourceMap.get(source.chunkId())).toList();
                assertThat(selected).doesNotContainNull();
                assertThat(selected).hasSizeLessThanOrEqualTo(topK);
                run.set("evidenceCoverage", evidenceCoverage(selected, caseLabels.get(id)));
                run.put("sourcesInDocument", true);
            } catch (RuntimeException exception) {
                run.put("success", false);
                run.put("errorType", exception.getClass().getSimpleName());
            }
            run.set("plan", mapper.valueToTree(planner.lastPlan));
            run.put("planningFallback", planner.failed);
            run.put("planningFailureReason", planner.failureReason);
            run.put("seconds", (System.nanoTime() - start) / 1e9);
            save(output, result);
        }
        verifyStoredChunks(mainId, baseline.path("main_chunks"));
        verifyStoredChunks(isolationId, isolation.path("chunks"));
        assertThat(vectorHash(mainId, isolationId)).isEqualTo(storedVectorHash);
        result.put("storedChunksAndVectorsUnchanged", true);
        result.put("finishedAt", LocalDateTime.now().toString());
        save(output, result);
        assertThat(oracleRuns).allSatisfy(run -> assertThat(run.path("success").asBoolean()).isTrue());
        assertThat(retrievalRuns).allSatisfy(run -> assertThat(run.path("success").asBoolean()).isTrue());
    }

    private ObjectNode evidenceCoverage(List<SemanticSearchMatch> selected, JsonNode label) {
        ObjectNode coverage = mapper.createObjectNode();
        ArrayNode needs = coverage.putArray("needs");
        String context = normalize(String.join("\n", selected.stream().map(SemanticSearchMatch::chunkText).toList()));
        if (label != null) {
            for (JsonNode need : label.path("required_evidence")) {
                ObjectNode item = needs.addObject();
                item.put("facet", need.path("facet").asText());
                ArrayNode missing = item.putArray("missingSpans");
                need.path("spans").forEach(span -> { if (!context.contains(normalize(span.asText()))) missing.add(span.asText()); });
                item.put("covered", missing.isEmpty());
            }
        }
        coverage.put("allNeedsCovered", !needs.isEmpty() && java.util.stream.StreamSupport.stream(needs.spliterator(), false)
            .allMatch(need -> need.path("covered").asBoolean()));
        coverage.put("semanticClaimReviewRequired", true);
        return coverage;
    }

    private void verifyStoredChunks(UUID documentId, JsonNode saved) {
        var actual = chunks.findAllByDocumentExtractionDocumentIdOrderByChunkIndexAsc(documentId);
        assertThat(actual).hasSize(saved.size());
        for (int index = 0; index < actual.size(); index++) {
            assertThat(actual.get(index).getId().toString()).isEqualTo(saved.get(index).path("chunkId").asText());
            assertThat(actual.get(index).getChunkIndex()).isEqualTo(saved.get(index).path("chunkIndex").asInt());
            assertThat(actual.get(index).getChunkText()).isEqualTo(saved.get(index).path("chunkText").asText());
        }
    }

    private Map<UUID, SemanticSearchMatch> sources(JsonNode saved) {
        Map<UUID, SemanticSearchMatch> result = new HashMap<>();
        saved.forEach(chunk -> {
            UUID id = UUID.fromString(chunk.path("chunkId").asText());
            result.put(id, new SemanticSearchMatch(id, chunk.path("chunkIndex").asInt(), chunk.path("chunkText").asText(), 0.0, "BAAI/bge-m3"));
        });
        return result;
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

    private String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void save(Path output, ObjectNode result) throws Exception {
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
    }

    @TestConfiguration
    static class PlannerRecordingConfiguration {
        @Bean @Primary
        RecordingPlanner recordingPlanner(LlmQuestionPlanner delegate) {
            return new RecordingPlanner(delegate);
        }
    }

    static class RecordingPlanner implements QuestionPlanner {
        private final LlmQuestionPlanner delegate;
        private QuestionPlan lastPlan;
        private boolean failed;
        private String failureReason;
        RecordingPlanner(LlmQuestionPlanner delegate) { this.delegate = delegate; }
        @Override
        public QuestionPlan plan(String question) {
            try {
                lastPlan = delegate.plan(question);
                return lastPlan;
            } catch (RuntimeException exception) {
                failed = true;
                failureReason = exception.getMessage();
                throw exception;
            }
        }
    }
}
