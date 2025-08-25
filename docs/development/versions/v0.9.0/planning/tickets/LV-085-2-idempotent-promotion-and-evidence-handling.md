# LV-085-2 — Idempotent promotion and evidence handling [refactor]

Context

- Upgrading edges and storing evidence must be idempotent and consistent.
- With triad-based Pass 2 (LV-085-0), each adjacency can have two votes (Curr→Next and Next→Prev).
- See planning: ../v0.9.0-scene-to-event-entity-plan.md; Research: ../../research/Narrative event DAG.md

Problem

- Without careful design, repeated runs might duplicate edges or overwrite evidence/counter-votes inconsistently.

Proposal

- Define idempotent upsert semantics for neighbor TEMPORAL edges keyed by (sceneA, sceneB, source="triad-pass2").
- Merge evidence fields and retain counter-vote deterministically (e.g., edge.votes) while keeping a single primary relation for parity.
- Preserve existing MEETS link if promotion thresholds aren’t met; never remove the only link.

Scope

- Implement merge/update strategy for relation, certainty, evidence, state (Confirmed/Contested/SingleSided), and counter-votes.
- Unit tests for idempotency, evidence preservation, and deterministic merges.

Out of scope

- Evidence versioning/history (deferred if structure grows).

Technical notes

- Treat equivalent inverses as agreement when merging.
- Work with LV-084-2 to downgrade would-be cycles to Contested.

Acceptance criteria

- [ ] Re-running promotion preserves or upgrades edge without duplicates.
- [ ] Evidence and counter-votes are preserved and merged predictably.

Quality gates

- [ ] Unit tests for idempotent updates and merge determinism.

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/Narrative event DAG.md
