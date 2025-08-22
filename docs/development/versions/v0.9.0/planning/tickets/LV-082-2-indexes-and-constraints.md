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

- [x] Unique constraint exists on Event.eventId
- [x] Index exists to support per-chapter ordering via sceneIndex
- [x] Documentation notes how ordering query uses the index

## Implementation

### Chosen Approach

Scene and Event share the same `id` (polymorphism). Event is a dynamic label on Scene nodes using `@DynamicLabels` in Spring Data Neo4j.

**Schema Changes:**

- **Event identity constraint**: `CREATE CONSTRAINT event_id_unique IF NOT EXISTS FOR (e:Event) REQUIRE e.id IS UNIQUE`
- **Per-chapter ordering index**: `CREATE INDEX event_per_chapter_scene_idx IF NOT EXISTS FOR (e:Event) ON (e.chapterId, e.sceneIndex)`

**Model Changes:**

- Added `chapterId` property to `SceneNode` for efficient per-chapter ordering when Scene is labeled as Event
- Event identity uses the same `id` as Scene (no separate `eventId` property needed)

### Usage Examples

**Per-chapter Event ordering (uses composite index):**

```cypher
MATCH (e:Event {chapterId: $chapterId}) 
RETURN e 
ORDER BY e.sceneIndex
```

**Cross-chapter Event queries (benefits from Event.id unique constraint):**

```cypher
MATCH (e:Event) 
WHERE e.id IN $eventIds 
RETURN e
```

**Index verification:**

```cypher
SHOW INDEXES YIELD name, labelsOrTypes, properties 
WHERE name IN ['event_id_unique', 'event_per_chapter_scene_idx']
```

Quality gates

- [ ] Build green; schema checks or a smoke test verifies index presence
- [ ] Architecture tests unaffected

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#082—event-shell-and-storage-readiness
- Research: ../../research/event-model.md
