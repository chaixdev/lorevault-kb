# Deep Review: Object and Collective Entity Lanes

## Summary

This branch/worktree adds Object and Collective extraction evidence, chapter-level resolution, book-level reduction, command rerun endpoints, schema/index updates, and completion fan-in wiring. The implementation follows the existing lane shape and has good focused coverage for happy paths, grouping behavior, controller validation, schema labels, and the Collective claim-exhaustion regression that was already fixed once. However, the review found merge-blocking reliability defects in the book-level reducers: both Object and Collective can permanently delete existing derived book aggregates before replacement data is committed, and claim contention is not modeled safely across async fan-in. Verdict: 🔁 **Request Changes**.

## Findings

### HIGH-1 — Object book rebuild can permanently delete existing aggregates on mid-run failure
**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/object/BookObjectPersistenceService.java`, line 21  
**Problem:** `deleteByBookId()` runs in `REQUIRES_NEW`, and `BookObjectReductionService.resolveBook()` calls it before `saveAndLinkBookObjects()`. If `saveAll`, `linkBookToObject`, or `linkChapterObjectsToBookObject` fails after the delete commits, the old `BookObject` graph is gone and the replacement graph is incomplete or absent. That turns a transient Neo4j/write error into durable derived-data loss.  
**Fix:** Make delete + save + link one atomic rebuild transaction, or stage the replacement graph first and swap only after the replacement is fully persisted. A minimal direction is to remove the independently committed delete and expose a single transactional rebuild method:

```java
@Transactional
public List<BookObject> replaceBookObjects(
        UUID bookId,
        List<BookObject> bookObjects,
        List<List<UUID>> chapterObjectIdsByBookObject
) {
    bookObjectRepository.deleteByBookId(bookId);
    List<BookObject> saved = new ArrayList<>(bookObjectRepository.saveAll(bookObjects));
    for (int i = 0; i < saved.size(); i++) {
        bookObjectRepository.linkBookToObject(bookId, saved.get(i).id());
        bookObjectRepository.linkChapterObjectsToBookObject(chapterObjectIdsByBookObject.get(i), saved.get(i).id());
    }
    return saved;
}
```

### HIGH-2 — Collective book rebuild has the same split-transaction data-loss path
**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/collective/BookCollectivePersistenceService.java`, line 21  
**Problem:** The Collective reducer repeats the Object reducer’s split rebuild: `deleteByBookId()` commits in `REQUIRES_NEW`, then `saveAndLinkBookCollectives()` runs later. Any failure after the delete leaves prior `BookCollective` aggregates permanently removed, with the claim released in `finally`.  
**Fix:** Apply the same atomic rebuild or staging/swap approach as HIGH-1 for `BookCollective`. The old aggregate graph must survive unless the replacement graph commits successfully.

### HIGH-3 — Object claim contention can satisfy fan-in without durable book reduction
**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/completion/IngestionCompletionCoordinator.java`, line 290  
**Problem:** `completeIfReady()` treats `BookObjectsReducedEvent` as a satisfied branch by event arrival only. `BookObjectReductionService.resolveBook()` returns `success=false` when `BookReductionClaimService.tryAcquireClaimWithRetry(...)` is exhausted, but `BookObjectReductionHandler` still publishes `BookObjectsReducedEvent`. Under same-book concurrent chapter ingestion, a job can complete even though its Object book reduction did not run; the other in-flight reducer may not include this chapter’s just-created chapter objects depending on timing.  
**Fix:** Do not publish `BookObjectsReducedEvent` for claim-exhaustion skips, or make the coordinator count only semantically completed branch events. Prefer a retryable typed claim-contention outcome or lane-scoped queued claim so the affected `(jobId, chapterId)` eventually receives a real completed reduction event.

### HIGH-4 — Collective claim contention is a normal concurrency case but fails the job as non-retryable
**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/collective/BookCollectiveReductionService.java`, line 46  
**Problem:** Collective claim exhaustion now throws `IllegalStateException`, and `BookCollectiveReductionHandler` passes `e -> false` to `PipelineStageSupport`, so the pipeline marks normal same-book claim contention as a terminal non-retryable ingestion failure. This avoids the false fan-in completion defect, but it still models expected lock contention as an unrecoverable defect.  
**Fix:** Introduce a typed claim-contention exception or structured result with explicit retryability, and handle it so the stage is retried/requeued instead of either completing falsely or failing terminally. If the shared claim is meant to serialize per lane, consider extending the claim key to `(bookId, lane)` and making claim exhaustion a retryable stage failure.

