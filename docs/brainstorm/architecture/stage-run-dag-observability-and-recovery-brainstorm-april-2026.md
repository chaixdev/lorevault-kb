# StageRun DAG Observability and Recovery Brainstorm — April 2026

**Status:** Brainstorm — not yet accepted  
**Related current pattern:** `../../patterns/ingestion/ingestion-job-observability.md`  
**Related proposal:** `../entity-pipelines/provenance-generation-model-brainstorm-april-2026.md`

## Summary

LoreVault's ingestion pipeline has outgrown the linear `(:StatusRecord)-[:HAS_NEXT]->(:StatusRecord)` model as the primary durable status shape. The runtime pipeline is a DAG: scene detection fans out into chunking/embedding, entity resolution lanes, event resolution, and fan-in completion. A linear status chain can preserve chronological audit history, but it cannot naturally represent branch dependencies, pending work, retries, deferred stages, stale downstream work, or JVM crash recovery.

This brainstorm proposes replacing `StatusRecord` as the primary orchestration model with a persisted **StageRun DAG**. The timeline remains available, but it becomes a view over stage-run events rather than the storage shape that tries to explain the whole pipeline.

## Problem

The current observability pattern records status as an append-only chain attached to an `IngestionJob`. That was useful when the pipeline was mostly linear. It is now misleading because a job is not simply in one status at a time.

After scene detection, multiple branches can be true simultaneously:

- chunking may be complete while embedding is running
- chapter object resolution may be complete while book object reduction is deferred
- chapter event resolution may be complete while event embedding/candidate generation is running
- one branch may fail while late events from another branch still arrive
- a manual rerun may invalidate a downstream branch that previously succeeded

A single latest status cannot represent this. A chronological chain can say what happened, but not what remains runnable.

## Design Goals

- Represent ingestion orchestration as the DAG it actually is.
- Preserve a durable operator timeline without making the timeline the control plane.
- Support JVM crash recovery by persisting what ran, what succeeded, and what is still eligible.
- Support retry, deferred, stale, and manual rerun states without false success events.
- Link orchestration runs to semantic projection generations when a stage produces graph outputs.
- Keep logs/MDC as runtime correlation, not durable orchestration truth.

## Proposed Model

### StageRun

`StageRun` is the durable unit of orchestration state.

```text
(:IngestionJob)-[:HAS_STAGE_RUN]->(:StageRun {
  id,
  jobId,
  correlationId,
  stageKey,
  branchKey,
  scopeType,
  scopeId,
  chapterId,
  bookId,
  lane,
  attempt,
  triggerType,
  status,
  retryable,
  startedAt,
  completedAt,
  heartbeatAt,
  failureCode,
  failureMessage
})

(:StageRun)-[:DEPENDS_ON_RUN]->(:StageRun)
(:StageRun)-[:UNLOCKS_RUN]->(:StageRun)
(:StageRun)-[:CONSUMED_GENERATION]->(:ProjectionGeneration)
(:StageRun)-[:PRODUCED_GENERATION]->(:ProjectionGeneration)
```

Representative `status` values:

- `PENDING`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `DEFERRED`
- `STALE`
- `SKIPPED`
- `CANCELLED`

Representative `triggerType` values:

- `EVENT`
- `RETRY`
- `MANUAL`
- `RECOVERY`

### StageRunEvent

`StageRunEvent` preserves the append-only timeline now carried by `StatusRecord`.

```text
(:StageRun)-[:HAS_EVENT]->(:StageRunEvent {
  id,
  jobId,
  stageRunId,
  eventType,
  message,
  occurredAt,
  properties
})

(:LlmCallRecord)-[:OF_STAGE_RUN]->(:StageRun)
(:LlmCallRecord)-[:OF_STAGE_EVENT]->(:StageRunEvent)
```

The job timeline can be derived by sorting all `StageRunEvent`s for a job by `occurredAt`. This keeps chronological auditability without forcing the status model to be linear.

## Example Pipeline DAG

For one chapter ingestion:

```text
chapter-submission
  -> scene-detection
     -> chunking
        -> embedding
     -> chapter-individual-resolution
        -> book-individual-reduction
     -> chapter-location-resolution
        -> book-location-reduction
     -> chapter-object-resolution
        -> book-object-reduction
     -> chapter-collective-resolution
        -> book-collective-reduction
     -> chapter-event-resolution
        -> event-embedding-and-candidates

completion depends on all required leaf StageRuns succeeding/current
```

This structure can be rendered directly in an operator dashboard. The UI can show each branch as pending/running/succeeded/deferred/failed/stale rather than compressing the whole job into one latest status.

## Crash Recovery

Persisted `StageRun`s are a first step toward JVM crash resiliency.

Today, the system relies heavily on in-memory event delivery plus durable side effects. If the JVM dies after one branch succeeds but before another branch receives its event, the graph may contain partial work but no durable scheduler state saying what is still pending.

With `StageRun`, a recovery worker can resume from durable state:

```cypher
MATCH (s:StageRun {status: 'PENDING'})
WHERE NOT EXISTS {
  MATCH (s)-[:DEPENDS_ON_RUN]->(dep:StageRun)
  WHERE dep.status <> 'SUCCEEDED'
}
RETURN s
```

