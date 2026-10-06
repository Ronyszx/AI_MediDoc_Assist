# Facet Retrieval and Oracle-Context Control

Date: 6 October 2026.

This implements the initial control and retrieval experiment from the [research-based design](../design/Research_Based_QA_Reliability_Design.md), not the entire future evidence-extraction pipeline. Facet retrieval is experimental and disabled by default. There is no clinical-readiness claim.

## What Changes

```text
Existing question API
  -> existing QA application service
  -> existing context retrieval boundary
     -> flag disabled: unchanged relevance/MMR route
     -> flag enabled: bounded question planner
        -> focused question or planning failure: original-query search
        -> multi-part question: original + facet queries
           -> batch query embeddings
           -> document/model-scoped pgvector candidates per query
           -> chunk-ID union and reciprocal rank fusion
           -> facet nominations, then remaining fusion-ranked candidates
           -> topK and estimated prompt/evidence budget checks
  -> unchanged answer prompt, LLM settings, response mapping and success audit
```

The planner receives only the question, not the document. It plans requested information needs rather than supplying diagnoses or answers. Its JSON is checked for schema, text lengths, count, duplicates, and retention of supplied numeric/date/identifier qualifiers. These checks do not prove that rewritten queries preserve meaning. Semantic drift must be reviewed in the saved plans.

Plans with zero or one facet use the existing search rather than rewriting a focused question. Planning failures are explicitly logged as degradation and fall back to original-query search. Embedding and answer-provider errors are not swallowed by that fallback. When facets and MMR are both enabled, facets take precedence; the strategies are not combined in this experiment.