### MED-1 — `firstSeenChapterId` is deterministic but not actually first-seen chronology
**Severity:** 🟡 MEDIUM  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/object/BookObjectReductionService.java`, line 50  
**Problem:** Object and Collective book reducers select representative/first-seen metadata from sort order (`normalizedName`, `displayName`, `chapterId`, `id`), not narrative or chapter chronology. The stored `firstSeenChapterId` is therefore deterministic but arbitrary.  
**Fix:** Sort by a real chronology field before selecting the representative, such as book chapter order plus scene/extraction order, or rename the field to reflect deterministic representative selection rather than first-seen semantics.

### MED-2 — Mention records keep caller-owned alias lists
**Severity:** 🟡 MEDIUM  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/ObjectPersistenceService.java`, line 52  
**Problem:** `ObjectMention` and `CollectiveMention` are immutable records only shallowly; the services pass `extracted.aliases()` directly into the record. If that source list is mutable, later mutation can change the object being saved or observed in tests.  
**Fix:** Defensively copy aliases before constructing mentions, preserving `null` only if the repository contract requires it. For example: `List<String> aliases = extracted.aliases() == null ? List.of() : List.copyOf(extracted.aliases());`.

### LOW-1 — New command response DTOs are mutable JavaBeans instead of records
**Severity:** 🟢 LOW  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/BookObjectResolutionResponse.java`, line 5  
**Problem:** The four new command response DTOs use mutable fields and no-arg constructors even though the corresponding result types are immutable records. This is a consistency and accidental-mutation nit, not a functional blocker.  
**Fix:** Convert `BookObjectResolutionResponse`, `ChapterObjectResolutionResponse`, `BookCollectiveResolutionResponse`, and `ChapterCollectiveResolutionResponse` to records.

## Priority Action Table

| ID | Severity | File | Description | Must Fix Before Merge? |
|----|----------|------|-------------|------------------------|
| HIGH-1 | 🟠 HIGH | `BookObjectPersistenceService.java` | Object book rebuild deletes old graph in a committed transaction before replacement succeeds. | Yes |
| HIGH-2 | 🟠 HIGH | `BookCollectivePersistenceService.java` | Collective book rebuild repeats the same split-transaction data-loss path. | Yes |
| HIGH-3 | 🟠 HIGH | `IngestionCompletionCoordinator.java` | Object `processed=false` reduced events can satisfy completion fan-in. | Yes |
| HIGH-4 | 🟠 HIGH | `BookCollectiveReductionService.java` | Collective claim contention fails ingestion terminally instead of retrying/requeuing. | Yes |
| MED-1 | 🟡 MEDIUM | `BookObjectReductionService.java` | `firstSeenChapterId` is based on arbitrary deterministic sort order. | Recommended |
| MED-2 | 🟡 MEDIUM | `ObjectPersistenceService.java` | Mention aliases are not defensively copied. | Recommended |
| LOW-1 | 🟢 LOW | `BookObjectResolutionResponse.java` | New command response DTOs are mutable instead of records. | No |

## Test Gaps

- ⚠️ Add an Object reducer failure-path test where old `BookObject` data exists, the rebuild delete starts, `saveAndLinkBookObjects()` throws, and the old graph is verified to remain intact.
- ⚠️ Add a Collective reducer failure-path test with the same rollback/preservation assertion for existing `BookCollective` data.
- ⚠️ Add fan-in coverage proving `BookObjectsReducedEvent(processed=false, ...)` does not complete ingestion for that `(jobId, correlationId, chapterId)`.
- ⚠️ Add same-book contention coverage proving Object and Collective reductions either retry/requeue or emit a typed retryable failure, but never false-complete and never terminal-fail normal lock contention.
- Add chronology coverage for `firstSeenChapterId` once the intended ordering source is defined.

## Positive Notes

The lane shape is consistent with the established entity ladder, and the implementation includes focused tests across extraction persistence, chapter grouping, book grouping, handlers, controllers, schema labels, and fan-in. Concept is correctly deferred rather than partially introduced into persistence or completion semantics.
