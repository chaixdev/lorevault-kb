# Deep Code Quality Review — Thematic Sessions

**Status:** IN PROGRESS — P1 complete, P2-P5 in review  
**Last Updated:** June 2, 2026

## Motivation

`feature/durable-ingestion-orchestration` has 77 commits diverged from `main` (585 files, +40K/−15K lines). None of this code has been reviewed against `main` — it accumulated incrementally with per-commit reviews on the isolated feature branch (including a targeted Concept-lane review most recently). A systematic deep review of the entire delta against current head is needed before merging to `main`.

**Scope:** User acceptance testing (UAT) has been performed at each stage — the code is **functionally correct**. The review targets **code quality and hidden defects**: anti-patterns, concurrency risks, copy-paste drift, missing guards, observability gaps, security concerns, and maintainability issues that UAT won't catch.

## Branch Context

| Metric | Value |
|--------|-------|
| Merge base | `7c47262` |
| Commits since divergence | 77 |
| Files changed | 585 |
| Lines added | +40,567 |
| Lines deleted | −14,985 |
| Current head | `436b923` |

## Approach

Rather than oneshotting the entire 585-file delta, the code is partitioned into **5 bounded review packages** by thematic domain. Each package is reviewed independently by the `lorevault-deep-reviewer` persona (5-track analysis: Logic & Correctness, Data & Persistence, Async & Events, Security & Observability, Structure & Quality). Reviews examine the code **as it is today**, not as a historical diff.

Each review package targets ~30–80 files, yielding a manageable review scope. Packages are designed to be independent so reviews can run in parallel if desired.

---

## Review Package 1: Orchestration Core

**Focus:** The DAG-based pipeline backbone — stage management, dispatch, coordination, job lifecycle.

**Key files (~30):**

| Layer | Files |
|-------|-------|
| Pipeline model | `StageDag.java`, `StageKey.java`, `StageStatus.java`, `Stage.java`, `StageGraphRepository.java` |
| Dispatch | `StageDispatcher.java`, `ForStage.java`, `StageOperation.java`, `IngestionCompleteHandler.java` |
| Coordination | `IngestionPipelineCoordinator.java`, `StageExecutionContext.java` |
| Step execution | `StepKey.java`, `StepCatalog.java`, `StepDefinition.java`, `StepResult.java` |
| Events | `StageCompletedEvent.java`, `StageTriggeredEvent.java` |
| Job management | `IngestionJobService.java`, `ChapterIngestionJob.java`, `ChapterIngestionJobGraphRepository.java`, `IngestionStatus.java`, `JobStatusDetails.java`, `JobSummary.java`, `PaginatedJobSummaries.java`, `IngestionFailure.java`, `IngestionFailureCarrier.java` |
| Ingestion service | `IngestionService.java`, `IngestionSubmissionResult.java` |
| Claim locking | `BookConsolidationClaim.java`, `BookConsolidationClaimRepository.java`, `BookConsolidationClaimService.java`, `BookConsolidationClaimUnavailableException.java` |
| Completion | `IngestionCompletionCoordinator.java` |

**Review emphasis:** DAG correctness, fan-in barrier semantics, retryable vs non-retryable classifications, claim-locking correctness, stage idempotency guards, event immutability, MDC propagation, executor binding.

**Review status:** ✅ **DONE** — Reviewed June 1, 2026. Audit: [`docs/reviews/2026-06-01T2220_p1-orchestration-core.md`](../../reviews/2026-06-01T2220_p1-orchestration-core.md).

### Review Results

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| CRIT-1 | 🔴 | Fan-out partial completion — `evaluateDownstream` loop abandons siblings on Neo4j error | **FIXED** `d3d7ab5` — wrapped `tryTrigger` in try-catch |
| HIGH-1 | 🟠 | Dead `isAlreadyCompleted()` check — always returns false after CAS guard, wasting 1 Neo4j query per dispatch | **FIXED** `d3d7ab5` — removed dead code + 5 related tests |
| MED-1 | 🟡 | `recoverStaleTriggers` publishes duplicate events under executor saturation | Open |
| LOW-1 | 🟢 | `BookConsolidationClaimRepository.tryAcquireClaim` binds `bookId` as `String` | Open |
| LOW-2 | 🟢 | `BookConsolidationClaim` writes unmapped `stageId` property | Open |
| LOW-3 | 🟢 | Events (`StageCompletedEvent`, `StageTriggeredEvent`) are classes not records | Open |

