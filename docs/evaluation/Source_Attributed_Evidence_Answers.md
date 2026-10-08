# Source-Attributed Evidence Answers

Implementation date: 6 October 2026. Experimental, extractive-first, and disabled by default.

## Decision and Scope

The previous [facet retrieval experiment](Facet_Retrieval_And_Oracle_Control.md) improved some evidence coverage but still produced unsupported clinical interpretations, even with sufficient oracle context. Retrieval and generation therefore need separate controls. This milestone implements the provenance-first portion of the [research-based design](../design/Research_Based_QA_Reliability_Design.md), not its entire future clinical categorization workflow.

The model now selects server-generated passage IDs. The backend validates those IDs and renders the corresponding source wording itself. There is no second model call to paraphrase, assign clinical categories, invent authors or dates, declare contradictions, or decide which assessment is correct. This is deliberately less fluent than a synthesized answer. It narrows the path for unsupported generated claims without pretending that exact quotation proves relevance, completeness, clinical truth, or causal faithfulness.

The public question request and response models, controllers, semantic-search API, model defaults, migrations, entities, chunks, and embeddings are unchanged. All operational timestamps still use `LocalDateTime`. No chat history, new provider, knowledge graph, OCR, frontend, or AWS code was added.

## End-to-End Flow

```text
Existing question endpoint: documentId + question + topK + X-Actor
  -> existing document-scoped context retrieval
  -> DocumentAnswerGenerationService
     -> flag off: existing prompt and unrestricted text completion
     -> flag on: DocumentEvidenceApplicationService
        -> catalogue chunk-local paragraphs with P1/P2 IDs
        -> send question and catalogue as JSON data to existing LlmClient
        -> accept only {"passageIds":["P1", ...]}
        -> validate IDs, source membership, citation positions and exact spans
        -> deterministic quoted excerpts with [Chunk N] citations
  -> existing response mapper + LocalDateTime answeredAt
  -> existing DOCUMENT_QUESTION_ASKED audit event
```

`P1` is an ephemeral passage ID. `[Chunk 1]` is the first entry in the response's `sources` array, not database `chunkIndex=1`. One source chunk can contain several paragraphs, and several quotations can cite the same chunk. `sources` retains all retrieved context entries for compatibility; it is not a new list of selected evidence spans.

Each catalogue item stores its source UUID, stored chunk index, response citation position, exact quote, and half-open UTF-16 offsets `[startOffset, endOffset)` into the original Java chunk string. Whitespace trimming adjusts offsets before selection. Validation uses exact `substring` equality, not fuzzy matching or case normalization. The model cannot submit its own quote, offsets, source UUID, answer prose, or citation number.

Paragraphs are split on blank lines, including CRLF and other Java line-break characters. A single CRLF remains one line break; a regression test covers the regex backtracking bug discovered during implementation. A catalogue paragraph is whole within a retrieved chunk, not necessarily a complete paragraph or record from the original PDF. Existing chunk boundaries can still separate a heading, author, or date from its statement. No neighbour expansion or inferred attribution is performed.

## Bounds and Failure Policy

| Configuration | Default | Purpose |
| --- | --- | --- |
| `QA_EVIDENCE_ENABLED` | `false` | Explicit opt-in; default QA behavior remains unchanged. |
| `QA_EVIDENCE_MAX_ITEMS` | `12` | Maximum selected passage IDs; validated range 1-20. |
| `QA_EVIDENCE_MAX_PASSAGES` | `80` | Maximum catalogue size; validated range 1-100. |
| `QA_EVIDENCE_MAX_PASSAGE_CHARACTERS` | `1200` | Skip oversized chunk-local paragraphs, never truncate them; range 128-4000. |
| `QA_EVIDENCE_MAX_ANSWER_CHARACTERS` | `6000` | Bound rendered answer text by omitting whole quotes; range 1000-16000. |

The catalogue also reserves the configured output tokens plus a margin inside the existing LLM context window. The UTF-8-byte estimate is provisional, not an exact tokenizer or a universal guarantee against model-window overflow. Skipped catalogue passages or rendered quotes produce an explicit limits notice.

Provider output is bounded to 8192 characters and parsed as a single JSON object with exactly one array field. Duplicate keys, trailing JSON, repeated IDs, unknown IDs, invalid ID syntax, excessive counts, prose, and extra category/answer fields are rejected. A provider failure retains the existing unavailable error; malformed or unverifiable evidence becomes the existing RAG generation error. There is no fallback to unchecked free prose after evidence mode has been selected.

