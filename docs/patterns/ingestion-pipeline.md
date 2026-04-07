# Ingestion Pipeline Pattern

**Status:** Established

LoreVault ingests narrative content through a staged pipeline:

1. ingest content
2. detect scenes
3. build chunk hierarchy
4. generate embeddings
5. persist searchable content

The durable pattern is staged processing with explicit boundaries, while avoiding trivial wrapper layers.

Primary references:
- `../development/current/processes/scene-detection-specification.md`
- `../development/current/processes/triad-orchestration.md`
- `../archive/refactor/event-driven-ingestion-refactor-v0100.md`
