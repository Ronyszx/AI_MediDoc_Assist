# Local Qwen Setup

MediAssist now defaults to Ollama with `qwen3.5:4b` for document question answering.
Ollama runs separately from Spring Boot on the same Mac. The FastAPI BGE-M3
service still generates embeddings; PostgreSQL/pgvector still performs retrieval.

## Costs

Local inference does not require a paid account, subscription, API key, or
per-token payment. Ollama and the selected model are available under open-source
licenses. Your remaining costs are electricity, internet usage for the initial
download, and local hardware/storage. The model download is approximately 3.4 GB;
runtime memory use is larger and depends on the context size.

This applies to local inference, not paid hosted or cloud models.

## Install And Start Ollama

Install the native macOS application from https://ollama.com/download/mac and
open it. Native execution supports Apple Silicon acceleration; Docker Desktop
on macOS does not provide GPU passthrough for Ollama.

For local-only use, disable cloud features before starting/restarting the app:

```bash
launchctl setenv OLLAMA_NO_CLOUD 1
```

That launchctl setting lasts for the current login session. To persist local-only
mode, set `"disable_ollama_cloud": true` in `~/.ollama/server.json`, preserving any
other existing settings, and restart Ollama. Keep Ollama bound to localhost.

Download the model once:

```bash
ollama pull qwen3.5:4b
ollama list
```

If using the CLI instead of the application, start `ollama serve` in a separate
terminal with `OLLAMA_NO_CLOUD=1`. Do not start a second server if the app already
owns port 11434.

## Test Ollama Directly

```bash
curl http://localhost:11434/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "qwen3.5:4b",
    "stream": false,
    "think": false,
    "messages": [
      {"role": "system", "content": "Answer only from the supplied context. Cite [Chunk 1]."},
      {"role": "user", "content": "Context [Chunk 1]: The synthetic report lists hypertension. Question: What condition is mentioned?"}
    ],
    "options": {"temperature": 0.2, "num_predict": 800, "num_ctx": 8192}
  }'
```

The answer appears under `message.content`; a completed response has `done: true`.
First inference may be slower while the model loads. `think: false` requests
direct answers rather than an extra reasoning output. This does not enforce
factual accuracy: inspect answers against their sources.

## Run MediAssist

Start PostgreSQL/pgvector, FastAPI on port 8001, and Spring Boot on port 8080 using
the existing returning-developer guide. No LLM service on port 8002 is needed for
the default Ollama provider.

From the backend directory:

```bash
cd "/Users/rony/Desktop/AI MediDoc Assist/backend"
mvn spring-boot:run
```

Use an existing document that has completed extraction, chunking, and embeddings:

```bash
curl -X POST "http://localhost:8080/api/v1/documents/<documentId>/questions" \
  -H 'Content-Type: application/json' \
  -H 'X-Actor: demo-user' \
  -d '{"question":"What condition does this document mention?","topK":5}'
```

Replace `<documentId>` with the document UUID. The response contains `answer`,
`modelName`, `sources`, and `answeredAt`. Ask a question with no answer in the
document as well, and verify the model acknowledges missing information.

## Configuration

| Environment variable | Default |
| --- | --- |
| LLM_PROVIDER | ollama |
| LLM_SERVICE_URL | http://localhost:11434/api/chat |
| LLM_MODEL_NAME | qwen3.5:4b |
| LLM_TEMPERATURE | 0.2 |
| LLM_MAX_OUTPUT_TOKENS | 800 |
| LLM_CONTEXT_WINDOW_TOKENS | 8192 |
| LLM_CONNECT_TIMEOUT | 5s |
| LLM_READ_TIMEOUT | 120s |

The client disables streaming and thinking, maps output/context limits to
Ollama's `options.num_predict`/`options.num_ctx`, and returns only answer content.
Empty, malformed, incomplete, or output-limit-truncated answers become the
existing 503 LLM-unavailable API error. Connection and read timeouts are bounded;
the read timeout may need increasing for slow first inference.

Context size is deliberately smaller than the model's advertised maximum to
limit memory use. It is not a prompt token-budget validator; very large chunks
or different languages may require a future context-budget check.

The original custom HTTP contract is still available by setting `LLM_PROVIDER=http`
and explicitly overriding `LLM_SERVICE_URL` and `LLM_MODEL_NAME`. That contract is
not the native Ollama or OpenAI format. No provider calls are made at application
startup; a running server and downloaded model are required when asking questions.

## Verification And Privacy

Run `mvn clean compile` and `mvn test` from `backend/`. Contract tests use a mock
HTTP server and do not require Ollama, PostgreSQL, or the model download. They
verify configuration selects exactly one client and the Ollama JSON mapping/error
handling. Real model behavior still requires the direct and end-to-end checks above.

Use synthetic documents while testing. Local execution avoids sending passages
to a cloud LLM but does not add authentication, document authorization, encryption,
or clinical correctness guarantees. Source previews are retrieved context, not
automatically verified citations.

References:
- https://ollama.com/library/qwen3.5:4b
- https://docs.ollama.com/api/chat
- https://docs.ollama.com/faq
- https://docs.ollama.com/macos

The returning-developer PDF is a historical snapshot from before this integration;
its port-8002/placeholder-model instructions are superseded by this setup guide.
