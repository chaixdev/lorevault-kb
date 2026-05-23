# Durable Ingestion Orchestration — Implementation Review

**Date:** May 23, 2026
**Reviewed by:** Oracle (deep implementation review) + author pushback reclassification
**Plan document:** `docs/planning/2026-05-22T2300_durable-ingestion-orchestration.md`
**Branch:** `feature/durable-ingestion-orchestration`
**Files:** 13 created, 21 modified, 3 deleted
**Review scope:** All new files, spot-checked 7 modified files, cross-referenced all 18 success criteria

> **Reclassification note:** The initial oracle review over-weighted speculative defense-in-depth issues and under-weighted real operational issues. After pushback, H1 dropped to Medium, H2 to Low, H3 to Medium; M2 elevated to High, M5 elevated to High. Rationale inline.

---

## Verdict

**Not safe to start testing.** Three critical/high issues will cause stale recovery failures (C2), silent non-progression of CLI-fired pipelines (C1), and false SKIPs on book-level reruns (C3). Two additional high-severity issues (M2 audit trail corruption, M5 silent regression risk) need verification and remediation. The core orchestration engine is well-designed and correctly implemented — the gaps are in boundary/wiring code.

---

## 1. Critical Issues

### C1. `@TransactionalEventListener(AFTER_COMMIT)` silently drops events from non-transactional CLI flow

**Files:** `IngestionPipelineCoordinator.java:75`, `StepExecutionCommandController.java` (and 7 other command controllers in `lorevault-web`)

**Problem:** The coordinator's `onStageCompleted()` is annotated `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Spring's default behavior for `TransactionalEventListener` is: if the publishing thread has no active transaction, the event is **silently discarded** (the listener is never invoked).

All 12 CLI command controllers (e.g., `StepExecutionCommandController.detectScenes()`, `ChapterIndividualResolutionCommandController`, etc.) publish `StageCompletedEvent` via `StepEventMapper.publishCompletionEvent()` **without** an `@Transactional` boundary. When `fireEvents=true`, the event is published, the coordinator never receives it, and **no downstream stages are ever triggered**.

This means the entire `fireEvents=true` CLI rerun flow is **non-functional**.

**Fix:** Either wrap the command controller publish sites in `@Transactional`, or change the coordinator listener to `@EventListener` (which fires regardless of transaction). If switching to `@EventListener`, ensure the coordinator's DAG evaluation isn't reading stale state — `@EventListener` fires synchronously in the publishing thread, which is fine here since `publishCompletionEvent` is called after the work is done.

---

### C2. `findChapterId()` hardcoded to return `null` — breaks stale recovery entirely

**File:** `IngestionPipelineCoordinator.java:265-268`

```java
private UUID findChapterId(UUID jobId) {
    return null; // caller handles null (book-level stages)
}
```

**Problem:** `recoverStaleTriggers()` (line 139) and `recoverStaleRunning()` (line 156) call `findChapterId(s.getJobId())` to get `chapterId` when publishing `StageTriggeredEvent`. It returns `null`.

This means **every stale-recovered `StageTriggeredEvent` has `chapterId=null`**. When a handler receives this event:
1. `stageOutputRepo.existsByChapterIdAndStep(null, event.getStage())` — the Cypher query `MATCH (o:StageOutput {chapterId: null, step: $step})` will likely not find anything (chapterId should never be null on valid StageOutputs), so the idempotency check passes incorrectly.
2. The handler proceeds to `execute(jobId, null)` — most handlers immediately fail or throw NPE because they need `chapterId` to fetch data.

