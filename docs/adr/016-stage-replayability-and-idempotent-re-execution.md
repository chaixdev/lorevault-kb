# ADR 016: Stage Replayability and Idempotent Re-Execution

**Status:** Accepted
**Date:** May 2026

## Context

LoreVault's ingestion pipeline processes books in stages. Stages can fail for transient reasons (LLM rate limits, network timeouts, Neo4j connection pool exhaustion) or need re-execution when data is corrected or a model is upgraded.

When a stage re-runs, it must:

1. Not produce duplicate output if the stage already completed successfully
2. Clean up its previous output before writing new data
3. Not interfere with other stages running in parallel for the same book

The previous approach had no formal idempotency guard — handlers checked for existing output nodes, but this was inconsistent and unreliable.

## Decision

Stages are replayable through two mechanisms:

### 1. Atomic status guard

`StageDispatcher` performs an atomic `TRIGGERED → RUNNING` transition on the `Stage` node before executing any handler. If the stage is already `RUNNING` or `COMPLETED`, the transition fails and the stage is skipped. This prevents duplicate execution of the same stage for the same job.

### 2. Idempotency via `isAlreadyCompleted`

Before executing the handler, the dispatcher checks whether the `Stage` node's status is already `COMPLETED`. If so, the stage is marked `SKIPPED` and a success result is returned. This makes re-triggering a completed stage safe — it simply skips.

### 3. Cleanup via `stageId` provenance

When a stage needs to re-run with fresh data (e.g., after a model upgrade), the caller first invokes `deleteDataByStageId(stageId)` to remove all domain nodes and edges created by that stage execution. Then the stage is re-triggered with a new `Stage` node, producing a new `stageId`. The new execution's output carries the new `stageId`, making cleanup of the old output complete and precise.

This is a deliberate two-step process: cleanup is never automatic on re-run. The caller must explicitly request cleanup before re-triggering.

## Alternatives Considered

### Automatic cleanup on re-run

Delete previous output automatically when a stage is re-triggered. Rejected because:

- Re-running a stage is not always intended to replace previous output. A retry after a transient failure should not delete the successful output from a previous attempt.
- Automatic cleanup makes it impossible to compare old and new output side by side.
- The caller should decide when cleanup is appropriate, not the pipeline.

### Upsert-based idempotency

Use `MERGE` instead of `CREATE` for domain nodes, so re-running a stage updates existing nodes rather than creating duplicates. Rejected because:

- `MERGE` on domain nodes requires a stable natural key, which most domain nodes don't have (mentions are identified by extraction, not by a unique business key).
- Upsert semantics hide data changes — you can't tell what changed between runs.
- Cleanup + re-create is simpler and more auditable.

### Optimistic locking on Stage node

Use a version field on the `Stage` node and fail if the version doesn't match. Rejected because:

- The `TRIGGERED → RUNNING` atomic transition already provides the necessary guard.
- Version fields add complexity without additional benefit for a single-writer model.

### No idempotency — just let it fail

Skip the guard entirely and rely on manual deduplication. Rejected because:

- Duplicate stage executions produce duplicate domain nodes, corrupting the graph.
- Manual cleanup is error-prone and doesn't scale.

## Implications

- Re-triggering a completed stage is safe — it skips with a `SKIPPED` result.
- To force re-execution with fresh data, the caller must: (1) delete the old `Stage` node's output via `deleteDataByStageId`, then (2) create a new `Stage` node in `TRIGGERED` status.
- The `Stage` node's lifecycle is: `TRIGGERED → RUNNING → COMPLETED` (or `FAILED`). The `SKIPPED` status is a terminal state for already-completed stages.
- `deleteDataByStageId` is a destructive operation. It must be called deliberately, never automatically.
- ADR 015 (stage node provenance) is a prerequisite — `deleteDataByStageId` relies on `stageId` being tagged on every domain node.