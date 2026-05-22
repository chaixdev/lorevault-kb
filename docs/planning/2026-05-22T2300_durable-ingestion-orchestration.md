# Durable Ingestion Orchestration

**Status:** Design — not yet implemented
**Last Updated:** May 23, 2026
**Depends on:** Current ingestion pipeline, `StatusRecord`, `IngestionCompletionCoordinator`

## Problem

The current ingestion orchestration has two fatal defects:

1. **Fan-in state is JVM-local.** `IngestionCompletionCoordinator` tracks which branches completed in `ConcurrentHashMap<CompletionKey, CompletionState>`. A restart loses all partial fan-in progress.

2. **Failure poisons replay.** `ConcurrentHashMap<CompletionKey, Long> terminalFailures` permanently blocks any branch event with the same `(jobId, correlationId, chapterId)` key after an earlier failure. A manual stage rerun succeeds, persists the right data, and publishes correct downstream events — but the coordinator ignores them because the old in-memory failure marker still exists.

Manual stage reruns (`?fireEvents=true`) cannot nudge a failed job back to completion. The only recovery path today is DB meddling — which is not intended.

The root cause is that workflow truth lives in memory, not in durable state.

## Solution

Replace `StatusRecord` and the in-memory coordinator with a **durable graph-owned orchestration model** that mirrors the Spring `ApplicationEvent` DAG 1:1.

### Core Concepts

- **`ChapterIngestionJob`** — a stable correlation node, one per chapter. Stores `chapterId`, `createdAt`. No mutable orchestration state — completion is derived from the Stage subgraph. Renamed from `IngestionJob` to reflect it's chapter-scoped, not a Spring Batch `Job`.

- **`Stage`** — a mutable node representing one vertex in the pipeline DAG. Owned by `ChapterIngestionJob` via `[:HAS_STAGE]`. Carries `step` (the `StageKey`), `status` (the `StageStatus`), `attemptCount`, `error*`, and timestamps. One Stage node per `(job, step)`. Rerun deletes the old Stage node and creates a fresh one in place — no historical Runs.

- **`StageOutput`** — an immutable proof-of-work node. Created when a Stage completes successfully. Keyed by `(chapterId, step, completedAt)` for chapter-level stages, or `(bookId, step, completedAt)` for book-level stages. Multiple StageOutputs for the same `(chapterId, step)` can exist — they are the audit trail showing "this stage was completed at time T1, then rerun and completed at time T2." Used by idempotency checks: if a StageOutput already exists for this chapter and step (and the stage was not cascade-invalidated), the handler SKIPs execution. Stale StageOutputs are deleted during cascade invalidation alongside stale Stage nodes — a fresh rerun creates a fresh StageOutput.

- **`stageId` on output data** — every node and edge produced by a stage carries a `stageId` property (or `[:CREATED_BY_STAGE]` relationship). Examples: `EntityMention.stageId`, `BookLocation.stageId`, `RelationClaim.stageId`, temporal edges. This is not optional provenance — it is the invalidation mechanism. When a stage is rerun and its downstream cascade invalidates old Stage nodes, the coordinator deletes all graph data tagged with those stale stageIds before the rerun executes. This ensures the rerun starts from clean state without stale data from the previous attempt conflicting.

- **`StageDag`** — a static data structure defining the pipeline topology: which stages trigger which downstream stages, and which stages form fan-in barriers. One source of truth, not distributed across `@EventListener` annotations. Also provides `transitiveDownstream(StageKey)` — all stages reachable from a given stage via `[:TRIGGERS]` edges.

- **`IngestionPipelineCoordinator`** — listens for `StageCompleted` events, writes Stage state to Neo4j, evaluates the DAG, and emits `StageTriggered` events for ready downstream stages. No in-memory state.

