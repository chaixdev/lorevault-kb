# Incremental Book Consolidation

**Status:** NOT STARTED  
**Created:** 2026-05-30  
**Updated:** 2026-06-01 — Added ConsolidationEngine analysis, concurrent chapter processing feasibility, O(N²) quantification

## Problem Statement

Book-level consolidation currently runs as a per-job stage embedded in each chapter's DAG. When a chapter completes, the coordinator triggers `BOOK_INDIVIDUAL_CONSOLIDATION` (and four other book-level lanes), which:

1. Reads **all** `ChapterIndividual` nodes for the entire book (not just the new chapter)
2. Clusters them via `ConsolidationEngine` into `BookIndividual` nodes
3. Calls `replaceBookIndividuals()` which **deletes all existing `BookIndividual` nodes** and recreates from scratch
4. Uses a claim mutex (`BookConsolidationClaimService`) to prevent concurrent full-rebuilds on the same book

**Waste:** With N chapters, chapter N's book-level consolidation rebuilds data for N-1 already-consolidated chapters. Each new chapter triggers a full O(K) recomputation where K is the cumulative entity count across all chapters processed so far — effectively O(N²) total work across N chapters. The claim is not deduplication — it's a lock preventing concurrent corruption of the full rebuild.

**Failure mode:** With 8 concurrent chapter uploads, 7 of 8 book-level stages hit claim contention and return `retryableFailure`. These FAILED retryable stages have **no automatic recovery path** — `recoverStaleRunning` only handles RUNNING, `recoverStaleTriggers` only handles TRIGGERED.

**Root cause:** The claim system, `@Retryable` annotations, and separate `PersistenceService` boundary exist **solely** because `deleteByBookId` + `saveAll` cannot overlap safely. Chapter and book consolidation use the same clustering algorithm (`ConsolidationEngine.cluster()` — connected components on shared name keys) and the same batch recomputation strategy. The claim lock is a concurrency workaround, not a structural necessity.

## Proposed Architecture

### Core Design

**Chapters are fire-and-forget.** When a chapter's pipeline completes (all chapter-scope stages done), it emits a `ChapterIngestionComplete` event containing a small delta packet. The chapter's work is done — it doesn't know or care about book-level processing.

**Book coordinator listens in batch.** A book-scoped coordinator collects delta packets, deduplicates rapid-fire completions, and applies incremental merges against existing `BookIndividual` nodes.

**Incremental by default.** Day-to-day ingestion merges new `ChapterIndividual` data into existing `BookIndividual` clusters. Full rebuild (`deleteByBookId + saveAll`) is an admin-only operation.

### Event Packet

```
ChapterIngestionComplete {
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

The `ConsolidationEngine.cluster()` algorithm is an **online connected-components processor** — it processes entities sequentially and can accept new ones incrementally in O(k) time (k = keys per entity). The only thing blocking true incrementality is the full-map rebuild at lines 82-92 (`clusterIndexByKey.clear()` + repopulate from all remaining clusters) — an 8-line removal if cluster state is persisted between invocations.

For a single lane (e.g., individuals):

```
1. Load existing BookIndividual → cluster state from DB
   Each BookIndividual carries its normalizedName and aliases (NameKeys)
   The "cluster state" is the set of existing clusters and their key→cluster mappings

2. For each incoming ChapterIndividual (or batch from delta packet):
   a. Compute NameKeys.from(normalizedName, aliases)
   b. Check for key overlap with any existing BookIndividual cluster
      (using the persisted clusterIndexByKey map, no full scan needed)
   c. If overlap found:
      - Add REFERS_TO edge: ChapterIndividual → BookIndividual
      - Merge aliases into BookIndividual.aliases
      - Update the key→cluster mapping incrementally (append new key entries,
        remove stale ones from old cluster members — no full rebuild)
   d. If no overlap:
      - Create new BookIndividual + new cluster
      - Register keys in cluster mapping

3. No deletes of existing BookIndividual nodes. No re-queries of ChapterIndividual table.
   No claim lock — incremental writes don't conflict.
```

**ConsolidationEngine changes required:**
- Remove the full `clusterIndexByKey.clear()` + rebuild after each merge
- Make the engine stateful: accept an existing `clusterIndexByKey` and `clusters` list as input, return mutated versions as output
- Persist cluster state per-book between invocations (in-memory cache or Neo4j-backed)

**Encounter-order determinism:** The engine relies on encounter order for deterministic cluster ordering. In an incremental system, batches must be globally ordered (by `chapterNumber + entityIndex`) to ensure the same merge sequence as a full rebuild would produce.

The `ConsolidationEngine` is still used — but as a per-batch incremental clusterer, not a full-rebuilder. If the batch contains multiple `ChapterIndividual` records that share name keys, the engine clusters them first, then the resulting cluster is merged-or-created as a unit against the existing book-level clusters.

### Coordinator Design

```
BookConsolidationCoordinator (book-scoped, not chapter-scoped)

