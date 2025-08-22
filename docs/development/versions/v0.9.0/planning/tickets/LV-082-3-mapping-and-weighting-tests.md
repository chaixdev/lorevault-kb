# LV-082-3 — Mapping and weighting tests [user story]

Context

- Establish correctness of enumeration mapping and weight calculations before wiring ingestion.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/event-model.md, ../../research/tests-and-qa.md

Problem

- Without tests, certainty→weight mapping and enum values could drift, breaking temporal ordering semantics later.

Proposal

- Create unit tests to verify enum values and certainty→weight mapping.
- Add minimal repository mapping tests for TemporalEdge properties.

Scope

- Unit: CertaintyLevel→weight constants; presence and spelling of enum values; defensive default behavior.
- Mapping: TemporalEdge serialization of rationale and evidence fields.

Out of scope

- Ingestion pipeline tests
- API tests

Technical notes

- Include at least one edge case: unknown certainty defaults to Heuristic weight.

Acceptance criteria

- [ ] Unit tests assert mapping constants (0.95, 0.8, 0.6, 0.5)
- [ ] Unit tests assert enum set { BEFORE, MEETS, OVERLAPS, DURING, STARTS, FINISHES, EQUALS } and certainty set
- [ ] Mapping tests confirm rationale and evidence fields stored/read correctly

Quality gates

- [ ] Tests pass locally and in CI; coverage thresholds met
- [ ] No new ArchUnit violations

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#082—event-shell-and-storage-readiness
- Research: ../../research/event-model.md
