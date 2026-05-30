# Incremental Book Consolidation

**Status:** NOT STARTED  
**Created:** 2026-05-30  
**Context:** Code walkthrough uncovered that book-level consolidation does a full delete+rebuild on every chapter completion, with a claim mutex as a concurrency workaround. Concurrent chapter uploads expose claim contention as permanent failures.

## Problem Statement

Book-level consolidation currently runs as a per-job stage embedded in each chapter's DAG. When a chapter completes, the coordinator triggers `BOOK_INDIVIDUAL_CONSOLIDATION` (and four other book-level lanes), which:

1. Reads **all** `ChapterIndividual` nodes for the entire book (not just the new chapter)
2. Clusters them via `ConsolidationEngine` into `BookIndividual` nodes
3. Calls `replaceBookIndividuals()` which **deletes all existing `BookIndividual` nodes** and recreates from scratch
4. Uses a claim mutex (`BookConsolidationClaimService`) to prevent concurrent full-rebuilds on the same book

**Waste:** With N chapters, chapter N's book-level consolidation rebuilds data for N-1 already-consolidated chapters. The claim is not deduplication — it's a lock preventing concurrent corruption of the full rebuild.

**Failure mode:** With 8 concurrent chapter uploads, 7 of 8 book-level stages hit claim contention and return `retryableFailure`. These FAILED retryable stages have **no automatic recovery path** — `recoverStaleRunning` only handles RUNNING, `recoverStaleTriggers` only handles TRIGGERED.

## Proposed Architecture

### Core Design

**Chapters are fire-and-forget.** When a chapter completes entity consolidation, it emits a `ChapterEntitiesReady` event containing a small delta packet. The chapter's work is done — it doesn't know or care about book-level processing.

**Book coordinator listens in batch.** A book-scoped coordinator collects delta packets, deduplicates rapid-fire completions, and applies incremental merges against existing `BookIndividual` nodes.

**Incremental by default.** Day-to-day ingestion merges new `ChapterIndividual` data into existing `BookIndividual` clusters. Full rebuild (`deleteByBookId + saveAll`) is an admin-only operation.

### Event Packet

```
ChapterEntitiesReady {
  bookId: UUID
  chapterId: UUID
  individuals: [{ id, normalizedName, aliases, displayName }]
  collectives:  [{ id, normalizedName, aliases, displayName }]
  locations:    [{ id, normalizedName, aliases, displayName }]
  objects:      [{ id, normalizedName, aliases, displayName, type, material, purpose }]
}
```

The packet contains just identifiers and name keys — enough for the book coordinator to decide merge-or-create, not enough to be a heavyweight payload. Each lane consumes its own sub-packet.

### Incremental Merge Algorithm

For a single lane (e.g., individuals):

```
1. Load existing BookIndividual → NameKeys mapping from DB
   (one cheap read: id, normalizedName, aliases per BI)

2. For each incoming ChapterIndividual:
   a. Compute NameKeys.from(normalizedName, aliases)
   b. Check for key overlap with any existing BookIndividual
   c. If overlap found:
      - Add REFERS_TO edge: ChapterIndividual → BookIndividual
      - Merge aliases into BookIndividual.aliases
   d. If no overlap:
      - Create new BookIndividual
      - Add REFERS_TO edge

3. No deletes. No re-queries of ChapterIndividual table.
```

The `ConsolidationEngine` is still used — but as a per-batch clusterer, not a full-rebuilder. If the batch contains multiple `ChapterIndividual` records that share name keys, the engine clusters them first, then the resulting cluster is merged-or-created as a unit.

### Coordinator Design

```
BookConsolidationCoordinator (book-scoped, not chapter-scoped)

onChapterEntitiesReady(event):
  1. Add packet to per-book, per-lane batch buffer
  2. Debounce: schedule processing in N ms, reset on each new event
  3. On fire: drain buffer, process all buffered items as one batch
  4. Each lane processes independently (parallel)
  5. No claim mutex — batch processing is naturally serial per lane
```

**Deduplication:** The debounce timer means 8 chapters completing within 200ms → one batch process, not 8. If processing is already in-flight, buffered items are picked up in the next batch.

**Lane parallelism:** Each lane (individual, location, object, collective, event) processes independently. Within a lane, batching ensures serial execution. No cross-lane coordination needed.

### Full Rebuild (Admin)

```
POST /api/admin/rebuild-book-consolidation/{bookId}
  → deleteByBookId + full consolidateBook() from scratch
```

