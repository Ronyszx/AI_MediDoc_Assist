import logging
from threading import Lock

import numpy as np
import torch
from FlagEmbedding import BGEM3FlagModel

from app.config import Settings

logger = logging.getLogger(__name__)


class EmbeddingModel:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model: BGEM3FlagModel | None = None
        self._lock = Lock()
        self._device = self._resolve_device(settings.device)

    @property
    def model_name(self) -> str:
        return self._settings.model_name

    @property
    def dimensions(self) -> int:
        return self._settings.expected_dimensions

    def embed(self, texts: list[str], requested_model: str) -> list[list[float]]:
        if requested_model != self._settings.model_name:
            raise UnsupportedModelError(
                f"Unsupported model '{requested_model}'. Supported model: '{self._settings.model_name}'."
            )

        try:
            model = self._get_model()
            with torch.no_grad():
                result = model.encode(
                    texts,
                    batch_size=min(len(texts), self._settings.max_batch_size),
                    return_dense=True,
                    return_sparse=False,
                    return_colbert_vecs=False,
                )
        except Exception as exception:
            logger.exception("Embedding inference failed")
            raise EmbeddingInferenceError("Embedding inference failed") from exception

        dense_vectors = np.asarray(result["dense_vecs"], dtype=np.float32)
        normalized_vectors = self._normalize(dense_vectors)
        self._validate_dimensions(normalized_vectors)
        return normalized_vectors.astype(float).tolist()

    def _get_model(self) -> BGEM3FlagModel:
        if self._model is not None:
            return self._model

        with self._lock:
            if self._model is None:
                logger.info(
                    "Loading embedding model '%s' on device '%s'",
                    self._settings.model_name,
                    self._device,
                )
                use_fp16 = self._settings.use_fp16 and self._device == "cuda"
                self._model = BGEM3FlagModel(
                    self._settings.model_name,
                    normalize_embeddings=True,
                    use_fp16=use_fp16,
                    devices=self._device,
                )
                logger.info("Embedding model loaded")

        return self._model

    def _resolve_device(self, configured_device: str) -> str:
        if configured_device != "auto":
            return configured_device

        if torch.cuda.is_available():
            return "cuda"
        if torch.backends.mps.is_available():
            return "mps"
        return "cpu"

    def _normalize(self, vectors: np.ndarray) -> np.ndarray:
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        if np.any(norms == 0):
            raise EmbeddingInferenceError("Embedding model returned a zero vector")
        return vectors / norms

    def _validate_dimensions(self, vectors: np.ndarray) -> None:
        if vectors.ndim != 2:
            raise EmbeddingInferenceError("Embedding model returned an invalid vector shape")

        actual_dimensions = vectors.shape[1]
        if actual_dimensions != self._settings.expected_dimensions:
            raise EmbeddingInferenceError(
                f"Embedding dimension mismatch: expected {self._settings.expected_dimensions}, got {actual_dimensions}"
            )


class UnsupportedModelError(ValueError):
    pass


class EmbeddingInferenceError(RuntimeError):
    pass
