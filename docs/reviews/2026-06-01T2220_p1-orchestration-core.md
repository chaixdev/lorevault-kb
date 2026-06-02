# P1: Orchestration Core — Deep Quality Review

**Status:** REVIEWED — 2 FIXED, 2 TRACKED, 6 actionable (1 MEDIUM, 3 LOW)
**Date:** June 1, 2026
**Source:** `docs/planning/2026-06-01T2220_deep-quality-review-sessions.md` — Review Package 1
**Reviewed:** 32 files (15 pipeline model + dispatch + coordination, 2 signals, 9 job management, 2 submission, 4 claim locking)
**Scope:** DAG-based pipeline backbone — stage management, dispatch, coordination, job lifecycle, claim locking

---

## Section 1 — Summary

**Verdict: ✅ Conditional pass — CRITICAL and HIGH defects fixed during review.**

The orchestration core is well-designed: the DAG topology is clean, the two-phase CAS guard (PENDING→TRIGGERED→RUNNING) provides correct double-fire protection, and the barrier evaluation Cypher is sound. The review uncovered **1 CRITICAL** (fan-out partial completion) and **1 HIGH** (dead idempotency check), both fixed in-review. Two known gaps (MDC in coordinator, Step→Stage migration) are tracked in planning docs. Four remaining findings (MEDIUM/LOW) are non-blocking cleanup.

**Maintainer disposition:** CRIT-1 and HIGH-1 fixed in `d3d7ab5` (evaluateDownstream try-catch + dead isAlreadyCompleted removal). MDC gap confirmed as tracked (downgraded from HIGH after verifying existing planning doc). Step→Stage migration uncovered as a documentation drift incident — retirement was marked IMPLEMENTED in a docs-only commit (`1ffbb244`) and archived 5 minutes later (`20ca49bd`) without any code changes. Corrective docs fix in `954aeb3` (moved back to `docs/planning/`, status corrected to PLANNING). Two new rules codified from this review (`d3d7ab5`). 436 tests green.

---

## Section 2 — Findings

### CRITICAL

#### CRIT-1 — Fan-out partial completion on Neo4j error permanently stalls pipeline
**Severity:** 🔴 CRITICAL
**File:** `lorevault-core/.../pipeline/IngestionPipelineCoordinator.java:121-132`
**Cross-track:** Track A (Logic) + Track C (Async)
**Problem:** `evaluateDownstream` iterates `dag.childrenOf(completedStage)` and calls `stageRepo.tryTrigger()` for each child. If `tryTrigger` throws a `DataAccessException` (transient Neo4j error, connection blip) on child N, the loop terminates, leaving children N+1 through end unevaluated. The parent is already durable-marked COMPLETED (set before `evaluateDownstream` is called). Children whose parents are all COMPLETED but remain in PENDING status are permanently stalled — neither `recoverStaleTriggers` (only handles TRIGGERED) nor `recoverStaleRunning` (only handles RUNNING) will ever recover them. No scheduled job scans for PENDING stages with all-parents-completed barriers.

**Fix:** Wrap `tryTrigger` in try-catch and continue iteration on failure, logging at ERROR. Since `tryTrigger` is atomic per child (Cypher CAS), a failed attempt for one child should not block siblings.

**Resolution:** FIXED in `d3d7ab5`. Additionally, new rule added: [Fan-out loop resilience](../../rules/coding-standards.md) under Event-Driven Pipeline.

---

### HIGH

#### HIGH-1 — Dead-code idempotency check wastes 1 Neo4j query per dispatch
**Severity:** 🟠 HIGH
**File:** `lorevault-core/.../pipeline/StageDispatcher.java:148-182`
**Cross-track:** Track A (Logic) + Track E (Structure)
**Problem:** The dispatch flow is: (1) CAS TRIGGERED→RUNNING via `setRunningConditionally`, (2) `isAlreadyCompleted()` checks `status == COMPLETED`. The CAS at step 1 atomically transitions from TRIGGERED to RUNNING. A stage in COMPLETED status can never satisfy the `WHERE s.status = 'TRIGGERED'` condition in `setRunningConditionally`. Therefore the winning thread always finds the stage in RUNNING status (just set), and `isAlreadyCompleted` always returns `false`. This is dead code adding an unnecessary Neo4j round-trip (`findByJobIdAndStep`) on every single stage dispatch. With 15 stages per job, this is 15 wasted queries.

**Fix:** Remove the `isAlreadyCompleted` check, the `setSkipped` call, and the private method. The CAS guard is sufficient for double-fire protection.

**Resolution:** FIXED in `d3d7ab5`. Additionally, new rule added: [Unreachable code after refactor](../../rules/code-organization-guidance.md).

---

### MEDIUM

