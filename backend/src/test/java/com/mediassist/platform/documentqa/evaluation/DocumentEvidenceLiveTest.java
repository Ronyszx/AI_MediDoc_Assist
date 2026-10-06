package com.mediassist.platform.documentqa.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassist.platform.documentchunk.domain.DocumentChunkRepository;
import com.mediassist.platform.documentembedding.domain.SemanticSearchMatch;
import com.mediassist.platform.documentqa.api.dto.DocumentQuestionRequest;
import com.mediassist.platform.documentqa.application.DocumentAnswerGenerationService;
import com.mediassist.platform.documentqa.application.DocumentEvidenceExtractor;
import com.mediassist.platform.documentqa.application.DocumentQaApplicationService;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import com.mediassist.platform.documentqa.infrastructure.client.DocumentQaProperties;
import com.mediassist.platform.documentqa.infrastructure.evidence.LlmDocumentEvidenceExtractor;
import java.nio.charset.StandardCharsets;
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

@EnabledIfEnvironmentVariable(named = "MEDIASSIST_QA_EVIDENCE_EVALUATION", matches = "true")
@SpringBootTest(properties = {"mediassist.qa.evidence.enabled=true", "mediassist.qa.facets.enabled=false",
    "mediassist.qa.retrieval.diversity-enabled=false"})
