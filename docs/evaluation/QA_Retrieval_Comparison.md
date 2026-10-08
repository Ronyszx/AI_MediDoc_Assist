# QA Context Retrieval: Implementation and Evaluation

Date: 6 October 2026. All database and live QA evaluation used existing, explicitly fictional documents.

## Decision

The QA-specific retrieval service and configurable MMR selector are implemented and verified. **The overall answer-quality defect is still open, and diversity selection remains disabled by default.** The evaluated strategy improves one broad-question retrieval case at topK 10, but does not improve that question at topK 5 or eliminate classification and grounding mistakes. This is an evaluated retrieval option, not a completed accuracy fix.

No controllers, public API contracts, entities, migrations, stored chunks, stored embeddings, model settings, or prompt instructions changed in this step. Earlier uncommitted prompt-builder, chunk-boundary, and extraction-directory casing changes were preserved.

## Architecture and Flow

```text
DocumentQaController
  -> DocumentQaApplicationService
     -> DocumentQaContextRetrievalService
        -> DocumentEmbeddingApplicationService
           -> EmbeddingService: embed the question once
           -> DocumentChunkEmbeddingRepository: read scoped candidates
        -> DocumentQaContextSelector: select at most topK matches
     -> existing DocumentQaPromptBuilder and LlmClient
     -> existing mapper, success audit, and response
```

With diversity disabled, the retrieval service delegates to the original cosine-ranked search using the requested topK. With diversity enabled, it requests up to 20 candidates by default, including their already-stored vectors, then selects no more than the requested topK. It does not re-embed document text or call the semantic-search HTTP endpoint internally.

