# LV-085-3 — Internal neighbors API for events [user story]

Context

- Internal consumers and tests need a way to inspect temporal neighbors of an Event.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/timeline-apis.md

Problem

- Lack of a stable read interface slows iteration on edge upgrades and ordering.

Proposal

- Add GET /api/timeline/events/{eventId}/neighbors returning previous and next neighbors with relation, certainty, weight, rationale, and offsets.

Scope

- Controller (internal), service, and repository method(s); controller slice tests.

Out of scope

- Publicized API documentation (research-only docs are sufficient for now)

Technical notes

- Response shape: { prev: TemporalNeighbor[], next: TemporalNeighbor[] }.

Acceptance criteria

- [ ] Endpoint returns neighbors for a sample event with required fields
- [ ] Controller slice tests validate response contract

Quality gates

- [ ] Web tests pass; ArchUnit rules preserved

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/timeline-apis.md
