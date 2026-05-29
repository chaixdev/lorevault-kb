# Stage Execution Context & Domain Provenance

**Date:** May 29, 2026
**Status:** Design — implementation pending
**Category:** Architecture / Observability / Ingestion Pipeline

## Problem

The pipeline execution identity — which `Stage` node triggered the current work — is needed in three places but handled three different ways:

| Concern | Current mechanism | Status |
|---------|-------------------|--------|
| **Logging** | MDC (`stage`, `jobId`) set in `StageDispatcher` | ✅ Works |
| **Provenance** | `TemporalEdgeProvenance` → `GraphTriadAnalysisArtifactLookup.findLatestTriadStageIdByCurrentSceneId()` → returns `SCENE_SEGMENTATION` stage per-chapter (ignores `currentSceneId`) | ❌ Stubbed, wrong |
| **Rerun cleanup** | `IngestionPipelineCoordinator.deleteDataByStageId()` | ❌ No-op stub (comment: "handler-specific cleanup not yet wired") |

The identity already exists — the `Stage` node has a Neo4j UUID — but it stops at the `StageDispatcher`. It doesn't flow into domain services or domain data.

Additionally, `StageOutput` is a redundant disconnected node. Its only consumer is the idempotency check (`existsByChapterIdAndStep`), which could use `Stage.status == COMPLETED` instead. Nothing reads the `StageOutput` audit trail.

## Direction

Extend the execution identity into a first-class cross-cutting concern — `StageExecutionContext` — that flows from the dispatcher through handlers, services, and repositories into every domain node and edge created during that stage's execution.

This aligns with the project's existing three-tier observability model (logging, graph audit, metrics) and parked plans (Micrometer stage timing, distributed tracing). A single execution identity feeds all three tiers from one source of truth.

## Design

### Core Abstraction: `StageExecutionContext`

Replaces `DispatchContext`. Same fields + `stageId`:

```java
/**
 * Execution identity for a single pipeline stage invocation.
 *
 * <p>Flows from {@code StageDispatcher} through the handler → service →
 * repository chain into domain data. Every node or relationship created
 * during this execution carries {@link #stageId()} as a provenance property.
 *
 * <p>Feeds three observability tiers:
 * <ul>
 *   <li><b>Logging:</b> MDC ({@code stage}, {@code jobId}, {@code chapterId})</li>
 *   <li><b>Graph audit:</b> {@code stageId} tagged on domain nodes/edges</li>
 *   <li><b>Metrics:</b> Micrometer Timer tags (planned)</li>
 * </ul>
 */
public record StageExecutionContext(
        UUID stageId,      // Stage node ID — the durable execution identity
        UUID jobId,        // Ingestion job ID
        UUID chapterId,    // Chapter scope (nullable for book-level stages)
        UUID bookId,       // Book scope (nullable for chapter-level stages)
        StageKey stage     // Which DAG vertex is executing
) {}
```

### Transport: Explicit Threading

No `ThreadLocal`. No hidden state. The context flows as an explicit parameter through every layer that creates data:

```
StageDispatcher
  │
  ├─ 1. Resolves Stage node ID (available from setRunningConditionally)
  │     Creates StageExecutionContext
  │
  ├─ 2. LOGGING: populates MDC (stage, jobId, chapterId, stageId)
  │
  ├─ 3. METRICS: Micrometer Timer.Sample tags (parked — see micrometer-stage-timing.md)
  │
  ├─ 4. EXECUTION: calls handler.execute(StageExecutionContext ctx)
  │     │
  │     └─ handler → domainService.createEntities(entities, ctx)
  │         └─ service → repository.create(node, ctx.stageId())
  │             └─ Cypher: CREATE (n:Scene {stageId: $stageId, ...})
  │
  └─ 5. Clears MDC
```

### Domain Tagging

Every domain artifact created during a stage execution carries `stageId` as a Neo4j property:

- **Nodes:** `Scene`, `Chunk`, `ChapterIndividual`, `BookIndividual`, `ChapterCollective`, `BookCollective`, `ChapterLocation`, `BookLocation`, `ChapterObject`, `BookObject`, `ChapterEvent`, `BookEvent`, `BookConsolidationClaim`
- **Relationships:** temporal edges (`BEFORE`, `AFTER`, `OVERLAPS`, etc.)

Repositories extract `stageId` from the context and include it in their Cypher `CREATE` statements.

### Rerun Cleanup

With `stageId` on every domain artifact, `deleteDataByStageId` becomes two generic Cypher statements:

```cypher
// Delete tagged nodes (relationships auto-deleted by DETACH DELETE)
MATCH (n {stageId: $stageId}) DETACH DELETE n;

// Delete tagged relationships between non-tagged nodes
MATCH ()-[r {stageId: $stageId}]->() DELETE r;
```

No per-handler, per-repository cleanup queries needed. Works for any stage and any domain node type.

### Idempotency Without StageOutput

`StageOutput` is eliminated. The idempotency check in `StageDispatcher` uses `Stage.status`:

```java
// Before: stageOutputRepo.existsByChapterIdAndStep(chapterId, stage)
// After:  stageRepo.findByJobIdAndStep(jobId, stage).status() == COMPLETED
```

The `Stage` node already tracks `COMPLETED` status. `StageOutput` was a redundant copy with no readers.

### Provenance Without Lookups

