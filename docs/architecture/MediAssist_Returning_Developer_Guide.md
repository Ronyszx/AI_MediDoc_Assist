# MediAssist
## Returning Developer Guide

**Repository snapshot: 1 October 2026**

A practical map of the code, the last completed phase, and the steps to get the project running again.

### Where We Left Off

The last phase we implemented was the **RAG Answer API**: `POST /api/v1/documents/{documentId}/questions`. Its Spring Boot code exists, including internal semantic retrieval, a grounded prompt, answer/source DTOs, an HTTP LLM client, and audit migration V9.

You previously reported manual verification of the pipeline through semantic search. The latest QA implementation compiled and passed the available tests, but this repository still contains no external LLM server. Answer generation needs that service and a real model name configured before it can work end to end.

### The Pipeline

[[pipeline]]

Each processing step has its own endpoint. Uploading a PDF does not automatically run extraction, chunking, or embedding.

### How To Use This Guide

| Pages | What You Will Find |
| --- | --- |
| 2-4 | Repository map, code responsibilities, retrieval and RAG |
| 5-6 | Database relationships, migrations, configuration |
| 7-9 | Startup commands and an API walkthrough |
| 10-11 | Endpoint reference and external service contracts |
| 12-13 | Troubleshooting, current checks, and the next checkpoint |

This is a code-based handover, not a claim that all services are running today. The backend test passed during this review; Docker was not running and no listeners were observed on the normal service ports.

<!-- page -->

# 02 / The Repository Map

### Top-Level Structure

```text
AI MediDoc Assist/
  backend/                    Java / Spring Boot API
    pom.xml                   Java 21 target; Spring Boot 3.5.14
    src/main/java/com/mediassist/platform/
    src/main/resources/application.yml
    src/main/resources/db/migration/   V1 through V9
    src/test/java/            One PDFBox extraction test
  services/embedding-service/ Python / FastAPI / BGE-M3
  infra/compose/              PostgreSQL + pgvector Compose
  docs/architecture/          This guide and its source
  storage/                   Existing local document files
```

`backend/target/` is Maven output. `.idea/` is IDE configuration. Neither directory explains the application architecture.

### Package By Feature

Java features are `patient`, `document`, `audit`, `documentextraction`, `documentchunk`, `documentembedding`, and `documentqa`. `config` wires infrastructure beans; `shared` holds API errors and exception handling.

Within a feature, the request path is **api -> application -> domain -> infrastructure**. The application calls a repository or client interface; the infrastructure implementation performs the actual database, file, or HTTP operation.

| Layer | Responsibility | Example |
| --- | --- | --- |
| api | Routes, validated DTOs, HTTP responses | DocumentQaController |
| application | Workflows, rules, mapping, auditing | DocumentQaApplicationService |
| domain | Data and repository contracts | DocumentChunkRepository |
| infrastructure | Technical implementations | HttpLlmClient |

Controllers stay short. Constructor injection connects their dependencies. Entities hold fields and relationships; services decide what happens. DTOs keep JPA objects and vector internals out of normal responses.

QA has answer/source records rather than a persisted entity. There is no chat-history table or QA repository in this phase.

<!-- page -->

# 03 / How The Code Processes A PDF

### Patient And Document

`PatientApplicationService` creates, updates, lists, and changes patient status through `PatientRepository`; `PatientMapper` translates between DTOs and entities.

`DocumentApplicationService.uploadDocument()` calls `DocumentStorageService.store()`, creates metadata, and records an upload audit event. It attempts file cleanup if metadata creation fails.

`LocalFileSystemStorageService` validates the filename extension, declared content type, and `%PDF-` signature. It creates date-based directories, uses a UUID filename, computes SHA-256 while writing, and prevents paths escaping the configured root. The original filename remains in database metadata.

### Extraction

`DocumentExtractionApplicationService.extractDocument()` finds the document and its extraction record. Completed extraction is returned on repeat calls. `PdfBoxTextExtractionService` loads the stored PDF, extracts text, and counts pages. The service saves `COMPLETED` and records audit events.

PDFBox reads an existing text layer. Image-only scanned PDFs need a future OCR phase; a successful extraction can still contain little or no text.

### Chunking

