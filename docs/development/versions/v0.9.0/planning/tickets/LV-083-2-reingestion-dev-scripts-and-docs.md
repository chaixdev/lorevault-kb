# LV-083-2 — Reingestion dev scripts and documentation [chore]

Context

- Development is reingestion-only; we need a simple way to wipe and reprocess to materialize Events for existing content.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md

Problem

- Without lightweight tooling, iterating on ingestion changes is slow and error-prone.

Proposal

- Provide a dev script and README instructions to wipe a book's content and reingest chapters.

Scope

- Bash/Python script to: delete book/chapter subtree from Neo4j and re-run ingestion for provided inputs.
- Update docs in docs/development/current/processes/ or local README with steps and cautions.
- Ensure script guards against accidental prod usage (env check).

Out of scope

- Production-grade backfill/migration tooling

Technical notes

- Reuse existing `scripts/reset-dev-db.sh` patterns where possible.

Acceptance criteria

- [ ] Script exists and can wipe + reingest a sample dataset locally
- [ ] Documentation lists commands and expected outputs
- [ ] Safety guard prevents running without `DEV`/`TEST` environment

Quality gates

- [ ] Manual smoke successful; no code changes required in API

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#083—dual-write-ingestion-scenes-→-events
