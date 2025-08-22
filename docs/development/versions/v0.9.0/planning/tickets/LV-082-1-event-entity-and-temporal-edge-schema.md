# LV-082-1 — Define Event entity and TemporalEdge schema [user story]

Context

- v0.9.0 aims to model Scenes as first-class Events and introduce temporal edges for a skeleton timeline.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/event-model.md

Problem

- We need concrete domain/infrastructure models to represent :Event:Scene nodes and TEMPORAL edges with evidence and certainty.

Proposal

- Introduce EventNode (dual-labeled :Event:Scene) and TemporalEdge relationship properties in the data model.
- Establish enums, mapping constants, and basic repository contracts in preparation for ingestion and timeline services.

Scope

- Define EventNode with fields: eventId (PK), eventType, title, description, flags[], sceneIndex, startOffset?, endOffset?, firstChunkId?, lastChunkId?
- Define TemporalEdge with: temporalRelation, certainty, source, weight, rationale, evidenceStartOffset?, evidenceEndOffset?, evidenceChunkId?
- Define enums: TemporalRelation, CertaintyLevel, EventFlag
- Implement certainty→weight mapping constants
- Create repositories for Event and temporal links (as needed by Spring Data Neo4j patterns)

Out of scope

- Ingestion or edge creation logic
- Public API exposure

Technical notes

- Directional contract: edge owner is earlier; target is later.
- Labels: Node is dual-labeled :Event:Scene to preserve existing Chapter→HAS_SCENE structure.
- Weight mapping initial table: Explicit=0.95, StronglyImplied=0.8, WeaklyImplied=0.6, Heuristic=0.5.

Acceptance criteria

- [ ] EventNode and TemporalEdge classes exist in the domain/infrastructure
- [ ] Enums defined with specified values
- [ ] Mapping constants implemented and unit-tested
- [ ] Repositories compile; no public behavior change

Quality gates

- [ ] Build green; unit tests for enum/mapping pass
- [ ] Coverage thresholds unchanged and met for new code
- [ ] No new ArchUnit violations

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#082—event-shell-and-storage-readiness
- Research: ../../research/event-model.md