- **Stage events** — two event types replace the implicit `@EventListener` chain:
  - `StageTriggered` — signals a handler to execute. Carries `jobId`, `chapterId`, `stage`.
  - `StageCompleted` — signals the handler completed. Carries `jobId`, `chapterId`, `stage`, `result`, `counts`.

### No Historical Runs — Mutable Stages + Cascade Invalidation

There is no `Run` wrapper. Rerunning a stage means:

1. Delete the existing Stage node for `(jobId, step)`.
2. Walk the DAG forward from `step` using `StageDag.transitiveDownstream()`.
3. Delete every downstream Stage node as well — they are now invalidated.
4. Create fresh `PENDING` Stage nodes for the rerun stage and all invalidated downstream stages.
5. Emit `StageTriggered` for the rerun stage.

Example DAG: `A → {B, C, D} → E → F`. If B is rerun:

```
Rerun B:
    1. Delete existing Stage B
    2. transitiveDownstream(B) = [E, F]
    3. Delete existing Stage E and Stage F
    4. Create fresh B (PENDING), E (PENDING), F (PENDING)
    5. Emit StageTriggered(B)

When B completes and coordinator evaluates fan-in for E:
    parents of E = [B, C, D]
    B is now COMPLETED, C and D are still COMPLETED from before
    → barrier open → TRIGGERED → emit StageTriggered(E)
    → E completes → same for F
```

StageOutput nodes for invalidated stages are deleted alongside the Stage nodes during cascade invalidation. When B is rerun, the old `StageOutput {chapterId, step: B, completedAt: T1}` is deleted and a new `StageOutput {chapterId, step: B, completedAt: T2}` is created when the rerun completes. StageOutputs for untouched sibling branches (e.g., C and D) are preserved — their handlers will SKIP on re-trigger since those branches were never invalidated.

## Matching Strategy

Explicit DAG evaluation:

```
StageCompleted received for SCENE_SEGMENTATION
    │
    ▼
Coordinator writes Stage.status = COMPLETED (Neo4j)
    │
    ▼
Coordinator creates StageOutput {chapterId, step: SCENE_SEGMENTATION, completedAt} (Neo4j)
    │
    ▼
Coordinator queries StageDag: what stages are triggered by SCENE_SEGMENTATION?
    → [CHUNKING, CHAPTER_INDIVIDUAL_RESOLUTION, CHAPTER_COLLECTIVE_RESOLUTION,
       CHAPTER_LOCATION_RESOLUTION, CHAPTER_OBJECT_RESOLUTION, CHAPTER_EVENT_RESOLUTION]
    │
    ▼
For each child, coordinator evaluates fan-in: are ALL parents COMPLETED or SKIPPED?
    ├── Yes (barrier open) → SET Stage.status = TRIGGERED
    │                          emit StageTriggered(child)
    └── No (waiting) → do nothing

Fan-in is evaluated by traversing `[:TRIGGERS]` in reverse — no separate `[:WAITS_FOR]` edges needed:
```

The INGESTION_COMPLETE barrier stage transitions to COMPLETED only when all 7 leaf stages are terminal. This is a Cypher conditional write, not a ConcurrentHashMap counter.

## Node/Relationship Model

```
(:ChapterIngestionJob {id, chapterId, createdAt})
    |
    [:HAS_STAGE]   // one per DAG vertex, mutable in place
    |
(:Stage {id, jobId, step, status, attemptCount, errorMessage, errorRetryable, startedAt, completedAt})
    [:TRIGGERS]-> (:Stage)               // DAG topology edge — reversed for barrier evaluation

(:StageOutput {id, chapterId, step, completedAt})          // chapter-level stages
(:StageOutput {id, bookId, step, completedAt})             // book-level stages
    // immutable append-only audit — deleted only during cascade invalidation
    // multiple StageOutputs for the same key = replay history

(:LlmCallRecord)
    [:OF_JOB]-> (:ChapterIngestionJob)
    [:OF_STAGE]-> (:Stage)               // replaces [:OF_STATUS]->(:StatusRecord)
    [:WITH_REQUEST]-> (:LlmCallRequest)
    [:WITH_RESPONSE]-> (:LlmCallResponse)
```