This is the existing `replaceBookIndividuals()` path, exposed only via admin API. Used when:
- Consolidation algorithm changes (e.g., new version of ConsolidationEngine)
- Schema migration
- Manual correction of drifted data

## Implementation Phases

### Phase 1: Immediate Fix — Retry on Claim Contention

**Scope:** Swap `tryAcquireClaim` → `tryAcquireClaimWithRetry(bookId, lane, stageId, 3, 200)` in all 5 book-level handlers.

**Files:** `BookIndividualConsolidationHandler`, `BookLocationConsolidationHandler`, `BookObjectConsolidationHandler`, `BookCollectiveConsolidationHandler`, `BookEventCandidateGenerationHandler`

**Impact:** Prevents concurrent chapter uploads from producing permanent FAILED stages. Doesn't fix the full-rebuild waste.

### Phase 2: ChapterEntitiesReady Event + Delta Packet

**Scope:** Chapter-level consolidation handlers emit a new event with delta packet. Book-level handlers become book-scoped listeners (not per-job DAG stages).

**Changes:**
- New `ChapterEntitiesReady` event class
- Chapter-level handlers publish the event on successful completion (after the existing `StageCompletedEvent`)
- `ChapterIndividual`, `ChapterLocation`, etc. repositories gain delta-focused query methods (or the packet is constructed from in-memory data already available in the handler)
- Book-level stages removed from per-job DAG (`StageDag.java`)
- New `BookConsolidationCoordinator` listens for `ChapterEntitiesReady`

### Phase 3: Incremental Merge

**Scope:** Replace `replaceBookIndividuals()` delete+rebuild with incremental merge in each lane.

**Changes per lane:**
- New `mergeBookIndividuals(bookId, deltaPacket.individuals)`
- Existing `replaceBookIndividuals()` retained for admin rebuild
- `BookIndividualGraphRepository` gains: `findNameKeysByBookId()`, `mergeAliasesIntoBookIndividual()`, `linkChapterIndividualToBookIndividual()` (already exists)

### Phase 4: Batching + Debounce

**Scope:** Coordinator batches rapid-fire completions into single processing runs.

**Changes:**
- `BookConsolidationCoordinator` maintains per-book, per-lane buffers
- Configurable debounce window (default 200ms)
- On timeout: drain all buffered lanes, process in parallel

## Scope Boundaries

**In scope:**
- `ChapterEntitiesReady` event with delta packet
- Book-scoped coordinator with batching
- Incremental merge for all 5 lanes (individual, location, object, collective, event)
- Admin rebuild endpoint
- Migration path that preserves existing book-level consolidation correctness

**Out of scope:**
- Changing chapter-level consolidation behavior
- Changing the per-job DAG for chapter-level stages
- INGESTION_COMPLETE terminal barrier redesign (depends on new coordinator completion signaling)
- UI for admin rebuild
- Cross-book consolidation (future work)

## Open Questions

1. **Debounce vs counting:** Should the coordinator process after N milliseconds of quiet, or after M chapters have reported? Both? (N ms since last event OR M chapters buffered, whichever comes first)

2. **INGESTION_COMPLETE:** Currently fans in from all book-level stages. If book-level stages are decoupled from jobs, how does the per-chapter job signal "all work is done for this chapter"? Options:
   - Chapter's INGESTION_COMPLETE depends only on chapter-level stages (book-level is independent)
   - Book coordinator emits its own completion signal, chapter's INGESTION_COMPLETE fans in from both

3. **Cross-lane dependencies:** Do any book-level lanes depend on other lanes' output? Currently no — they're independent.

4. **Event ordering:** If two chapters produce conflicting aliases for the same entity (rare), does the incremental merge produce the same result as a full rebuild? Answer: yes, as long as merge order doesn't matter after ConsolidationEngine clustering.

5. **Packet size:** For a book with 200 chapters and 100 individuals per chapter, the delta packet is ~100 records. For a 10K-chapter book, still ~100 records per completion. No scalability concern.

## Related

- `docs/planning/2026-05-29T2308_code-walkthrough-issues.md` — ConsolidationEngine restoration (completed)
- `docs/planning/2026-05-27T0015_unified-entity-consolidation.md` — Original consolidation design
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/individual/BookIndividualPersistenceService.java` — Current full rebuild (`deleteByBookId + saveAll`)
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/location/BookConsolidationClaimService.java` — Claim mutex to be obsoleted by batching
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/StageDag.java` — Per-job DAG containing book-level stages (to be extracted)