An explicit valid empty selection returns a scoped abstention: no supporting excerpt was selected from the retrieved context. It does not claim absence from the whole PDF. If no eligible paragraph fits, or no selected quote fits the response budget, generation fails rather than disguising a technical limit as medical absence.

The renderer collapses display whitespace and escapes source Markdown, HTML delimiters, links, and fake citations before adding its own citation. Source-span validation happens before display formatting. These controls do not replace frontend output escaping, authorization, or privacy review.

The existing success audit now includes `answerMode`, `evidenceCount`, and `contextLimited`, in addition to the previous topK, source count, and model name. Questions, prompts, source text, quotations, and answers are not added to audit payloads or production diagnostic logging.

## Verification

`mvn clean compile` passed. `mvn clean test` passed: 179 tests discovered, 177 passed, two opt-in live tests skipped, zero failures or errors. The new live evidence test was separately enabled and passed. Compilation targets Java 21; these local runs used JDK 25.0.2, not a Java 21 runtime. No JDK was installed or changed.

The real Spring evaluation context started successfully, validated all nine existing Flyway migrations, and initialized JPA against PostgreSQL 16.14. No migration was necessary. The existing user-owned backend on port 8080 remained healthy. Existing Commons Logging, Lombok/Unsafe and Mockito dynamic-agent warnings remain outside this milestone.

A temporary evidence-enabled backend also started on `127.0.0.1:8081` for real HTTP checks: health 200/UP, Swagger 200, a fictional-document question 200 with the unchanged response shape and a correctly matched `[Chunk 2]` excerpt, blank question 400, topK 11 rejected with 400, and unknown document 404. That question wrote one additional normal success audit event. The temporary instance was stopped afterward; the user-owned port 8080 process was not stopped or restarted. `git diff --check` passed.

### Live Evaluation Method

The opt-in `DocumentEvidenceLiveTest` uses the real Spring application context and local PostgreSQL, FastAPI embeddings, and Ollama, but a mock servlet context. It does not replay HTTP endpoints. The user-owned server on port 8080 is not restarted or replaced.

- Seven oracle controls reuse exactly the saved source lists from `qa_facet_oracle_comparison_v2.json`, isolating generation from retrieval.
- Twenty-one existing fictional QA requests run through the application service with facet retrieval and MMR disabled. Their ordered source IDs must match the previous relevance-only baseline.
- Ten new agent-authored, in-memory fictional probes cover negation, family history, differing dates, coexisting conditions, patient reports versus observations, pending tests, missing doses, document commands, irrelevant context, and another subject's identifier.
- Independently parsed rendered quotes must resolve to the selected server catalogue and exact original source spans, with the correct source UUID, chunk index, and response citation position.
- Literal evidence-span coverage is measured on the rendered quotations, not on all retrieved chunks. Case/whitespace normalization is used only for this coverage diagnostic, never for source validation.
- Before/after chunk IDs, order and text, plus a hash of stored embedding IDs, model names and vectors, must remain unchanged. Only the 21 normal QA calls can write the existing success audit events.

The original cases, labels, and new probes are development fixtures written by an agent. They are not an independent human-labeled held-out corpus or a clinical benchmark. One non-deterministic run cannot establish a general accuracy rate. Exact provenance, selection relevance, omission, and false abstention are separate outcomes.

### Results

Raw results: [qa_source_evidence_evaluation.json](qa_source_evidence_evaluation.json). All source hashes in the artifact matched the implementation after the run. The old free-text prompt hash matched the frozen baseline.

| Check | Observed Result |
| --- | --- |
| Fixed oracle controls | 7/7 completed; all 16 declared literal evidence needs across the six labeled cases were present in rendered quotes. Q05 has no literal coverage labels and was reviewed as a missing-information case. |
| Existing QA-path regressions | 21/21 completed; ordered retrieval source IDs were unchanged from the relevance-only baseline. |
| New adversarial development probes | 10/10 completed and met their declared selection expectations in this single run. The injection paragraph was not selected; irrelevant and other-subject cases returned empty selections. |
| Quote/source/citation checks | Passed for all 38 cases, including valid empty selections. No model-written answer prose was rendered. |
| Stored chunks and vectors | Before/after assertions passed; no regeneration or vector changes. |
| Local latency | Oracle median 3.07 s, maximum 9.35 s; QA-path median 7.19 s, maximum 16.60 s; adversarial median 1.26 s, maximum 1.81 s. These small sequential local runs are not production performance estimates. |