@Import(DocumentEvidenceLiveTest.RecordingConfiguration.class)
class DocumentEvidenceLiveTest {
    private static final Pattern QUOTE = Pattern.compile("(?m)^- \"(.*)\" \\[Chunk ([0-9]+)]$");
    @Autowired private ObjectMapper mapper;
    @Autowired private DocumentAnswerGenerationService answers;
    @Autowired private DocumentQaApplicationService qa;
    @Autowired private RecordingExtractor extractor;
    @Autowired private DocumentChunkRepository chunks;
    @Autowired private DocumentQaProperties settings;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldEvaluateProvenanceAndSelectionWithoutChangingStoredChunksOrVectors() throws Exception {
        Path root = Path.of("..").toRealPath();
        Path folder = root.resolve("docs/evaluation");
        String outputName = System.getenv("MEDIASSIST_QA_EVIDENCE_OUTPUT");
        assertThat(outputName).isNotBlank();
        Path output = Path.of(outputName).toAbsolutePath().normalize();
        assertThat(output.getParent()).isEqualTo(folder);
        assertThat(Files.exists(output)).isFalse();
        JsonNode baseline = read(folder, "rag_chunk_boundary_comparison_v2.json");
        JsonNode oracle = read(folder, "qa_facet_oracle_comparison_v2.json");
        JsonNode labels = read(folder, "qa_evidence_labels.json");
        JsonNode controls = read(folder, "qa_evidence_adversarial_controls.json");
        JsonNode isolation = read(folder, "rag_prompt_evaluation_results_v3.json").path("documents").path("isolation");
        UUID mainId = UUID.fromString(baseline.path("main_document_id").asText());
        UUID isolationId = UUID.fromString(isolation.path("document_id").asText());
        verifyChunks(mainId, baseline.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        String vectorHash = vectorHash(mainId, isolationId);
        String oldPromptHash = hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaPromptBuilder.java"));
        assertThat(oldPromptHash).isEqualTo(baseline.path("source_sha256").path("prompt_builder").asText());
        assertThat(settings.getProvider()).isEqualTo("ollama");
        assertThat(settings.getModelName()).isEqualTo("qwen3.5:4b");
        assertThat(settings.getTemperature()).isEqualTo(0.2);
        assertThat(settings.getMaxOutputTokens()).isEqualTo(800);
        assertThat(settings.getContextWindowTokens()).isEqualTo(8192);

        Map<String, JsonNode> caseLabels = new HashMap<>();
        labels.path("cases").forEach(label -> caseLabels.put(label.path("id").asText(), label));
        ObjectNode result = mapper.createObjectNode();
        result.put("startedAt", LocalDateTime.now().toString());
        result.put("scope", "Fictional development cases, not a clinical benchmark. Real providers and application services, mock servlet context, no HTTP endpoint replay. Oracle contexts fixed; normal retrieval remains relevance-only. Only normal QA audit records are written.");
        result.put("oldPromptSha256", oldPromptHash);
        result.put("controlsSha256", hash(folder.resolve("qa_evidence_adversarial_controls.json")));
        result.set("llmSettings", mapper.valueToTree(Map.of("model", settings.getModelName(), "temperature", settings.getTemperature(),
            "maxOutputTokens", settings.getMaxOutputTokens(), "contextWindowTokens", settings.getContextWindowTokens())));
        ObjectNode hashes = result.putObject("sourceSha256");
        for (String file : List.of("application/DocumentAnswerGenerationService.java", "application/DocumentEvidenceApplicationService.java",
            "application/DocumentEvidenceContextBuilder.java", "application/DocumentEvidencePromptBuilder.java",
            "application/DocumentEvidenceValidator.java", "application/DocumentEvidenceAnswerRenderer.java",
            "infrastructure/evidence/LlmDocumentEvidenceExtractor.java", "infrastructure/evidence/DocumentEvidenceProperties.java")) {
            hashes.put(file, hash(root.resolve("backend/src/main/java/com/mediassist/platform/documentqa/" + file)));
        }
        ArrayNode oracleRuns = result.putArray("oracleRuns");
        ArrayNode retrievalRuns = result.putArray("retrievalRuns");
        ArrayNode adversarialRuns = result.putArray("adversarialRuns");
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);

        for (JsonNode previous : oracle.path("oracleRuns")) {
            String id = previous.path("caseId").asText();
            List<SemanticSearchMatch> sources = mapper.convertValue(previous.path("sources"), new TypeReference<>() {});
            ObjectNode run = oracleRuns.addObject();
            run.put("caseId", id);
            run.put("question", previous.path("question").asText());
            run.set("previousCompletion", previous.path("completion"));
            evaluateAnswer(run, sources, caseLabels.get(id));
            save(output, result);
        }

        String actor = "evidence-evaluation-" + LocalDateTime.now();
        for (JsonNode previous : baseline.path("rag_runs")) {
            String id = previous.path("case_id").asText();
            boolean isolated = "ISO".equals(id);
            Map<UUID, SemanticSearchMatch> sourceMap = sourceMap(isolated ? isolation.path("chunks") : baseline.path("main_chunks"));
            ObjectNode run = retrievalRuns.addObject();
            run.put("caseId", id);
            run.put("question", previous.path("question").asText());
            run.put("topK", previous.path("top_k").asInt());
            extractor.reset();
            long start = System.nanoTime();
            System.out.println("Evidence QA: " + id + " topK=" + run.path("topK"));
            try {
                var response = qa.answerQuestion(isolated ? isolationId : mainId,
                    new DocumentQuestionRequest(run.path("question").asText(), run.path("topK").asInt()), actor);
                run.set("response", mapper.valueToTree(response));
                List<SemanticSearchMatch> sources = response.sources().stream().map(source -> sourceMap.get(source.chunkId())).toList();
                assertThat(sources).doesNotContainNull();
                List<String> oldIds = new ArrayList<>();
                previous.path("response").path("sources").forEach(source -> oldIds.add(source.path("chunkId").asText()));
                assertThat(response.sources().stream().map(source -> source.chunkId().toString()).toList()).isEqualTo(oldIds);
                run.put("retrievalSourceIdsUnchanged", true);
                inspectAnswer(run, response.answer(), sources, caseLabels.get(id));
                run.put("success", true);
            } catch (RuntimeException exception) {
                run.put("success", false);
                run.put("errorType", exception.getClass().getSimpleName());
            }
            run.put("seconds", (System.nanoTime() - start) / 1e9);
            save(output, result);
        }

        for (JsonNode control : controls.path("cases")) {
            ObjectNode run = adversarialRuns.addObject();
            run.put("caseId", control.path("id").asText());
            run.put("question", control.path("question").asText());
            List<SemanticSearchMatch> sources = new ArrayList<>();
            for (int index = 0; index < control.path("chunks").size(); index++) {
                UUID id = UUID.nameUUIDFromBytes((control.path("id").asText() + index).getBytes(StandardCharsets.UTF_8));
                sources.add(new SemanticSearchMatch(id, index, control.path("chunks").get(index).asText(), 0.7, "fixture"));
            }
            evaluateAnswer(run, sources, null);
            if (run.path("success").asBoolean()) {
                String text = normalize(run.path("renderedEvidenceText").asText());
                boolean expectedSelection = control.path("expectedAbstention").asBoolean()
                    ? run.path("renderedEvidenceCount").asInt() == 0 : run.path("renderedEvidenceCount").asInt() > 0;
                for (JsonNode required : control.path("requiredSpans")) {
                    expectedSelection &= text.contains(normalize(required.asText()));
                }
                for (JsonNode forbidden : control.path("forbiddenSelectedSpans")) {
                    expectedSelection &= !text.contains(normalize(forbidden.asText()));
                }
                run.put("expectedSelectionCheckPassed", expectedSelection);
            }
            save(output, result);
        }
        verifyChunks(mainId, baseline.path("main_chunks"));
        verifyChunks(isolationId, isolation.path("chunks"));
        assertThat(vectorHash(mainId, isolationId)).isEqualTo(vectorHash);
        result.put("storedChunksAndVectorsUnchanged", true);
        result.put("finishedAt", LocalDateTime.now().toString());
        save(output, result);
        for (ArrayNode runs : List.of(oracleRuns, retrievalRuns, adversarialRuns)) {
            assertThat(runs).allSatisfy(run -> assertThat(run.path("success").asBoolean()).isTrue());
        }
    }

