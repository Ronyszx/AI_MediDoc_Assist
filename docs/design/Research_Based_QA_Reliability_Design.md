# Research-Based Design for Reliable MediAssist Document QA

Date: 6 October 2026.

Status: proposed design, not implemented. This document records a targeted primary-literature review and its application to the existing codebase. It is not a systematic literature review or clinical validation. No application code, dependencies, configuration, migrations, stored data, or running services were changed in this design exercise.

## 1. Problem and Recommendation

MediAssist currently has two different defects:

- Retrieval can miss a required part of a broad question, including one side of a disagreement.
- Generation can misclassify or overstate evidence even when the right passages were retrieved.

The latest local MMR evaluation retrieved 2/5 literal evidence markers for the broad Q08 case at topK 5 and 4/5 at topK 10. More aggressive diversity lost focused contradiction evidence. The relevant records and full qualifications are in [QA_Retrieval_Comparison.md](</Users/rony/Desktop/AI MediDoc Assist/docs/evaluation/QA_Retrieval_Comparison.md>). These fractions are retrieval diagnostics, not medical accuracy scores.

Recommendation: adopt a bounded, facet-aware retrieval pipeline, followed by attributed evidence extraction and claim-level evaluation. Keep the present embedding model and answer model fixed during the first retrieval experiment. Do not introduce a knowledge graph, web search, OCR, fine-tuning, or an autonomous agent loop to solve this problem.

The proposals below are engineering adaptations, not claims that any paper evaluated this exact pipeline, our healthcare documents, or qwen3.5:4b.

## 2. Papers Reviewed and What Transfers

### Question Decomposition for Retrieval-Augmented Generation

