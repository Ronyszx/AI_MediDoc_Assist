# Document QA Prompt Builder: Implementation and Evidence

Date: 5 October 2026. All live checks used the existing, explicitly fictional evaluation PDFs.

## Outcome

The prompt-construction refactor is complete and the backend compiles, starts, and passes its tests. The final live comparison returned HTTP 200 for all 21 saved questions. **The answer-grounding defect is not resolved.** Manual review still found unsupported cross-condition claims and classification errors. HTTP success, in-range citations, and passing unit tests are not measures of factual accuracy.

The new builder supplies clearer evidence rules; it does not rewrite or improve the user's question. It also does not validate the factual truth of the model's answer. Treat this as a maintainability change and an evaluated prompt mitigation, not as a completed hallucination fix or clinical-use validation.

## Code Changes

The backend base directory is `/Users/rony/Desktop/AI MediDoc Assist/backend`.

| File | Change and Purpose |
| --- | --- |
| [DocumentQaPromptBuilder.java](</Users/rony/Desktop/AI MediDoc Assist/backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaPromptBuilder.java>) | Created a small, stateless application-layer component responsible only for assembling system instructions, the unchanged question, and full retrieved chunk text. |
| [DocumentQaApplicationService.java](</Users/rony/Desktop/AI MediDoc Assist/backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationService.java>) | Modified to constructor-inject the builder and delegate prompt construction. Retrieval, completion settings, mapping, auditing, exception behavior, and LocalDateTime remain unchanged. |
| [DocumentQaPromptBuilderTest.java](</Users/rony/Desktop/AI MediDoc Assist/backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaPromptBuilderTest.java>) | Created 10 tests for message roles, full evidence, source order, uncertainty, attribution, missing information, contradiction instructions, embedded commands, citations, and concise output instructions. These test the prompt contract, not model obedience. |
| [DocumentQaApplicationServiceTest.java](</Users/rony/Desktop/AI MediDoc Assist/backend/src/test/java/com/mediassist/platform/documentqa/application/DocumentQaApplicationServiceTest.java>) | Created six mocked service tests covering builder integration, configured settings, response/source mapping, LocalDateTime, success audits, missing context, retrieval failures, provider failures, and unexpected generation failures. |
| [compare_prompt_baseline.py](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/compare_prompt_baseline.py>) | Created a standard-library comparison script that reuses saved documents, questions, embeddings, and topK values. It refuses to overwrite existing evidence and records full responses, source order, citation ranges, and evidence-marker coverage. |
| [rag_prompt_evaluation_results.json](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/rag_prompt_evaluation_results.json>) | Created the first prompt comparison, including its failures. |
| [rag_prompt_evaluation_results_v2.json](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/rag_prompt_evaluation_results_v2.json>) | Created a second comparison after tightening missing-information and ambiguous-fragment instructions. Includes the evaluated builder's SHA-256. |
| [rag_prompt_evaluation_results_v3.json](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/rag_prompt_evaluation_results_v3.json>) | Created the final comparison after requiring quoted evidence for disagreements. Its builder SHA-256 matches the final source. |
| [Prompt_Builder_Comparison.md](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/Prompt_Builder_Comparison.md>) | Created this implementation and verification record. |

No controllers, DTOs, entities, migrations, embedding logic, retrieval queries, provider configuration, or Python service code changed. Earlier evaluation documents and PDF snapshots were not replaced.

## Design and Flow

1. The existing controller validates the request and calls DocumentQaApplicationService.
2. The application service trims the question as before and calls the existing semantic-search application logic internally.
3. DocumentQaPromptBuilder constructs two messages: system instructions and a user message containing the question and retrieved evidence.
4. Chunks retain retrieval order, full text, source indexes, and similarity metadata. `[Chunk 1]` refers to the first response source, not PDF page 1 or stored chunk index 1.
5. The unchanged LlmClient sends the messages to the configured provider.
6. The application service maps the completion, records the existing success audit, and returns the existing response DTO with LocalDateTime.

Prompt construction belongs in `documentqa.application` because it is part of the QA use case, not an HTTP-provider concern. Separating it keeps the application service focused and makes prompt behavior independently testable. No additional domain entity or interface is needed for a stateless builder with one implementation.

The instructions distinguish recorded diagnoses, investigated conditions, reported symptoms, observed findings, and attributed opinions. They request evidence for both sides of a real disagreement, preserve uncertainty, discourage whole-document absence claims, and treat embedded document commands as untrusted content. They also request answers within 180 words to reduce output-budget pressure. This is a soft instruction, not programmatic truncation; the existing rejection of a known token-limit completion remains intact.

## Verification

- `mvn clean test`: BUILD SUCCESS; 32 tests, zero failures, errors, or skipped tests. Sixteen tests were added in this change.
- Main and test code compiled with Java release 21. The available local runtime used for tests and startup was JDK 25; execution on JDK 21 was not separately verified.
- The temporary backend started on port 8081 with PostgreSQL, Hibernate, and all nine Flyway migrations validated. No migration was necessary.
- The comparison script passed `py_compile`; `git diff --check` passed.
- The temporary backend was stopped after evaluation. The pre-existing server on port 8080 was not restarted to activate these changes.

