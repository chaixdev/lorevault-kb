# LV-085-3 — Internal neighbors API for events [user story]

Context

- Internal consumers and tests need a way to inspect temporal neighbors.
- Primary persisted edges are Scene↔Scene neighbors from triad-based Pass 2 (LV-085-0). Event↔Event neighbors can be derived when Event↔Scene links exist.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md; Research: ../../research/timeline-apis.md and ../../research/Narrative event DAG.md

Problem

- Lack of a stable read interface slows iteration on upgrades and ordering.

Proposal

- Provide internal endpoints:
  - GET /api/timeline/scenes/{sceneId}/neighbors -> { prev: TemporalNeighbor[], next: TemporalNeighbor[] } including relation, certainty, state (Confirmed/Contested/SingleSided), evidence, and counter-vote when applicable.
  - Optionally (if needed now): GET /api/timeline/events/{eventId}/neighbors derived via the scene(s) that depict/contain the event; document limitations.

Scope

- Controller (internal), service, repository.
- Controller slice tests to validate response contract.

Out of scope

- Public docs (keep as internal for now)

Technical notes

- Treat inverse-equivalent relations as compatible when presenting neighbors.
- For derived event neighbors, pick representative scene(s) and map through Scene neighbors; prefer Confirmed edges.

Acceptance criteria

- [ ] Endpoint returns scene neighbors with required fields and state.
- [ ] If event endpoint included: mapping strategy documented and covered by tests.

Quality gates

- [ ] Web tests pass; ArchUnit rules preserved.
- [ ] After LV-085 completes: verify determinism, cross-chapter boundary handling, cycle downgrades, and performance.

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/timeline-apis.md, ../../research/Narrative event DAG.md
