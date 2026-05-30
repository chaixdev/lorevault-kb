# Code Walkthrough Issues

**Status:** Open — collecting issues during manual code walkthrough

## Summary

Issues and observations discovered during a manual walkthrough of the LoreVault codebase after the StageExecutionContext + domain node tagging implementation (Phases 1-2 + domain node tagging shipped, 463 tests green).

## Issues

### 1. `TomcatMultipartProperties` is unnecessary indirection ✅ FIXED

**Found in:** `lorevault-web/src/main/java/com/lorevault/api/config/`

**Fix applied:** Deleted `TomcatMultipartProperties.java`, inlined `@Value("${lorevault.web.multipart.max-part-count:200}")` on `TomcatMultipartConfiguration`. 463 tests green.

---

### 2. `ingestion.resolution.event` package in `lorevault-web` — module boundary bleed ✅ FIXED

**Found in:** `lorevault-web/src/main/java/com/lorevault/api/ingestion/resolution/event/`

**Fix applied:** Moved 2 production classes + 4 test classes from `lorevault-web` to `lorevault-core` (same package path). Copied `FakeEmbeddingModel` to core test sources. Core: 85/85 tests pass. Web: compiles clean.

---

### 3. ConsolidationEngine regression — alias-aware merging removed ✅ FIXED

**Found in:** All 8 consolidation services (4 entity types × 2 lifecycle levels)

**Problem:** Commit `94e3072` removed the `ConsolidationEngine` with alias-aware connected-components clustering and replaced it with per-entity divergent algorithms:
- Individual: Cypher pushdown (exact `normalizedName` only, no aliases)
- Location: kept its own connected-components (alias-aware, survived)
- Object: O(n²) scan (exact `normalizedName` only, no aliases)
- Collective: O(n²) scan (exact `normalizedName` only, no aliases)

This caused degraded smoke-test results: same character gets separate `ChapterIndividual` nodes because the LLM extracts inconsistent names across scenes (e.g., "Kevin Jenkins" vs "Jenkins").

**Fix applied:** Restored `ConsolidationEngine` + `NameKeys` + `PickFirstNonBlank` + `ChapterEntityGuardService` + `EntityMerger` in `consolidation/` subpackage. All 8 services now use shared alias-aware connected-components clustering. Individual services switched from Cypher pushdown to in-memory clustering with ID-based linking. 453 tests green.

**Files created:**
- `lorevault-core/.../resolution/consolidation/ConsolidationEngine.java`
- `lorevault-core/.../resolution/consolidation/NameKeys.java`
- `lorevault-core/.../resolution/consolidation/PickFirstNonBlank.java`
- `lorevault-core/.../resolution/consolidation/EntityMerger.java`
- `lorevault-core/.../resolution/consolidation/ChapterEntityGuardService.java`
- `lorevault-core/.../resolution/consolidation/ConsolidationEngineTest.java` (18 tests)

**Files deleted:**
- `ChapterIndividualCandidate.java`
- `ChapterIndividualCandidateView.java`

**Remaining concern:** LLM extraction quality — even with alias-aware merging, "Kevin Jenkins" (aliases: `["Kevin Jenkins", "Purveyor Jenkins"]`) and "Jenkins" (aliases: `["Jenkins"]`) won't merge because their alias sets don't overlap. The LLM prompt needs improvement to produce overlapping aliases for the same character across scenes.

---

### 4. Missing unique constraints on Mention nodes ✅ FIXED

**Found in:** `Neo4jSchemaInitializer.java`

**Problem:** `EventMention.id` had a unique constraint, but `IndividualMention.id`, `LocationMention.id`, `ObjectMention.id`, `CollectiveMention.id` did NOT. This was an inconsistency that could cause performance issues with duplicate nodes.