| Run | HTTP 200 Responses | Notable Finding |
| --- | --- | --- |
| Original saved baseline | 20/21 | Broad topK=5 answer invented a lumbar-strain/diabetes conflict; topK=10 broad answer hit the output-token limit. |
| First builder prompt | 20/21 | Output-limit failure persisted; unsupported attribution and absence wording remained. |
| Second prompt | 21/21 | Broad completion succeeded, but invented disagreement and an incorrect CRPS consensus claim remained. |
| Final prompt | 21/21 | Completion succeeded within the requested word count, but grounding and evidence-category errors remained. |

In the final run, all 20 responses comparable with successful original responses retained exactly the same source IDs and order. The original failed response supplied no sources to compare for the remaining case. All final sources belonged to the selected document; all detected bracketed chunk numbers were in range. This does not establish that those sources support the claims. One abstention answer used bare chunk references rather than the requested bracket syntax.

The replay created no new patients, documents, chunks, or stored embeddings. Query embeddings were generated normally and successful requests wrote their existing question audit events.

## Remaining Quality Failures

- **Q08, topK=5:** The final answer put lumbar strain and CRPS under conflicting labels, then acknowledged they are distinct diagnoses and do not explicitly contradict each other. This is still an unsupported and internally inconsistent disagreement claim. It also mixed observed swelling/contact with reported symptoms or reviewer opinions.
- **Q07, topK=5:** The final answer introduced an unsupported CRPS-versus-diabetes contradiction that the question did not ask for. It also added a credibility interpretation not established by the cited evidence. This is a regression relative to the original focused answer's central findings.
- **Q04:** The central answer preserved the recorded CRPS assessment and later non-confirmation, but still borrowed correction-letter/label-reconciliation language from the diabetes passages. Evidence fragments crossing record boundaries remain risky.
- **Q05, topK=10:** The opening phrase said no medication or dose was prescribed "in the supplied document context." The explanation discussed missing documentation, but the opening still blurs missing evidence with whether a prescription occurred. The desired answer is that the prescription cannot be determined from the retrieved context.
- **Q08, topK=10:** More relevant evidence was retrieved and the diabetes disagreement was identified. However, observed contact/swelling was still placed under reported symptoms, and the requested two-quote disagreement format was not consistently followed.

Q08's five-chunk context still contains only three of five expected evidence markers; it lacks the Type I diabetes label and possible carpal-tunnel investigation. The ten-chunk context contains all five. A prompt cannot recover missing evidence, and missing retrieval evidence never justifies inventing a contradiction.

The fictional access-code, document-isolation, and embedded BANANA-instruction checks continued to behave as expected in this run. These toy cases do not prove authorization, robust prompt-injection resistance, or clinical reliability. The fixture explicitly describes itself and its uncertainty, so harder synthetic records and repeated runs are required before drawing quality conclusions.

## Recommended Next Work

1. Keep the quality defect open. Improve chunk boundaries so sentences and dated record attribution are preserved; evaluate retrieval coverage for broad, multi-part questions rather than simply increasing topK everywhere.
2. Repeat these regressions and add less explicitly labelled synthetic records. Review claim support, not just source presence. Evaluate the configured model on the same corpus if unsupported assertions persist after better evidence retrieval.
3. Consider citation/quote consistency checks as a separate change. Exact quote matching can detect invented quotes, but cannot by itself establish that two statements genuinely contradict each other.
4. Fix the independently identified processing issues: failure-state/audit rollback, FastAPI blank-string validation serialization, and inherited framework error-response consistency. None was changed in this prompt-only task.

## Activate and Repeat

Restart the existing backend to load the new builder. Keep PostgreSQL, the embedding service, and Ollama running. From the backend directory, use the same storage root as the existing documents:

```sh
cd "/Users/rony/Desktop/AI MediDoc Assist/backend"
DOCUMENT_STORAGE_ROOT="/Users/rony/Desktop/AI MediDoc Assist/storage/documents" mvn spring-boot:run
```

In Swagger or Postman, send `POST /api/v1/documents/{documentId}/questions` with `Content-Type: application/json`, the existing required `X-Actor` header, and a body such as:

```json
{
  "question": "Which conditions are recorded and which were only investigated?",
  "topK": 5
}
```

Compare the answer with the full retrieved chunk text, not only the previews. Test focused and broad questions at topK 5 and 10, missing-information questions, conflicting dated records, and document isolation. No extraction, chunking, or stored-embedding regeneration is needed for a prompt-only change.

To replay the saved fictional baseline against the restarted server, run from the repository root and choose a new output filename:

```sh
python3 docs/evaluation/compare_prompt_baseline.py \
  --base-url http://localhost:8080 \
  --output docs/evaluation/rag_prompt_comparison_next.json
```

Do not use this script with real patient records without reviewing its response-storage and privacy implications. Its saved outputs include full fictional chunk text and answers.