`TemporalEdgeProvenance` (and the `TriadTemporalEdgeRequestFactory` that creates it) gets `stageId` directly from `StageExecutionContext`. No `GraphTriadAnalysisArtifactLookup` query, no `resolveRequiredProvenance()` stub, no per-scene Stage granularity in the DAG.

The provenance is on the edge itself: `MATCH ()-[r:BEFORE]->() WHERE r.stageId = $id RETURN r`.

## What Gets Deleted

| Artifact | Reason |
|----------|--------|
| `DispatchContext` | Replaced by `StageExecutionContext` |
| `StageOutput` class | Redundant with `Stage.status` |
| `StageOutputGraphRepository` (entire class) | All queries replaced by `Stage.status` checks |
| `existsByChapterIdAndStep()` / `existsByBookIdAndStep()` | Idempotency now checks `Stage.status` |
| `deleteByJobAndSteps()` | Replaced by generic `deleteDataByStageId` |
| `GraphTriadAnalysisArtifactLookup.findLatestTriadStageIdByCurrentSceneId()` | Provenance lives on the edge itself |
| `TriadTemporalEdgeRequestFactory.resolveRequiredProvenance()` | Replaced by `stageId` from `StageExecutionContext` |
| `TemporalEdgeProvenance` record | Simplify/remove — `stageId` is the provenance |

## What Gets Created

| Artifact | Purpose |
|----------|---------|
| `StageExecutionContext` record | Execution identity — replaces `DispatchContext` |
| `StageGraphRepository.setRunningConditionally(UUID, StageKey) → UUID` | Now returns the Stage node ID |
| Updated CREATE Cypher across ~15 repositories | Each adds `{stageId: $stageId}` |
| `deleteDataByStageId` implementation | Generic cleanup: `MATCH (n {stageId}) DETACH DELETE n` + edge cleanup |

## Scope

| Change | Files touched | Complexity |
|--------|---------------|------------|
| `DispatchContext` → `StageExecutionContext` (+ `stageId`) rename | ~50 (handlers, operations, tests, StageDispatcher, coordinator) | Mechanical — find-replace + add field |
| `StageGraphRepository.setRunningConditionally` returns ID | 1 | One-line Cypher change |
| `StageDispatcher`: resolve stageId, include in ctx, remove StageOutput calls | 1 | ~20 lines changed |
| `IngestionPipelineCoordinator`: remove StageOutput writes, implement `deleteDataByStageId` | 1 | ~15 lines changed |
| Delete: `StageOutput`, `StageOutputGraphRepository`, stubbed provenance methods | ~4 | Straightforward |
| Repository CREATE queries: add `{stageId: $stageId}` | ~15 | One-line per query |
| Domain services: accept `StageExecutionContext` parameter | ~15 | Signature change, pass through |
| Tests: update constructor calls + StageOutput mocks → Stage.status mocks | ~15 test files | Mechanical |
| `Neo4jSchemaInitializer`: no index needed | 0 | `stageId` is not indexed — cleanup uses `DETACH DELETE` |
| **Total** | **~100 files** | **Mostly mechanical** |

## Relationship to Existing Plans

- **Smoke test issue #1 (triad provenance):** closed by this change. `stageId` on temporal edges replaces the stubbed `resolveRequiredProvenance()`.
- **Smoke test issue #4 (stage key mislabeling):** `StepEventMapper` is the remaining unvalidated emission path. Not addressed here — separate validation test.
- **Micrometer stage timing (2026-05-23T1700):** `StageExecutionContext` carries the tags (`stage`, `jobId`). The parked Micrometer plan slots into `StageDispatcher` after this change.
- **Logging philosophy (rules/logging-philosophy.md):** MDC already gets stage/jobId from dispatcher. `stageId` added. No other logging changes.
- **Ingestion observability pattern:** The three-tier model (logs, graph audit, metrics) is preserved. This change strengthens tier 2 (graph audit) by making `stageId` the universal provenance key.

## Sequencing

1. Rename `DispatchContext` → `StageExecutionContext` + add `stageId` field
2. Update `StageGraphRepository.setRunningConditionally` to return stage ID
3. Update `StageDispatcher` to resolve stageId, include in ctx, switch idempotency to Stage.status
4. Update all handlers to use `StageExecutionContext` (mechanical rename)
5. Delete `StageOutput` + `StageOutputGraphRepository`
6. Update `IngestionPipelineCoordinator` to remove StageOutput writes, implement `deleteDataByStageId`
7. Update ~15 repositories' CREATE Cypher to include `stageId`
8. Update ~15 domain services to accept `StageExecutionContext`
9. Unstub `resolveRequiredProvenance()` / update `TriadTemporalEdgeRequestFactory`
10. Update tests
11. Compile + run full test suite

## Open Questions

- **Index on `stageId`?** Not needed for `deleteDataByStageId` (uses label-agnostic property scan). But if queries ever need to find all artifacts from a stage (e.g., operator dashboard), a composite index on `(stageId, :Label)` would help. Deferred — add when the query exists.
- **`TemporalEdgeProvenance` simplification:** Currently a 4-field record (`jobId`, `chapterId`, `statusRecordId`, `llmCallRecordId`). With `stageId` on edges, `statusRecordId` is redundant. `llmCallRecordId` could also move to the edge. Simplify to `(jobId, chapterId, stageId, llmCallRecordId)` or remove entirely and rely on the edge's `stageId` property.
