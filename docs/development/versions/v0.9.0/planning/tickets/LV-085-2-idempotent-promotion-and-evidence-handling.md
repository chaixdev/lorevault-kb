# LV-085-2 — Idempotent promotion and evidence handling [refactor]

Context

- Upgrading edges and storing evidence must be idempotent and consistent.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md

Problem

- Without careful design, repeated runs might duplicate edges or overwrite evidence inconsistently.

Proposal

- Define idempotent update semantics for TEMPORAL edges; merge evidence fields; keep original MEETS when uncertain.

Scope

- Implement merge/update strategy; unit tests for idempotency and evidence preservation.

Out of scope

- Evidence versioning/history

Technical notes

- Prefer immutable evidence blobs stored separately if structure grows (deferred).

Acceptance criteria

- [ ] Re-running promotion preserves or upgrades edge without duplicates
- [ ] Evidence fields are not lost and are merged predictably

Quality gates

- [ ] Unit tests for idempotent updates

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
