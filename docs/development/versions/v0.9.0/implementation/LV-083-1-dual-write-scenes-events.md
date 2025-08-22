# LV-083-1 — Dual-write Scenes → Events (Implementation Notes)

## Summary
Implements dual-writing of Scenes as Events by storing Scene nodes with dynamic labels `:Scene:Event` during ingestion. Maintains Chapter→HAS_SCENE→Scene linkage and Scene→HAS_CHUNK→Chunk relations. Adds `chapterId` property on scenes for efficient timeline queries.

## Changes
- Mapper (`Neo4jMapper.toNode(Scene)`): sets dynamic labels to include `"Event"` to realize `:Scene:Event` dual-labeling.
- Adapter (`Neo4jContentPersistenceAdapter.addSceneToChapter`): ensures `chapterId` is set on scenes and guarantees HAS_SCENE relationship list initialization.
- Tests: Added `SceneEventDualWriteIntegrationTest` to validate dual labels and relationships.

## Data Model Impact
- SceneNode now carries dynamic labels via `@DynamicLabels`; this change uses that capability explicitly.
- Scene nodes now include `chapterId: UUID` property.
- Query patterns should prefer `(:Scene:Event)` for timeline/event traversals.

## Ingestion Behavior
- After scene detection, each scene persisted will be labeled `:Event:Scene`.
- Chapter nodes maintain `HAS_SCENE` relationships to these dual-labeled scene nodes.
- Chunks added to scenes are linked via `(:Scene:Event)-[:HAS_CHUNK]->(:Chunk)`.

## Backward Compatibility
- Domain API and public contracts unchanged.
- Legacy queries targeting `(:Scene)` continue to work as the node still has the `:Scene` label.

## Verification
- Integration test asserts:
  - Count of `(:Scene:Event)` nodes equals number of scenes ingested.
  - `Chapter -[:HAS_SCENE]-> (:Scene:Event)` exists.
  - `(:Scene:Event)-[:HAS_CHUNK]->(:Chunk)` created.
  - `chapterId` property is present on scenes.

## Follow-ups
- Optional: include `eventIds` in ingestion job output (non-breaking).
- Add a reingestion invariant test that ensures no `:Scene` nodes exist without `:Event` after reprocessing.