    private void evaluateAnswer(ObjectNode run, List<SemanticSearchMatch> sources, JsonNode label) {
        extractor.reset();
        long start = System.nanoTime();
        System.out.println("Evidence fixed context: " + run.path("caseId"));
        try {
            var answer = answers.generateAnswer(run.path("question").asText(), sources);
            run.set("answer", mapper.valueToTree(answer));
            inspectAnswer(run, answer.content(), sources, label);
            assertThat(run.path("renderedEvidenceCount").asInt()).isEqualTo(answer.evidenceCount());
            run.put("success", true);
        } catch (RuntimeException exception) {
            run.put("success", false);
            run.put("errorType", exception.getClass().getSimpleName());
        }
        run.put("seconds", (System.nanoTime() - start) / 1e9);
    }

    private void inspectAnswer(ObjectNode run, String answer, List<SemanticSearchMatch> sources, JsonNode label) {
        run.set("sources", mapper.valueToTree(sources));
        run.set("passageCatalog", mapper.valueToTree(extractor.passages));
        run.set("selection", mapper.valueToTree(extractor.selection));
        var quotes = QUOTE.matcher(answer);
        List<String> rendered = new ArrayList<>();
        while (quotes.find()) {
            String quote = unescape(quotes.group(1));
            int citation = Integer.parseInt(quotes.group(2));
            assertThat(citation).isBetween(1, sources.size());
            var source = sources.get(citation - 1);
            var passage = extractor.passages.stream().filter(item -> extractor.selection.passageIds().contains(item.passageId())
                && item.citationNumber() == citation && displayText(item.quote()).equals(quote)).findFirst().orElseThrow();
            assertThat(passage.chunkId()).isEqualTo(source.chunkId());
            assertThat(passage.chunkIndex()).isEqualTo(source.chunkIndex());
            assertThat(passage.quote()).isEqualTo(source.chunkText().substring(passage.startOffset(), passage.endOffset()));
            rendered.add(quote);
        }
        if (rendered.isEmpty()) {
            assertThat(extractor.selection.passageIds()).isEmpty();
            assertThat(answer).contains("does not establish absence from the whole document");
        }
        run.put("exactQuoteAndCitationChecksPassed", true);
        run.put("renderedEvidenceCount", rendered.size());
        run.put("renderedEvidenceText", String.join("\n", rendered));
        ObjectNode coverage = run.putObject("selectedEvidenceCoverage");
        ArrayNode needs = coverage.putArray("needs");
        if (label != null) {
            String selectedText = normalize(String.join("\n", rendered));
            for (JsonNode need : label.path("required_evidence")) {
                ObjectNode item = needs.addObject();
                item.put("facet", need.path("facet").asText());
                ArrayNode missing = item.putArray("missingSpans");
                for (JsonNode span : need.path("spans")) {
                    if (!selectedText.contains(normalize(span.asText()))) {
                        missing.add(span.asText());
                    }
                }
                item.put("covered", missing.isEmpty());
            }
        }
        coverage.put("claimRelevanceAndCompletenessReviewRequired", true);
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

    private String displayText(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private String normalize(String text) {
        return displayText(text).toLowerCase(Locale.ROOT).trim();
    }

    private JsonNode read(Path folder, String name) throws Exception {
        return mapper.readTree(folder.resolve(name).toFile());
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

    private Map<UUID, SemanticSearchMatch> sourceMap(JsonNode saved) {
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
    }

    static class RecordingExtractor implements DocumentEvidenceExtractor {
        private final LlmDocumentEvidenceExtractor delegate;
        private List<DocumentEvidenceItem> passages;
        private DocumentEvidenceSelection selection;

        RecordingExtractor(LlmDocumentEvidenceExtractor delegate) {
            this.delegate = delegate;
        }

        void reset() {
            passages = List.of();
            selection = null;
        }

        @Override
        public DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages) {
            this.passages = List.copyOf(passages);
            selection = delegate.selectEvidence(question, passages);
            return selection;
        }
    }
}
