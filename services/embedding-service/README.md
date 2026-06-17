# MediAssist Embedding Service

This FastAPI service converts text chunks into dense embedding vectors for the MediAssist backend.

The Spring Boot backend calls this service at:

```text
POST http://localhost:8001/api/v1/embeddings
```

The Python service only generates embeddings. Spring Boot stores vectors in PostgreSQL/pgvector and performs semantic search. This service does not implement RAG, LLM answering, summaries, or search.

## Model

The default model is `BAAI/bge-m3` using `FlagEmbedding`.

The service returns dense, normalized embeddings only. For BGE-M3, the expected dense vector dimension is `1024`.

The model is loaded once and reused across requests. The first request may take time because the model may need to download from Hugging Face and initialize locally.

## Configuration

Copy the example environment file if you want local overrides:

```bash
cp .env.example .env
```

Available environment variables:

```text
EMBEDDING_MODEL_NAME=BAAI/bge-m3
EMBEDDING_DEVICE=auto
EMBEDDING_MAX_BATCH_SIZE=32
EMBEDDING_EXPECTED_DIMENSIONS=1024
EMBEDDING_SERVICE_PORT=8001
EMBEDDING_USE_FP16=true
```

`EMBEDDING_DEVICE` supports `auto`, `cpu`, `mps`, or `cuda`.

## Create A Virtual Environment

Use Python 3.10, 3.11, or 3.12 if possible. PyTorch and FlagEmbedding may not publish wheels immediately for very new Python releases.

From `services/embedding-service`:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

## Install Dependencies

```bash
pip install --upgrade pip
pip install -r requirements.txt
```

The install can take a while because `torch`, `FlagEmbedding`, and model dependencies are large.

## Run Locally

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

You can also run:

```bash
python -m app.main
```

`python -m app.main` uses `EMBEDDING_SERVICE_PORT`, which defaults to `8001`.

## Test Health

```bash
curl http://localhost:8001/health
```

Expected response:

```json
{
  "status": "UP",
  "model": "BAAI/bge-m3",
  "dimensions": 1024
}
```

## Test Embeddings

```bash
curl -X POST http://localhost:8001/api/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "BAAI/bge-m3",
    "texts": [
      "The patient was diagnosed with hypertension.",
      "Follow-up visit scheduled in two weeks."
    ]
  }'
```

Expected response shape:

```json
{
  "model": "BAAI/bge-m3",
  "dimensions": 1024,
  "embeddings": [
    [0.01, 0.02],
    [0.03, 0.04]
  ]
}
```

The real vectors contain `1024` floating-point values each.

## Mac Notes

For Apple Silicon Macs, `EMBEDDING_DEVICE=auto` will choose `mps` when PyTorch detects MPS support. If you hit PyTorch/MPS compatibility issues, use CPU:

```bash
EMBEDDING_DEVICE=cpu uvicorn app.main:app --host 0.0.0.0 --port 8001
```

CPU works but is slower. The first model download and first inference can take noticeably longer.

## Spring Boot Integration

Make sure the Spring Boot backend has matching config:

```yaml
mediassist:
  embedding:
    endpoint-url: http://localhost:8001/api/v1/embeddings
    model-name: BAAI/bge-m3
    dimensions: 1024
```

The backend sends chunk text to this service, receives normalized dense vectors, stores them in PostgreSQL/pgvector, and runs semantic search from Java.