The first experiment exposed malformed planning JSON and exercised fallback for 10/21 requests, including Q08. That historical output is preserved, not overwritten. The final planner requests JSON output through the LLM abstraction; the Ollama adapter translates that to its optional `format` field, following [Ollama's chat API contract](https://docs.ollama.com/api/chat). Answer requests still default to text, with no format field and unchanged instructions/settings. JSON mode improves syntax, not semantic validity: the same strict plan checks and fallback remain. The generic HTTP adapter retains its existing external contract and relies on the planning prompt plus validation; a future provider-specific structured-output adapter needs its own capability tests.

For a multi-part question, the existing embedding abstraction receives the original question and all facets together. Document/chunk/model validation occurs once within the batch use case. The configured embedding batch size still applies. Each database query uses the server-controlled document ID and configured model, never a filter supplied by the planner.

Maximum candidate entries are 20 original plus six times ten facet entries, or 80 before ID deduplication. Similar text is not deduplicated: it can contain opposing statements. RRF sums reciprocal rank contributions with a default constant of 60. The constant is unrelated to user topK. Internal fusion scores and stored vectors are not returned as answer confidence or exposed through normal API responses.

Similarity in returned sources is recomputed against the original question vector even when a chunk was found only by a facet. It is not the facet similarity or RRF score. Facets nominate their top-three-ranked candidates, share an already-selected nominee where possible, and then selection fills remaining slots in fusion order. This is rank-based allocation, not a semantic proof that every requested aspect is answered. Early requested facets have priority when topK is too small; this policy needs further evaluation.

The selector keeps whole chunks, never silently increases topK, and reserves space for the unchanged answer instructions, question, output and framing. The provisional estimator is UTF-8 byte length divided by two; evidence is capped at 4000 estimated tokens and checked against the configured context window. This is not the provider's tokenizer and is not a strict token-overflow guarantee, particularly for unusual scripts, code or punctuation. Calibrate against the deployed tokenizer/provider before enabling by default. A chunk too large for the budget is skipped, not truncated into a new source.

No partial-coverage metadata or guaranteed semantic coverage check is added to the API. The existing prompt warns that retrieved context is partial. Explicit facet coverage/partial-answer response handling belongs in the later structured-evidence milestone and remains a release concern.

## Oracle Control

`qa_evidence_labels.json` labels the existing fictional development document with requested evidence spans and manually selected oracle chunk indexes. The labels were authored and reviewed by the coding assistant from the actual saved text, not independently validated by a clinician or human annotation team. They are not production retrieval rules.

The control passes sufficient original source chunks to the unchanged prompt and qwen3.5:4b. It bypasses retrieval only for evaluation. It tests whether generation still misclassifies, overstates or invents facts after the evidence is present. In particular, the broad Q08 control has five real chunks covering six overlapping evidence needs, including both diabetes labels and both dated CRPS assessments.

Span coverage requires every labeled supporting phrase for a need, after whitespace/case normalization. A disagreement needs both actual statements; a general reference to disagreement is insufficient. This remains a source-presence diagnostic, not entailment, category accuracy or citation faithfulness. Unsupported paraphrases and fabricated interpretations require claim review even if every span is present.

The historical relevance-only comparison comes from the final boundary-aware baseline on these same chunks. Model, temperature, output/context settings and prompt are held fixed, but answers are nondeterministic and historical versus new runs are not a causal accuracy estimate. No settings should be tuned on a held-out set. Independent documents and repeated runs are still required.

## Results and Acceptance Decision

**The implementation and integration checks pass; the answer-quality acceptance gates do not. Keep `QA_FACET_RETRIEVAL_ENABLED=false`.** This is a reproducible development experiment, not a completed accuracy fix.

The final replay is `qa_facet_oracle_comparison_v2.json`. The first file is retained as the pre-JSON-mode experiment and must not be treated as results for the final source. The final result records hashes of eight relevant implementation files plus the unchanged answer prompt and labels. Those implementation hashes matched the workspace at review time.

| Check | Final Result |
| --- | --- |
| Clean compilation | Java release 21, 148 main source files; runtime JDK 25. |
| Normal tests | 129 passing; one explicitly opt-in live test skipped by default; zero failures/errors. |
| Real-provider controls | Seven sufficient-context controls completed successfully. |
| Real application QA calls | 21/21 successful; mock servlet application startup, not HTTP replay. |
| Database startup | PostgreSQL 16.14; nine Flyway migrations valid/up to date; JPA initialized successfully. |
| Source isolation / citation ranges | All returned IDs were in the selected fixture; all detected bracketed chunk numbers were in range. These do not prove claim support. |
| Data preservation | Existing fixture chunk IDs/order/text and vector IDs/values/model names unchanged. No extraction/chunking/stored-embedding generation endpoint called. |
| Planning degradation | 2/21 final requests fell back: Q01's planned query failed duplicate/qualifier validation. The first experiment had 10/21 fallbacks, including broad Q08. |
| Focused labeled spans | No regressions across Q01-Q04/Q07 at topK 5 or 10 in this fixture. Some focused comparisons were nevertheless decomposed, so intent routing is not yet reliably focused-only. |
| Local final latency | Median 8.47 seconds; maximum 16.01 seconds across 21 calls. This is one local development run, not a performance SLA. |
| Existing user application | Port 8080 remained healthy and was not restarted. No temporary HTTP server was started. |

### Broad Question Retrieval

The new span labels define six overlapping evidence needs, unlike the earlier five literal markers. Both measures are shown to avoid confusing retrieval diagnostics with answer accuracy.

| Q08 | Relevance-Only Baseline | Final Facet Retrieval |
| --- | --- | --- |
| topK 5, all spans per evidence need | 1/6 needs | 3/6 needs |
| topK 10, all spans per evidence need | 3/6 needs | 5/6 needs, using nine budget-fitting chunks |
| topK 5, historical literal markers | 2/5 markers | 3/5 markers |
| topK 10, historical literal markers | 3/5 markers | 4/5 markers |

Top-five final chunk indexes were `[8, 4, 10, 0, 9]`. Top-ten final selection was `[8, 4, 10, 0, 9, 11, 7, 5, 3]`. The investigation passage is recovered. However, the actual Type I/Type II statements in chunk 6 remain missing at both budgets. Rank-based facet nomination still favors the reviewer-disagreement material over the explicit diabetes-label pair. A general query for contradictions does not guarantee every disagreement is covered.

The strict recorded-diagnosis span is also missing at topK 5, though chunk 10 paraphrases the earlier CRPS record. That illustrates why span presence and medical/category interpretation must be reviewed separately rather than scoring a paraphrase as automatic failure or success. Increasing topK is not the answer-generation fix and would not meet the existing top-five gate.

### Oracle and Answer Findings

All positive-evidence oracle contexts contain their labeled supporting spans. With these sufficient chunks and the unchanged model/prompt:

- The focused core distinctions generally remain: possible carpal tunnel investigation, both dated diabetes labels, and earlier recorded CRPS versus later non-confirmation.
- In the first Q07 control, the model treated an assertion of dishonesty as a reviewer interpretation, even though the source explicitly warned that the evidence did not establish dishonesty. The second control did not repeat this particular invention. This is observed output variability, not proof the defect was fixed.
- In both broad Q08 controls, the model attached the CRPS chunk to Type I/Type II statements. The numbers were in range but the added citation did not support those labels.
- The final Q08 top-ten retrieved answer invented a conflict "between diabetes and CRPS diagnoses" despite neither the actual Type I statement nor the Type II statement being present. These are different conditions, not a supported disagreement about the same subject.
- The final broad answers grouped observed swelling with reported symptoms, and the top-five answer blurred a patient report with a reviewer observation. The generated answer also exceeded the prompt's soft 180-word instruction.

Thus the experiment separates missing evidence from generation defects and improves some source presence, but does not pass the grounded-answer release gate. It is also not an independent held-out evaluation: the labels and fixture were development material, and the two runs changed planner syntax/routing rather than being a statistical model comparison. Do not promote the flag on these results.

Next, implement attributed evidence items and constrained rendering as a separately reviewed milestone. Require real source spans and preserve unknown/uncertain categories; quote matching alone still cannot validate medical interpretation. Add independently reviewed, less explicitly labelled held-out documents. Reassess the retrieval of disagreement pairs on that corpus rather than overfitting this fixture or immediately adding a new model/reranker.

## Created Files

Paths are relative to the repository root.

| File | Reason |
| --- | --- |
| `backend/src/main/java/com/mediassist/platform/documentembedding/domain/SemanticSearchQuery.java` | Data-only, validated text/count request for each internal search query. |
| `backend/src/main/java/com/mediassist/platform/documentembedding/domain/SemanticSearchQueryResult.java` | Immutable internal query vector and candidate list; not an API DTO. |
| `backend/src/main/java/com/mediassist/platform/documentqa/domain/QueryFacet.java` | Data-only facet label and retrieval question. |
| `backend/src/main/java/com/mediassist/platform/documentqa/domain/QuestionPlan.java` | Original question plus immutable facet list. |
| `backend/src/main/java/com/mediassist/platform/documentqa/domain/RankedContextCandidate.java` | Internal match, fusion score and immutable per-query rank map. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/QuestionPlanner.java` | Provider-independent planning boundary. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/QuestionPlanningException.java` | Distinguishes degradable planning errors from search or answer failures. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/LlmResponseFormat.java` | Provider-independent text/JSON output preference; answer requests remain text. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/FacetRetrievalSettings.java` | Application-facing bounded settings, separate from infrastructure binding. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/ReciprocalRankFusion.java` | Focused, deterministic ID union/rank fusion and original-query cosine scoring. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/FacetContextSelector.java` | Focused count/budget-aware rank-based facet allocation. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/FacetContextRetrievalService.java` | Coordinates planning, query retrieval, fusion and selection, including explicit fallback. |
| `backend/src/main/java/com/mediassist/platform/documentqa/infrastructure/retrieval/FacetRetrievalProperties.java` | Validated configuration, disabled by default. |
| `backend/src/main/java/com/mediassist/platform/documentqa/infrastructure/retrieval/LlmQuestionPlanner.java` | Reuses the existing provider/model with a small planning prompt and strict JSON/qualifier checks. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/ReciprocalRankFusionTest.java` | Tests rank contributions, ID-only deduplication, original-query scores and invalid vectors. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/FacetContextSelectorTest.java` | Tests nominations, shared chunks, topK, missing facets and prompt/evidence budget handling. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/FacetContextRetrievalServiceTest.java` | Tests focused route, planning fallback, ordered batch queries and failure propagation. |
| `backend/src/test/java/com/mediassist/platform/documentqa/infrastructure/retrieval/LlmQuestionPlannerTest.java` | Tests planning settings, focused questions, malformed/duplicate/oversized JSON, qualifiers and provider failures. |
| `backend/src/test/java/com/mediassist/platform/documentqa/evaluation/DocumentQaReliabilityLiveTest.java` | Explicitly opt-in real-provider evaluation with mock servlet startup, fixture isolation, saved answers/plans and unchanged-data checks. |
| `docs/evaluation/qa_evidence_labels.json` | Development evidence needs, sufficient controls, interpretation notes and predeclared acceptance gates. |
| `docs/evaluation/qa_facet_oracle_comparison.json` | Generated results from the opt-in live experiment; no full vectors. |
| `docs/evaluation/qa_facet_oracle_comparison_v2.json` | Separate final replay after JSON-mode and single-facet safeguards; no full vectors. |
| `docs/evaluation/Facet_Retrieval_And_Oracle_Control.md` | This implementation/evaluation record. |

## Modified Files

| File | Reason |
| --- | --- |
| `backend/src/main/java/com/mediassist/platform/documentembedding/application/DocumentEmbeddingApplicationService.java` | Adds the read-only batch query use case while retaining the existing public search and generation paths. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaContextRetrievalService.java` | Adds the feature-flag delegation; disabled behavior remains unchanged. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/LlmSettings.java` | Exposes the existing context-window setting to application selection policy. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/LlmCompletionRequest.java` | Adds an output-format preference with a backward-compatible four-argument constructor defaulting to text. |
| `backend/src/main/java/com/mediassist/platform/documentqa/infrastructure/client/OllamaLlmClient.java` | Requests JSON only for structured planning calls; omits the field for ordinary answers. |
| `backend/src/main/java/com/mediassist/platform/config/DocumentQaConfiguration.java` | Registers facet properties beside existing QA settings. |
| `backend/src/main/resources/application.yml` | Centralizes the opt-in strategy and bounded planning/retrieval budgets. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaContextRetrievalServiceTest.java` | Updates constructor wiring and tests precedence without combining MMR and facets. |
| `backend/src/test/java/com/mediassist/platform/documentembedding/application/DocumentEmbeddingRetrievalTest.java` | Tests one ordered embedding batch, scoped repository calls, no writes and missing-document rejection. |
| `backend/src/test/java/com/mediassist/platform/config/DocumentQaRetrievalConfigurationTest.java` | Tests disabled default and invalid facet configuration bounds. |
| `backend/src/test/java/com/mediassist/platform/documentqa/infrastructure/client/OllamaLlmClientTest.java` | Verifies structured JSON requests and unchanged text-answer requests. |

Earlier uncommitted prompt, chunk-boundary, extraction package-case and MMR changes were preserved. No controllers, entities, migrations, dependencies, public API DTOs, answer prompts or model defaults were changed by this milestone. Operational timestamps remain LocalDateTime. Business policy stays in application components; the planner's provider prompt/JSON parsing stay in infrastructure.

## Run and Reproduce

Normal Maven tests do not call local providers or need a database; the live test is skipped unless explicitly enabled. From `backend`:

```sh
mvn clean compile
mvn test
```

For the live development experiment, run PostgreSQL, the existing FastAPI embedding service and Ollama first. Use a new absolute output filename directly under `docs/evaluation`; the harness refuses to overwrite previous evidence:

```sh
MEDIASSIST_QA_LIVE_EVALUATION=true \
MEDIASSIST_QA_EVALUATION_OUTPUT="/Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/qa_facet_oracle_next.json" \
DOCUMENT_STORAGE_ROOT="/Users/rony/Desktop/AI MediDoc Assist/storage/documents" \
mvn test -Dtest=DocumentQaReliabilityLiveTest
```

The harness starts a Spring mock-web application context, validating migrations, JPA and bean wiring. It does not start or restart an HTTP server and therefore is not an HTTP endpoint replay. It runs seven oracle controls and the 21 saved application-service QA requests. Successful QA requests write normal audits without question text. The oracle calls do not create chat history or QA records. Fixture IDs/text are checked before calls, and chunk/vector content is checked again afterward. No vector regeneration occurs.

For an explicit Swagger/Postman experiment, start the backend on an unused port with `QA_FACET_RETRIEVAL_ENABLED=true` and use the unchanged question endpoint and required `X-Actor`. A backend restart is required to load workspace changes. Do not stop the user's existing server as part of this evaluation. Disabling the flag restores the previous route; existing documents need no reprocessing.

## Remaining Work

Keep this experiment disabled until evaluation supports it. It does not implement structured evidence items, source-span enforcement on generated answers, semantic claim validation, reranking, neighbour expansion, page provenance or guaranteed partial-coverage answers. A planning call currently precedes the batch use case's document validation; invalid document requests can incur planning cost before the usual not-found error. Moving eligibility checks ahead of planning without duplicating database work is a future orchestration cleanup.

The QA transaction still spans provider calls. Existing audit/failure rollback, FastAPI error serialization and framework error-shape issues were not silently changed. Document filtering is not authorization. Real patient use still needs access control, privacy review and security hardening.

The next decision should follow the oracle result: if the model invents or misclassifies claims with sufficient context, prioritize attributed evidence extraction and constrained answers instead of another global similarity/MMR/topK adjustment.
