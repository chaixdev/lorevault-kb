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

## Implementation Notes

**Status:** Partially implemented (May 29, 2026). Commit `bb9f196`.

### What shipped

**Phase 1 — Foundation (complete):**
- `DispatchContext` → `StageExecutionContext` with `stageId` field added
- `StageGraphRepository.setRunningConditionally()` returns `Optional<UUID>` (stage ID) instead of `boolean`
- `StageDispatcher` rewritten: resolves stageId, includes in context, switches idempotency to `Stage.status == COMPLETED`
- `StageOutput` + `StageOutputGraphRepository` deleted (293 lines removed)
- `IngestionPipelineCoordinator` updated: removed StageOutput writes, implemented `deleteDataByStageId` with generic Cypher
- All 15 handlers updated to use `StageExecutionContext` (mechanical rename)

**Phase 2 — Provenance + Tests (complete):**
- `TemporalEdgeProvenance.statusRecordId` → `stageId` (field rename)
- `TriadTemporalEdgeRequestFactory` unstubbed: accepts `stageId` parameter, constructs provenance directly
- Deleted `resolveRequiredProvenance()`, `findRequiredTriadStageId()`, `findLatestTriadStageIdByCurrentSceneId()`
- `GraphTriadAnalysisArtifactLookup` simplified: removed `StageGraphRepository` dependency
- Temporal edge Cypher updated: `statusRecordId` → `stageId` in `TemporalEdgeWriteRepository`
- `SceneTemporalRelationshipPersistenceService` updated: `statusRecordId` → `stageId`
- All 3 orchestration test suites rewritten (66 tests): `StageDispatcherTest`, `IngestionPipelineCoordinatorTest`, `StageDispatcherWiringTest`

**Result:** 44 files changed, +255 / -684 lines (net -429). 463 tests, 0 failures.

### What's deferred

**Domain node tagging (next iteration):**
- ~15 repository CREATE queries need `stageId` added (Scene, Chunk, ChapterIndividual, BookIndividual, etc.)
- ~15 domain services need to accept `StageExecutionContext` parameter and pass `ctx.stageId()` to repositories
- The infrastructure is in place (`ctx.stageId()` flows to handlers), but the queries haven't been updated
- **Impact:** `deleteDataByStageId` is implemented but won't find anything to delete until domain nodes are tagged
- **Effort:** Mechanical — one-line per query, signature changes in services

### Key decisions

**Explicit threading over ThreadLocal:**
Initial proposal was a tactical `StageIdHolder` (ThreadLocal) to avoid signature changes. User redirected: *"provenance is a deep cross-cut concern. we should design for it"* and *"avoiding signature changes will inform hacks and workarounds."* Chose explicit parameter passing through handler → service → repository chain. More invasive but architecturally clean.

**`StageExecutionContext` naming:**
Initial proposal was `PipelineExecution`. User chose `StageExecutionContext` — more precise, aligns with `Stage` node terminology.

**StageOutput elimination:**
Non-obvious insight. `StageOutput` was designed as an "append-only audit trail" but had no consumers. The idempotency check (`existsByChapterIdAndStep`) could use `Stage.status == COMPLETED` directly. Deleting it removed 293 lines and simplified the coordinator.

**Provenance simplification:**
Old code had a complex lookup chain: `TriadTemporalEdgeRequestFactory` → `GraphTriadAnalysisArtifactLookup.findLatestTriadStageIdByCurrentSceneId()` → `StageGraphRepository.findByJobIdAndStep()`. The lookup was stubbed (returned chapter-level `SCENE_SEGMENTATION` stage, ignored `currentSceneId`). New code passes `stageId` directly from `StageExecutionContext` — no lookup needed. Deleted 3 methods, removed `StageGraphRepository` dependency from `GraphTriadAnalysisArtifactLookup`.

**Generic `deleteDataByStageId`:**
Two Cypher statements work for any node type:
```cypher
MATCH (n {stageId: $stageId}) DETACH DELETE n;
MATCH ()-[r {stageId: $stageId}]->() DELETE r;
```
No per-handler cleanup queries. The first deletes tagged nodes (DETACH DELETE removes their relationships). The second deletes tagged relationships between non-tagged nodes (e.g., temporal edges between scenes).

### Implementation details

**Test rewrites:**
- `StageDispatcherTest`: Removed `StageOutputGraphRepository` mock. Changed `setRunningConditionally` stubs from `thenReturn(true/false)` to `thenReturn(Optional.of(STAGE_ID)/Optional.empty())`. Changed idempotency stubs from `stageOutputRepo.existsByChapterIdAndStep()` to `stageRepo.findByJobIdAndStep().status() == COMPLETED`. Updated `StageExecutionContext` construction to 5-arg (added `stageId`).
- `IngestionPipelineCoordinatorTest`: Removed `StageOutputGraphRepository` mock and all `verify(stageOutputRepo).save()` assertions. Removed `stageOutputRepo.deleteByJobAndSteps()` verification from rerun tests.
- `StageDispatcherWiringTest`: Removed `StageOutputGraphRepository` mock from constructor call.

**Cross-JAR visibility:**
Used `mvn test -pl lorevault-core,lorevault-web` initially, which caused stale JAR issues (lorevault-web tests couldn't see updated lorevault-core classes). User called this out. Switched to `mvn test` (full reactor build) which builds all modules in dependency order. Lesson: always use full reactor unless there's a specific reason to limit scope.

**Subagent provider failure:**
Subagent provider went down mid-execution. Fell back to doing everything sequentially instead of flagging the failure and asking how to proceed. Should have communicated the blocker earlier.

### Verification

```bash
mvn clean install -DskipTests  # Build all modules
mvn test                        # Run full test suite (463 tests, 0 failures)
```

**Commit:** `bb9f196` — `feat: StageExecutionContext + domain provenance + deleteDataByStageId`

### Next steps

1. **Domain node tagging:** Update ~15 repository CREATE queries to add `stageId` property. Update ~15 domain services to accept `StageExecutionContext` and pass `ctx.stageId()` to repositories. This enables `deleteDataByStageId` to actually clean up domain data on rerun.
2. **Smoke test:** Run end-to-end ingestion test to verify temporal edges now have `stageId` provenance and `deleteDataByStageId` works for tagged nodes.
3. **Optional:** Add `StepEventMapper` validation test (smoke test issue #4 residual risk).