`DocumentChunkApplicationService.chunkDocument()` requires completed extraction, returns existing chunks when present, and otherwise calls `TextChunkingService`. `DefaultTextChunkingService` uses character windows, prefers whitespace boundaries, and retains overlap. Defaults: **1,000 characters** and **150 characters overlap**, not tokens. Chunk indexes start at zero.

### Embeddings

`DocumentEmbeddingApplicationService.generateEmbeddings()` finds chunks missing embeddings for the configured model. `DefaultEmbeddingService` calls `EmbeddingClient` in batches of 32 and checks model, dimension, count, and finite vector values.

FastAPI returns normalized dense BGE-M3 vectors with 1,024 values. The Java repository inserts them into pgvector with `ON CONFLICT (chunk_id, model_name) DO NOTHING`. Repeat calls skip existing rows rather than regenerate them.

Read these classes in order to understand the ingestion pipeline. Mappers construct entities/responses; infrastructure classes perform PDF, file, model, and database work.

<!-- page -->

# 04 / Retrieval And The Latest RAG Phase

### Semantic Search

`DocumentEmbeddingApplicationService.searchSimilarChunks()` validates the document, requires chunks and embeddings, embeds the query through FastAPI, and asks the embedding repository for matches.

The repository filters by **documentId and model name**. pgvector's `<=>` operator computes cosine distance; the response uses `1 - distance` as similarity. A score such as `0.63` is a similarity value, not a probability or confidence guarantee.

### What The Question API Adds

[[qa]]

`DocumentQaApplicationService.answerQuestion()` calls that Java retrieval method directly. It builds system/user messages from the selected chunks and passes them to `LlmClient.complete()`.

The system prompt asks the model to answer only from context, cite chunks, avoid invented facts, say when information is absent, and summarize the document without offering medical advice. `HttpLlmClient` sends those messages to the configured external endpoint.

`DocumentQaMapper` returns the answer plus source IDs, indexes, similarity scores, and 240-character previews. `answeredAt` uses `LocalDateTime`. Successful requests record `DOCUMENT_QUESTION_ASKED`; the question and answer are not saved as chat history.

### Understanding Citations

Prompt citations such as `[Chunk 1]` identify the first retrieved source in the response list. They are separate from the original zero-based `chunkIndex`, which might be 20. All retrieved chunks are returned as sources; this is not a validated list of chunks the model actually cited.

### Current Boundary

No-match retrieval produces a clear context-not-found error. There is no minimum similarity threshold, so top-K results may still be weakly relevant. Prompt instructions encourage grounding but do not enforce factual correctness or resist every instruction embedded in a PDF.

The RAG client exists; the external LLM service and its real model still need to be connected and tested.

<!-- page -->

# 05 / Database And Relationships

### The Data Chain

```text
Patient 1 ---- many MedicalDocument
MedicalDocument 1 ---- 0..1 DocumentExtraction
DocumentExtraction 1 ---- many DocumentChunk
DocumentChunk 1 ---- many DocumentChunkEmbedding
```

An embedding row belongs to a chunk and a model. Different 1,024-dimensional models can have separate rows for the same chunk. `audit_events` references an entity type and ID separately; it is not a chained child of the document tables.

| Table | Main Data And Constraints |
| --- | --- |
| patients | UUID; unique MRN; identity fields; ACTIVE/INACTIVE |
| medical_documents | Patient FK; unique file/path; checksum; type; UPLOADED/ARCHIVED |
| document_extractions | Unique document FK; status; text; page count |
| document_chunks | Extraction FK; unique extraction/index; text; CREATED |
| document_chunk_embeddings | Chunk FK; unique chunk/model; VECTOR(1024); dimension = 1024 |
| audit_events | Numeric ID; entity reference; action; actor; JSONB details |

The extraction, chunk, and embedding foreign keys cascade when their parent row is deleted. No delete API currently exposes that pipeline operation. Archiving a document changes status; it does not delete its file or derived rows.

### Flyway History In Source

