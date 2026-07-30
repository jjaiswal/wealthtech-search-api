"""
Embedder sidecar — turns text into a 384-dim vector using all-MiniLM-L6-v2.

Stateless HTTP service; the only job is text -> embedding. The Java app calls it via the
EmbeddingClient interface (local now, swappable for a hosted API in production — DESIGN D4).
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL_NAME = "all-MiniLM-L6-v2"  # 384-dim, matches the pgvector vector(384) column
_model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Load the model once at startup (first load downloads weights ~90MB).
    global _model
    _model = SentenceTransformer(MODEL_NAME)
    yield


app = FastAPI(title="Embedder", lifespan=lifespan)


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    embedding: list[float]


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    # normalize_embeddings=True -> unit vectors, so cosine distance behaves well.
    vector = _model.encode(req.text, normalize_embeddings=True)
    return EmbedResponse(embedding=vector.tolist())
