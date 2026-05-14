-- ============================================================================
-- rag-ingestor — schema inicial Postgres + pgvector.
-- ============================================================================
-- Tabla vector_store: layout esperado por Spring AI PgVectorStore (default).
-- text-embedding-3-large produce 3072 dimensiones.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id          uuid          PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     text          NOT NULL,
    metadata    jsonb,
    embedding   vector(3072)
);

-- pgvector HNSW/IVFFlat indexes have a 2000-dimension limit for vector columns.
-- text-embedding-3-large is 3072 dimensions, so the initial deployment uses
-- exact cosine search. For the current documentation corpus this is simpler and
-- keeps the higher-quality embedding model.

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