| Version | Purpose |
| --- | --- |
| V1 / V2 / V3 | Patients / medical documents / audit events |
| V4 | Change checksum_sha256 from CHAR(64) to VARCHAR(64) |
| V5 / V6 | Extraction table / extraction audit actions |
| V7 | Chunks table and chunking audit actions |
| V8 | Enable vector extension; embeddings table; HNSW cosine index |
| V9 | Allow DOCUMENT_QUESTION_ASKED audit action |

Flyway manages schema changes; Hibernate uses `ddl-auto: validate`. Do not edit an already applied migration; add a new version instead.

Java timestamps consistently use `LocalDateTime`. Legacy V1-V3 use `TIMESTAMPTZ`; derived tables use `TIMESTAMP`. Hibernate's JDBC time zone is UTC. That is the current schema, not a timestamp migration proposed by this guide.

<!-- page -->

# 06 / Processes And Configuration

### What Runs Where

| Component | Port | Responsibility |
| --- | --- | --- |
| PostgreSQL with pgvector | 5432 | Metadata, chunks, vectors, audit records |
| Spring Boot | 8080 | APIs, orchestration, persistence, retrieval, QA |
| FastAPI embedding service | 8001 | Text -> normalized dense vectors |
| External LLM service | 8002 default | Prompt/messages -> answer text |

Compose starts **only PostgreSQL**. Spring Boot and FastAPI run separately. The LLM service is not supplied in this repository. These defaults assume services run on your Mac; container networking needs different hostnames if Java/Python are containerized later.

### Spring Settings

Source: `backend/src/main/resources/application.yml`.

| Environment Variable | Default / Meaning |
| --- | --- |
| DB_URL / DB_USERNAME / DB_PASSWORD | localhost:5432/mediassist; mediassist / mediassist |
| SERVER_PORT | 8080 |
| DOCUMENT_STORAGE_ROOT | ./storage/documents; relative to Java working directory |
| DOCUMENT_CHUNK_MAX_SIZE / DOCUMENT_CHUNK_OVERLAP_SIZE | 1000 / 150 characters |
| EMBEDDING_SERVICE_URL | http://localhost:8001/api/v1/embeddings |
| EMBEDDING_MODEL_NAME / EMBEDDING_DIMENSIONS | BAAI/bge-m3 / 1024 |
| EMBEDDING_BATCH_SIZE | 32 |
| LLM_SERVICE_URL | http://localhost:8002/api/v1/chat/completions |
| LLM_MODEL_NAME | local-model-name; replace with actual model |
| LLM_TEMPERATURE / LLM_MAX_OUTPUT_TOKENS | 0.2 / 800 |

Multipart file and request limits are both 25MB; multipart overhead can make the effective usable file size slightly smaller.

### Python Settings

`app/config.py` reads environment variables and an optional `.env` in the service working directory. The existing example config uses model BAAI/bge-m3, device `auto`, batch limit 32, expected dimension 1024, and port 8001.

Keep Java and Python model/dimension settings aligned. `VECTOR(1024)` is fixed in V8: changing only an env var cannot enable a model with a different dimension.

<!-- page -->

# 07 / Run The Project Again

Commands below use separate terminals. Start from your current project path and keep those terminals open.

### Terminal 1: PostgreSQL

Start Docker Desktop and wait for its engine. Then:

```bash
cd "/Users/rony/Desktop/AI MediDoc Assist"
docker compose -f infra/compose/docker-compose.postgres.yml up -d
docker ps
```

The configured image is `pgvector/pgvector:pg16`; the container is `mediassist-postgres`. Data lives in a named volume. `docker compose down` stops containers; adding `-v` deletes volumes, so avoid it when preserving data.

### Terminal 2: Embeddings

An installed Python 3.11 environment already exists in this checkout:

```bash
cd "/Users/rony/Desktop/AI MediDoc Assist/services/embedding-service"
source .venv/bin/activate
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

If rebuilding the environment, use `python3.11 -m venv .venv`, activate it, and run `python -m pip install -r requirements.txt`. CPU fallback: prefix the uvicorn command with `EMBEDDING_DEVICE=cpu`. The first embedding request may download/load the model.

### Terminal 3: Spring Boot

```bash
cd "/Users/rony/Desktop/AI MediDoc Assist/backend"
mvn spring-boot:run
```

Maven targets Java 21. Today's shell reported Java 26 while Maven used Java 25; check `mvn -version`. If JDK 21 is installed, run `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` before running Maven.

Flyway applies pending migrations on startup. Open `http://localhost:8080/swagger-ui/index.html`. OpenAPI JSON is at `/api-docs`. Actuator is included; the default health check is `/actuator/health`.