onChapterIngestionComplete(event):
  1. Extract bookId + lane-specific delta from packet
  2. Each lane processes independently (parallel)
  3. No claim mutex — incremental writes don't conflict
  4. No batching needed — chapters complete minutes apart,
     and concurrent merges are naturally safe on targeted
     MERGE/CREATE operations
```

**Per-lane serialization:** Within a lane for a given book, processing is naturally serial via single-threaded event dispatch. Two `ChapterIngestionComplete` events for the same book arrive in order and process sequentially. No explicit buffering or timer machinery needed.

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

### Phase 2: ChapterIngestionComplete Event + Delta Packet

**Scope:** Chapter-level consolidation handlers emit a new event with delta packet. Book-level handlers become book-scoped listeners (not per-job DAG stages).

**Changes:**
- New `ChapterIngestionComplete` event class
- Chapter-level handlers publish the event on successful completion (after the existing `StageCompletedEvent`)
- `ChapterIndividual`, `ChapterLocation`, etc. repositories gain delta-focused query methods (or the packet is constructed from in-memory data already available in the handler)
- Book-level stages removed from per-job DAG (`StageDag.java`)
- New `BookConsolidationCoordinator` listens for `ChapterIngestionComplete`

### Phase 3: Incremental Merge

**Scope:** Replace `replaceBookIndividuals()` delete+rebuild with incremental merge in each lane. Claim system removed.

**Changes per lane:**
- New `mergeBookIndividuals(bookId, deltaPacket.individuals)` — always-incremental (admin rebuild = merge from empty)
- Existing `replaceBookIndividuals()` → admin rebuild endpoint (deletes all, replays incremental merge per chapter in order)
- `BookIndividualGraphRepository` gains: `findByBookId()` (existing nodes with name keys), `mergeAliasesIntoBookIndividual()`, `linkChapterIndividualToBookIndividual()` (already exists)
- Delete `BookConsolidationClaimService`, `BookConsolidationClaim`, `BookConsolidationClaimRepository`, `BookConsolidationClaimUnavailableException`
- Remove claim constraint from `Neo4jSchemaInitializer`

## Scope Boundaries

**In scope:**
- `ChapterIngestionComplete` event with delta packet
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

## Known Constraints / Prior Findings

1. **ConsolidationEngine supports incremental addition.** The algorithm is an online connected-components processor. The full-map rebuild (lines 82-92 in `ConsolidationEngine.java`) is the only blocker — remove it, persist cluster state, and the engine is incremental. No Union-Find/DSU required; the existing `Map<String, Integer>` key→cluster mapping is sufficient when cluster state is preserved across invocations.

2. **Concurrent chapter processing is architecturally feasible.** Scene detection is fully chapter-local (reads only the chapter's text). Cross-chapter `NEXT_IN_READING_ORDER` edges are created via idempotent `MERGE` Cypher operations as post-processing, after scene persistence. AI-determined semantic `TEMPORAL` edges are within-chapter only. No timeline constraint blocks concurrent SCENE_SEGMENTATION, CHUNKING, EMBEDDING, or CHAPTER_*_CONSOLIDATION across different chapters.

3. **Chapter and book consolidation are algorithmically identical.** Both use `ConsolidationEngine.cluster()` (connected components on shared NameKeys). Both use full delete+recompute. The claim lock, `@Retryable`, and separate `PersistenceService` at the book level are all reactions to the full-rebuild concurrency problem — not structural differences between chapter and book scope.

4. **The claim system exists solely because `deleteByBookId` + `saveAll` cannot overlap safely.** If book consolidation becomes incremental (merge-only, no deletes), the claim system disappears naturally. No replacement mechanism needed — incremental writes don't conflict.

5. **The `scene:chapter :: chapter:book` analogy holds.** Both are the same structural pattern (nested entity clustering over a progressively expanding scope). The temporal difference (scenes known upfront, chapters arrive over time) is handled by the event-driven batching design — the book coordinator doesn't wait for "all chapters done," it processes each batch incrementally.

6. **Neo4j write patterns inform the design.** The current bulk-delete pattern is a Neo4j convenience (a single `DELETE WHERE chapterId=X` Cypher call is cheap and simple). Targeted link deletion on affected clusters only is more complex in Neo4j's relationship model (no reverse-index on relationship properties). The incremental design must account for this by avoiding deletes entirely — only MERGE and CREATE operations on individual nodes and relationships.

## Open Questions

1. **Batching / debounce:** ✓ Resolved — neither needed. With incremental merge, there is no concurrent corruption risk (no `deleteByBookId`). Each `ChapterIngestionComplete` event triggers immediate processing. Chapters take minutes between completions (LLM scene detection + consolidation), so rapid-fire stacking is not a real scenario. Serial per-lane ordering from single-threaded event dispatch is sufficient. The coordinator is a simple listener, not a buffered batcher.

2. **INGESTION_COMPLETE scope:** ✓ Resolved. Chapter-scope terminal only. Chapter completion has zero awareness of book-scope events. Chapter's INGESTION_COMPLETE depends only on chapter-level stages. Book coordinator completion is independent.

3. **Cross-lane dependencies:** ✓ Resolved. No book-level lane depends on another's output. All 5 lanes process independently and in parallel.

4. **Event ordering determinism:** ✓ Resolved. Incremental merge produces the same result as full rebuild as long as batches enforce encounter order (global ordering by `chapterNumber + entityIndex`). This is a mechanical constraint, not an algorithmic limitation.

5. **Efficient BookIndividual lookup:** For incremental merge, incoming ChapterIndividual entities need to find matching BookIndividual nodes by name key. Options:
   - Load all existing `BookIndividual` nodes per book on each merge (O(B) read, B = existing book entities). For a book with 500 BookIndividuals, this is trivial.
   - Index lookup via Neo4j text index on `BookIndividual.normalizedName` (O(1) per key). More efficient but adds index maintenance.
   - In-memory cache (rebuild on cold start from existing BookIndividual nodes — no separate persisted state needed).
   
   Recommendation: load existing BookIndividuals in a single Cypher query, build the key→cluster map in memory per merge invocation. No persistent cluster state — the BookIndividual nodes themselves are the source of truth. If a cold start occurs, the map is rebuilt from scratch (same as the first incremental merge after startup).

6. **Claim system removal sequencing:** Claim removal belongs in Phase 3 (incremental merge). Phase 2 decouples events and introduces the coordinator, but still uses `replaceBookIndividuals()` internally — which still needs the claim to prevent concurrent full-rebuild corruption. Phases 2 and 3 can ship together for a clean cut, or Phase 2 ships first with the claim moved inside the coordinator, then Phase 3 removes it. Either path works; shipping together is simpler (no transitional code).

7. **ConsolidationEngine API surface — tradeoff analysis:**

   **Dual-mode** (batch `cluster()` + incremental `mergeIntoExisting()`):
   - Two code paths in the engine, two sets of tests
   - Admin rebuild uses batch mode (matching current behavior exactly)
   - Day-to-day uses incremental mode
   - Risk: batch mode drifts from incremental, producing different results for same data
   
   **Always-incremental** (only `mergeIntoExisting()`):
   - One code path, one set of tests
   - Admin rebuild: delete all BookIndividuals, replay `mergeIntoExisting()` for each ChapterIndividual in `chapterNumber` order
   - Simpler mental model: "merge is always append-only, full rebuild is merge from empty"
   - No risk of batch-mode drift
   - Slightly slower for admin rebuild (O(N) individual merges vs O(K) batch cluster), but admin rebuild is rare and book-scale data is small
   
   **Recommendation:** Always-incremental. One code path, no dual-mode regression risk, admin rebuild performance is irrelevant at this data scale.

## Related

- `docs/planning/2026-05-29T2308_code-walkthrough-issues.md` — ConsolidationEngine restoration (completed)
- `docs/archive/planning/2026-05-27T0015_unified-entity-consolidation.md` — Original consolidation design
- `docs/brainstorm/architecture/2026-05-11T2027_orchestration-domain-separation.md` — Orchestration/domain separation exploration
- `lorevault-core/src/main/java/com/lorevault/api/orchestration/consolidation/ConsolidationEngine.java` — Clustering algorithm (online connected-components; full-map rebuild at lines 82-92 is the only blocker to incrementality)
- `lorevault-core/src/main/java/com/lorevault/api/graph/individual/consolidation/book/BookIndividualPersistenceService.java` — Current full rebuild (`deleteByBookId + saveAll`)
- `lorevault-core/src/main/java/com/lorevault/api/graph/location/consolidation/book/BookConsolidationClaimService.java` — Claim mutex to be obsoleted by incremental merge
- `lorevault-core/src/main/java/com/lorevault/api/orchestration/pipeline/StageDag.java` — Per-job DAG containing book-level stages (to be decoupled)
- `lorevault-core/src/main/java/com/lorevault/api/graph/timeline/DefaultTemporalEdgeService.java` — Cross-chapter scene linking (idempotent MERGE, safe for concurrent chapter processing)