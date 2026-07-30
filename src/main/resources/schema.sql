-- Extensions: pgvector (vector type + <=> operators) and pg_trgm (trigram index for ILIKE).
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- CLIENT
--   nullable fields are coalesced so the whole value never goes null.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clients (
    id           UUID PRIMARY KEY,
    first_name   TEXT NOT NULL,
    last_name    TEXT NOT NULL,
    email        TEXT NOT NULL UNIQUE,
    description  TEXT,
    social_links TEXT[],
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- DOCUMENT
--   embedding is a 384-dim vector (all-MiniLM-L6-v2); HNSW index for ANN search.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documents (
    id         UUID PRIMARY KEY,
    client_id  UUID NOT NULL REFERENCES clients(id),
    title      TEXT NOT NULL,
    content    TEXT NOT NULL,
    summary    TEXT,                    -- LLM-generated summary, computed once at ingest (D7)
    embedding  vector(384),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW index on the embedding for fast cosine-distance (<=>) nearest-neighbour search.
CREATE INDEX IF NOT EXISTS idx_documents_embedding_hnsw
    ON documents USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_documents_client_id
    ON documents (client_id);