### File Location Matters

From `backend/`, the default storage path is `backend/storage/documents`. Existing PDFs may also be under root `storage/documents`. Use the same root as earlier uploads; changing it can make a stored file appear missing. An absolute `DOCUMENT_STORAGE_ROOT` removes working-directory ambiguity.

<!-- page -->

# 08 / Walkthrough: Patient To Stored Chunks

Use Swagger or Postman for the following requests. Java URLs start with `http://localhost:8080`. Use a synthetic patient and a PDF with selectable text.

### 1. Find Or Create A Patient

`GET /api/v1/patients` lists existing patients. To create one, send `POST /api/v1/patients` with JSON and `X-Actor: demo-user`:

```json
{
  "mrn": "DEMO-001",
  "firstName": "Demo",
  "lastName": "Patient",
  "dateOfBirth": "1990-01-01",
  "gender": "UNKNOWN",
  "phone": null,
  "email": null
}
```

Save the returned `id` as patientId. Use a new MRN if this example was already created; duplicate MRNs return 409.

### 2. Upload A PDF

`POST /api/v1/patients/{patientId}/documents/upload` consumes multipart form data. Add `X-Actor: demo-user`, a `file` part with a real PDF, and a `documentType` field such as `DISCHARGE_SUMMARY` or `OTHER`. Let Postman/Swagger set the multipart boundary.

Expect 201 and metadata. Save the returned `id` as documentId. The metadata-only POST route does not upload binary content; use the `/upload` route for this walkthrough.

### 3. Extract Text

Send `POST /api/v1/documents/{documentId}/extract` with `X-Actor`. Inspect `GET /api/v1/documents/{documentId}/extraction` for status/page count and `GET /api/v1/documents/{documentId}/text` for extracted text.

### 4. Generate Chunks

Send `POST /api/v1/documents/{documentId}/chunk` with `X-Actor`. Then use `GET /api/v1/documents/{documentId}/chunks`. Check that chunk text is present and indexes are ordered from zero.

These POST processing routes do not require a JSON request body. Repeat calls reuse completed extraction or existing chunks. The stages are synchronous and explicitly requested; there is no background job pipeline here.

Download remains available at `GET /api/v1/documents/{documentId}/download`.

<!-- page -->

# 09 / Walkthrough: Vectors, Search, And Answers

### 5. Generate Embeddings

With FastAPI running, send `POST /api/v1/documents/{documentId}/embeddings` and `X-Actor: demo-user`. No JSON body is needed.

The response reports `documentId`, `modelName`, `embeddingDimension`, `totalChunks`, `embeddedChunks`, `skippedChunks`, and `processedAt`. Repeating the request should report zero newly embedded chunks if all are already stored for this model.

`GET /api/v1/documents/{documentId}/embeddings` returns metadata and timestamps without full vectors.

### 6. Search The Document

Send `POST /api/v1/documents/{documentId}/semantic-search` with `Content-Type: application/json`, `X-Actor`, and:

```json
{
  "query": "What diagnosis is mentioned in the document?",
  "topK": 5
}
```

The response contains ranked chunks with text, IDs, indexes, similarity scores, and the embedding model. Search topK is required and accepts 1-20.

### 7. Ask A RAG Question

Start/configure the external LLM service described on page 11. Then send `POST /api/v1/documents/{documentId}/questions` with the same headers and:

```json
{
  "question": "What diagnosis does this document mention?",
  "topK": 5
}
```

Question topK is required and accepts 1-10. Questions must be nonblank and at most 1,000 characters.

Expect `documentId`, `question`, `answer`, `modelName`, `sources`, and `answeredAt`. QA modelName identifies the answer-generating LLM; search modelName identifies the embedding model. They are different jobs and need not share a model name.

Check the answer against its source previews and full chunk text. Also ask a question absent from the document to see whether the model appropriately says the context is insufficient. Without a running compatible LLM service, this route can return 503 even when retrieval works.