### Review Discoveries (Beyond Planned Scope)

- **Step→Stage migration never executed:** Retirement plan (`docs/planning/2026-05-31T0000_retire-stepkey-consolidate-stagekey.md`) was marked IMPLEMENTED in commit `1ffbb244` (docs-only update, May 31) and archived — but no code changes were made. `StepKey`, `StepDefinition`, `StepCatalog` still live; 12 controllers and `StepEventMapper` still import `StepKey`. Corrected doc status to PLANNING and moved back to `docs/planning/`.
- **`IngestionCompletionCoordinator.java` does not exist:** Listed in the original scope but intentionally deleted during the durable orchestration refactor — replaced by `IngestionPipelineCoordinator`. Not a defect.
- **Two new rules added to `docs/rules/`:** [Fan-out loop resilience](../../rules/coding-standards.md) (from CRIT-1) and [Unreachable code after refactor](../../rules/code-organization-guidance.md) (from HIGH-1).

**Known planned refactor (not defects):** The orchestration core currently couples book-level stages into the per-chapter DAG — each chapter completion triggers a full O(N²) book-level rebuild protected by claim locks. This is a known architectural debt item, planned for replacement via [Incremental Book Consolidation](../planning/2026-05-30T1750_incremental-book-consolidation.md). When reviewing, do not flag the following as defects — they are intentionally temporary:

- Book-level stages (`BOOK_*_CONSOLIDATION`) living inside `StageDag` as children of chapter-level stages
- `BookConsolidationClaimService` / claim-locking mechanism (exists only to prevent concurrent full-rebuild corruption; will be removed with incremental merge)
- `StageExecutionContext` carrying both `chapterId` and `bookId` (one is always null depending on stage scope; will simplify when book coordinator is separate)
- Book-level stages appearing in `CHAPTER_STAGES` classification sets (current DAG topology requires it; will move to separate book-level DAG)
- Fan-in from N chapter completions into a single `INGESTION_COMPLETE` per chapter (future: chapter completion is terminal; book completion is a separate lifecycle)

The reviewer should still flag **correctness** issues within the current architecture (e.g., barrier miscounts, missing claim release, retryable failure misclassification) — just not flag the architecture itself as a defect.

---

## Review Package 2: Entity Lanes

**Focus:** The 6 entity type lanes (Individual, Collective, Concept, Location, Object, Event) — extraction models, persistence nodes, GraphRepositories, consolidation services, DAG handlers.

**Key files (~90):**

### Shared infrastructure
| Layer | Files |
|-------|-------|
| Consolidation engine | `ConsolidationEngine.java`, `NameKeys.java`, `PickFirstNonBlank.java`, `EntityMerger.java`, `ChapterEntityGuardService.java` |
| Common | `Mention.java`, `NameNormalizer.java` |
| Schema | `Neo4jSchemaInitializer.java` (constraints + indexes for all 18 entity types) |

### Per-lane files (×6 lanes: Individual, Collective, Concept, Location, Object, Event)

**Persistence layer** (per lane, in `graph/<lane>/persistence/`):
```
<Lane>Mention.java            — Node record (implements Mention)
<Lane>MentionGraphRepository.java
<Lane>PersistenceService.java — Extracts LLM output into Mention nodes
Chapter<Lane>.java            — Chapter-aggregate Node record
Chapter<Lane>GraphRepository.java
Book<Lane>.java               — Book-aggregate Node record
Book<Lane>GraphRepository.java
```

**Consolidation layer** (per lane, in `graph/<lane>/consolidation/`):
```
chapter/Chapter<Lane>ConsolidationService.java
chapter/Chapter<Lane>ConsolidationHandler.java
chapter/Chapter<Lane>ConsolidationOperation.java
chapter/Chapter<Lane>ConsolidationResult.java
book/Book<Lane>ConsolidationService.java
book/Book<Lane>ConsolidationHandler.java
book/Book<Lane>ConsolidationOperation.java
book/Book<Lane>ConsolidationResult.java
book/Book<Lane>PersistenceService.java
```

