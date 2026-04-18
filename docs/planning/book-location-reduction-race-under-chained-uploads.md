# Book-location reduction can fail under chained uploads

**Status:** PARKED

## Summary

Book-level location reduction can fail during realistic multi-chapter ingest flows with a Neo4j uniqueness violation on `BookLocation(bookId, normalizedName)`.

The strongest current finding is that repeated async book-reduction runs for the same book are using a destructive rebuild pattern that is not robust under overlapping execution.

## Problem

When multiple chapters for the same book are processed in close succession, `BookLocationReductionHandler` can fail with a `ConstraintValidationFailed` / `DataIntegrityViolationException` for an already-existing `BookLocation` scoped by the same `bookId` and `normalizedName`.

This interrupts the happy-path ingest flow and weakens confidence in chained chapter uploads as a realistic operator workflow.

## Product Context

- Operators can hit this while doing normal “queue all for processing” chapter uploads for the same book.
- The failure appears after useful upstream work has already completed, so it creates frustrating partial-success behavior.
- It makes bulk or chained book ingestion feel unreliable even when the content itself is valid.

## Technical Context

The observed failure happened in:

- `lorevault-api/src/main/java/com/lorevault/api/ingestion/BookLocationReductionHandler.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/BookLocationReductionService.java`
- `lorevault-api/src/main/java/com/lorevault/api/content/BookLocationGraphRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/config/Neo4jSchemaInitializer.java`

Current write path:

1. chapter-level location resolution publishes `ChapterLocationsResolvedEvent`
2. `BookLocationReductionHandler` handles the event asynchronously
3. `BookLocationReductionService.resolveBook(bookId)` loads all chapter locations for the book
4. existing book-level locations are deleted with `deleteByBookId(bookId)`
5. a fresh set of `BookLocation` nodes is built in memory
6. `saveAll(...)` persists the new nodes
7. relationships are re-linked with repository `MERGE` queries

The database currently enforces a uniqueness constraint on:

- `BookLocation(bookId, normalizedName)`

That constraint is behaving correctly and is surfacing duplicate create attempts.

## Scope

- Preserve the UAT findings for the book-location reduction failure.
- Capture the currently strongest explanation for why chained uploads can trigger it.
- Keep enough context to resume fix design later without repeating the same investigation.
- Include pragmatic mitigation directions that should be considered during implementation.

## Out of Scope

- Choosing the final implementation strategy now
- Refactoring all reduction flows in the repository immediately
- Redesigning all ingestion concurrency behavior beyond what is needed for this issue
- Canonicalizing the eventual fix into a pattern or ADR before implementation lands

## Known Constraints / Prior Findings

### Observed UAT failure

- During chained chapter uploads for the same book, book-level location reduction failed with:
  - `DataIntegrityViolationException`
  - `Neo.ClientError.Schema.ConstraintValidationFailed`
  - duplicate `BookLocation` scope on `(bookId, normalizedName)`
- Example offending key observed in UAT:
  - `bookId = 78f3bf41-3b29-4bf8-b544-4eb1d52a6e39`
  - `normalizedName = "hydroponics bay"`

### Strongest current diagnosis

The current implementation uses a delete-and-rebuild write model for `BookLocation` nodes:

- delete prior book-level nodes for a book
- rebuild a fresh reduced set
- insert fresh `BookLocation` nodes with new UUIDs

This is vulnerable when multiple reductions for the same book happen close together.

### Concurrency / idempotency findings

- `BookLocationReductionHandler` is `@Async` and runs on every chapter-resolution event.
- `BookLocationReductionService` uses an in-memory `ReentrantLock` per `bookId`.
- That lock only coordinates calls inside one JVM / one service instance.
- Node creation uses repository `saveAll(...)`, not a database-level upsert/merge keyed by `(bookId, normalizedName)`.
- Relationship writes use `MERGE`, but node creation itself is still rebuild-style.

### Why the uniqueness error is meaningful

- The Neo4j uniqueness constraint is not the bug.
- It is correctly surfacing a duplicate-create attempt for the same logical book-level location.

### Adjacent risk

- `BookIndividualReductionService` follows a very similar delete-and-rebuild pattern.
- The exact failure was observed for locations, but the reduction style may represent a broader bug class.

### Test coverage gap

- Unit coverage exists for handler wiring and mocked reduction-service behavior.
- There is no integration coverage for repeated/chained reductions against a real Neo4j database.
- There is no current test that verifies idempotent or concurrency-safe `BookLocation` persistence under realistic chained uploads.

### Pragmatic mitigation direction already surfaced

- A practical first fix may be to serialize chained upload follow-up processing so book-level reductions for the same workflow do not overlap.
- One concrete option to evaluate is reducing async concurrency for this path (for example, effectively single-threading the relevant handler executor).
- This should be treated as a candidate mitigation, not yet an accepted solution.

## Open Questions

- Is the dominant failure mode overlapping reductions inside one app process, across multiple app instances, or both?
- Can the clustering logic itself ever produce duplicate `normalizedName` values in one rebuild batch?
- Should the first implementation target be workflow serialization, reduction-service idempotency, or both?
- Should book-level reductions be serialized globally, per book, or only within chained ingest workflows?
- Should `BookIndividualReductionService` be addressed in the same change or tracked separately?

## Success Criteria

- Chained uploads for the same book can complete without `BookLocation(bookId, normalizedName)` uniqueness failures.
- Re-running book-level location reduction for the same book is operationally safe under expected ingest concurrency.
- The fix is validated against a realistic multi-chapter flow, not only mocked unit tests.
- The resulting behavior is easy for operators to reason about during bulk/chained uploads.

## Links

- Related implementation files:
  - `../../lorevault-api/src/main/java/com/lorevault/api/ingestion/BookLocationReductionHandler.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/ingestion/BookLocationReductionService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/content/BookLocationGraphRepository.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/ingestion/BookIndividualReductionService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/config/Neo4jSchemaInitializer.java`
- Related planning item style reference:
  - `./stuck-ingestion-status.md`
