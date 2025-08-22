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

## Dev Notes (Implementation Refinements)

Modality and Labeling

- Domain modality: `Scene` will implement a new `Event` interface to express event semantics in the business layer without persistence concerns.
- Infrastructure modality: we will keep `SceneNode` as the single SDN entity mapped to label `Scene` and add dynamic labels (`@Labels`) to support dual-labeling `:Scene:Event` when a scene is also an event.
- Rationale: avoids Spring Data Neo4j mapper conflicts (two @Node classes for one label) while enabling event-shaped queries via `:Scene:Event` filters.

Repositories

- Keep `ChapterNode` → `List<SceneNode>` for `HAS_SCENE`. No need to switch to `EventNode` type; queries can select `(s:Scene:Event)` where needed.
- Keep `SceneGraphRepository`; add a minimal repository for temporal traversals (or methods on `SceneGraphRepository`) and a lightweight repository to fetch `:Scene:Event` nodes.
- No public API behavior changes.

Temporal Edge Properties

- Direction: edge owner is earlier; target is later → `(earlier:Scene:Event)-[:TEMPORAL {…}]->(later:Scene:Event)`.
- Keep both `certainty` (enum) and `weight` (double). `weight` is a denormalized numeric derived via `CertaintyWeights` for efficient sorting/algorithms in Cypher.
- certainty→weight mapping (initial): Explicit=0.95, StronglyImplied=0.80, WeaklyImplied=0.60, Heuristic=0.50.

Enums and Flags

- Define enums: `TemporalRelation`, `CertaintyLevel`.
- Defer flags/tags entirely per YAGNI (remove `EventFlag` from this ticket scope; add later when needed).

Event Fields

- `eventType` remains a String in domain (`Event` interface); no persistence fields added until required.
- Title/description are domain-level getters; mapping may default to existing `Scene` fields (e.g., title from `contextSummary`) or remain null until populated by future features.

Testing & Architecture

- Unit-test only the `CertaintyWeights` mapping table.
- No integration tests required in this ticket.
- ArchUnit boundaries preserved (domain vs infrastructure separation maintained).