**Review emphasis:** Copy-paste fidelity across lanes (common patterns, lane-specific divergences), consolidation correctness (cluster.isEmpty guards, PickFirstNonBlank, description preservation), Cypher parameter naming, MERGE vs CREATE, DETACH DELETE correctness, @Transactional scope, claim-locking presence on all book-level handlers, retryable error classification consistency.

---

## Review Package 3: LLM Integration & Scene Analysis

**Focus:** AI-driven scene detection, triad analysis, structured extraction, prompt templates.

**Key files (~18):**

| Layer | Files |
|-------|-------|
| Scene detection | `SceneDetectionHandler.java`, `SceneDetectionOperation.java`, `SceneDetectionService.java`, `SceneDetectionResult.java`, `SceneProcessingService.java`, `SceneDetectionException.java`, `SceneLocalizationException.java`, `SceneWithCoordinates.java`, `Scene.java`, `SceneGraphRepository.java` |
| Triad analysis | `SceneRelationshipAnalysisService.java`, `TriadAnalysisModels.java`, `TriadAnalysisException.java`, `TriadBuilderService.java`, `GraphTriadAnalysisArtifactLookup.java`, `TriadAnalysisArtifactLookup.java` |
| LLM infrastructure | `LlmClient.java`, `LlmCallLogger.java`, `LlmCallRecord.java`, `LlmCallRecordGraphRepository.java`, `LlmCallLoggingService.java`, `ModelSlot.java` |
| Prompt management | `PromptRepository.java`, `PromptName.java`, `PromptLocationResolver.java`, `scene-analysis.txt` (prompt file) |

**Review emphasis:** LLM output null-guarding, structured output validation, retry logic (`MAX_SEMANTIC_TRIAD_ATTEMPTS`), prompt injection risks, token observability, `@Builder` on SceneRelationshipOutcome (all convenience constructors removed), TriadStructuredResult null-safety, temperature progression, llmCallId provenance.

---

## Review Package 4: Web & REST Layer

**Focus:** REST endpoints, SSE streaming, step execution controllers, UI controllers.

**Key files (~28):**

| Layer | Files |
|-------|-------|
| Ingestion command | `CommandIngestionController.java`, `PrepareCommandController.java`, `StepExecutionCommandController.java`, `StepExecutionResponse.java`, `StepEventMapper.java`, `EventAnnRerunCommandController.java` |
| Consolidation endpoints | `Chapter{Individual,Collective,Concept,Location,Object}ConsolidationCommandController.java` (5 files), `Book{Individual,Collective,Concept,Location,Object}ConsolidationCommandController.java` (5 files) |
| Job query | `JobsController.java`, `JobStatusBroadcaster.java`, `JobStatusResponse.java`, `JobListResponse.java` |
| Step query | `StepQueryController.java` |
| Library | `LibraryCommandController.java` |
| UI controllers | `UiOperatorActionsController.java`, `DashboardController.java`, `IngestionUiController.java`, `JobsUiController.java`, `LibraryUiController.java`, `LibraryOptionsController.java`, `UiQueryController.java` |

**Review emphasis:** UUID validation consistency, error response patterns (ErrorResponse.builder().build()), fireEvents semantics, StepKey→StageKey mapping correctness, trailing slash behavior, @WebMvcTest slice test adequacy, REST surface auth coverage (no Spring Security present), UI controller template poisoning.

---

## Review Package 5: Package Structure & Module Boundaries

**Focus:** Module dependency direction, package cohesion, dead code, import hygiene, Lombok discipline.

**Key areas to examine:**

