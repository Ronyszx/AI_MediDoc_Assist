from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    model_name: str = Field(default="BAAI/bge-m3", validation_alias="EMBEDDING_MODEL_NAME")
    device: Literal["auto", "cpu", "mps", "cuda"] = Field(default="auto", validation_alias="EMBEDDING_DEVICE")
    max_batch_size: int = Field(default=32, ge=1, le=256, validation_alias="EMBEDDING_MAX_BATCH_SIZE")
    expected_dimensions: int = Field(default=1024, ge=1, validation_alias="EMBEDDING_EXPECTED_DIMENSIONS")
    port: int = Field(default=8001, ge=1, le=65535, validation_alias="EMBEDDING_SERVICE_PORT")
    use_fp16: bool = Field(default=True, validation_alias="EMBEDDING_USE_FP16")


@lru_cache
def get_settings() -> Settings:
    return Settings()
