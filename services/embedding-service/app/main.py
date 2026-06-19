import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings, get_settings
from app.embedding_model import EmbeddingInferenceError, EmbeddingModel, UnsupportedModelError
from app.schemas import EmbeddingRequest, EmbeddingResponse, HealthResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger(__name__)

settings: Settings = get_settings()
embedding_model = EmbeddingModel(settings)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Embedding service starting with model '%s'", settings.model_name)
    yield
    logger.info("Embedding service stopped")


app = FastAPI(
    title="MediAssist Embedding Service",
    description="Local embedding service for converting document chunks into dense vectors.",
    version="0.1.0",
    lifespan=lifespan,
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    body = await request.body()
    body_text = body.decode("utf-8", errors="ignore")

    logger.error(
        "Validation error for path=%s body_preview=%s errors=%s",
        request.url.path,
        body_text[:2000],
        exc.errors(),
    )

    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors()},
    )


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        model=settings.model_name,
        dimensions=settings.expected_dimensions,
    )


@app.post("/api/v1/embeddings", response_model=EmbeddingResponse)
def create_embeddings(request: EmbeddingRequest) -> EmbeddingResponse:
    if request.model != settings.model_name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported model '{request.model}'. Supported model: '{settings.model_name}'.",
        )

    if len(request.texts) > settings.max_batch_size:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Batch size {len(request.texts)} exceeds max batch size {settings.max_batch_size}.",
        )

    cleaned_texts = [text.strip() for text in request.texts]

    try:
        embeddings = embedding_model.embed(cleaned_texts, request.model)
    except UnsupportedModelError as exception:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exception)) from exception
    except EmbeddingInferenceError as exception:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exception),
        ) from exception

    return EmbeddingResponse(
        model=settings.model_name,
        dimensions=settings.expected_dimensions,
        embeddings=embeddings,
    )


if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port)