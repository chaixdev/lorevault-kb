# ADR 015: Stage Node Provenance Over StageOutput Nodes

**Status:** Accepted
**Date:** May 2026

## Context

LoreVault's ingestion pipeline creates domain nodes (mentions, chapter entities, book entities, scenes, chunks, relation claims) during each stage execution. When a stage is re-run — whether for retry, correction, or because the data was corrupted — the pipeline needs to clean up the previous execution's output before writing new data.

The previous approach used `StageOutput` nodes: each handler wrote a `StageOutput` record linking the stage execution to its output. Cleanup traversed these output nodes to find what to delete.

This had problems:

- **StageOutput was a separate graph concern** that every handler had to manage alongside its real work
- **Traversal-based cleanup is fragile.** If a handler forgot to write a `StageOutput` link, its domain nodes became orphans that no cleanup pass could reach
- **StageOutput conflated observability with cleanup.** The same node served as both "what did this stage produce?" and "what should we delete on re-run?" — two different questions with different lifecycles

## Decision

Tag every domain node with a `stageId` property pointing back to the `Stage` node that created it. Cleanup uses a single Cypher query:

```cypher
MATCH (n {stageId: $stageId}) DETACH DELETE n
```

This eliminates `StageOutput` entirely. The `Stage` node (already persisted by `StageDispatcher`) becomes the provenance anchor. Domain nodes carry their own provenance via `stageId`.

For edges that are not domain nodes (temporal edges, structural edges), `stageId` is set as a relationship property. Structural edges (like `HAS_SCENE`, `REFERS_TO`) don't need `stageId` because `DETACH DELETE` on the domain node cascades to all connected edges.

`stageId` is nullable on existing data. Nodes created before this change have `stageId = null`. The cleanup query only targets nodes where `stageId` matches, so pre-existing data is never accidentally deleted.

## Alternatives Considered

### Keep StageOutput nodes

The previous approach. Rejected because:
- Every handler must write StageOutput links — easy to forget, hard to detect
- Traversal-based cleanup is slower and more complex than a property-based query
- StageOutput mixes observability and cleanup concerns

### ThreadLocal-based stageId

Store `stageId` in a ThreadLocal and have repositories read it implicitly. Rejected per ADR 014 — explicit parameter threading is the chosen pattern.

### Composite cleanup query with type enumeration

Instead of `stageId` on every node, write a cleanup query that explicitly lists all node labels (`IndividualMention`, `ChapterIndividual`, etc.) and deletes by `(jobId, stageKey)` properties. Rejected because:
- The query must be updated every time a new entity type is added
- No single property ties a node to its creating stage execution
- Fragile — easy to miss a label

### Event-sourced cleanup

Record every node creation as an event and replay events to determine what to delete. Rejected because:
- Massive overengineering for a cleanup operation
- Event sourcing introduces eventual consistency concerns that don't exist with direct property tagging

## Implications

- Every `@Node` entity class must include `@Property("stageId") UUID stageId` as a record component (for records) or a field with setter (for `@Data` classes like `Scene` and `Chunk`).
- Every service method that creates domain nodes must accept `StageExecutionContext ctx` and pass `ctx.stageId()` to entity constructors (per ADR 014).
- `deleteDataByStageId` is a single Cypher query, not a traversal. It is fast and complete.
- Pre-existing nodes with `stageId = null` are safe — the cleanup query only matches nodes where `stageId` equals the provided value.
- A future index on `stageId` would speed cleanup for large graphs, but is not required for correctness.
- `StageOutput` is eliminated. The `Stage` node (persisted by `StageDispatcher`) is the sole provenance anchor.