<!-- page -->

# 10 / API Reference

All routes below are Spring Boot routes. Prefix with `http://localhost:8080`. `{p}` means patientId; `{d}` means documentId.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | /api/v1/patients | Create patient |
| GET | /api/v1/patients | List patients |
| GET | /api/v1/patients/{p} | Patient details |
| PUT | /api/v1/patients/{p} | Update patient |
| PATCH | /api/v1/patients/{p}/status | ACTIVE / INACTIVE |
| POST | /api/v1/patients/{p}/documents | Metadata only |
| POST | /api/v1/patients/{p}/documents/upload | Multipart PDF upload |
| GET | /api/v1/patients/{p}/documents | Patient's documents |
| GET | /api/v1/documents/{d} | Document metadata |
| PATCH | /api/v1/documents/{d}/status | UPLOADED / ARCHIVED |
| GET | /api/v1/documents/{d}/download | PDF resource |
| POST | /api/v1/documents/{d}/extract | Extract PDF text |
| GET | /api/v1/documents/{d}/extraction | Extraction metadata |
| GET | /api/v1/documents/{d}/text | Extracted text |
| POST | /api/v1/documents/{d}/chunk | Generate/reuse chunks |
| GET | /api/v1/documents/{d}/chunks | Ordered chunk text |
| POST | /api/v1/documents/{d}/embeddings | Embed missing chunks |
| GET | /api/v1/documents/{d}/embeddings | Vector metadata |
| POST | /api/v1/documents/{d}/semantic-search | Retrieve ranked chunks |
| POST | /api/v1/documents/{d}/questions | RAG answer with sources |

Mutation/processing routes above require `X-Actor`; GET routes do not. `X-Actor` is an audit label supplied by the caller, not authentication. This codebase has no security/JWT authorization layer.

The audit module provides internal recording and history retrieval services/repositories. There is no `AuditController` in the current source; do not assume an `/audits` route exists.

Other useful endpoints: Spring `/swagger-ui/index.html`, `/api-docs`, and default Actuator `/actuator/health`; FastAPI `http://localhost:8001/health` and `/docs`.

Use Swagger's DTO schemas for exact update/status payloads. Keep UUIDs distinct: patientId, documentId, extractionId, chunkId, and embeddingId refer to different records.

<!-- page -->

# 11 / Python And The External LLM Contract

### The FastAPI Files

| File In services/embedding-service | Responsibility |
| --- | --- |
| app/main.py | FastAPI routes, health, validation/inference errors |
| app/config.py | Pydantic env/.env settings |
| app/schemas.py | Request, response, health models |
| app/embedding_model.py | Lazy BGE-M3 load; dense inference; normalization |
| app/__init__.py | Python package marker |
| requirements.txt / .env.example / README.md | Install, configure, operate |

`/health` returns UP/model/dimensions without loading the model. It proves the API is responding, not that model inference works. Make an embedding request to verify inference. `auto` selects CUDA, then MPS, then CPU. Model loading is cached per process; one worker keeps memory use simpler.

```bash
curl http://localhost:8001/health
curl -X POST http://localhost:8001/api/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"BAAI/bge-m3","texts":["Synthetic test text."]}'
```

### The LLM Service You Still Need

`HttpLlmClient` expects a custom, non-streaming JSON contract at the configured `LLM_SERVICE_URL`:

```json
{
  "model": "your-real-model-name",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.2,
  "maxTokens": 800
}
```

Response: `{"model":"your-real-model-name","content":"Answer text"}`.

Configure `LLM_MODEL_NAME` and `LLM_SERVICE_URL` before starting Java. The current `local-model-name` is a placeholder. No `services/llm-service` implementation is present. Native Ollama/OpenAI response formats differ from this flat contract, so changing only the URL is insufficient; use a compatible adapter or implement another `LlmClient`.

<!-- page -->

# 12 / Troubleshooting The Restart