| Concern | What to check |
|---------|--------------|
| Old package remnants | `lorevault-core/.../ingestion/` — many files in the old package structure still exist (events, resolution services, job management). Are these dead imports or still-referenced classes? Some have been restructured into `graph/`, `orchestration/`, `library/` — verify no dual references. |
| Event class cleanup | 16 event classes in `ingestion/events/` — are they still referenced or dead? Past cleanup commits deleted 15 dead event classes — verify no new dead events were introduced. |
| Module dependency direction | `lorevault-web` → `lorevault-core` → `lorevault-catalog` — check for upward references, circular deps, web module importing core internals directly. |
| Config cleanliness | `AsyncConfig.java`, `RetryConfig.java`, `SpringAiConfig.java`, `LoreVault*Properties.java` — check for hardcoded values, config bloat, unused properties. |
| Lombok discipline | Check for @Data on entities, @SneakyThrows, missing @ToString exclusions, @Builder.Default misuse. |
| Single-impl interfaces | ConsolidationOperation, EmbeddingOperation, ChunkingOperation, SceneDetectionOperation — YAGNI? |
| Dead code | Any unreferenced classes, unused imports, orphaned enum values in IngestionStatus or StageKey. |

**Review emphasis:** Module boundary violations, dead code in `ingestion/` package remnants, import cross-contamination, config property usage, @Deprecated violations (codebase rule: zero tolerance).

---

## Review Methodology

Each package is reviewed using the `lorevault-deep-reviewer` skill:

1. **Phase 1 — Context:** Read all files in the package + key callers
2. **Phase 2 — Analysis:** 5 parallel oracle tracks (Logic, Data, Async, Security, Structure)
3. **Phase 3 — Aggregation:** Cross-track hit detection, dedup, severity ranking
4. **Phase 4 — Synthesis:** Structured report with findings + priority action table

Reviews are **independent** — any package can be reviewed first. The recommended order for cumulative understanding is 1→2→3→4→5, but any order works.

## Scheduling

| Package | Files | Est. Review Effort | Priority |
|---------|-------|-------------------|----------|
| P1: Orchestration Core | ~30 | Complete | ✅ DONE — 2 FIXED, 2 TRACKED, 4 open (audit) |
| P2: Entity Lanes | ~90 | Largest (volume) | High |
| P3: LLM & Scene Analysis | ~18 | Medium | High |
| P4: Web & REST | ~28 | Medium | Medium |
| P5: Package Structure | ~30 | Medium (scan-heavy) | Medium |

A single package takes one deep-review session (~10–20 minutes of agent work). All 5 packages can be reviewed sequentially in a single long session or split across multiple sessions.

## Outcome

Each review package produces a standalone findings report. Once all 5 are complete:
1. Aggregate findings across packages
2. Identify cross-package patterns (e.g., same defect replicated across lanes)
3. Prioritize fixes
4. Apply fixes in bounded batches
5. Final `mvn clean test` verification
6. Merge to `main`

---

## P4: Web & REST — Completed June 2, 2026

**Report:** `docs/reviews/2026-06-02T0000_p4-web-rest-layer-review.md`

**Findings:** 6 CRITICAL, 11 HIGH, 20 MEDIUM, 18 LOW across 29 controller-layer files (~1,800 lines).

### Disposition

Of the 17 CRITICAL/HIGH findings, 5 were LLM drift — the guidance existed, the code contradicted it, and the review caught it:

| Finding | Existing rule that should have prevented it |
|---------|--------------------------------------------|
| CRIT-4 (missing `correlationId`) | `ingestion-pipeline.md` mandates it on every event class |
| CRIT-5 (backward-compat redirect) | `code-organization-guidance.md` forbids backward-compatibility code |
| CRIT-6 (random `stageId`/`jobId`) | `ingestion-pipeline.md` mandates `stageId` provenance; ADR-014/ADR-015 |
| HIGH-7 (constructor ambiguity) | Specific code defect, not a guidance gap |
| HIGH-9 (TOCTOU on `findById`) | `coding-standards.md` — atomic idempotency guards rule (codified from P2/P3) |

The remaining 11 exposed gaps — either the guidance didn't exist, or existing guidance covered part but not all of the problem:

