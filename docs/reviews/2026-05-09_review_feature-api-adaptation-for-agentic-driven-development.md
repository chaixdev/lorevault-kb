# Deep Code Review: Agent-Driven Step Execution API

**Branch:** `feature/api-adaptation-for-agentic-driven-development`
**Date:** 2026-05-09
**Reviewer:** Principal Engineer (automated deep review)
**Scope:** All changes on this branch — 13 modified files, 24 new files, 1 doc update

---

## Section 1 — Summary

This branch adds a synchronous step-execution API that allows an external agent to invoke individual pipeline steps directly, bypassing the async event pipeline. The implementation is well-structured: `*Operation` interfaces extract core logic from handlers, `StepResult` provides a uniform return type, `StepEventMapper` bridges step keys to domain events, and `StepExecutionResponse` gives agents a consistent response shape. The separation of `execute()` (pure logic) from event emission (caller's responsibility) is a clean design.

**Most important findings:**

1. **CRITICAL: Spring mapping conflict** — 4 old `Book*ResolutionCommandController` files map the same `/resolve-*` URLs as the new `BookReductionRedirectController`, causing `AmbiguousHandlerException` at startup.
2. **CRITICAL: Fan-in coordinator memory leak** — REST-driven book-scoped events with null `chapterId` create orphaned `CompletionState` entries that are never evicted.
3. **HIGH: `PipelineStageSupport.runStage()` bypasses null-jobId guard** — direct call to `ingestionJobService.updateJobStatus()` on line 104 skips the null check, risking NPE for ad-hoc step execution.
4. **HIGH: Book reduction handlers classify ALL exceptions as retryable** — permanent errors like constraint violations cause infinite retry loops.
5. **HIGH: `StepExecutionResponse.step` field violates its Javadoc contract** — returns SCREAMING_SNAKE_CASE instead of kebab-case, breaking agent routing.

**Verdict:** 🔁 **Request Changes** — must fix CRITICAL and HIGH items before merging.

---

## Section 2 — Findings

### CRIT-1 — Spring mapping conflict: old controllers shadow redirect controller

**Severity:** 🔴 CRITICAL
**Files:** `BookIndividualResolutionCommandController.java`, `BookCollectiveResolutionCommandController.java`, `BookLocationResolutionCommandController.java`, `BookObjectResolutionCommandController.java`, `BookReductionRedirectController.java`
**Problem:** Four old `Book*ResolutionCommandController` classes still map `POST /books/{bookId}/resolve-individuals|collectives|locations|objects` — the exact same URLs as the new `BookReductionRedirectController` (which provides 307 redirects to `/reduce-*`). Spring will throw `IllegalStateException: Ambiguous handler methods mapped` at startup. The application will not start.
**Fix:** Delete all 4 old `Book*ResolutionCommandController.java` files and their 8 orphaned response DTOs (`Book*ResolutionResponse.java`, `Chapter*ResolutionResponse.java`). The redirect controller handles old URLs; the new `Book*ReductionCommandController` handles new URLs.

---

### CRIT-2 — Fan-in coordinator memory leak from REST-path events with null chapterId

**Severity:** 🔴 CRITICAL
**File:** `IngestionCompletionCoordinator.java` (unchanged, but affected by new code paths)
**Problem:** When `StepEventMapper` emits book-scoped reduction events (`BookIndividualsReducedEvent`, etc.) from the REST `fireEvents=true` path, those events are constructed with `chapterId = null` (see `StepEventMapper.java` lines 244, 257, 270, 283). The coordinator's per-branch handlers create a `CompletionKey(jobId, correlationId, null)`. Since only a single branch arrives for these standalone REST calls (never all 7), the `completionStates` entry never satisfies `completeIfReady` and never gets evicted. The `ConcurrentHashMap` grows unbounded. If `jobId` is also null (ad hoc debugging calls), all such events share the same key `(null, null, null)`, making the leak worse.
**Fix:** Two layers: (1) In `StepEventMapper`, skip publishing book-scoped events to the fan-in coordinator when called from the REST path — or add a guard in the coordinator to skip registration when `chapterId == null`. (2) Defense in depth: add a scheduled eviction task that removes `CompletionState` entries older than a configurable TTL (e.g., 30 minutes).

---

### HIGH-1 — `PipelineStageSupport.runStage()` bypasses null-safe `updateJobStatus()` wrapper

**Severity:** 🟠 HIGH
**File:** `PipelineStageSupport.java`, line 104
**Problem:** The convenience overload `updateJobStatus(UUID, IngestionStatus, String)` at line 41 has a null-guard (`if (jobId == null) return;`), but `runStage()` at line 104 calls `ingestionJobService.updateJobStatus(jobId, ...)` directly, bypassing the guard. If `jobId` is null (ad hoc step execution), this will NPE or create a `StatusRecord` with null FK. Currently `runStage()` is only called from `ChapterEventEmbeddingHandler` with a guaranteed non-null `jobId`, but the asymmetry is a latent bug.
**Fix:** Replace line 104 with `this.updateJobStatus(jobId, IngestionStatus.FAILED, stage + " failed: " + safeMessage(e), failure.toProperties());` to route through the null-guard.

---

### HIGH-2 — Book reduction handlers classify ALL exceptions as retryable

**Severity:** 🟠 HIGH
**Files:** `BookIndividualReductionHandler.java:131-134`, `BookCollectiveReductionHandler.java:120-124`, `BookLocationReductionHandler.java:116-120`, `BookObjectReductionHandler.java:117-121`
**Problem:** All 4 book reduction handlers unconditionally return `StepResult.retryableFailure(...)` in their catch blocks. Permanent errors — Neo4j constraint violations, `IllegalArgumentException`, invalid UUID — are classified as retryable. An agent retrying will loop forever on non-transient failures.
**Fix:** Add retryable classification similar to `SceneDetectionHandler.isRetryableError()`. At minimum, classify `ResourceAccessException`, `HttpClientErrorException.TooManyRequests`, `HttpServerErrorException`, and LLM-specific errors as retryable; everything else as non-retryable.

---

### HIGH-3 — `StepExecutionResponse.step` violates kebab-case Javadoc contract

**Severity:** 🟠 HIGH
**File:** `StepExecutionResponse.java`, line 38
**Problem:** The Javadoc states the `step` field is a "kebab-case step identifier (e.g. `detect-scenes`)", but `StepExecutionResponse.from()` populates it from `result.stepName()`, which returns SCREAMING_SNAKE_CASE values like `"SCENE_DETECTION"`, `"CHAPTER_INDIVIDUAL_RESOLUTION"`. Agents consuming the API to route subsequent requests will get mismatched identifiers — they cannot match `"SCENE_DETECTION"` to the step catalog's `"detect-scenes"`.
**Fix:** Add a `StepKey` parameter to `StepExecutionResponse.from()` and use `key.toUrlSegment()` for the `step` field. Each controller already knows its `StepKey`.

---

### HIGH-4 — `ChunkingHandler` uses `EMBEDDING_CHUNKS` status for chunking work

**Severity:** 🟠 HIGH
**File:** `ChunkingHandler.java`, lines 100 and 132
**Problem:** Both status updates in `ChunkingHandler.execute()` use `IngestionStatus.EMBEDDING_CHUNKS`. Chunking is a distinct stage from embedding — using the same status value makes job progress indistinguishable between chunking and embedding phases.
**Fix:** Add a `CHUNKING` status to `IngestionStatus` (or use an existing appropriate value) and update both calls.

---

### HIGH-5 — Missing `@Valid` on `PrepareChapterRequest` — Bean Validation annotations are dead code

**Severity:** 🟠 HIGH
**File:** `PrepareCommandController.java`, line 48
**Problem:** The controller method `prepareChapter(@RequestBody PrepareChapterRequest request)` lacks `@Valid`. Spring never activates the Jakarta Bean Validation provider, so `@NotNull` on `bookId`/`chapterNumber` and `@NotBlank` on `chapterTitle`/`chapterText` are dead code. A null `bookId` or blank `chapterText` reaches the service layer unvalidated.
**Fix:** Add `@Valid` before `@RequestBody`: `public ResponseEntity<?> prepareChapter(@Valid @RequestBody PrepareChapterRequest request)`.

---

### HIGH-6 — No `@Size(max=...)` on `chapterText` — no input size limit

**Severity:** 🟠 HIGH
**File:** `PrepareChapterRequest.java`, line 33
**Problem:** The `chapterText` field has only `@NotBlank` — no `@Size(max=...)` constraint. Combined with the missing `@Valid`, there is zero limit on submitted chapter text size. A malicious client can submit multi-megabyte text, causing memory exhaustion and unbounded LLM API costs.
**Fix:** Add `@Size(max = 500_000)` on `chapterText` and `@Size(max = 500)` on `chapterTitle`. Pair with a Tomcat max-request-size limit.

---

### MED-1 — Resolution handlers return `success=true` for skipped results

**Severity:** 🟡 MEDIUM
**Files:** All 4 chapter resolution handlers + 4 book reduction handlers
**Problem:** When the underlying service reports `response.success() == false` (the "skipped" / "nothing to process" case), the handler still returns `StepResult.success(true, ...)`. Callers cannot programmatically distinguish "successfully processed 0 entities" from "step was skipped due to precondition failure".
**Fix:** Consider adding a `"skipped": 1` count key to `StepResult` for the skipped case, or return `success=false` with `retryable=false` for definitive skip conditions.

---

### MED-2 — `PrepareCommandController` catches only `IllegalArgumentException`

**Severity:** 🟡 MEDIUM
**File:** `PrepareCommandController.java`, lines 66-73
**Problem:** The try/catch only handles `IllegalArgumentException`. Any other runtime exception (Neo4j `DataAccessException`, NPE from null fields) propagates uncaught to a Spring 500 error with no structured `ErrorResponse`.
**Fix:** Add a catch for `Exception` as fallback, returning a structured error response.

---

### MED-3 — `ChapterEventResolutionHandler` has no retryable classification

**Severity:** 🟡 MEDIUM
**File:** `ChapterEventResolutionHandler.java`, lines 177-183
**Problem:** Unlike `SceneDetectionHandler` and `EmbeddingHandler` which implement `isRetryableError()`, the `ChapterEventResolutionHandler` catch block always returns `StepResult.failure()` (non-retryable). LLM API failures, rate limits, and transient infrastructure errors during event coreference are not classified as retryable.
**Fix:** Add retryable classification for known transient errors similar to `SceneDetectionHandler.isRetryableError()`.

---

### MED-4 — Chapter resolution handlers never call `updateJobStatus` on the success path

**Severity:** 🟡 MEDIUM
**Files:** `ChapterIndividualResolutionHandler.java`, `ChapterCollectiveResolutionHandler.java`, `ChapterLocationResolutionHandler.java`, `ChapterObjectResolutionHandler.java`
**Problem:** These 4 handlers' `execute()` methods never call `stageSupport.updateJobStatus()` to record progress. The job stays at whatever status the previous step set, making SSE-observable progress invisible for these stages.
**Fix:** Add `stageSupport.updateJobStatus(jobId, ...)` calls at the start and end of each handler's `execute()` method, consistent with other handlers.

---

### MED-5 — `StepEventMapper` passes null `chapterId` to book-scoped reduction events

**Severity:** 🟡 MEDIUM
**File:** `StepEventMapper.java`, lines 244, 257, 270, 283
**Problem:** All four `publishBook*ReducedEvent` methods construct the event with `null` as the `chapterId` parameter. This is the root cause of CRIT-2 (fan-in coordinator memory leak). While book-scoped reduction steps legitimately operate on a book, not a chapter, the `IngestionEvent` base class requires `chapterId` for correlation.
**Fix:** Either (a) look up a representative chapter ID from the book, or (b) skip publishing to fan-in coordinator listeners for book-scoped events from the REST path, or (c) add a `bookId`-only constructor to the event classes.

---

### MED-6 — `EmbeddingHandler.execute()` lacks idempotency check

**Severity:** 🟡 MEDIUM
**File:** `EmbeddingHandler.java`, lines 96-126
**Problem:** Both `SceneDetectionHandler.execute()` and `ChunkingHandler.execute()` check for existing work and return early with a "Skipped" result. `EmbeddingHandler.execute()` has no such check — it calls `embeddingService.generateEmbeddingsForChapter()` unconditionally. Repeated REST calls to `/embed` re-generate embeddings wastefully.
**Fix:** Add an idempotency check: query whether embeddings already exist for the chapter's chunks, and skip if so.

---

### MED-7 — `@Data` on Neo4j entity classes without `@ToString.Exclude` on relationship collections

**Severity:** 🟡 MEDIUM
**Files:** `Chapter.java`, `Scene.java`, `Chunk.java` (pre-existing, not changed in this branch)
**Problem:** `@Data` generates `equals()`, `hashCode()`, and `toString()` including all fields. For Neo4j entities with `@Relationship` collections, `toString()` triggers lazy-loading N+1 queries and potential `StackOverflowError` from circular references (`Chapter` → `Scene` → `Chapter`).
**Fix:** Replace `@Data` with `@Getter` + `@Setter` on entity classes. Add `@ToString.Exclude` on `@Relationship` fields.

---

### MED-8 — StepExecutionCommandController lacks entity existence checks

**Severity:** 🟡 MEDIUM
**File:** `StepExecutionCommandController.java`, lines 35-221
**Problem:** The 4 step-execution endpoints (`detect-scenes`, `chunk`, `embed`, `resolve-events`) do not verify chapter existence before invoking the operation handler. A non-existent chapter UUID results in `StepResult.failure()` returned as HTTP 200, inconsistent with the resolution controllers which return HTTP 404.
**Fix:** Add `chapterGraphRepository.findById(chapterUuid).isEmpty()` check before calling `execute()`, returning `ResponseEntity.notFound().build()` if absent.

---

### LOW-1 — `Collectors.toMap` NPE risk from null scene eventId

**Severity:** 🟢 LOW
**File:** `SceneDetectionHandler.java`, lines 203-207
**Problem:** `Collectors.toMap()` does not allow null values. If a `Scene` has a non-null `sceneIndex` but null `eventId`, this throws NPE.
**Fix:** Add `.filter(scene -> scene.getEventId() != null)` before the `.collect()`.

---

### LOW-2 — `BookReductionRedirectController` loses query parameters on redirect

**Severity:** 🟢 LOW
**File:** `BookReductionRedirectController.java`, lines 24-48
**Problem:** The redirect URLs don't include `jobId` or `fireEvents` query parameters. The downstream controller gets defaults (`fireEvents=false`, `jobId=null`).
**Fix:** Propagate query parameters to the redirect URL, or document that clients should call `/reduce-*` directly.

---

### LOW-3 — UUID-from-String parsing duplicated 12× across controllers

**Severity:** 🟢 LOW
**Files:** All 12 controller methods in `StepExecutionCommandController`, `Chapter*ResolutionCommandController`, `Book*ReductionCommandController`
**Problem:** Every controller method duplicates the same 20-line UUID parsing + ErrorResponse pattern. ~240 lines of boilerplate.
**Fix:** Extract a shared `StepControllerSupport` utility with `parseChapterId()`, `parseBookId()`, `parseJobId()` methods.

---

### LOW-4 — `StepEventMapper` private event-publishing methods heavily duplicated

**Severity:** 🟢 LOW
**File:** `StepEventMapper.java`, lines 156-288
**Problem:** 8 private methods are structurally identical — each fetches Chapter, extracts bookId, reads count keys, builds an event, publishes, and logs. Adding a 13th step requires copy-pasting another ~25-line method.
**Fix:** Extract parameterized helpers for chapter-scoped and book-scoped event publishing.

---

### LOW-5 — Missing `correlationId` in synchronous step execution log entries

**Severity:** 🟢 LOW
**Files:** All controller files
**Problem:** The REST-driven step execution path does not generate or propagate a `correlationId`. Log entries use `[CMD]` prefix with `jobId` and `chapterId` but no `correlationId`, making cross-handler log correlation impossible.
**Fix:** Generate a UUID correlation ID at the controller level and propagate it through to handler `execute()` calls and log statements.

---

### LOW-6 — `SceneDetectionHandler` string-based retryability check is case-sensitive

**Severity:** 🟢 LOW
**File:** `SceneDetectionHandler.java`, lines 415-418
**Problem:** The fallback message-based retryability check uses case-sensitive `contains()`. LLM APIs commonly return messages with mixed case.
**Fix:** Use `message.toLowerCase().contains(...)` or rely solely on structured exception type checks.

---

### LOW-7 — `StepCatalog` as Spring `@Component` for static data

**Severity:** 🟢 LOW
**File:** `StepCatalog.java`
**Problem:** `StepCatalog` has no injected dependencies, no mutable state, and a single `all()` method. It could be a static constant or enum method.
**Fix:** Move the definitions list to `StepKey` as a static method, or document that `StepCatalog` exists for future dynamic step registration.

---

### LOW-8 — Orphaned response DTOs (8 files)

**Severity:** 🟢 LOW
**Files:** `Book*ResolutionResponse.java` (4 files), `Chapter*ResolutionResponse.java` (4 files)
**Problem:** These custom response classes were the old return types for chapter and book resolution controllers. The controllers now return `StepExecutionResponse`. These 8 classes are dead code.
**Fix:** Delete all 8 unused response classes.

---

## Section 3 — Priority Action Table

| ID | Severity | File | Description | Must Fix Before Merge? |
|----|----------|------|-------------|----------------------|
| CRIT-1 | 🔴 CRITICAL | `Book*ResolutionCommandController.java` (4 files) | Spring mapping conflict — app won't start | Yes |
| CRIT-2 | 🔴 CRITICAL | `IngestionCompletionCoordinator.java` | Memory leak from null chapterId events | Yes |
| HIGH-1 | 🟠 HIGH | `PipelineStageSupport.java:104` | `runStage()` bypasses null-jobId guard | Yes |
| HIGH-2 | 🟠 HIGH | `Book*ReductionHandler.java` (4 files) | All exceptions classified as retryable | Yes |
| HIGH-3 | 🟠 HIGH | `StepExecutionResponse.java:38` | `step` field returns SCREAMING_SNAKE_CASE, not kebab-case | Yes |
| HIGH-4 | 🟠 HIGH | `ChunkingHandler.java:100,132` | Uses `EMBEDDING_CHUNKS` status for chunking work | Yes |
| HIGH-5 | 🟠 HIGH | `PrepareCommandController.java:48` | Missing `@Valid` — Bean Validation dead code | Yes |
| HIGH-6 | 🟠 HIGH | `PrepareChapterRequest.java:33` | No `@Size(max=...)` on `chapterText` | Yes |
| MED-1 | 🟡 MEDIUM | All resolution/reduction handlers | `success=true` for skipped results | Recommended |
| MED-2 | 🟡 MEDIUM | `PrepareCommandController.java:66` | Narrow exception catching | Recommended |
| MED-3 | 🟡 MEDIUM | `ChapterEventResolutionHandler.java:177` | No retryable classification | Recommended |
| MED-4 | 🟡 MEDIUM | 4 chapter resolution handlers | Missing `updateJobStatus` on success path | Recommended |
| MED-5 | 🟡 MEDIUM | `StepEventMapper.java:244,257,270,283` | Null chapterId in book-scoped events | Recommended |
| MED-6 | 🟡 MEDIUM | `EmbeddingHandler.java:96` | No idempotency check | Recommended |
| MED-7 | 🟡 MEDIUM | `Chapter.java`, `Scene.java`, `Chunk.java` | `@Data` on Neo4j entities | Recommended |
| MED-8 | 🟡 MEDIUM | `StepExecutionCommandController.java` | No entity existence check | Recommended |
| LOW-1..8 | 🟢 LOW | Various | NPE risk, redirect params, duplication, etc. | No |

---

## Section 4 — Test Gaps

- ⚠️ **No tests for any new controller** — `StepExecutionCommandController`, `PrepareCommandController`, `StepQueryController`, `Book*ReductionCommandController`, `BookReductionRedirectController`, `Chapter*ResolutionCommandController` (modified) have zero test coverage. A `@WebMvcTest` slice test for each endpoint is needed.
- ⚠️ **No tests for `StepEventMapper`** — the event mapping logic (12 switch cases, 8 private methods) has no unit test. A wrong count key or null chapter lookup would silently produce incorrect events.
- ⚠️ **No tests for `StepCatalog`** — trivial, but the prerequisite chain definitions should be verified.
- **No test for `PipelineStageSupport.updateJobStatus(null, ...)`** — the null-jobId path was added but has no unit test verifying it skips the DB write.
- **No test for `BookReductionRedirectController`** — redirect behavior and 307 status code should be verified.
- **No test for `PrepareCommandController`** — validation, error handling, and the `IngestionService.prepareChapter()` integration need coverage.
- **No test for book reduction handlers' claim contention path** — `StepResult.retryableFailure()` is returned when `tryAcquireClaim` fails, but no test verifies this path.
- **No test for `fireEvents=true` vs `fireEvents=false`** — the core design decision (event emission controlled by caller) has no integration test verifying that events are published only when `fireEvents=true`.

---

## Section 5 — Positive Notes

The `*Operation` interface extraction is a clean separation of concerns — it decouples synchronous business logic from async event dispatch, enabling both the REST API and the event pipeline to share the same core logic. The `StepResult` record provides a uniform return type that makes the controller layer thin and consistent. The `StepKey`/`StepDefinition`/`StepCatalog` metadata model is well-designed for discoverability. The `PipelineStageSupport.updateJobStatus()` null-jobId tolerance is a thoughtful addition that enables ad-hoc debugging without job tracking. The 307 redirect approach for URL migration is correct and preserves POST semantics.