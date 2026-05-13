-- ============================================================================
-- rag-ingestor — schema inicial Postgres + pgvector.
-- ============================================================================
-- Tabla vector_store: layout esperado por Spring AI PgVectorStore (default).
-- text-embedding-3-small produce 1536 dimensiones; ajusta si cambias modelo.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id          uuid          PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     text          NOT NULL,
    metadata    jsonb,
    embedding   vector(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS vector_store_metadata_path_idx
    ON vector_store ((metadata ->> 'path'));

CREATE INDEX IF NOT EXISTS vector_store_metadata_sha_idx
    ON vector_store ((metadata ->> 'sha'));

-- ============================================================================
-- Tabla rag_ingest_state — mantiene puntero al último commit ingerido.
-- ============================================================================
CREATE TABLE IF NOT EXISTS rag_ingest_state (
    id            integer       PRIMARY KEY DEFAULT 1,
    last_sha      text          NOT NULL,
    last_run_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT single_row CHECK (id = 1)
);