#### MED-1 — `recoverStaleTriggers` may publish duplicate events under executor saturation
**Severity:** 🟡 MEDIUM
**File:** `lorevault-core/.../pipeline/IngestionPipelineCoordinator.java:141-153`
**Problem:** `findStaleTriggered` returns stages where `status = 'TRIGGERED' AND triggeredAt < now - grace`. It does not update `triggeredAt`. If the dispatcher's `@Async("ingestionTaskExecutor")` pool is saturated and hasn't processed the event within 30s, the same stale stage is returned every cycle and a duplicate `StageTriggeredEvent` is published. The dispatcher's CAS guard prevents duplicate execution, but duplicate events waste resources and create noisy logs.

**Validation performed during review:** In normal operation, the dispatcher receives the event and CAS-es TRIGGERED→RUNNING in milliseconds via `setRunningConditionally`. Once RUNNING, `findStaleTriggered` (which filters on `status = 'TRIGGERED'`) won't re-find it. The duplicate scenario requires the executor to be so saturated that an event sits unprocessed for >30s. Practical risk is low — the CAS guard is the ultimate defense against double-execution.

**Fix:** After re-publishing, update `triggeredAt` to `datetime()` so the stage won't be re-picked until the next grace window expires. Add a `touchTriggeredAt(UUID stageId)` Cypher method to `StageGraphRepository`.

**Resolution:** OPEN — low-priority cleanup. Impact limited to resource waste under heavy load; no correctness issue.

---

### LOW

#### LOW-1 — `BookConsolidationClaimRepository.tryAcquireClaim` binds `bookId` as `String` instead of `UUID`
**Severity:** 🟢 LOW
**File:** `lorevault-core/.../graph/location/consolidation/book/BookConsolidationClaimRepository.java:44-52`
**Problem:** The method signature takes `@Param("bookId") String bookId`. Callers in `BookConsolidationClaimService` pass `bookId.toString()`. The `BookConsolidationClaim` entity has `private UUID bookId`. The parameter should be `UUID` for type safety.

**Fix:** Change parameter type to `UUID`. Update callers to pass `bookId` directly instead of `bookId.toString()`. Neo4j Java driver handles UUID→string conversion automatically.

**Resolution:** OPEN.

---

#### LOW-2 — `BookConsolidationClaim` writes unmapped `stageId` property to Neo4j
**Severity:** 🟢 LOW
**File:** `BookConsolidationClaimRepository.java:41`, `BookConsolidationClaim.java:30-50`
**Problem:** The `tryAcquireClaim` Cypher sets `c.stageId = $stageId` on CREATE, but `BookConsolidationClaim` has no `stageId` Java field. The property is stored in Neo4j but never mapped back to Java.

**Fix:** Either add `private UUID stageId` to `BookConsolidationClaim` (useful for diagnostics), or remove the property from the Cypher and method signature.

**Resolution:** OPEN.

---

#### LOW-3 — Events are mutable classes, not records
**Severity:** 🟢 LOW
**Files:** `StageCompletedEvent.java`, `StageTriggeredEvent.java`
**Problem:** Both events extend `ApplicationEvent` with private final fields and getters. They are effectively immutable but enforced by convention rather than by type system. Java records would guarantee immutability, provide `equals`/`hashCode`/`toString` for free, and remove boilerplate.

**Fix:** Convert to records when these classes need their next change. Wire compatibility is unchanged since records still provide accessor methods matching the getter convention.

**Resolution:** OPEN — purely cosmetic.

---

## Section 3 — Known Gaps (Tracked, Not Findings)

### KG-1 — No MDC context in Coordinator, recovery, or bootstrap paths
**Severity:** TRACKED
**File:** `IngestionPipelineCoordinator.java:93-204`
**Status:** Known gap with existing planning document. Logs from `onStageCompleted`, recovery methods, and `bootstrapJob` have no `jobId`/`stage` — making pipeline debugging require UUID substring matching across log streams. The reviewer initially flagged this as HIGH, then downgraded to TRACKED after confirming it was already captured in an existing MDC propagation planning item — not a surprise finding.

### KG-2 — Step→Stage migration documented but never executed
**Severity:** TRACKED
**Files:** `StepKey.java`, `StepDefinition.java`, `StepCatalog.java`, `StepResult.java`, `StepEventMapper.java`, 12 command controllers
**Status:** Retirement plan was marked IMPLEMENTED prematurely — but the code was never changed. Git archaeology reveals:

- Commit `0bcfa94f`: StepKey/StepDefinition/StepCatalog created (initial)
- Commit `c3710adb`: One partial move — `StepResult.stepName` typed to `StageKey` (but not renamed)
- Commit `aebfc6ba`: Concept lane entries ADDED to StepKey (grew it, not retired it)
- Commit `1ffbb244` (May 31, 02:14): **Docs-only** update — changed retirement doc status to IMPLEMENTED and claimed "StepKey retired" in `PROJECT-STATUS.md`. Zero `.java` files touched.
- Commit `20ca49bd` (May 31, 02:19, 5 min later): Archived the doc to `docs/archive/planning/`