**Stale recovery is fundamentally broken.** A crash-between-write-and-publish (the exact scenario it's designed to recover) cannot be recovered.

**Fix:** Implement `findChapterId()` by querying the `ChapterIngestionJob` node:
```java
private UUID findChapterId(UUID jobId) {
    return neo4jClient.query(
        "MATCH (j:ChapterIngestionJob {id: $jobId}) RETURN j.chapterId", ...)
        .fetchAs(UUID.class).one().orElse(null);
}
```

---

### C3. Book-level StageOutputs survive cascade invalidation

**File:** `StageOutputGraphRepository.java:120-143`

**Problem:** `deleteByJobAndSteps` looks up `chapterId` from the `ChapterIngestionJob` node and deletes StageOutputs by `{chapterId, step}`. But book-level stages (`BOOK_INDIVIDUAL_REDUCTION`, `BOOK_COLLECTIVE_REDUCTION`, `BOOK_LOCATION_REDUCTION`, `BOOK_OBJECT_REDUCTION`) have StageOutputs keyed by `{bookId, step}`, NOT `{chapterId, step}`. These book-level StageOutputs will **survive cascade invalidation**.

**Example:** Rerun `BOOK_INDIVIDUAL_REDUCTION`. The cascade invalidates `BOOK_INDIVIDUAL_REDUCTION` and `INGESTION_COMPLETE`. Step 4 calls `deleteByJobAndSteps(jobId, [BOOK_INDIVIDUAL_REDUCTION, INGESTION_COMPLETE])`. The query only looks for `StageOutput {chapterId: ..., step: 'BOOK_INDIVIDUAL_REDUCTION'}` — but the actual book-level StageOutput was created with `bookId`, not `chapterId`. The stale StageOutput survives. When the rerun handler checks `existsByBookIdAndStep(bookId, BOOK_INDIVIDUAL_REDUCTION)`, it finds the old StageOutput and SKIPs — **the rerun doesn't actually execute.**

**Fix:** Extend `deleteByJobAndSteps` to also query `bookId` and delete `StageOutput {bookId: ..., step: ...}` for book-level stages.

---

## 2. High-Severity Issues

### H1. `LlmCallRecordGraphRepository` queries old `IngestionJob` node label

**File:** `LlmCallRecordGraphRepository.java`

```cypher
OPTIONAL MATCH (r)-[jobRel:OF_JOB]->(j:IngestionJob)
```

**Problem:** New `LlmCallRecord` nodes have `OF_JOB` relationships to `ChapterIngestionJob` nodes (set in `LlmCallLoggingService:114`). The repository's `OPTIONAL MATCH` looks for `IngestionJob`-labeled nodes, which won't match `ChapterIngestionJob` nodes. The `job` field on fetched `LlmCallRecord` entities will always be `null` for new records.

**Impact:** `findByJobId`, `findByJobIdAndStep`, and `findLatestByJobStepAndStage` return records but with broken `job` relationship hydration. `GraphTriadAnalysisArtifactLookup` uses `findLatestByJobStepAndStage` but doesn't access the `job` field — so it's not broken for its use case. But the API response for LLM call records will be incomplete.

**Fix:** Change `(j:IngestionJob)` to `(j)` (unlabeled) or to `(j:ChapterIngestionJob)`.

---

### H2. `LlmCallLoggingService` step-to-`StageKey` mapping corrupts LLM audit trail

**File:** `LlmCallLoggingService.java:115-117`

```java
StageKey.valueOf(step.replace("-", "_").toUpperCase())
```

**Problem:** LLM calls use string step values like `"chapter-segmentation"` and `"scene-analysis"`. After `replace("-", "_").toUpperCase()`, these become `"CHAPTER_SEGMENTATION"` and `"SCENE_ANALYSIS"` — neither of which is a valid `StageKey` enum value. The `StageKey.valueOf()` call throws `IllegalArgumentException` on **every single call**, which is caught by the outer try-catch, and falls through to the "any RUNNING stage" fallback.

**Impact:** Every LLM call record is linked via the fallback (which picks the first RUNNING stage), not via the correct stage. This is non-deterministic if multiple stages run concurrently — LLM call records are linked to potentially the wrong stage every time. The LLM call audit trail is systematically corrupted.

**Fix:** Map the string step values to valid `StageKey` constants explicitly (e.g., a `Map<String, StageKey>` lookup table), or propagate `StageKey` through the LLM call path instead of string step names.

---

### H3. `IngestionJobService.completeJob` / `failJob` are no-ops — silent regression risk

**File:** `IngestionJobService.java:94-154`

**Problem:** `completeJob()` only logs. `failJob()` only logs. `failJobWithCleanup()` deletes chunks and scenes directly but doesn't touch Stage nodes or set a FAILED status on the INGESTION_COMPLETE stage. These methods exist for API compatibility but have no effect on the durable orchestration model.

**Impact:** If existing code calls `completeJob()` or `failJob()` expecting side effects, **nothing happens silently**. Prior to this refactor, these methods created StatusRecord nodes that downstream logic depended on. If any caller still exists (e.g., `IngestionService`, `IngestionWebController`, error recovery paths), the behavior change is a silent regression. **Must be verified** that no callers depend on the old side effects, or the methods must be wired to update Stage state.

**Fix:** Verify all callers — if any depend on side effects, wire `completeJob`/`failJob` to set terminal status on the INGESTION_COMPLETE Stage node. If none do, rename to `logJobComplete`/`logJobFailed` to make intent explicit.

---

## 3. Medium-Severity Issues

### M1. `IngestionJob.completedAt` still present on legacy entity

**File:** `IngestionJob.java:29`

**Problem:** Implementation notes say `currentStatus` was removed, but `completedAt` still exists. The plan says: "currentStatus, completedAt removed from the node." Minor since it's a legacy class, but contradicts both plan and implementation notes.

---

### M2. `tryTrigger` Cypher doesn't scope `[:TRIGGERS]` parents by `jobId` (defense-in-depth)

**File:** `StageGraphRepository.java:113-127, 213-214`

```cypher
OPTIONAL MATCH (parent:Stage)-[:TRIGGERS]->(s)
```

**Problem:** The `OPTIONAL MATCH` traverses the `[:TRIGGERS]` edge to ANY parent Stage node, regardless of its `jobId`. While the implementation always wires edges within the same job, a future bug in `rewireEdges` or manual DB manipulation could create cross-job edges. If that happened, a parent from job A could satisfy the barrier for job B's child — fan-in evaluation would be silently corrupted across unrelated jobs.

*Reclassified from High → Medium:* This is a defense-in-depth guard against a bug that doesn't currently exist. Worth the 5-minute fix but not blocking.

**Fix:** Add `{jobId: $jobId}` to the parent match:
```cypher
OPTIONAL MATCH (parent:Stage {jobId: $jobId})-[triggers:TRIGGERS]->(s)
```

---

### M3. `deleteDataByStageId()` is a no-op (documented deferred gap)

**File:** `IngestionPipelineCoordinator.java:255-261`

```java
private void deleteDataByStageId(UUID stageId) {
    log.debug("[ORCHESTRATION] Deleting data for stageId={} — handler-specific cleanup not yet wired", stageId);
}
```

**Problem:** When `rerunStage()` is called, step 3 iterates `byDepth` and calls `deleteDataByStageId()` — which does nothing. All graph data from the previous attempt survives the rerun.

*Reclassified from High → Medium:* No handlers currently write `stageId` on their output data, so there is zero current impact. This is a documented deferred gap — it only becomes relevant once handlers adopt `stageId` tagging.

**Fix:** Either (a) handlers register cleanup callbacks, or (b) a generic query deletes `MATCH (n) WHERE n.stageId = $stageId DETACH DELETE n`. Deferred until handlers write `stageId`.

---

### M4. `IngestionJobGraphRepository` (old) used by `IngestionIsolatedLookupService`

**File:** `IngestionIsolatedLookupService.java`

**Problem:** `IngestionIsolatedLookupService` uses `IngestionJobGraphRepository` (old) which queries `IngestionJob` nodes. But `IngestionService.createIngestionJob()` creates `ChapterIngestionJob` nodes. So `existsActiveForChapter()` and `findMostRecentJobId()` query `IngestionJob` nodes — and find **nothing** for newly created jobs.

This is a known deviation (#4 in implementation notes), and the plan correctly labels it "low risk since both node types coexist during transition." But after all data is migrated, these lookups will be permanently broken.

---

### M5. FAILED stages leave downstream stages in permanent PENDING with no visibility

**File:** `IngestionPipelineCoordinator.java:98-103`

**Problem:** When `onStageCompleted` receives a failure result, it writes FAILED to the stage and logs. It does NOT call `evaluateDownstream`. The `tryTrigger` barrier requires ALL parents to be COMPLETED or SKIPPED, so a FAILED parent permanently blocks the barrier. But there's no way for users to see WHICH stage is blocked or WHY.

**Fix:** Consider marking downstream stages with a BLOCKED status or recording the blocking stage's ID. This is a UX/observability gap, not a correctness bug.

---

### M6. `@Async` handlers + synchronous `@EventListener` = no feedback on handler start failure

**Files:** All 13 handlers (e.g., `SceneDetectionHandler.onTrigger()`)

**Observation:** Handlers use `@Async("ingestionLaneTaskExecutor")` — the `onTrigger` method executes asynchronously. The call to `setRunningConditionally` is the guard, but if it returns `false` (stage already RUNNING), the handler exits with no feedback. The coordinator has no way to know whether a handler actually started after emitting `StageTriggered`.

**Not a correctness bug** — `recoverStaleRunning` will eventually pick up the stalled stage. But `recoverStaleRunning` has a 300s threshold, meaning silently-started-failure scenarios go undetected for up to 5 minutes.

**Fix (deferred):** Consider counting `setRunningConditionally` failures in a metric or logging at WARN when the guard rejects a trigger. This is an observability gap for now.

---

### M7. `StepEventMapper.publishStartEvent` is dead code

**File:** `StepEventMapper.java:64-84`

**Problem:** `publishStartEvent()` creates a `StageTriggeredEvent` and publishes it directly. The coordinator's `tryTrigger` (which atomically transitions PENDING→TRIGGERED in the graph) is never called. The handler's `onTrigger` runs `setRunningConditionally` which requires `status='TRIGGERED'` — but the stage node still shows `PENDING`. So `setRunningConditionally` returns `false` and the handler silently exits. This method has zero callers in the codebase — dead code.

**Fix:** Remove, or wire up properly (call `stageRepo.tryTrigger` first, then publish).

---

## 4. Low-Severity / Nits

### L1. `rerunStage()` resets `attemptCount` to 0 — intentional operator override

**File:** `IngestionPipelineCoordinator.java:233-234`

```java
UUID newId = stageRepo.create(jobId, s, StageStatus.PENDING);
```

**Behavior:** `create()` sets `attemptCount=0`. A stage that previously FAILED after exhausting `maxAttempts` via auto-recovery can be manually rerun with `attemptCount` reset. This is a design choice: `maxAttempts` is enforced by auto-recovery; manual `rerunStage` is an explicit operator action that intentionally overrides the limit.

*Reclassified from High → Low:* The original review acknowledged this may be intentional but still rated it High. It is correctly a documented design choice.

**Deferred consideration:** If operator override should itself have a cap, add a separate `maxManualRerunAttempts` config. Not needed for wipe-state dev.

---

### L2. Handler logging formats inconsistent
SceneDetectionHandler uses `[SCENE_DETECTION]`, ChunkingHandler uses `[LANE:CONTENT] [CHUNKING]`, ChapterIndividualResolutionHandler uses `[LANE:INDIVIDUAL] [CHAPTER_INDIVIDUAL_RESOLUTION]`. Cosmetic.

### L3. `PipelineStageSupport.updateJobStatus()` calls retained in handler `execute()` methods
Known deviation #5 — these now do `log.debug()` only. Not harmful but misleading in stack traces.

### L4. `IngestionPipelineCoordinator.dag()` package-private accessor unused
Line 270-272 — `dag()` is package-private, likely for testing. Not a bug but confirms no production caller.

---

## 5. Gap Analysis

| Gap | Status | Severity | Plan Reference |
|-----|--------|----------|----------------|
| `findChapterId()` hardcoded null | **Open** — must implement | Critical | Stale recovery design (lines 221-248) |
| `StageOutput.deleteByJobAndSteps` book-level gap | **Open** — book-level SOs not deleted | Critical | Cascade invalidation step 4, Test gap #11 |
| `LlmCallLoggingService` step-to-StageKey mapping | **Open** — audit trail corruption | High | LlmCallRecord migration (line 450) |
| `completeJob`/`failJob` silent no-ops | **Open** — regression risk, must verify callers | High | Migration (line 450) |
| `deleteDataByStageId()` no-op | **Open** — no handlers write stageId; deferred gap | Medium | Cascade invalidation step 3 |
| Cross-job parent filtering in Cypher | **Open** — defense-in-depth | Medium | Race condition fixes (lines 203-217) |
| `IngestionJob`→`ChapterIngestionJob` full migration | **Open** — legacy class + old repo remain | Medium | Migration (line 450) |
| `TriadAnalysisArtifactLookup` returns empty | **Open** — documented deviation #3 | Medium | Misc design notes |
| `LlmCallRecordGraphRepository` label mismatch | **Open** — IngestionJob vs ChapterIngestionJob | Medium | Migration (line 450) |
| `@Async` + sync `@EventListener` handler feedback gap | **Open** — observability | Medium | Handler contract |
| StageOutput unbounded growth policy | **Open** — deferred per plan (line 506) | Low | Misc design notes |
| ChapterEventEmbedding event lane stages | **Open** — no dedicated handler yet | Low | Event lane unbundling (line 514) |
| `IngestionJob.completedAt` removal | **Open** — still present | Low | Success criteria #3 |
| `maxAttempts` bypass on manual rerun | **Closed** — intentional design choice | Low | State transitions (lines 165-167) |

---

## 6. Success Criteria Assessment

| # | Criterion | Status | Reasoning |
|---|-----------|--------|-----------|
| 1 | `IngestionCompletionCoordinator` deleted or reduced to thin Neo4j-backed coordinator | ✅ **Met** | Deleted |
| 2 | `StatusRecord` deleted. `StatusRecordGraphRepository` deleted. Constraints/indexes removed | ✅ **Met** | All three deleted |
| 3 | `IngestionJob` renamed to `ChapterIngestionJob`. Fields removed | ⚠️ **Partial** | `ChapterIngestionJob` exists; old `IngestionJob` still present; `completedAt` not removed |
| 4 | `IngestionJobService` rewritten to manage Stages + StageOutputs | ✅ **Met** | Full rewrite; derives status from Stage subgraph |
| 5 | Coordinator evaluates DAG fan-in from Neo4j. No `[:WAITS_FOR]`. No in-memory maps | ✅ **Met** | Conditional Cypher via `tryTrigger`; reverse `[:TRIGGERS]` traversal |
| 6 | `StageDag` defines complete pipeline topology. No handler event type references | ✅ **Met** | 15-stage DAG; all stages in one file |
| 7 | `StageTriggered`/`StageCompleted` replace implicit chain. `correlationId` removed | ✅ **Met** | Events implemented; no `correlationId` on new events |
| 8 | `StageOutput` immutable audit + idempotency. Book-level keying by `bookId` | ⚠️ **Partial** | Implemented but book-level StageOutput deletion gap (C3) |
| 9 | `LlmCallRecord` uses `[:OF_STAGE]` + `stageId`. Request/Response preserved | ⚠️ **Partial** | Migration complete structurally; audit trail corrupted by step-to-StageKey mapping (H2) |
| 10 | Manual rerun invalidates downstream stages and replays correctly | ⚠️ **Partial** | `rerunStage` implemented; `deleteDataByStageId` is no-op (deferred); book-level SOs not deleted (C3) |
| 11 | Stale trigger recovery handles crash-between-write-and-publish | ❌ **Not Met** | Implemented but `findChapterId()` returns null → broken (C2) |
| 12 | Stale RUNNING recovery resets stalled stages | ❌ **Not Met** | Same as above — `findChapterId()` returns null → broken (C2) |
| 13 | Conditional Cypher prevents double-trigger of barrier stages | ✅ **Met** | `tryTrigger` with `WHERE s.status = 'PENDING'` + all-parents check |
| 14 | Cascade invalidation transactional | ⚠️ **Partial** | `@Transactional` on `rerunStage`; data deletion is no-op (deferred); book-level gap (C3) |
| 15 | `(jobId, step)` UNIQUE constraint | ✅ **Met** | Schema initializer line 48-49 |
| 16 | Handlers use `setRunningConditionally` | ✅ **Met** | All 13 handlers follow the pattern |
| 17 | `maxAttempts` exhaustion → permanent FAILED | ✅ **Met** | Auto-recovery enforces it; manual rerun override is intentional |
| 18 | StageDag connectivity validated | ✅ **Met** | `validateConnectivity()` called in `bootstrapJob`; all 15 reachable |

**Summary:** 10 met, 5 partial, 2 not met.

---

## 7. Recommended Fix Order

| Priority | Issue | Effort | Fix |
|----------|-------|--------|-----|
| **1** | C1 — `@TransactionalEventListener` drops CLI events | ~5 min | Change to `@EventListener` or add `@Transactional` to CLI controllers |
| **2** | C2 — `findChapterId()` returns null | ~5 min | Implement Neo4j query for `j.chapterId` |
| **3** | C3 — Book-level StageOutput deletion gap | ~20 min | Extend `deleteByJobAndSteps` to handle `bookId`-keyed SOs |
| **4** | H2 — `LlmCallLoggingService` StageKey mapping | ~30 min | Add explicit `Map<String, StageKey>` lookup; audit trail currently corrupted on every call |
| **5** | H1 — `LlmCallRecordGraphRepository` label | ~5 min | Change `(j:IngestionJob)` to `(j)` |
| **6** | M2 — Cross-job parent filtering | ~5 min | Add `{jobId: $jobId}` to parent matches (defense-in-depth) |
| **7** | H3 — Verify `completeJob`/`failJob` callers | ~15 min | Audit all callers; wire to Stage state or rename to log-only |
| **8** | M5 — FAILED barrier visibility | ~1 hr | Add BLOCKED status or blocking-stage recording (observability) |

After fixing items 1-5, the implementation is safe for integration testing. Items 6-8 are deferred or quick wins.

---

## 8. What Works Well

- **Core orchestration engine** — `onStageCompleted()`, `tryTrigger()` conditional Cypher, `setRunningConditionally()` guard, handler pattern, and `rerunStage()` structure are well-designed and correctly implemented
- **DAG topology** — All 15 stages reachable from a single root (`SCENE_SEGMENTATION`); correct fan-in barriers for `INGESTION_COMPLETE` (6 parents)
- **Handler contract compliance** — All 13 handlers follow the same `setRunningConditionally` → `StageOutput` check → execute → `StageCompletedEvent` pattern
- **Schema constraints** — Complete and well-placed: unique constraints on `Stage.id`, `Stage.(jobId,step)`, `ChapterIngestionJob.id`, `StageOutput.id`; indexes for lookup patterns
- **Event design** — Clean separation: `StageTriggered` (dispatch to handlers) and `StageCompleted` (feedback to coordinator); `correlationId` correctly removed
- **Separation of concerns** — `StageDag` (static topology), `StageGraphRepository` (Neo4j operations), `IngestionPipelineCoordinator` (orchestration) are cleanly separated
