-- Evolving development schema for the relation catalog.
--
-- LoreVault is still in wipe-state development. Until a durable schema is
-- explicitly shipped and documented, keep the catalog schema collapsed into
-- this single migration instead of adding incremental V2/V3/etc. migrations.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS catalog_definition (
    id              UUID PRIMARY KEY,
    definition_key  TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    description     TEXT,
    created         TIMESTAMPTZ NOT NULL,
    updated         TIMESTAMPTZ NOT NULL,
    last_seen       TIMESTAMPTZ NOT NULL,
    embedding       vector(1536)
);

CREATE TABLE IF NOT EXISTS catalog_definition_variant (
    definition_id   UUID NOT NULL REFERENCES catalog_definition(id),
    raw_name        TEXT NOT NULL,
    PRIMARY KEY (definition_id, raw_name)
);

CREATE TABLE IF NOT EXISTS catalog_definition_signature (
    definition_id   UUID NOT NULL REFERENCES catalog_definition(id),
    subject_kind    TEXT NOT NULL,
    object_kind     TEXT NOT NULL,
    PRIMARY KEY (definition_id, subject_kind, object_kind)
);

CREATE INDEX IF NOT EXISTS idx_catalog_definition_signature_kinds
    ON catalog_definition_signature(subject_kind, object_kind);

CREATE INDEX IF NOT EXISTS idx_catalog_definition_embedding_hnsw
    ON catalog_definition USING hnsw (embedding vector_cosine_ops);