The broad oracle Q08 selected the actual Type I/Type II passage, the recorded CRPS diagnosis, the later non-confirmation, and investigation/uncertainty text: 6/6 literal needs. The backend did not compose an invented conflict between different conditions or convert the reviewer's uncertain opinion into its own diagnosis. That is a result of constrained quotation, not proof of semantic classification accuracy.

**Completeness and usefulness gates still fail. Keep evidence mode disabled by default.** On the unchanged retrieval path, broad Q08 rendered only 1/6 literal needs at both topK 5 and topK 10. At topK 10 the model selected the first 12 catalogue passages, including headers and repetitive meta-text, instead of selecting all useful evidence. Some needed source spans were not retrieved at all; others were available but omitted by selection. Increasing topK did not solve this.

Focused Q04 at topK 10 quoted later statements referring to the earlier CRPS diagnosis but omitted the actual earlier diagnosis paragraph, covering only 1/2 declared needs; its topK 5 and oracle runs covered 2/2. Q07 at topK 5 covered 2/3 needs, while topK 10 and oracle covered 3/3. Redundant overlap quotations also remain. These observations distinguish selective copying from a complete, well-attributed answer and motivate independent selection/relevance evaluation rather than silent default activation.

The 10 new probe successes do not cancel the failures on the more cluttered existing cases. They are agent-authored controls, not an independently reviewed held-out validation. No unsupported-clinical-claim accuracy percentage is inferred from the provenance checks.

## Files Created

Paths below are relative to `backend/src/main/java/com/mediassist/platform/documentqa/` unless specified otherwise.

| File | Why It Exists |
| --- | --- |
| `domain/DocumentEvidenceItem.java` | Immutable source span and citation metadata; no persistence or clinical logic. |
| `domain/DocumentEvidenceContext.java` | Immutable catalogue with a context-limit flag. |
| `domain/DocumentEvidenceSelection.java` | Actual provider model name and immutable selected IDs. |
| `domain/DocumentAnswerMode.java` | Distinguishes free-text and evidence-excerpt generation internally. |
| `domain/DocumentAnswerDraft.java` | Internal generated content, mode, evidence count and limit state before existing response mapping. |
| `application/DocumentEvidenceSettings.java` | Application-facing configuration abstraction. |
| `application/DocumentEvidenceException.java` | Privacy-safe validation/generation failure without raw provider output. |
| `application/DocumentEvidenceExtractor.java` | Application port for evidence selection; provider-independent. |
| `application/DocumentEvidencePromptBuilder.java` | Fixed selection policy and serialized question/passages; no free-prose request. |
| `application/DocumentEvidenceContextBuilder.java` | Catalogue creation, exact offsets, whole-passage bounds and prompt-budget estimate. |
| `application/DocumentEvidenceValidator.java` | Rejects foreign/duplicate IDs, altered quotes, invalid offsets and wrong source positions. |
| `application/DocumentEvidenceAnswerRenderer.java` | Deterministic source excerpts, citations, partial-context notice and scoped abstention. |
| `application/DocumentEvidenceApplicationService.java` | Orchestrates catalogue, selection, provenance validation and rendering. |
| `application/DocumentAnswerGenerationService.java` | Selects opt-in evidence mode or the unchanged legacy prompt/completion path. |
| `infrastructure/evidence/DocumentEvidenceProperties.java` | Environment-backed, validated configuration binding. |
| `infrastructure/evidence/LlmDocumentEvidenceExtractor.java` | Existing LLM adapter integration in JSON mode with strict bounded response parsing. |

Test paths are relative to `backend/src/test/java/com/mediassist/platform/`.

| File | Coverage |
| --- | --- |
| `config/DocumentEvidenceConfigurationTest.java` | Defaults, overrides, and invalid configuration limits. |
| `documentqa/application/DocumentAnswerGenerationServiceTest.java` | Legacy compatibility, enabled routing, and no unsafe fallback. |
| `documentqa/application/DocumentEvidenceContextBuilderTest.java` | CRLF, Unicode/UTF-16 offsets, catalogue ordering and limits. |
| `documentqa/application/DocumentEvidenceValidatorTest.java` | Negation/whitespace exactness, source membership, offsets, duplicates and citations. |
| `documentqa/application/DocumentEvidenceAnswerRendererTest.java` | Deterministic wording, escaped malicious source formatting, scoped abstention and response bounds. |
| `documentqa/application/DocumentEvidenceApplicationServiceTest.java` | Complete workflow, unknown selections, explicit abstention, empty catalogue and provider failure. |
| `documentqa/infrastructure/evidence/LlmDocumentEvidenceExtractorTest.java` | JSON settings, malformed/duplicate/extra fields, count/size bounds and unavailable provider. |
| `documentqa/evaluation/DocumentEvidenceLiveTest.java` | Opt-in real-provider development evaluation with independent quote checks and unchanged stored-data assertions. |