| Symptom | Where To Look / What To Check |
| --- | --- |
| Docker daemon connection fails | Start Docker Desktop; wait for the engine, then run Compose. |
| Database connection refused | Compose status, port 5432, DB_URL/user/password. |
| vector extension not available | Running image must contain pgvector; source Compose uses pgvector/pgvector:pg16. |
| Flyway checksum mismatch | Compare migration history/source; use a new migration for changes, not edits to applied versions. |
| Missing stored PDF | DOCUMENT_STORAGE_ROOT and launch working directory; metadata holds a relative path. |
| Extraction yields no useful text | Try a text PDF; image-only scans need OCR, which is absent. |
| Missing chunks / embeddings | Call extraction, chunking, then embedding endpoints in order. |
| Model/dimension mismatch | Align Java/Python model names and 1024 dimensions; DB vectors are fixed-size. |
| Slow first embedding call | Model download/initialization occurs on first inference, not health. |
| MPS inference failure | Retry using EMBEDDING_DEVICE=cpu in the Python terminal. |
| QA returns LLM unavailable (503) | Check external service, exact JSON contract, URL, and real LLM model name. |
| Answer not supported by sources | Inspect full chunks; current prompting does not validate answer factuality. |

### HTTP Errors And The Earlier Startup Fix

Java's `GlobalExceptionHandler` returns an `ApiErrorResponse` containing timestamp, status, error, message, and path for the explicitly handled exceptions. Typical statuses: invalid DTO 400; missing document/extraction/embedding 404; missing chunks or unfinished extraction 409; oversized upload 413; processing failure 500; LLM unavailable 503.

FastAPI uses its own `detail` format: validation normally 422, unsupported model 400, oversized batch 413, inference failure 500. Its validation-handler JSON serialization deserves a focused regression test.

We previously fixed ambiguous exception-handler startup failures. Since `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, framework-owned errors such as `MaxUploadSizeExceededException` use protected method overrides. Adding another competing `@ExceptionHandler` for the same framework type can break startup again.

On normal shutdown use Ctrl-C for Java/Python and Compose stop/down for PostgreSQL. Preserve the database volume and both the chosen document directory and its metadata.

<!-- page -->

# 13 / Your Next Checkpoint

### What Was Checked For This Guide

| Check | Result On 1 October 2026 |
| --- | --- |
| Current Java/Python code and config | Read directly from this checkout |
| Maven backend tests | Passed: 1 PDFBox test; zero failures |
| Existing Python environment | Python 3.11.15; FastAPI, FlagEmbedding, torch, uvicorn installed |
| Java runtime selection | Shell Java 26; Maven Java 25; project target Java 21 |
| Runtime listeners | None observed on 5432, 8001, 8002, 8080 |
| Docker engine | Unavailable during check |
| Fresh end-to-end pipeline / QA answer | Not exercised during this documentation task |

Earlier manual verification through semantic search came from your reports in this conversation. The available single unit test does not prove PostgreSQL, real embeddings, or LLM answers work today. Model weights/cache were not inspected or downloaded.

### The Best Place To Resume

1. Start Docker, FastAPI, and Spring Boot using page 7.
2. Inspect an existing document's extraction, chunks, and embedding metadata.
3. Run one semantic search and verify its chunk text.
4. Connect a compatible external LLM with a real model name.
5. Test the question API with both answerable and absent-information questions.

### Read The Code In This Order

`DocumentController` -> `DocumentApplicationService` -> `LocalFileSystemStorageService`; then `DocumentExtractionApplicationService`, `DocumentChunkApplicationService`, `DocumentEmbeddingApplicationService`, and `DocumentQaApplicationService`. Read their mapper/repository/client contracts alongside them. Start at `backend/src/main/java/com/mediassist/platform/`.

### Known Gaps Before Building Further

RAG still needs live LLM integration, retrieval thresholds/context budgeting, and answer/citation tests. Transactions span external calls; failure status/audit writes in extraction, chunking, and embedding can roll back with thrown runtime exceptions. Concurrent chunk creation can hit the unique constraint. Python validation logging can include submitted document text, and its error response serializer needs testing.

Automated coverage is narrow. Frontend, authentication/authorization, OCR/Textract, AWS/S3, knowledge graph, image analysis, and persisted chat history are absent. The next useful milestone is a repeatable RAG smoke test, before extending the pipeline again.

Application code was not changed for this guide. Existing local source edits were preserved. The Markdown is the editable content source for the accompanying PDF.