## Schema

```cypher
// Constraints
CREATE CONSTRAINT chapter_ingestion_job_id_unique IF NOT EXISTS
    FOR (j:ChapterIngestionJob) REQUIRE j.id IS UNIQUE;

CREATE CONSTRAINT stage_id_unique IF NOT EXISTS
    FOR (s:Stage) REQUIRE s.id IS UNIQUE;

CREATE CONSTRAINT stage_output_id_unique IF NOT EXISTS
    FOR (o:StageOutput) REQUIRE o.id IS UNIQUE;

// Indexes
CREATE CONSTRAINT stage_job_step_unique IF NOT EXISTS
    FOR (s:Stage) REQUIRE (s.jobId, s.step) IS UNIQUE;

CREATE INDEX stage_output_chapter_step IF NOT EXISTS
    FOR (o:StageOutput) ON (o.chapterId, o.step);

CREATE INDEX stage_output_book_step IF NOT EXISTS
    FOR (o:StageOutput) ON (o.bookId, o.step);

// LlmCallRecord — rename statusRecordId to stageId, [:OF_STATUS] to [:OF_STAGE]
CREATE INDEX llm_call_record_job_step_stage IF NOT EXISTS
    FOR (r:LlmCallRecord) ON (r.jobId, r.step, r.stageId);

// Data output nodes — every node produced by a stage carries a stageId property
// for invalidation. Indexes per node type as handlers begin writing stageId:
// CREATE INDEX entity_mention_stage_id IF NOT EXISTS FOR (e:EntityMention) ON (e.stageId);
// CREATE INDEX book_location_stage_id IF NOT EXISTS FOR (l:BookLocation) ON (l.stageId);
// etc.
```

## State Transitions

### Stage lifecycle

```
PENDING → TRIGGERED → RUNNING → COMPLETED
                              → SKIPPED
                              → FAILED
                              → TRIGGERED       (stale RUNNING recovery, attemptCount < maxAttempts)
                              → FAILED           (stale RUNNING recovery, attemptCount >= maxAttempts)
FAILED → PENDING                                 (manual rerun, attemptCount < maxAttempts)
FAILED → FAILED                                  (maxAttempts exhausted — permanent)
```

- `PENDING`: Stage exists in graph, waiting for its trigger.
- `TRIGGERED`: Coordinator emitted `StageTriggered`. Handler has been invoked (or will be recovered).
- `RUNNING`: Handler has acknowledged and started work.
- `COMPLETED`: Handler emitted `StageCompleted`, coordinator persisted result, StageOutput created.
- `SKIPPED`: Idempotency check found existing `StageOutput` for `(chapterId, step)` — work already done. Handler emits `StageCompleted` with skip result. Coordinator records `StageOutput` and evaluates downstream triggers.
- `FAILED`: Unrecoverable error. Handler emitted failure. Coordinator persists error details.

### ChapterIngestionJob completion

The job is `COMPLETE` when `INGESTION_COMPLETE` stage reaches `COMPLETED`. This is computed — no mutable field on the job node.

### Replay semantics

A manual rerun of stage S only invalidates the downstream path — sibling branches are untouched.

Example: `A → {B, C, D} → E → F`. Rerun B:

```
transitiveDownstream(B) = [B, E, F]    // C and D are NOT downstream of B
Sibling branches C and D: untouched. Their stages, data, and StageOutputs remain.
```

Invalidation steps (in a single transaction):