MMR selects the most query-relevant candidate first. Subsequent selections balance query similarity against the greatest cosine similarity to an already-selected candidate. This aims to reduce redundant evidence, but can also penalize useful passages about the same subject, including contradictory statements. The relevance weight controls that tradeoff. [Original MMR paper](https://www.cs.cmu.edu/afs/cs/Web/People/jgc/publication/MMR_DiversityBased_Reranking_SIGIR_1998.pdf)

The selector normalizes internal vectors for pairwise comparisons, uses deterministic tie-breaking, removes duplicate chunk IDs, and rejects invalid vectors/settings. Returned matches retain their original text, IDs, model names, and query-similarity scores; the MMR selection score is not presented as confidence. The existing prompt and response source list receive exactly the same selected order, so citation numbering remains aligned.

The repository query parameterizes the document ID, configured embedding model, query vector, and candidate limit. Vectors exist only in an internal projection and are not exposed by normal QA or semantic-search responses. There is no new database schema or entity behavior. Existing LocalDateTime use and audit behavior remain unchanged.

## Configuration

| Property | Environment Variable | Default and Validation |
| --- | --- | --- |
| `mediassist.qa.retrieval.diversity-enabled` | `QA_RETRIEVAL_DIVERSITY_ENABLED` | `false`; explicit opt-in |
| `mediassist.qa.retrieval.candidate-count` | `QA_RETRIEVAL_CANDIDATE_COUNT` | `20`; allowed range 10-100 |
| `mediassist.qa.retrieval.relevance-weight` | `QA_RETRIEVAL_RELEVANCE_WEIGHT` | `0.7`; allowed range 0-1 |

The candidate limit is internal, not an increase in the number of chunks sent to the model. Request topK remains limited to 1-10. This change does not introduce an arbitrary similarity cutoff: similarity is not factual confidence, and an abstention threshold would need its own evaluation.

## Created Files

Paths below are relative to the repository root.

| File | Purpose |
| --- | --- |
| `backend/src/main/java/com/mediassist/platform/documentembedding/domain/SemanticSearchCandidate.java` | Immutable, data-only projection combining a match with its internal vector. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaContextRetrievalService.java` | Coordinates QA-only candidate retrieval and selection; preserves the original route when disabled. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaContextSelector.java` | Implements focused, deterministic MMR selection without provider or persistence dependencies. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaRetrievalSettings.java` | Keeps the application layer dependent on configuration semantics rather than an infrastructure properties class, following the existing LlmSettings pattern. |
| `backend/src/main/java/com/mediassist/platform/documentqa/infrastructure/retrieval/DocumentQaRetrievalProperties.java` | Binds and validates bounded retrieval settings. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaContextSelectorTest.java` | Nine tests for relevance/diversity, duplicate removal, deterministic ordering, unchanged matches, empty input, and invalid vectors/settings. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaContextRetrievalServiceTest.java` | Three tests for disabled delegation, larger candidate pools with bounded selection, and missing-embedding error propagation. |
| `backend/src/test/java/com/mediassist/platform/config/DocumentQaRetrievalConfigurationTest.java` | Six configuration tests for defaults, explicit overrides, and invalid settings. |
| `backend/src/test/java/com/mediassist/platform/documentembedding/application/DocumentEmbeddingRetrievalTest.java` | Four tests for document/chunk/model validation and a single query-embedding call with no persistence writes. |
| `backend/src/test/java/com/mediassist/platform/documentembedding/infrastructure/persistence/JpaDocumentChunkEmbeddingRepositoryTest.java` | Two JDBC-adapter tests for scoped SQL parameters and internal-vector/result mapping. Live replay separately exercised the actual PostgreSQL query. |
| `backend/src/test/java/com/mediassist/platform/documentembedding/domain/SemanticSearchCandidateTest.java` | Verifies defensive copying and an immutable internal vector. |
| `docs/evaluation/evaluate_qa_retrieval.py` | Read-only retrieval probe and actual QA replay tool for saved fictional fixtures; refuses to overwrite previous outputs. |
| `docs/evaluation/qa_retrieval_probe.json` | Saved reference comparisons across weights 1.0, 0.9, 0.7, 0.5, and 0.3 without storing raw vectors. |
| `docs/evaluation/qa_retrieval_mmr_comparison.json` | Saved full answers and source/coverage checks from the actual Java QA path with diversity enabled, candidate count 20, and weight 0.7. |
| `docs/evaluation/QA_Retrieval_Comparison.md` | This implementation record, acceptance decision, and run instructions. |

## Modified Files

| File | Why It Changed |
| --- | --- |
| `backend/src/main/java/com/mediassist/platform/documentembedding/domain/DocumentChunkEmbeddingRepository.java` | Added the internal candidate-with-vector query to the existing repository abstraction. |
| `backend/src/main/java/com/mediassist/platform/documentembedding/infrastructure/persistence/JpaDocumentChunkEmbeddingRepository.java` | Implements candidate retrieval with document/model filters, a bounded limit, deterministic ordering, and vector parsing; leaves the existing public search query unchanged. |
| `backend/src/main/java/com/mediassist/platform/documentembedding/application/DocumentEmbeddingApplicationService.java` | Adds a validated, read-only candidate retrieval use case that reuses existing document/chunk/embedding checks and the configured embedding service. |
| `backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationService.java` | Constructor-injects the context retrieval service instead of calling the embedding application service directly. Prompt construction, completion settings, mapping, errors, and audit workflow are otherwise unchanged from the previous step. |
| `backend/src/main/java/com/mediassist/platform/config/DocumentQaConfiguration.java` | Registers the new retrieval properties alongside the existing LLM properties. |
| `backend/src/main/resources/application.yml` | Adds the three bounded QA retrieval settings, with diversity disabled by default. |
| `backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationServiceTest.java` | Updates mocks and interaction assertions to the retrieval-service boundary while retaining the existing six QA workflow tests. |

## Verification

- `mvn clean test`: BUILD SUCCESS; 87 tests, zero failures, errors, or skipped tests. This step adds 25 tests to the previous 62-test suite.
- Main and test sources compile with Java release 21. The available local test/startup runtime was JDK 25; execution on JDK 21 was not separately verified.
- The temporary backend started on port 8081 with diversity enabled, PostgreSQL 16.14, valid JPA mappings, and all nine Flyway migrations validated. No migration was required.
- Actual QA replay: all 21 saved requests returned HTTP 200; every returned source belonged to its selected fixture document; all detected bracketed citation numbers were in range.
- All 21 source selections matched the independent Python MMR reference at weight 0.7. Source hashes in the saved replay match the final prompt, retrieval service, and selector files.
- All existing fixture chunks remained unchanged. No upload, extraction, chunking, or stored-embedding generation endpoint was called. Question embeddings were generated normally; successful QA requests and the semantic-search smoke test wrote their existing audit events.
- Public semantic-search smoke test returned the original broad-question top-five indexes `[8, 10, 9, 13, 0]` and no vectors. Blank questions and topK 11 returned the project's consistent HTTP 400 validation response; an unknown document returned HTTP 404.
- The temporary backend was stopped after evaluation. The user's existing port-8080 backend and local providers were not stopped or restarted.

Passing these checks does not establish factual accuracy, correct clinical interpretation, authorization, or robust prompt-injection resistance.

## Evidence Results

The baseline here is the final boundary-aware extraction/chunking run, not the earlier pre-boundary prompt run. The main document is `d432e3f2-f9fc-4636-b7f1-565e224e0f4c`, with 15 existing chunks and BGE-M3 embeddings. The separate isolation document is `1dcafba0-ad85-4a19-bf3d-48cf1812b0f1`.

| Broad Question Q08 | Relevance-Only Baseline | Live MMR Weight 0.7 |
| --- | --- | --- |
| topK 5 | 2/5 evidence markers | 2/5; no improvement |
| topK 10 | 3/5 evidence markers | 4/5; investigation passage recovered, Type I label still missing |

Focused-question marker coverage did not regress at weight 0.7 in this fixture. However, weight 0.5 reduced Q03's top-five coverage from 3/3 to 2/3, despite increasing Q08's top-five coverage from 2/5 to 3/5. This demonstrates why tuning only for one broad question is unsafe. Weight 0.3 also lost focused evidence.

The earlier pre-boundary top-ten Q08 run had 5/5 coverage. The new 4/5 result therefore does not fully recover that historical baseline. Marker presence is only a retrieval diagnostic: a later paragraph may paraphrase a diagnosis without containing its original literal marker. Review the full source text and claims rather than treating these fractions as accuracy percentages.

## Remaining Answer Problems

- Q03 retains the two actual diabetes labels and leaves the disagreement unresolved, but some other answers still add unrelated conditions.
- Q08 topK 5 still classifies the reviewer's CRPS non-confirmation as an investigation result and mixes observed swelling into patient-reported symptoms. These are errors even when the passages are available.
- Q08 topK 10 recovers the possible carpal-tunnel investigation, but describes a diabetes-label disagreement without retrieving the explicit Type I statement. It also still mixes observed swelling with reported symptoms.
- Q07 topK 10 places the absence of a completed nerve-study result under reviewer opinions. That is a category error, not a retrieval-score problem.
- Q05 correctly opens with uncertainty about medication and dose, but its top-ten answer overstates scope with a claim about all other records in the bundle when only retrieved context is available.
- Q04's central recorded-diagnosis/later-non-confirmation distinction is retained, but quoted wording and citation support still need claim-level review.

The fictional AMBER-FOX/SILVER-PINE isolation checks behaved as expected, and answers did not obey the embedded BANANA instruction in this replay. These small toy cases are not security certification. The fixture contains explicit explanatory language that can itself influence retrieval; test less labelled, held-out synthetic records and repeat runs before drawing broader conclusions. Temperature 0.2 is unchanged and model output can vary between runs.

## Recommended Next Step

Evaluate multi-aspect retrieval for broad questions: retrieve evidence separately for the requested subjects/categories, merge candidates by chunk ID, and select a bounded context that preserves both sides of disagreements. Do not hardcode diagnoses, patient identifiers, fixture markers, or expected answers. Keep focused-question retrieval and the public semantic-search endpoint unchanged unless evaluation supports a wider change.

Automatic neighbouring-chunk expansion is not implemented in this step. It needs a context-size budget and explicit source/citation handling. A cross-encoder reranker is another option if multi-aspect retrieval remains inadequate, but adds a separate model dependency. Neither should be presented as guaranteeing grounded answers.

After improving coverage, separately evaluate output checks or another answer model on the same corpus. Exact quote/citation checks can catch some unsupported quotes, but cannot prove that a statement is a diagnosis, an opinion, or a genuine contradiction. Keep the defect open until claim-level regression checks pass.

Existing technical debt outside this change includes failure-state/audit rollback, FastAPI blank-text error serialization, framework error-response consistency, security/privacy hardening, and database transactions held across provider calls. None was silently changed here.

## Run and Reproduce

Restarting the backend loads the new service, but the default route remains relevance-only. Existing documents do not need re-extraction, re-chunking, or new stored embeddings for this retrieval-only change.

For an explicit local evaluation on an unused port, with PostgreSQL, FastAPI embeddings, and Ollama already running:

```sh
cd "/Users/rony/Desktop/AI MediDoc Assist/backend"
SERVER_PORT=8081 \
QA_RETRIEVAL_DIVERSITY_ENABLED=true \
QA_RETRIEVAL_CANDIDATE_COUNT=20 \
QA_RETRIEVAL_RELEVANCE_WEIGHT=0.7 \
DOCUMENT_STORAGE_ROOT="/Users/rony/Desktop/AI MediDoc Assist/storage/documents" \
mvn spring-boot:run
```

Use the unchanged `POST /api/v1/documents/{documentId}/questions` endpoint, `Content-Type: application/json`, and required `X-Actor` header:

```json
{
  "question": "Which diagnoses are recorded and which conditions were only investigated?",
  "topK": 5
}
```

From the repository root, replay the saved fictional cases and choose a new output filename:

```sh
python3 docs/evaluation/evaluate_qa_retrieval.py replay \
  --base-url http://localhost:8081 \
  --candidate-count 20 \
  --relevance-weight 0.7 \
  --output docs/evaluation/qa_retrieval_next.json
```

The script flags label the evaluated settings; they do not reconfigure the running server. Match them to the backend's environment variables. To disable MMR, restart without the enable flag or set `QA_RETRIEVAL_DIVERSITY_ENABLED=false`.

The optional `probe` mode uses Docker/psql to read the two saved fictional documents and calls the local embedding service for question vectors. Its weight comparisons use a Python reference implementation; the live replay separately verified the Java selection. Both modes preserve previous files and stored document data. Saved answers contain fictional document text, so do not repoint these tools at real patient data without reviewing storage and privacy implications.