Ammann, Golde, and Akbik, ACL Student Research Workshop 2025. The method retains the original question, generates subquestions, retrieves for each, deduplicates candidates, and applies a cross-encoder reranker. This is the closest structural match to our missing-facet problem. Its experiments also show that answer-level improvements do not imply uniform improvements in supporting-fact or joint metrics. Decomposition adds inference cost; it can overproduce subqueries. Its larger model and non-clinical benchmarks do not establish results for our local model. [Paper and PDF](https://aclanthology.org/2025.acl-srw.32/)

### Typed-RAG: Type-Aware Decomposition of Non-Factoid Questions

Lee et al., XLLM 2025. The pipeline treats different question intents differently and uses multi-aspect processing where appropriate, rather than applying one retrieval policy everywhere. We borrow intent-aware routing, not its complete taxonomy or answer-generation workflow. Our clinical evidence categories are our own domain adaptation. The paper acknowledges missing comparisons against other reformulation methods and possible bias in model-based evaluation. [Paper and PDF](https://aclanthology.org/2025.xllm-1.14/)

### Reciprocal Rank Fusion

Cormack, Clarke, and Buettcher, SIGIR 2009. RRF combines ranked lists through reciprocal rank contributions rather than requiring comparable raw retrieval scores. The original work combined IR systems, not our clinical subquestions. Applying it to facet rankings is an adaptation. Rank fusion alone can still favor popular evidence that appears in many lists, so it is not our completeness check. [Original paper](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf)

### Passage Re-ranking with BERT

Nogueira and Cho, first released 2019. This work jointly scores a query and passage after initial retrieval, illustrating the role of a more expensive relevance model in a two-stage search pipeline. A reranker estimates relevance, not whether a claim is a confirmed diagnosis or an unresolved opinion. We would use a separately evaluated pretrained reranker, not train the paper's model or claim its benchmark results transfer to MediAssist. [Paper](https://arxiv.org/abs/1901.04085)

### ConText: Clinical Negation, Experiencer, and Temporal Status

Harkema et al., Journal of Biomedical Informatics 2009. Clinical mentions need contextual properties, including negation, temporality, and whether a condition concerns the patient or someone else. The work also explains limitations of surface clues for temporal interpretation. We borrow explicit contextual fields, not an assumption that every unmarked mention is affirmed or diagnosed. It is not a complete classifier for our reported-symptom, observed-finding, and reviewer-opinion distinctions. [Primary article](https://pmc.ncbi.nlm.nih.gov/articles/PMC2757457/)

### Lost in the Middle

Liu et al., TACL 2024. The evaluated models were sensitive to the position of relevant information in long contexts. This motivates testing ordering and keeping context bounded, not a claim that a particular ordering fixes our model. We will test compact context, nearby attribution, and adjacent comparison evidence rather than sending every retrieved passage. [Paper and PDF](https://aclanthology.org/2024.tacl-1.9/)

### ALCE: Enabling LLMs to Generate Text with Citations

Gao et al., EMNLP 2023. ALCE separates answer correctness from citation quality and evaluates whether cited passages support generated statements. Citation recall and precision address support and unnecessary citations; valid source numbers are insufficient. Automated entailment judgments have limitations and are not clinical verification. We borrow claim-level citation review and retain human-labeled regression cases. [Paper and PDF](https://aclanthology.org/2023.emnlp-main.398/)

### FActScore

Min et al., EMNLP 2023. FActScore decomposes generated text into atomic claims and checks their support against a knowledge source. Its main setting is long-form factual generation, not our clinical categories. We adapt atomic-claim evaluation to the selected document only; we do not validate patient-specific claims against Wikipedia or outside medical knowledge. [Paper](https://arxiv.org/abs/2305.14251)

### Correctness Is Not Faithfulness in RAG Attributions

Wallat et al., arXiv 2024. The paper distinguishes a cited passage supporting a statement from the model actually relying on that passage. The latter requires causal dependence and is harder to establish. We borrow evidence-removal and evidence-change tests. Exact quotation and entailment checks alone cannot certify causal faithfulness. [Paper](https://arxiv.org/abs/2412.18004)

## 3. Proposed Pipeline

```text
Validated question + server-controlled documentId
  -> small retrieval plan: original question + requested facets
  -> batch query embeddings
  -> document/model-scoped retrieval per query
  -> chunk-ID deduplication + rank fusion + facet nominations
  -> optional relevance reranking of the bounded candidate pool
  -> bounded, coverage-aware context selection
  -> source-attributed evidence extraction
  -> quote/source validation and uncertainty preservation
  -> constrained answer rendering + existing sources and audit
```

The first implementation milestone ends at context selection and evaluation. Evidence extraction and answer rendering are a separate milestone because retrieval and generation must be measured independently. An optional reranker operates on the bounded candidate pool before final selection: reranking only the already-selected topK cannot recover discarded evidence.

### A. Plan the Information Need, Not the Answer

For the existing broad example, produce queries for recorded diagnoses, suspected or investigated conditions, patient-reported symptoms, attributed reviewer assessments, and differing statements about the same subject. Include observed findings only if requested or necessary to separate them from symptoms. This example is not a hardcoded production list of diseases or expected answers.

Retain the original question. A focused medication-dose question should not acquire unrelated diabetes or CRPS queries. Planning is based on user intent, never on an invented diagnosis, date, patient, result, or hypothesis presented as fact.

Recommended first version: a QuestionPlanner abstraction with a bounded LLM implementation using the existing LlmClient, plus a single-query fallback. It returns a compact validated plan, not chain-of-thought or answers. Explicitly structured questions can later use a deterministic planner as an evaluated optimization; keyword splitting alone is not a reliable general solution.

Limit the planner to one call, up to six facet queries, and a configured output budget. Validate schema, count, lengths, duplicate facets, and preservation of supplied identifiers/date qualifiers where applicable. These checks do not prove semantic equivalence; evaluate planner drift separately. Malformed or unavailable planning falls back to original-question retrieval and records degradation, rather than silently claiming complete coverage. Genuine embedding/provider failures retain existing clear errors.

### B. Retrieve Independently and Merge Without Losing Facets

Embed the original question and facet queries in one batch through the existing embedding abstraction. Add a batch-query use case so document/chunk/model validation happens once, rather than calling the current single-query method repeatedly and issuing one HTTP request per facet.

Provisional experiment budgets are 20 candidates for the original question and 10 per facet, giving at most 80 retrieved entries before deduplication for six facets. Cap unique candidates at 80. These are starting values to test, not paper-derived optimums. All searches use the same validated documentId and configured embedding model. The planner must not control database filters or select another document.

Deduplicate only by chunk ID initially. Do not discard two passages merely because their text or embeddings are similar: similar passages can contain opposed labels, negations, or different attributed assessments.

Use RRF as a cheap merge baseline. Keep a rank map per query and use a configurable fusion constant; 60 is a conventional starting experiment value, not user topK. Do not average cosine scores from different queries and relabel the result as similarity to the original question.

Retain each facet's best-ranked nominees before applying the global candidate cap. Rank fusion is ordering information, not proof that a facet has been answered. A global rerank alone must not erase all evidence for a requested aspect without recording that it is uncovered.

### C. Select Context Under Both Count and Token Limits

Replace the assumption that global diversity implies completeness with a coverage-aware selection policy. Give requested facets an opportunity to nominate evidence, allow one chunk to address multiple facets, then fill remaining slots using relevance/fusion ranks. Do not force a low-quality passage into the answer merely to fill a category.

Keep the current topK meaning: a maximum number of distinct source chunks, not an embedding candidate count. Also impose an explicit prompt-token budget that reserves space for instructions, question, and output inside the configured 8192-token context. A provisional evidence-text limit is 4000 tokens, still reduced when actual prompt overhead requires it. Never equate the current 1000-character chunk size with 1000 tokens. Calibrate a tokenizer or conservative estimator for the deployed model.

If topK or the token budget cannot fit all required evidence, return a partial-context answer with explicit uncertainty for the missing parts. Do not secretly raise topK to 10, infer that evidence is absent from the whole document, or fabricate the missing side of a disagreement.

Keep the existing MMR component opt-in. Test it only as a secondary redundancy policy after facet evidence is protected; it is not the completeness mechanism.

Neighbour expansion is optional, not an automatic lookup for every chunk. If attribution is ambiguous, evaluate reading one adjacent chunk on either side from the same extraction. Every added source counts against topK and the token budget and must retain its real chunk ID. Do not invent PDF page numbers or assume the nearest heading applies: current chunks can contain multiple records, and there are no stored per-chunk page spans.

### D. Introduce a Reranker Only If Evaluation Justifies It

A configurable PassageReranker port can jointly score question-passage pairs. Compare original-question and facet-conditioned reranking while preserving facet nominations. Do not treat logits as probabilities of truth or clinical confidence.

Do not add a new model in the first retrieval baseline. If fusion/selection still admits too much noise, benchmark an off-the-shelf cross-encoder separately for relevance, local memory usage, and latency. Keep its infrastructure behind an HTTP adapter. A future Python reranker should be a clearly separated service, not semantic search or answer generation added to the embedding-only service.

### E. Build Attributed Evidence Before Writing Prose

The second milestone replaces unrestricted prose composition for classification/comparison questions with structured evidence extraction from the final selected chunks. An EvidenceItem is an ephemeral data record, not a new JPA entity or knowledge graph.

Useful fields are source chunk ID/index, an exact source span, the statement, evidence category, assertion/uncertainty wording, and explicitly supported author/date/subject context. Category values can include recorded diagnosis/history, investigated or suspected condition, patient report, observed finding, reviewer assessment, and unknown. Category and assertion properties are separate: negation or history must not be flattened into a diagnosis label.

Unknown author, date, subject, category, or certainty remains unknown. Operational timestamps remain LocalDateTime. A date written inside a PDF is source text unless safely parsed; do not fabricate a midnight timestamp or add a timezone to it.

An investigation request does not establish its result. A clinician's recorded diagnosis is an attributed assessment, not independent proof of clinical truth. A reviewer's non-confirmation at a later examination does not erase an earlier recorded diagnosis. Observed swelling is not a patient report just because both appear in one paragraph.

Avoid asking the model to make a medical judgment when labeling documents. Categories describe how statements are recorded, not which clinician is correct. If the classifier is unsure, retain an unclassified attributed quotation rather than guessing. This can reduce usefulness and must be tracked as unknown-category and false-abstention rates, not marketed as perfect accuracy.

### F. Validate What Can Be Validated, Then Constrain the Answer

Validate source IDs against the selected context and require supplied source spans to match actual chunk text. Define character-offset and whitespace rules precisely; do not accept fuzzy matching that can change negation. Preserve every quotation's true source even when overlap repeats text.

Quote matching proves provenance only. A copied quote can be interpreted incorrectly, and a correct category can still be attached to the wrong subject. Semantic support needs labeled tests and, optionally, a separately evaluated entailment/classification model. The same small model approving its own output is not an independent guarantee.

For a claimed conflict, require two attributed, supporting passages about the same subject and compatible time/scope. If there is only one label, do not infer the other from general wording about disagreement. Prefer reporting the two recorded statements and unresolved assessment differences over declaring a clinical contradiction. Different conditions can coexist; changed findings across dates need not be inconsistent.

Generate the final concise answer from the validated evidence items. Prefer a deterministic renderer for category lists and side-by-side comparisons first; test whether the extractive style is useful. If an LLM is used to paraphrase, perform final claim/citation review rather than assuming the evidence-list constraint is obeyed.

Maintain the current response DTO initially. Partial-context status can be stated in answer text; proposed explicit coverage metadata would require a separately reviewed API change. Never mislabel a provider failure as a medical absence, and never silently return an unvalidated answer after an extraction or verification failure. Preserve existing provider/generation exception behavior.

## 4. Fit to the Existing Architecture

| Layer or Module | Proposed Responsibility |
| --- | --- |
| `documentqa.api` | Existing validated question endpoint and response mapping; no retrieval or interpretation logic. |
| `documentqa.application` | Extend DocumentQaContextRetrievalService with planning, fusion, and bounded facet selection. Later add focused evidence extraction/validation/rendering components. |
| `documentqa.domain` | Data-only QuestionPlan, QueryFacet, and later EvidenceItem records/enums. No JPA relationships or entity behavior. |
| `documentqa.infrastructure` | Provider-specific planning and optional reranking adapters, configuration binding, and timeouts. |
| `documentembedding.application` | Batch query embedding/retrieval use case through the existing EmbeddingService and repository abstractions. |
| `documentembedding.infrastructure` | Scoped pgvector candidate queries; existing vector storage remains unchanged. |

Start with QuestionPlanner, small plan/facet records, a rank-fusion component, a facet selector, and extensions to the existing retrieval use case. Do not implement every future abstraction at once. Reuse constructor injection, existing audit patterns, and DTO mapping. Keep business policy outside entities.

No migration or regeneration of stored embeddings is required for the first milestone. Adding dependable page/section provenance later is a separate data-model and migration decision. Existing documents must not be silently re-chunked.

Keep database transactions short in the eventual orchestration: provider calls should not hold the current answerQuestion transaction open unnecessarily. Handle that as an explicit transaction-boundary change with audit/failure tests, not an incidental refactor.

All candidates, neighbour lookups, citations, and returned sources must stay inside the requested document scope. This filter is not authorization. Real patient use still requires access control and privacy/security hardening. Do not log questions, source text, prompts, or evidence items by default; diagnostic logs can contain counts, durations, strategy/version, and degradation codes. No chat-history table is needed.

## 5. Evaluation Before Implementation Choices

### Diagnose Retrieval Versus Generation

First create an oracle-context control: provide the answer model with human-selected sufficient source passages while keeping the question and model fixed. If classification errors persist, better retrieval alone cannot fix them. This is an evaluation control only, not hardcoded production retrieval.

For each question label the required source spans, requested facets, valid attribution/category, disagreement pairs, and whether the question is answerable under the declared context budget. Literal marker presence is insufficient; paraphrases and source overlap can distort it.

### Compare One Change at a Time

1. Existing single-query relevance-only retrieval.
2. Existing MMR variant as a historical comparison, not the default.
3. Decomposition plus chunk-ID union and RRF.
4. The same candidate pool plus facet-aware bounded selection.
5. Optional cross-encoder reranking on that fixed pool.
6. Structured evidence extraction and constrained rendering on fixed retrieved contexts.
7. Another answer model only if generation errors persist, evaluated against the same context and questions.

Hold PDF extraction, chunk boundaries, stored embeddings, query set, and final context budgets fixed within each retrieval comparison. Freeze development settings before testing held-out documents. Track planner failures separately from retrieval and generator errors.

### Corpus and Measures

A feasible starting corpus is 10-15 independently written synthetic documents and around 60 labeled questions, split by document into development and held-out sets. This is a proposed student-project scale, not a production validation standard. Retain the existing 21 cases as regressions and add less explicitly labeled notes.

Include focused questions, multi-part questions, explicit opposed labels, different assessments on different dates, family history, negation, pending tests, quoted reviewer opinions, reported symptoms next to observed findings, missing information, embedded commands, and cross-document isolation. Use adversarial pairs differing by one negation or attribution. Repeat nondeterministic model runs and report variability.

| Measure | What It Detects |
| --- | --- |
| Evidence recall at the declared context budget | Required facts or spans missing from selected context. |
| All-requested-facet coverage and disagreement-pair recall | Completeness failures hidden by good average similarity or first-hit ranking. |
| Supported-claim precision and citation support/completeness | Unsupported assertions, decorative citations, and missing support. |
| Category confusion matrix, attribution accuracy, unknown rate | Investigations presented as diagnoses; observations presented as reports; wrong record/author assignment. |
| Abstention precision/recall and partial-answer coverage | Confident guesses as well as excessive safe-but-useless refusal. |
| Source membership, quote-span validity, isolation checks | Deterministically checkable provenance failures. |
| Evidence-removal/change response | Whether an answer persists after its supporting evidence changes or disappears. |
| p50/p95 latency, calls, candidate count, context/output tokens | Local resource cost and performance tradeoffs. |

For evidence perturbation tests, remove an actual supporting statement while retaining distractors, or change a synthetic label, and require the answer to change or acknowledge insufficient evidence. Compare multiple runs; these are useful probes, not a complete proof of causal faithfulness.

Predefine acceptance gates before tuning. Require no regressions on focused critical cases, recovery of both statements in feasible known-disagreement cases, zero wrong-document references or invented source spans, and improved held-out facet coverage. Require no observed unsupported diagnosis/conflict claims in the fixed release-critical cases. These finite-test outcomes do not guarantee correctness on unseen inputs. Use human review for category and support labels; automated judges assist but do not certify clinical reliability.

If retrieval improves but oracle-context category accuracy remains weak, prioritize evidence extraction/answer-model evaluation instead of increasing candidate counts again. If a budget makes full coverage impossible, evaluate a correctly qualified partial answer rather than insisting on an impossible completeness score.

## 6. Implementation Order

| Milestone | Deliverable | Explicitly Excluded |
| --- | --- | --- |
| A: Evaluation baseline | Oracle contexts, labeled facets/spans, held-out corpus, predeclared metrics. | No production strategy change. |
| B: Facet retrieval | Bounded planner, batch query embeddings, scoped per-facet candidates, RRF, coverage-aware selection. | No new model, migration, re-embedding, or answer prompt change in the retrieval comparison. |
| C: Relevance refinement, only if needed | Optional reranker and measured context/attribution expansion. | No unbounded loop, hidden topK increase, or generated page citations. |
| D: Grounded answer structure | Evidence items, source-span checks, uncertainty/attribution categories, constrained rendering and semantic review. | No automatic clinical adjudication, medical advice, or claim that quote checks prove meaning. |

Use feature flags and preserve a rollback to single-query retrieval. Do not switch the default until the agreed gates pass. Keep configuration/model names centralized, preserve LocalDateTime, and retain the public semantic-search route as it is unless a separately evaluated change is approved.

## 7. What We Are Not Claiming

This is a design assembled from relevant research and our observed regressions, not a reproduction of published experiments. RRF, facet allocation, exact quotation, and deterministic rendering can each still fail or reduce answer usefulness. More components also add latency and failure modes. No percentage gain from a paper is a forecast for this project, and no paper reviewed establishes clinical readiness for MediAssist.

The immediate next work should be the oracle-context and facet-retrieval experiment, not another global MMR weight adjustment or blanket topK increase. Application implementation requires a separate follow-up; the current backend remains unchanged by this document.