1. Query `transitiveDownstream(S)` → set of stages on the rerun path.
2. Collect stageIds for all nodes on that path.
3. **Deepest first** deletion of graph data tagged with those stageIds (deleting E's and F's data before B's avoids dangling references mid-transaction).
4. Delete the existing StageOutput nodes for the invalidated set (so the fresh run doesn't falsely SKIP).
5. Delete the existing Stage nodes for the invalidated set.
6. Create fresh PENDING Stage nodes for the invalidated set.
7. Recreate `[:TRIGGERS]` edges between the fresh Stage nodes and their untouched siblings, and between stages on the rerun path.
8. Emit `StageTriggered(S)`.
9. The coordinator resolves normally. When E is triggered, it knits together B's new output with C and D's existing data. C and D's preserved `StageOutput`s will cause them to SKIP if re-triggered (their branches were never invalidated).

## Race Condition Fixes

### Double-trigger prevention

Two coordinator threads may evaluate the same barrier simultaneously. The fan-in Cypher must be conditional — only the thread that makes the transition publishes the event:

```cypher
MATCH (s:Stage {jobId: $jobId, step: $childStep})
WHERE s.status = 'PENDING'
MATCH (parent:Stage)-[:TRIGGERS]->(s)
WITH s, collect(parent.status) AS statuses
WHERE all(st IN statuses WHERE st IN ['COMPLETED', 'SKIPPED'])
SET s.status = 'TRIGGERED', s.triggeredAt = datetime()
RETURN s.id
```

If the query returns a row, this coordinator instance made the transition — emit `StageTriggered`. If empty, another thread already triggered it — do nothing.

### Stale trigger recovery

If the JVM crashes between the coordinator's Neo4j write (`SET status = TRIGGERED`) and `eventPublisher.publishEvent(new StageTriggered(...))`, the stage is TRIGGERED in the graph but no handler runs. A `@Scheduled` recovery job (every 30s, 60s grace window) re-publishes `StageTriggered` for any stage where `status = 'TRIGGERED' AND triggeredAt < now - 60s AND completedAt IS NULL`.

### Stale RUNNING recovery

If a handler calls `setRunning()` and then the JVM crashes before work completes, the Stage is stuck in RUNNING forever — downstream barriers will never resolve. The same `@Scheduled` recovery job also detects stalled RUNNING stages:

```cypher
MATCH (s:Stage)
WHERE s.status = 'RUNNING'
  AND s.startedAt < datetime() - duration({seconds: $staleRunningThreshold})
OPTIONAL MATCH (parent:Stage)-[:TRIGGERS]->(s)
WITH s, collect(parent.status) AS statuses,
     CASE WHEN s.attemptCount < $maxAttempts THEN 'TRIGGERED' ELSE 'FAILED' END AS target
WHERE target = 'FAILED'
   OR all(st IN statuses WHERE st IN ['COMPLETED', 'SKIPPED'])
SET s.status = target,
    s.attemptCount = s.attemptCount + 1,
    s.triggeredAt = datetime()
RETURN s
```

`staleRunningThreshold` is configurable (default 300s — well above any stage's expected execution time). `maxAttempts` is configurable (default 3). The Cypher handles three cases:

1. **Barrier still open** (some parent not terminal) → no row returned; the stage stays RUNNING for now.
2. **Barrier open AND attempts remain** → set TRIGGERED with incremented count. The Java recovery method publishes `StageTriggered` for returned rows.
3. **Max attempts exhausted** → set FAILED permanently. No trigger published. Manual intervention required.

The `status = 'RUNNING' AND startedAt` check on line 230 ensures the recovery job only targets stages that have been RUNNING longer than the threshold — a stage that just started is not prematurely flagged.

### Handler RUNNING guard

The stale RUNNING recovery can re-trigger a stage that's already RUNNING (if execution takes >300s). The recovery poller can also re-publish `StageTriggered` for a stage that's still RUNNING but took >60s. Every handler must guard against duplicate trigger:

```java
// Atomic conditional write: SET status=RUNNING WHERE status=TRIGGERED
boolean set = stageRepo.setRunningConditionally(event.getJobId(), event.getStage());
if (!set) {
    // Already RUNNING or no longer TRIGGERED — another thread/instance handled it
    return;
}
```

If `setRunningConditionally` returns false, the handler exits silently. The actual runner continues to completion and emits `StageCompleted`.

### Root trigger on fresh job

When a new job is created, the coordinator must emit `StageTriggered` for all root stages (those with zero incoming `[:TRIGGERS]` edges). The `StageDag` exposes `Set<StageKey> roots()` for this purpose.

### Cascade invalidation atomicity

When rerunning a stage, invalidation must be transactional: collect the path, delete deepest-first (graph data, then Stage nodes), recreate Stages, rewire DAG edges. Sibling branches outside the rerun path are untouched.

```java
@Transactional
public void rerunStage(UUID jobId, UUID chapterId, StageKey stage) {
    // 1. The rerun path — only stages downstream of the rerun point
    Set<StageKey> invalidated = dag.transitiveDownstream(stage);  // includes 'stage' itself

    // 2. Collect existing stageIds (needed for graph data cleanup)
    Set<UUID> invalidatedStageIds = stageRepo.findStageIdsByJobAndSteps(jobId, invalidated);

    // 3. Delete graph data, deepest first (children before parents)
    List<StageKey> byDepth = dag.topologicalDepthDescending(invalidated);
    for (StageKey s : byDepth) {
        UUID stageId = stageRepo.findStageId(jobId, s);
        dataCleanupService.deleteByStageId(stageId);
    }

    // 4. Delete stale StageOutputs for the invalidated path (prevents false SKIP)
    stageOutputRepo.deleteByJobAndSteps(jobId, invalidated);

    // 5. Delete stale Stage nodes
    stageRepo.deleteByJobIdAndStepIn(jobId, invalidated);

    // 6. Create fresh PENDING stages for the rerun path
    Map<StageKey, UUID> newIds = new HashMap<>();
    for (StageKey s : invalidated) {
        UUID newId = stageRepo.create(jobId, s, StageStatus.PENDING);
        newIds.put(s, newId);
    }

    // 7. Rewire [:TRIGGERS] edges — new stages connect to untouched siblings and to each other
    stageRepo.rewireEdges(jobId, newIds, dag);

    // 8. Emit trigger for the rerun stage
    eventPublisher.publishEvent(new StageTriggeredEvent(this, jobId, chapterId, stage));
}
```

## Public Contract

### StageKey enum

```java
public enum StageKey {
    SCENE_SEGMENTATION,
    CHUNKING,
    EMBEDDING,
    CHAPTER_INDIVIDUAL_RESOLUTION,
    BOOK_INDIVIDUAL_REDUCTION,
    CHAPTER_COLLECTIVE_RESOLUTION,
    BOOK_COLLECTIVE_REDUCTION,
    CHAPTER_LOCATION_RESOLUTION,
    BOOK_LOCATION_REDUCTION,
    CHAPTER_OBJECT_RESOLUTION,
    BOOK_OBJECT_REDUCTION,
    CHAPTER_EVENT_RESOLUTION,
    CHAPTER_EVENT_EMBEDDING,
    BOOK_EVENT_CANDIDATE_GENERATION,
    INGESTION_COMPLETE
}
```

### StageStatus enum

```java
public enum StageStatus {
    PENDING,
    TRIGGERED,
    RUNNING,
    COMPLETED,
    SKIPPED,
    FAILED
}
```

### StageDag

```java
public final class StageDag {

    // Which stages are triggered when a given stage completes?
    Map<StageKey, List<StageKey>> children;

    // Which stages must complete before a given stage can be triggered?
    Map<StageKey, List<StageKey>> parents;

    /** Stages with no parents — the spark for a fresh job. */
    Set<StageKey> roots();

    /** All stages reachable from the given stage via [:TRIGGERS] edges (includes the stage itself). */
    Set<StageKey> transitiveDownstream(StageKey stage);
}
```

### IngestionPipelineCoordinator

```java
@Component
public class IngestionPipelineCoordinator {

    private final StageDag dag;
    private final StageGraphRepository stageRepo;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onStageCompleted(StageCompletedEvent event) {
        // 1. Write Stage status + StageOutput to Neo4j (durable)
        stageRepo.setCompleted(event.getJobId(), event.getStage(), event.getResult());

        // 2. For each child stage, evaluate fan-in
        for (StageKey child : dag.childrenOf(event.getStage())) {
            // 3. Conditional Cypher: only transition if all parents done AND status is PENDING
            boolean triggered = stageRepo.tryTrigger(event.getJobId(), child);

            if (triggered) {
                eventPublisher.publishEvent(new StageTriggeredEvent(
                    this, event.getJobId(), event.getChapterId(), child));
            }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void recoverStaleTriggers() {
        // TRIGGERED without completion — crash between write and publish
        stageRepo.findStaleTriggered(/* triggeredAt < now - 60s */)
                 .forEach(s -> eventPublisher.publishEvent(
                     new StageTriggeredEvent(this, s.getJobId(), s.getChapterId(), s.getStep())));

        // RUNNING without completion — crash mid-execution
        stageRepo.findStaleRunning(/* startedAt < now - 300s, attemptCount < maxAttempts */)
                 .forEach(s -> eventPublisher.publishEvent(
                     new StageTriggeredEvent(this, s.getJobId(), s.getChapterId(), s.getStep())));
    }
}
```

### Handler contract

Every handler follows the same shape. No handler knows what triggered it or what comes next:

```java
@Component
public class SceneDetectionHandler {

    @Async("ingestionLaneTaskExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onTrigger(StageTriggeredEvent event) {
        // 1. Guard: only one thread executes at a time
        if (!stageRepo.setRunningConditionally(event.getJobId(), event.getStage())) {
            return; // already RUNNING or no longer TRIGGERED
        }

        // 2. Idempotency: does StageOutput already exist?
        // (Stale StageOutputs are deleted during cascade invalidation, so
        //  this only fires for genuinely already-completed work.)
        if (stageOutputRepo.exists(event.getChapterId(), event.getStage())) {
            stageRepo.setSkipped(event.getJobId(), event.getStage());
            eventPublisher.publishEvent(new StageCompletedEvent(
                this, event.getJobId(), event.getChapterId(),
                event.getStage(), StepResult.skipped(...)));
            return;
        }

        // 3. Do the work
        StepResult result = execute(event.getJobId(), event.getChapterId());

        // 4. Emit completion — coordinator handles DAG transitions
        eventPublisher.publishEvent(new StageCompletedEvent(
            this, event.getJobId(), event.getChapterId(),
            event.getStage(), result));
    }
}
```

## Migration

`StatusRecord` is deleted immediately. No dual-write — the project is wipe-state development with no production data. `LlmCallRecord.statusRecordId` becomes `LlmCallRecord.stageId`. `LlmCallRecord -[:OF_STATUS]-> StatusRecord` becomes `LlmCallRecord -[:OF_STAGE]-> Stage`. `StatusRecordGraphRepository` is deleted.

## Idempotency via StageOutput

StageOutput nodes provide immutable audit and durable proof that a stage's work was already done. Keyed by `(chapterId, step, completedAt)` for chapter-level stages, or `(bookId, step, completedAt)` for book-level stages. Created by the coordinator when a Stage reaches COMPLETED. Checked by handlers before executing work — if found, the stage was already completed and the handler emits StageCompleted with a skip result.

During cascade invalidation, stale StageOutputs for the invalidated path are deleted alongside stale Stage nodes and output data. This ensures handlers on the rerun path find no StageOutput and execute normally. StageOutputs for untouched sibling branches are preserved — their handlers will SKIP if re-triggered.

## Test Gaps

The following test categories must be covered before merge. The current suite tests the `ConcurrentHashMap`-based coordinator — none of these scenarios are currently guarded.

1. **Barrier evaluation Cypher.** All parents COMPLETED → barrier resolves. Some SKIPPED → barrier still resolves. One parent FAILED → barrier does NOT resolve. One parent still PENDING → barrier does NOT resolve.

2. **Concurrent trigger emission.** Two handler threads complete sibling stages simultaneously. Conditional Cypher prevents duplicate `StageTriggered` emission. Only one thread publishes.

3. **Stale trigger recovery.** Coordinator writes `TRIGGERED` to Neo4j, crashes before publishing. `@Scheduled` recovery re-publishes `StageTriggered`.

4. **Full-SKIP rerun.** Job 1 completes fully. Same chapter re-ingested → every stage finds a `StageOutput` and transitions to SKIPPED. No LLM calls. INGESTION_COMPLETE barrier resolves immediately.

5. **Partial-SKIP rerun.** Initial ingestion fails at `BOOK_INDIVIDUAL_REDUCTION`. Rerun that stage → upstream stages (SCENE_SEGMENTATION, CHUNKING, etc.) already have StageOutputs, SKIP. Failed branch re-executes.

6. **Cascade invalidation.** DAG `A → {B, C} → D`. Rerun B → D is invalidated and re-executed after B completes. C still has a StageOutput but D still replays because it was downstream of the rerun.

7. **Cascade invalidation atomicity.** Rerun deletes downstream stages and creates fresh PENDING nodes in one transaction. Concurrent coordinator thread does not trigger downstream based on stale parent state.

8. **Stale RUNNING recovery.** Handler crashes after `setRunning()` but before completion. Recovery job detects stalled RUNNING stage, sets TRIGGERED with incremented attemptCount, publishes `StageTriggered`. After `maxAttempts` exhausted, transitions to FAILED permanently.

9. **Duplicate trigger absorption (already RUNNING).** A long-running stage (>60s) receives a stale trigger re-publish. Handler's `setRunningConditionally` returns false, handler exits. Only the active runner completes.

10. **Concurrent cascade invalidation (UNIQUE constraint).** Two concurrent `rerunStage()` calls for the same stage. Second call hits `(jobId, step)` uniqueness constraint violation, returns error "rerun already in progress."

11. **SKIP suppressed for cascade-invalidated stages.** StageOutputs for invalidated path are deleted during cascade invalidation. Re-created PENDING stages find no StageOutput and execute normally.

12. **Full DAG connectivity validation.** Every `StageKey` enum value is reachable from at least one root stage. No orphan stages exist in the DAG topology.

13. **FAILED → PENDING rerun transition.** Manual rerun of a FAILED stage resets it to PENDING and re-triggers. After `maxAttempts` (default 3) are exhausted, the stage transitions to FAILED permanently.

14. **Max attempt exhaustion (permanent FAIL).** After `maxAttempts` exhausted, the stage transitions to FAILED and the recovery job does not reset it. Manual intervention required.

15. **LlmCallRecord migration (statusRecordId → stageId).** Existing LlmCallRecord nodes with `statusRecordId` are migrated to `stageId` + `[:OF_STAGE]`. Nodes without a matching Stage are handled gracefully.

16. **Book-level StageOutput scoping.** Book-level stages (BookIndividualReduction, BookCollectiveReduction, BookLocationReduction, BookObjectReduction) use `bookId` in StageOutput keying, not `chapterId`. Two chapters in the same book don't create duplicate book-level StageOutputs.

## Misc Design Notes

### `correlationId` removal

Current events carry `correlationId` (often equal to `jobId`). The new `StageTriggered`/`StageCompleted` events omit it — `jobId` is sufficient for all correlation. This simplifies the event payload.

### Book-level StageOutput keying

Book-level stages (`BookIndividualReduction`, `BookCollectiveReduction`, `BookLocationReduction`, `BookObjectReduction`) operate at book scope, not chapter scope. Their StageOutput key uses `bookId` instead of `chapterId`. This prevents duplicate StageOutputs when multiple chapters of the same book are ingested.

### StageOutput unbounded growth

Append-only StageOutputs with no eviction policy means 100 re-ingestions of the same chapter = 100 StageOutputs per stage. Not urgent for wipe-state dev, but a future `afterApplicationStart` cleanup job should delete StageOutputs older than N days or limit to the most recent K per `(scopeId, step)`.

### DAG connectivity validation

Not all 15 `StageKey` values may be reachable from roots if the DAG topology is hand-coded. A validation test (`StageDagTest.shouldHaveAllStagesReachableFromRoots()`) must assert every StageKey is reachable from at least one root via `[:TRIGGERS]` traversal.

### Event lane unbundling

The current `ChapterEventEmbeddingHandler` internally handles 4 stages and emits a single `BookEventCandidatesGeneratedEvent`. The plan's `StageKey` enum has `CHAPTER_EVENT_EMBEDDING` and `BOOK_EVENT_CANDIDATE_GENERATION` — a subset of the handler's internal stages. The mapping between current handler-internal stages and the new DAG Stages must be explicit before implementation. Each must become an independent Stage node responding to `StageTriggered` and emitting `StageCompleted`.

### `@TransactionalEventListener` preference

Handlers should use `@TransactionalEventListener(phase = AFTER_COMMIT)` rather than `@EventListener` so they don't receive `StageTriggered` before the coordinator's transaction commits. This prevents handlers from reading stale graph state.

## Success Criteria

- [ ] `IngestionCompletionCoordinator` deleted or reduced to a thin Neo4j-backed coordinator with no `ConcurrentHashMap` state.
- [ ] `StatusRecord` deleted. `StatusRecordGraphRepository` deleted. All Neo4j constraints/indexes on `StatusRecord` removed.
- [ ] `IngestionJob` renamed to `ChapterIngestionJob`. `currentStatus`, `completedAt` removed from the node — derived from Stage subgraph.
- [ ] `IngestionJobService` rewritten to manage Stages + StageOutputs instead of StatusRecords.
- [ ] `IngestionPipelineCoordinator` evaluates DAG fan-in from Neo4j via reverse `[:TRIGGERS]` traversal. No `[:WAITS_FOR]` edges. No in-memory maps.
- [ ] `StageDag` defines the complete pipeline topology in one place. Handlers do not reference other handlers' event types.
- [ ] `StageTriggered` / `StageCompleted` events replace the implicit `@EventListener`-based dispatch chain. `correlationId` removed from events.
- [ ] `StageOutput` nodes provide immutable audit and durable idempotency for replay. Stale StageOutputs deleted during cascade invalidation. Book-level StageOutputs keyed by `bookId`.
- [ ] `LlmCallRecord` uses `[:OF_STAGE]` and `stageId`. LlmCallRequest/LlmCallResponse preserved unchanged.
- [ ] Manual rerun (`?fireEvents=true`) invalidates downstream stages and replays them correctly through the coordinator.
- [ ] Stale trigger recovery handles crash-between-write-and-publish.
- [ ] Stale RUNNING recovery resets stalled stages to PENDING with attempt increment.
- [ ] Conditional Cypher prevents double-trigger of barrier stages.
- [ ] Cascade invalidation is transactional — StageOutputs, data, and Stage nodes deleted deepest-first, fresh PENDING nodes created, edges rewired atomically.
- [ ] `(jobId, step)` has a UNIQUE constraint — concurrent reruns are rejected cleanly.
- [ ] Handlers use `setRunningConditionally` (atomic CAS) to prevent concurrent re-execution.
- [ ] `maxAttempts` exhaustion transitions stage to permanent FAILED.
- [ ] StageDag connectivity validated — all StageKey values reachable from roots.
