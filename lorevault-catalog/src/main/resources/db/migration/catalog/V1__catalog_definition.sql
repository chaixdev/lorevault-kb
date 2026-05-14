CREATE TABLE IF NOT EXISTS catalog_definition (
    id              UUID PRIMARY KEY,
    definition_key  TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    description     TEXT,
    created         TIMESTAMPTZ NOT NULL,
    updated         TIMESTAMPTZ NOT NULL,
    last_seen       TIMESTAMPTZ NOT NULL
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

-- Index deferred to M3: signature matching is not used until embedding similarity is available.
-- CREATE INDEX IF NOT EXISTS idx_signature_kinds
--     ON catalog_definition_signature(subject_kind, object_kind);