| Finding | Gap |
|---------|-----|
| CRIT-2 (repo injection) | No rule forbade controller → repository dependencies |
| CRIT-3 (exception leak in responses) | Logging Philosophy covered logs, not HTTP response bodies |
| HIGH-1 (hardcoded password) | "Secrets in source" existed but wasn't applied; explicit env-var-only rule needed |
| HIGH-2 (830 lines of copy-paste) | Over-Abstraction existed but no inverse "excessive duplication" rule |
| HIGH-3 (SSE timeout) | Implementation detail, not codified (one-off fix) |
| HIGH-4 (sync event dispatch) | Async rules didn't cover `ApplicationEventPublisher` blocking HTTP |
| HIGH-5 (sync SSE broadcast) | Same gap as HIGH-4 |
| HIGH-6 (exception messages in UI) | Same gap as CRIT-3 — error response hygiene |
| HIGH-8 (UUID type bypass) | No rule requiring consistent `ErrorResponse` pattern across controllers |
| HIGH-10 (UI bypasses orchestration) | No rule requiring UI and API to use same `StageOperation` beans |
| HIGH-11 (500 vs 404) | No rule requiring consistent HTTP status across REST and UI |

### Fixes applied

| Finding | Fix |
|---------|-----|
| CRIT-4 | `correlationId` field added to `StageCompletedEvent` and `StageTriggeredEvent`; propagated through `StageDispatcher`, `IngestionPipelineCoordinator`, `StepEventMapper` |
| CRIT-2 + HIGH-9 | Removed `ChapterGraphRepository`/`BookGraphRepository` injection and `findById` TOCTOU blocks from 11 controllers |
| CRIT-5 | Deleted `BookConsolidationRedirectController.java` (77 lines) |
| HIGH-3 | `SseEmitter(300_000L)` — 5-minute timeout |
| HIGH-4 | `StepEventMapper` wraps `publishEvent` in `CompletableFuture.runAsync(ingestionTaskExecutor)` |
| HIGH-5 | `JobStatusBroadcaster` — dedicated `sseBroadcastExecutor` (2-4 threads); async `broadcast()` and `keepAlive()` with `List.copyOf()` snapshots |
| HIGH-8 | `CommandIngestionController` — UUID param changed to `String` with manual parsing + structured `ErrorResponse` |
| HIGH-11 | `JobsUiController` — `orElseThrow` replaced with explicit `Optional.isEmpty()` → 404 view |

### Tests

- 3 WebMvcTest files updated: removed `ChapterGraphRepository` mock and dead 404 tests; added `StageOperation` failure-path tests
- `JobStatusBroadcasterTest` updated: manual `initExecutor()`/`shutdownExecutor()` calls
- `StageDispatcherTest` and `IngestionPipelineCoordinatorTest`: 23 `StageTriggeredEvent`/`StageCompletedEvent` call sites updated for `correlationId`
- New `WebLayerArchitectureTest` with 2 ArchUnit rules: no controller may depend on `*Repository` types
- Build verification: 430 tests, 0 failures, 0 errors

### Rules codified from P4 review

**`coding-standards.md` (portable):**
- **Error response hygiene** (Security §) — never include exception messages in HTTP response bodies
- **Excessive duplication** (Over-Abstraction §) — 3+ near-identical blocks that differ only by mechanically derivable values is a defect
- **`ApplicationEventPublisher` is synchronous** (Async & Executors §) — offload from HTTP threads via executor

**New `docs/rules/web-layer-conventions.md` (LoreVault-specific):**
- Controllers must not inject repositories or data-access beans
- UI and API controllers must use the same `StageOperation` pipeline interfaces
- All controllers must use structured `ErrorResponse`; REST/UI status codes must agree
- Controllers must not contain business logic (event publishing, existence checks, conditional orchestration)

### Deferred

| Finding | Reason |
|---------|--------|
| CRIT-1 (no auth) | Separate IAM/RBAC sprint |
| CRIT-3 + HIGH-6 (error sanitization) | Needs project-wide pass; extracted to planning |
| HIGH-1 (hardcoded PG password) | Deferred |
| HIGH-2 (consolidation controller generification) | Extracted to `docs/planning/2026-06-02T0000_consolidation-controller-generification.md` |
| HIGH-7 (Step→Stage rename) | Separate cleanup pass planned |
| CRIT-6 + HIGH-10 (UI operator) | Blocked on HIGH-2 generification refactor |