Other created files:

| File | Purpose |
| --- | --- |
| `docs/evaluation/qa_evidence_adversarial_controls.json` | Ten fictional development probes with declared selection expectations. |
| `docs/evaluation/qa_source_evidence_evaluation.json` | Generated raw live evaluation outputs, catalogues, selections, timings and source hashes. Fictional content only. |
| `docs/evaluation/Source_Attributed_Evidence_Answers.md` | This design, file inventory, runbook, verification report and limitations. |

## Files Modified In This Milestone

Earlier staged renames, retrieval experiments, PDF extraction and chunking changes remain in the workspace; they are not new changes from this milestone.

| File | Change and Reason |
| --- | --- |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationService.java` | Delegates generation to the focused service; retains retrieval, DTO mapping, `LocalDateTime`, existing exceptions and success audit, with non-content mode metadata. |
| `backend/src/main/java/com/mediassist/platform/config/DocumentQaConfiguration.java` | Registers evidence configuration beside existing QA/retrieval configuration. |
| `backend/src/main/resources/application.yml` | Adds opt-in evidence settings; no existing model/provider/default retrieval change. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationServiceTest.java` | Tests delegation, preserved response/source behavior, existing errors, rejected evidence and privacy-safe audit metadata. |

## Run And Test Manually

Keep PostgreSQL, the existing BGE-M3 FastAPI service on port 8001, and Ollama on port 11434 running. No re-upload, extraction, chunking or re-embedding is needed for this milestone.

Start a separate backend instance on an unused port, or explicitly restart your own instance. The example avoids the existing server on 8080:

```bash
cd backend
QA_EVIDENCE_ENABLED=true \
QA_FACET_RETRIEVAL_ENABLED=false \
QA_RETRIEVAL_DIVERSITY_ENABLED=false \
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081 --server.address=127.0.0.1'
```

Use Swagger on `http://localhost:8081/swagger-ui/index.html` or Postman:

```text
POST http://localhost:8081/api/v1/documents/{documentId}/questions
Content-Type: application/json
X-Actor: evidence-manual-test

{
  "question": "Which statements about the diagnoses differ between records?",
  "topK": 5
}
```

Expect the same response fields: documentId, question, answer, modelName, sources, and answeredAt. The answer contains selected source excerpts rather than a synthesized clinical category list. Check `[Chunk N]` against the Nth source entry, not the persisted chunk index. Repeat at topK 10 to observe context coverage, not to assume a universal accuracy fix. Empty evidence can produce the scoped insufficiency response; provider/validation errors remain errors.

For reproducible provider evaluation, choose a new, non-existing output filename directly inside `docs/evaluation`:

```bash
cd backend
MEDIASSIST_QA_EVIDENCE_EVALUATION=true \
MEDIASSIST_QA_EVIDENCE_OUTPUT="/Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/qa_source_evidence_next.json" \
mvn test -Dtest=DocumentEvidenceLiveTest
```

The live test is skipped during ordinary builds. It requires the existing fictional fixtures already persisted in the local database. It deliberately refuses to overwrite previous results. Disable `QA_EVIDENCE_ENABLED` and restart to restore the free-text path; there is no automatic fallback after an evidence-mode failure.

## Remaining Work

This is an evidence-provenance foundation, not completion of reliable clinical QA. Next steps require separately reviewed semantics and usefulness, not another claim that stricter JSON guarantees understanding:

- Independently authored documents and human-reviewed held-out questions; measure relevance, omission, unknown attribution, false abstention and repeated-run variation.
- Evaluate safe author/date/subject support and clinical evidence categories, preserving unknowns; do not infer categories from quote matching alone.
- Require both actual source statements and compatible subject/time/scope before a labeled contradiction; quoted source opinions are not backend adjudications.
- Address retrieval omissions separately, then test the combined evidence/facet workflow before enabling either experimental flag by default.
- Evaluate chunk-local fragments and dependable page/section/record provenance; neighbour expansion or a data migration must be explicit and budgeted.
- Calibrate the deployed model's tokenizer, shorten provider-spanning database transactions, and review failure audit policy as separate changes.
- Authorization, PHI protection, secure logging, deployment controls and clinical validation remain necessary before real-patient production use.

No default strategy switch is justified solely by these finite fictional probes.