Recovery policy examples:

- `PENDING` with all dependencies `SUCCEEDED` -> eligible to enqueue
- `RUNNING` with expired heartbeat -> mark `DEFERRED` or schedule retry
- `SUCCEEDED` -> do not rerun unless invalidated/manual
- `FAILED` with `retryable=true` -> eligible for retry policy
- `STALE` -> requires recompute because upstream changed

Fan-in also becomes durable: completion is not “did all branch events arrive in this JVM?” but “are all required leaf `StageRun`s for this job/chapter succeeded and current?”

## Relationship to ProjectionGeneration

`StageRun` and `ProjectionGeneration` are related but distinct.

| Model | Answers | Example |
|---|---|---|
| `StageRun` | What execution work ran, failed, deferred, or remains pending? | Book object reduction attempt 2 is `DEFERRED` due to claim contention |
| `ProjectionGeneration` | What semantic graph projection is current, stale, or superseded? | Book object generation G is active and depends on chapter generations A/B/C |
| `StageRunEvent` | What happened over time? | Attempt started, LLM call made, retry scheduled, attempt succeeded |
| MDC/log fields | Which runtime log lines belong together? | `jobId`, `chapterId`, `stageKey`, `attempt` |

`StageRun` should link to produced and consumed `ProjectionGeneration`s. This lets operators answer both execution and semantic questions:

- “Which stage is stuck?”
- “Which projection did it produce?”
- “Which upstream generation invalidated it?”
- “What can recovery safely resume?”

## Why Not Keep StatusRecord as Primary?

The current `StatusRecord` chain is linear by design. It is good at timeline audit, but poor at orchestration truth.

Keeping `StatusRecord` as the primary model would force DAG semantics into properties such as `stage`, `branch`, `attempt`, and `dependencies`. That creates a second implicit scheduler model that is harder to query and easy to drift from the actual pipeline.

The better split is:

- `StageRun` is the orchestration state.
- `StageRunEvent` is the append-only audit timeline.
- job-level timeline APIs sort stage-run events.
- current job state is derived from the DAG.

Long term, `StatusRecord` can be migrated away or treated as a compatibility projection over `StageRunEvent`.

## Minimal Viable Slice

Do not retrofit every historical status immediately.

Suggested first slice:

1. Define a static stage catalog for the current ingestion DAG.
2. Create `StageRun` nodes when a job is submitted, initially `PENDING`.
3. Mark `scene-detection` as runnable first.
4. On stage start, set `RUNNING` and append a `StageRunEvent(started)`.
5. On success, set `SUCCEEDED`, append a `StageRunEvent(succeeded)`, and enqueue dependent `PENDING` runs whose dependencies are satisfied.
6. On failure, set `FAILED`, append a failure event, and prevent completion.
7. Change completion fan-in to inspect required leaf `StageRun` statuses.
8. Keep existing `StatusRecord` writes temporarily as compatibility/audit output.

This can coexist with current Spring events at first. Events can become execution signals, while `StageRun` becomes the durable control-plane state.

## Migration Path

### Phase 1 — Parallel StageRun tracking

- Add `StageRun` and `StageRunEvent` schema.
- Create stage runs for new jobs.
- Continue writing existing `StatusRecord`s.
- Link LLM calls to both current status records and stage runs where possible.

### Phase 2 — Fan-in from StageRun

- Update `IngestionCompletionCoordinator` to require succeeded/current leaf stage runs.
- Stop treating branch event arrival as sufficient completion.
- Make claim contention/deferred work update `StageRun` without emitting success-shaped completion events.

### Phase 3 — Recovery worker

- On startup, find eligible pending/deferred/running-expired stage runs.
- Re-enqueue work according to dependency and retry policy.
- Preserve terminal failures for operator attention.

### Phase 4 — StatusRecord retirement

- Replace user-facing status history queries with `StageRunEvent` timeline queries.
- Move `LlmCallRecord` links from `StatusRecord` to `StageRun`/`StageRunEvent`.
- Keep a compatibility read model only if external APIs still require `StatusRecord` shape.

### Phase 5 — Projection provenance integration

- Link stage runs to consumed/produced projection generations.
- Use stale projection generations to mark dependent stage runs `STALE`.
- Let manual rerun endpoints create new stage-run attempts with explicit invalidation semantics.

## Open Questions

- Should `StageRun` be created eagerly for the whole DAG at job submission or lazily as dependencies complete?
- What is the canonical stage catalog source: code enum, config, or graph nodes?
- Should completion be modeled as its own `StageRun` depending on leaf runs?
- How should retries be represented: new `StageRun` node per attempt, or one `StageRun` with attempt events?
- What heartbeat interval and stale-running policy is appropriate for local development and future production?
- Should manual reruns attach to the original job or create a new operator job that depends on previous projections?
- How much of this should exist before adding `ProjectionGeneration`?

## Recommendation

Adopt `StageRun` as the future primary orchestration model. Keep timeline semantics, but store them as `StageRunEvent`s attached to the DAG rather than as a single `StatusRecord` chain. Treat this as the observability and recovery companion to the projection-generation provenance model.

The immediate value is operator clarity. The strategic value is durable crash recovery and retry scheduling.
