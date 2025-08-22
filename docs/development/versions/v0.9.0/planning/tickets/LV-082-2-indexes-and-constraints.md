# LV-082-2 — Indexes and constraints for Events [refactor]

Context

- Storage readiness requires performant lookup and ordering for Events derived from Scenes.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/event-model.md

Problem

- We need minimal but sufficient indexes/constraints to ensure uniqueness and support ordering queries.

Proposal

- Add a unique constraint on Event.eventId.
- Add an index for (chapterId, sceneIndex) or equivalent path to enable per-chapter ordering efficiently.
- Consider a composite index (bookId, chapterNumber, sceneIndex) if chapter linkage is indirect.

Scope

- Define/record the Cypher migrations or Spring Data annotations to create the above indexes/constraints.
- Verify existing Chapter→HAS_SCENE→Event linkage supports efficient traversal.
- Document the chosen approach and rationale.

Out of scope

- Changes to public APIs
- Bulk migrations beyond dev reingestion

Technical notes

- Favor schema constraints that are supported in the current Neo4j version in use.
- Avoid over-indexing; keep to the minimum that supports read patterns.

Acceptance criteria

- [ ] Unique constraint exists on Event.eventId
- [ ] Index exists to support per-chapter ordering via sceneIndex
- [ ] Documentation notes how ordering query uses the index

Quality gates

- [ ] Build green; schema checks or a smoke test verifies index presence
- [ ] Architecture tests unaffected

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#082—event-shell-and-storage-readiness
- Research: ../../research/event-model.md
