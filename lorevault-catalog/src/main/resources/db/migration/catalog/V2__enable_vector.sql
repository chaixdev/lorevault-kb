-- V2__enable_vector.sql
-- Enable the pgvector extension for future embedding-based semantic matching (M3).
-- Idempotent: IF NOT EXISTS means it's a no-op if already enabled.
-- Requires pgvector/pgvector:pg16 Docker image (superset of postgres:16).

CREATE EXTENSION IF NOT EXISTS vector;