**Fix applied:** Added 4 unique constraints to `Neo4jSchemaInitializer`:
- `individual_mention_id_unique` for `IndividualMention.id`
- `location_mention_id_unique` for `LocationMention.id`
- `object_mention_id_unique` for `ObjectMention.id`
- `collective_mention_id_unique` for `CollectiveMention.id`

453 tests green.

---

---

### 5. `eventId` property doesn't exist on Scene nodes — Neo4j warnings ✅ FIXED

**Found in:** `SceneGraphRepository.java:46,49`

**Problem:** Two Cypher queries referenced `eventId` property: `MATCH ... (:Scene {eventId: $sceneId})`. `getEventId()` is a Java accessor returning `id` (the `@Id` field), but Neo4j stores the property as `id`, not `eventId`. Queries worked (fallback to property scan) but produced `UnknownPropertyKeyWarning` on every execution and missed the `id` index.

**Fix applied:** Changed `eventId` → `id` in both queries (`findPreviousSceneIdByReadingOrder`, `findNextSceneIdByReadingOrder`). Compiles clean.

---

### 6. Book-level consolidation claim contention — FAILED retryable stages ☐ PLANNED

**Found in:** All 5 book-level handlers + `BookConsolidationClaimService`

**Problem:** Book-level stages (BOOK_INDIVIDUAL_CONSOLIDATION, etc.) are per-job stages triggered on every chapter completion. Each uses a claim mutex to prevent concurrent full rebuilds of the same book. With 8 concurrent chapter uploads, 7 of 8 hit claim contention, return `retryableFailure`, and are marked FAILED with no automatic recovery path (retryable FAILED stages have no recovery scheduler).

Underlying waste: `replaceBookIndividuals()` does `deleteByBookId + saveAll` — full rebuild every time.

**Immediate fix (deferred):** Swap `tryAcquireClaim` → `tryAcquireClaimWithRetry(bookId, lane, stageId, 3, 200)` in all 5 handlers.

**Architectural fix:** Deferred to `docs/planning/2026-05-30T1750_incremental-book-consolidation.md` — fire-and-forget chapter events with delta packets, book-scoped coordinator with batching, incremental merge instead of full rebuild.

---

### 7. `LlmCallRecord`/`LlmCallRequest`/`LlmCallResponse` in wrong package ✅ FIXED

**Found in:** `ingestion/consolidation/event/`

**Problem:** LLM telemetry records were buried in an event consolidation package. Zero usage from event consolidation classes — only used by infrastructure telemetry (LlmCallLoggingService, LlmCallRecordGraphRepository, triad lookup). Cross-cutting observability, not event consolidation.

**Fix applied:** Moved to `ai/telemetry/` using IDE `move_file` (updates all 4 importers). 453 tests green.

---

### 8. `content/` + `ingestion/` are entity-type dumping grounds ☐ PLANNED

**Found across:** Both top-level packages

**Problem:** Every entity type (Individual, Location, Object, Collective, Event) has its model in `content/` and its pipeline in `ingestion/`, organized by implementation layer rather than domain concept. 93 files split across artificial boundary. `ingestion/content` collides with top-level `content` package. `content/extraction` is empty. `ingestion/consolidation/consolidation` double-names.

**Architectural fix:** Deferred to `docs/planning/2026-05-30T1830_entity-type-package-reorganization.md` — merge into entity-type packages (`individual/`, `location/`, `object/`, `collective/`, `event/`) with supporting packages for `scene/`, `chunk/`, `consolidation/`, `orchestration/`, `ingestion/` (narrowed).

---

## Walkthrough Log

- Web module config layer — `TomcatMultipartConfiguration`, `TomcatMultipartProperties`
- Web module — `ingestion.resolution.event` package
- Consolidation services — alias-aware merging regression
- Schema — missing unique constraints on Mention nodes
- Scene queries — `eventId` → `id` property fix
- Book-level consolidation — claim contention, incremental merge architecture
- LLM telemetry — moved from event consolidation to ai/telemetry
- Entity-type packages — merge content + ingestion by domain concept