No deletion commit exists for StepKey. `git log --diff-filter=D` returns nothing. `StageResult.java` and `StageQueryController.java` never existed. The retirement was documented as done but never executed — a documentation drift incident.

**Corrective actions taken:**
1. Doc moved `docs/archive/planning/` → `docs/planning/`  (re-activated)
2. Status changed IMPLEMENTED → PLANNING
3. `PROJECT-STATUS.md` line 67 corrected from past-tense ("Deleted… now use StageKey") to present-tense ("PLANNING, not yet executed… 32+ source files still reference StepKey")

---

## Section 4 — Priority Action Table

| ID | Severity | File | Description | Status |
|----|----------|------|-------------|--------|
| CRIT-1 | 🔴 CRITICAL | `IngestionPipelineCoordinator.java` | Fan-out partial completion on Neo4j error | FIXED `d3d7ab5` |
| HIGH-1 | 🟠 HIGH | `StageDispatcher.java` | Dead-code idempotency check | FIXED `d3d7ab5` |
| KG-1 | TRACKED | `IngestionPipelineCoordinator.java` | No MDC in coordinator/recovery | Existing planning doc |
| KG-2 | TRACKED | 32 files | Step→Stage migration not executed | Doc corrected `954aeb3`; plan active |
| MED-1 | 🟡 MEDIUM | `IngestionPipelineCoordinator.java` | Duplicate stale trigger events under load | Open |
| LOW-1 | 🟢 LOW | `BookConsolidationClaimRepository.java` | UUID/String type mismatch | Open |
| LOW-2 | 🟢 LOW | `BookConsolidationClaim.java` | Unmapped stageId property | Open |
| LOW-3 | 🟢 LOW | `StageCompletedEvent.java` | Events not records | Open |

---

## Section 5 — Test Gaps

| # | Gap | Severity | Where to add |
|---|-----|----------|--------------|
| TG1 | No test for fan-out partial failure — `tryTrigger` throwing mid-iteration in `evaluateDownstream` | CRITICAL | `IngestionPipelineCoordinatorTest` |
| TG2 | No test for `CHAPTER_CONCEPT_CONSOLIDATION → BOOK_CONCEPT_CONSOLIDATION` edge | MEDIUM | `StageDagTest.ChildrenOf` |
| TG3 | No test for `BookConsolidationClaimService` (acquire/release/contention/expiry) | HIGH | New `BookConsolidationClaimServiceTest` |
| TG4 | No integration test for recovery mechanisms (`recoverStaleTriggers`, `recoverStaleRunning`) | MEDIUM | `IngestionPipelineCoordinatorTest` or integration test |
| TG5 | No test for `IngestionJobService` status computation (edge cases: empty, all terminal, mixed, INGESTION_COMPLETE missing) | MEDIUM | New `IngestionJobServiceTest` |
| TG6 | No test for `rerunStage` domain data deletion via `deleteDataByStageId` | LOW | `IngestionPipelineCoordinatorTest` |
| TG7 | No test for barrier evaluation with all parents completed but child not triggered (stall scenario) | HIGH | `IngestionPipelineCoordinatorTest` |

---

## Section 6 — Positive Notes

1. **DAG topology is well-structured.** Single-source-of-truth pattern in `StageDag` is clean: children/parents/roots computed once at construction, immutable maps returned. Javadoc ASCII diagram is accurate.

2. **Two-phase CAS guard is correct.** `tryTrigger` (PENDING→TRIGGERED with barrier check) + `setRunningConditionally` (TRIGGERED→RUNNING) provides robust double-fire protection. Cypher conditional writes are properly scoped.

3. **Stale-stage recovery covers main crash scenarios.** TRIGGERED-without-publish and RUNNING-mid-crash are both handled. Grace-window pattern prevents premature re-triggering.

4. **Schema constraints exist.** `Neo4jSchemaInitializer` creates `CONSTRAINT stage_job_step_unique` — preventing duplicate Stage nodes.

5. **Claim locking uses `acquiredToken` for safe release.** Worker only deletes its own claim (token-matched), preventing accidental cross-worker claim release.

6. **Test coverage is strong for happy paths.** `StageDispatcherTest` covers MDC set/clear in all exit paths. `StageKeyTest` validates mutual-exclusion invariant. `StageDispatcherWiringTest` verifies all 17 handlers carry correct `@ForStage` annotations.

7. **No wildcard `@Async` or `@Transactional` on dispatcher.** Executor routing is programmatic and explicit, documented in Javadoc.

---

## Section 7 — Rules Added From This Review

| Rule | File | Triggered by |
|------|------|-------------|
| Fan-out loop resilience | `docs/rules/coding-standards.md` | CRIT-1 |
| Unreachable code after refactor | `docs/rules/code-organization-guidance.md` | HIGH-1 |
