from pydantic import BaseModel, Field, field_validator


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1)
    model: str = Field(..., min_length=1)

    @field_validator("texts")
    @classmethod
    def validate_texts(cls, texts: list[str]) -> list[str]:
        if any(not text or not text.strip() for text in texts):
            raise ValueError("texts must not contain blank values")
        return texts


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    status: str
    model: str
    dimensions: int
