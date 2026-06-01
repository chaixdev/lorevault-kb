# Deep Code Quality Review — Thematic Sessions

**Status:** PLANNING  
**Last Updated:** June 1, 2026

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
| P1: Orchestration Core | ~30 | Large | Highest (backbone) |
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
