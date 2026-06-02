# Deep Code Quality Review — P4: Web & REST Layer

**Branch:** `feature/durable-ingestion-orchestration`  
**Review Date:** June 2, 2026  
**Reviewer:** @oracle × 5 parallel tracks (Logic, Data, Async, Security, Structure)  
**Files Reviewed:** 29 main source files + 11 test files + 5 supporting docs

---

## Section 1 — Summary

Review Package 4 covers 29 controller-layer files (~1,800 lines of controller code plus ~350 lines of test code) spanning ingestion commands, consolidation endpoints, job queries, library commands, and UI controllers. The web layer is functionally correct (UAT passed), but the review uncovered **6 CRITICAL** and **11 HIGH** findings. The dominant structural issue is near-identical copy-paste across 10 consolidation controllers (830 lines of 95%-duplicated boilerplate) combined with a layering violation where 16 controllers directly inject Neo4j repositories. The dominant operational risk is the complete absence of authentication on all 40+ endpoints — no Spring Security dependency exists. Additionally, pipeline tracing is compromised because `StageCompletedEvent` and `StageTriggeredEvent` deliberately omit `correlationId` despite project-wide mandates requiring it on every event class.

**Verdict:** 🔁 **Request Changes** — must address all CRITICAL and HIGH items before merging to `main`.

---

## Section 2 — Findings

---

### CRIT-1 — No authentication on any endpoint
**Severity:** 🔴 CRITICAL  
**Track:** D — Security & Observability  
**File:** `lorevault-web/pom.xml` (missing dependency) + entire `lorevault-web/src/main/java/com/lorevault/api/web/`  
**Problem:** Zero authentication or authorization on any of 40+ endpoints. `spring-boot-starter-security` is not a dependency. No `SecurityFilterChain` bean exists. Every REST command endpoint (`POST /api/command/ingest`, all step execution endpoints, library creation), every LLM-costing query endpoint (`POST /api/query/ask/*`), and every UI controller is publicly accessible with no guard.

**Source→Flow→Sink→Impact:**
- **Source:** Any HTTP client
- **Flow:** Unauthenticated POST/GET through Spring MVC dispatcher → controller method
- **Sink:** Direct invocation of LLM pipeline, Neo4j writes, and SSE streaming
- **Impact:** Unauthorized pipeline execution consuming billable LLM tokens; arbitrary data mutation; unauthenticated access to job status and graph data

**Fix:** Add `spring-boot-starter-security` dependency. Create a `SecurityFilterChain` bean that:
1. Permits `/actuator/health`, `/api/docs/**` without auth
2. Requires authentication for `/api/command/**` and `/api/query/ask/**` (LLM-costing endpoints)
3. Secures `/ui/**` since UI controllers call the same backing services
4. Configure authentication mechanism appropriate to deployment (API key, OAuth2, or basic auth)
>! deferred. authentication and IAM/RBAC will be added in a focused sprint. not to be addressed now. 
---

### CRIT-2 — 16 controllers directly inject Neo4j graph repositories (layering violation)
**Severity:** 🔴 CRITICAL  
**Track:** B — Data & Persistence  
**Files:** `CommandIngestionController.java:33`, `StepExecutionCommandController.java:33`, 10 `*ConsolidationCommandController.java` files (each at `:31`), `LibraryCommandController.java:33`, `PrepareCommandController.java:42`, `UiQueryController.java:49-51`  
**Problem:** `ChapterGraphRepository` and `BookGraphRepository` are injected directly into REST controllers and used for entity existence checks (`findById()`). This is a layering violation — the web layer should delegate to services, not query the graph database directly. If the Neo4j driver, connection pool, or schema changes, 16 controllers must be updated instead of a single service.

**Fix:** Move the `findById()` existence check into a service method (e.g., `IngestionService.chapterExists(UUID)`). Let downstream `StageOperation.execute()` handle the not-found case and return an appropriate error result that the controller maps to a 404.
>! agreed. the needed services may already exixst. in general, in the XQRS paradigm, we should have focused Command and Query classes, that only inject the services they need. controllers delegate to these Command / Query handlers, who can further delegate to services or repository if needed. Does that look like an appropriate pattern to you? overkill for our use? flawed in a way i missed? too big for a bugfixing session and should be deferred into its own planning doc?

---

### CRIT-3 — `ErrorResponseFactory` exposes internal exception messages to API clients
**Severity:** 🔴 CRITICAL  
**Tracks:** C — Async & Events + D — Security & Observability (cross-track hit)  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/response/ErrorResponseFactory.java`, lines 103–105  
**Problem:** `createIngestionServiceError(Exception cause)` exposes `cause.getMessage()` and `cause.getClass().getSimpleName()` in the HTTP response body via `.details()`. Exception messages from downstream services may contain Neo4j connection strings, internal identifiers, Cypher fragments, or AI API metadata. This leaks internal state to API clients.

**Fix:** Remove `.details("message", cause.getMessage())` and `.details("cause", cause.getClass().getSimpleName())` from the public error response. Log the full cause server-side at ERROR level. Return a sanitized, client-safe error body.
>! needs a focused project-wide pass. extract to planning document stub
---

### CRIT-4 — `StageCompletedEvent` and `StageTriggeredEvent` deliberately omit `correlationId`
**Severity:** 🔴 CRITICAL  
**Track:** C — Async & Events  
**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/signals/StageCompletedEvent.java`, lines 14–15 (javadoc); `StageTriggeredEvent.java`, lines 11–12 (javadoc)  
**Problem:** Both event classes explicitly state in their javadoc: *"Omits correlationId — jobId is sufficient for all correlation."* This directly contradicts `docs/patterns/ingestion/ingestion-pipeline.md` ("Correlation Fields" section) which mandates: *"Every ingestion event class must carry both `jobId` and `correlationId`… Neither field is optional."* It also violates `docs/rules/coding-standards.md` ("Event-Driven Pipeline"): *"Every event class must carry a correlation identifier."* Without `correlationId`, interleaved log lines from concurrent ingestions cannot be traced across async handlers and executor threads.

**Fix:** Add a `correlationId` (UUID) field to both event classes. Populate it during construction. Set `MDC.put("correlationId", correlationId)` in `StageDispatcher.dispatch()`. In `StepEventMapper`, generate a correlationId or thread it from the request. Update the javadoc on both classes to remove the "Omits correlationId" statements.
>! agreed, this is llm drift and the wrong direction. needs fixing.
---

### CRIT-5 — `BookConsolidationRedirectController` exists for backward compatibility
**Severity:** 🔴 CRITICAL  
**Track:** E — Structure & Quality  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/BookConsolidationRedirectController.java`, lines 13–16  
**Problem:** Javadoc states: *"These redirects will be maintained for one release cycle after the final rename, then removed."* Per coding standards (`code-organization-guidance.md`), backward compatibility is never the goal — any code claiming to exist for backward compatibility is a deletion signal. This controller maintains 8 legacy URL paths (`resolve-*` and `reduce-*` prefixes) across two rename generations.

**Fix:** Delete `BookConsolidationRedirectController.java` entirely. The block was already intended to be temporary. Update any clients to use the canonical `/book-consolidate-*` paths.
>! good catch. eliminate all backward compatibility code, so remove this.
---

### CRIT-6 — `UiOperatorActionsController` uses random UUIDs for `stageId` and `jobId`
**Severity:** 🔴 CRITICAL  
**Tracks:** A — Logic & Correctness + B — Data & Persistence (cross-track hit)  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiOperatorActionsController.java`, lines 35, 44, 53  
**Problem:** Consolidation actions construct `StageExecutionContext` with `UUID.randomUUID()` for both `stageId` and `jobId`. Coding standards mandate that every `@Node` entity created during pipeline execution must carry a `stageId` for provenance, cleanup, and replay (ADR-014/ADR-015). Random UUIDs mean: (1) stage-scoped cleanup won't find these nodes, (2) job status tracking is broken, and (3) replay cannot target these operations. This also bypasses the entire orchestration layer — the UI controller injects and calls consolidation services directly instead of going through `StageOperation.execute()`.

**Fix:** Delegate to the same `StageOperation` beans used by the command API controllers. Do not construct `StageExecutionContext` manually — the orchestration layer manages stage lifecycle, jobId, stageId, and event publication. The UI controller should call `stageOperation.execute(context)` where the context comes from the pipeline, not from `UUID.randomUUID()`.
>! agreed. 
---

### HIGH-1 — Hardcoded PostgreSQL password in `application.yml`
**Severity:** 🟠 HIGH  
**Track:** D — Security & Observability  
**File:** `lorevault-web/src/main/resources/application.yml`, line 71  
**Problem:** `password: lorevault_secret` is hardcoded for the catalog datasource. Contrast with Neo4j credentials (line 23) which correctly use `password: ${NEO4J_PASSWORD}` from environment.

**Fix:** Change to `password: ${CATALOG_DB_PASSWORD}` and document the env var in `.env`.
>! defer for right now.
---

### HIGH-2 — 10 consolidation controllers are near-identical copy-paste (830 lines → ~80)
**Severity:** 🟠 HIGH  
**Track:** E — Structure & Quality  
**Files:** `Chapter{Individual,Collective,Concept,Location,Object}ConsolidationCommandController.java` + `Book{Individual,Collective,Concept,Location,Object}ConsolidationCommandController.java`  
**Problem:** All 10 consolidation controllers (83 lines each, 830 total) are ~95% structurally identical. Differences are mechanically derivable from `{scope=book|chapter, entityType=object|location|concept|collective|individual}`. UUID parsing, validation, not-found checks, log lines, `StageExecutionContext` construction, `StepExecutionResponse` construction, and event publishing are identical boilerplate repeated 10×. This duplication compounds the repository-injection layering violation (CRIT-2) since all 10 inject `ChapterGraphRepository` or `BookGraphRepository` with the same TOCTOU pattern.

**Fix:** Extract a generic consolidation controller with a configuration record per type. Expected reduction: ~830 lines → ~80 lines of controller code + ~10 config records (or ~50 lines with a generic pattern).

```java
record ConsolidationConfig(
    String pathSegment, StageKey stageKey, StepKey stepKey,
    java.util.function.Function<ConsolidationOperation, StageOperation> operationExtractor
) {}
```
>! i love deleting code. lets do this. but should we go in the Functional direction or the Abstract Generics class direction? i'm leaning towards generics due to familiarity, but wonder about your functional proposal.
---

### HIGH-3 — `SseEmitter(0L)` creates unbounded connection lifetime
**Severity:** 🟠 HIGH  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/query/job/JobStatusBroadcaster.java`, line 23  
**Problem:** `new SseEmitter(0L)` creates an SSE emitter with **no timeout** (0 = never timeout in Spring). Combined with no maximum emitter count limit, abandoned or very-slow SSE connections accumulate indefinitely in the `CopyOnWriteArrayList`. A TCP-connected-but-slow client (network congestion, mobile device) passes keepalive checks and remains in the list forever, consuming memory and blocking `broadcast()`/`keepAlive()` loops.

**Fix:** Set a reasonable timeout: `new SseEmitter(180_000L)` (3 minutes). Add a configurable maximum emitter count (e.g., `maxSseConnections`) in `register()` with rejection or oldest-eviction policy.
>! yep add timeout, but increase to 5 minutes
---

### HIGH-4 — `StepEventMapper.publishCompletionEvent()` blocks HTTP thread on synchronous event dispatch
**Severity:** 🟠 HIGH  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StepEventMapper.java`, lines 54, 85  
**Problem:** `eventPublisher.publishEvent()` is called **directly on the controller's HTTP (Tomcat) thread**. Spring's default event publisher is synchronous. This means the HTTP response is blocked until every `@EventListener` of `StageCompletedEvent` has completed — including `IngestionPipelineCoordinator.onStageCompleted()` which performs Neo4j writes, fan-out evaluation, and `StageTriggeredEvent` publishing. For `fireEvents=true` calls, the HTTP response time is the sum of: step execution + coordinator's Neo4j writes + fan-out evaluation + SSE broadcast to all clients. This creates a large response-time asymmetry versus `fireEvents=false`.

**Fix:** Decouple event publication from the HTTP thread. Wrap in `CompletableFuture.runAsync(() -> eventPublisher.publishEvent(event), ingestionTaskExecutor)` in `StepEventMapper`, or make the `IngestionPipelineCoordinator.onStageCompleted` and `JobStatusBroadcaster.onStageCompleted` listeners `@Async("ingestionTaskExecutor")`. Ensure the coordinator's `setRunningConditionally` idempotency guard still works under async delivery.
>! do we need a project wide thread policy? should we create dedicated threadpools that ALL tasks have to be triaged against? or is a one-off fix enough here?
---

### HIGH-5 — `JobStatusBroadcaster.broadcast()` synchronously iterates all emitters, blocking pipeline threads
**Severity:** 🟠 HIGH  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/query/job/JobStatusBroadcaster.java`, lines 42–44, 64–70  
**Problem:** `broadcast()` iterates over all registered SSE emitters and calls `send()` **synchronously** on each. Each `send()` is a network I/O operation that can block. A single slow SSE client delays broadcast to all other clients AND blocks the publishing thread (which may be a pipeline executor thread, starving stage processing). The `keepAlive()` method has the same problem on the `@Scheduled` thread pool.

**Fix:** Offload each emitter's `send()` to a separate task using a broadcast executor:
```java
private void broadcast(String eventName, Object data) {
    if (emitters.isEmpty()) return;
    emitters.forEach(e -> broadcastExecutor.execute(() -> {
        try { e.send(SseEmitter.event().name(eventName).data(data)); }
        catch (IOException ex) { emitters.remove(e); }
    }));
}
```
>! *same remark as HIGH-4
---

### HIGH-6 — Exception messages leaked in HTTP and UI responses across multiple controllers
**Severity:** 🟠 HIGH  
**Track:** D — Security & Observability (with cross-track hits from Track A)  
**Files:**
- `PrepareCommandController.java:67-72` — `e.getMessage()` in response body
- `EventAnnRerunCommandController.java:59, 67` — `e.getMessage()` in response body
- `UiOperatorActionsController.java:76` — `"Replay failed: " + e.getMessage()` rendered into HTML
- `LibraryUiController.java:63, 103, 152, 285` — `ex.getMessage()` in form validation errors
- `ErrorResponseFactory.java:105` — `cause.getMessage()` in response details (covered by CRIT-3)

**Problem:** Internal exception messages may contain Neo4j query fragments, database identifiers, file paths, or AI API metadata. These are rendered directly into HTTP response bodies or Thymeleaf templates without sanitization.

**Fix:** Return sanitized, user-safe messages in all error responses. Log the raw exception message server-side. Replace `e.getMessage()` in response bodies with fixed, client-safe strings (e.g., `"Chapter preparation failed. Please try again."`).
>! needs a focused  projet-wide pass later. (See CRIT-3 remark)
---

### HIGH-7 — `StepEventMapper` uses ambiguous `StageCompletedEvent` constructor overload
**Severity:** 🟠 HIGH  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StepEventMapper.java`, lines 36–53  
**Problem:** Chapter-level events use a 5-argument constructor while book-level events use a 6-argument form. The test (`JobStatusBroadcasterTest:45-48`) constructs chapter-level events with the 6-argument form, contradicting production code. If the 5-arg overload maps `scopeId` to the wrong field, downstream SSE consumers receive incorrect event context.

**Fix:** Use the 6-arg form consistently everywhere, passing `null` for the unused dimension. Verify that `JobStatusBroadcaster.buildPayload()` correctly dispatches on presence/absence of `chapterId` vs `bookId`.
>! there's a separate cleanup pass planned to remove all "Step" mention in favour of "Stage" domain language
---

### HIGH-8 — `CommandIngestionController` uses `UUID` type directly, bypassing structured error responses
**Severity:** 🟠 HIGH  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/CommandIngestionController.java`, line 39  
**Problem:** `@RequestParam("bookId") java.util.UUID bookId` lets Spring MVC handle UUID conversion. An invalid UUID string produces a `MethodArgumentTypeMismatchException` → generic 400 with Spring's default error body (potentially including stack traces in dev profiles), **not** the structured `ErrorResponse` used by every other controller in the package.

**Fix:** Accept `String bookIdStr` and parse manually with structured error response on failure, matching the pattern in `StepExecutionCommandController`, `JobsController`, and all consolidation controllers.
>! agreed
---

### HIGH-9 — TOCTOU race on `findById()` before `StageOperation.execute()`
**Severity:** 🟠 HIGH  
**Track:** B — Data & Persistence  
**File:** `StepExecutionCommandController.java:74-79` (and all 10 consolidation controllers at equivalent lines)  
**Problem:** The controller checks `chapterGraphRepository.findById(chapterUuid).isEmpty()` outside any transaction, then calls `stageOperation.execute()`. The entity could be deleted between the check and the execution. The execution operates on stale assumptions.

**Fix:** Remove the controller-level existence check. Let `StageOperation.execute()` handle the not-found case — if the entity doesn't exist, the service returns a failed `StepResult` that the controller maps to a 404. This closes the TOCTOU window and moves the existence concern into the transactional boundary of the service.
>! agreed, this is also better layer design
---

### HIGH-10 — `UiOperatorActionsController` bypasses orchestration layer entirely
**Severity:** 🟠 HIGH  
**Track:** B — Data & Persistence  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiOperatorActionsController.java`, lines 33–80  
**Problem:** This UI controller directly injects and calls `ChapterIndividualConsolidationService`, `ChapterLocationConsolidationService`, and `BookLocationConsolidationService` instead of going through `StageOperation` (which the command API controllers use). This bypasses the orchestration layer's stage lifecycle management, job tracking, and event publication. Consolidation runs outside any job context with synthetic stage/job IDs.

**Fix:** Delegate to the same `StageOperation` beans the command controllers use. The UI controller should not construct `StageExecutionContext` manually or inject consolidation services directly.
>! agreed, unified approach necessary. only caveat is that step-wise execution should remain possible.
---

### HIGH-11 — `JobsUiController` returns HTTP 500 for job-not-found instead of 404
**Severity:** 🟠 HIGH  
**Tracks:** A — Logic & Correctness + C — Async & Events (cross-track hit)  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/JobsUiController.java`, lines 36–41, 44–49  
**Problem:** `.orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId))` produces an unchecked exception that Spring converts to HTTP 500 with a stack trace. The REST API equivalent (`JobsController.getJobStatus`) correctly returns a 404. This creates inconsistent behavior between REST and UI clients.

**Fix:** Handle `Optional.empty()` explicitly and return a 404 view or redirect:
```java
if (statusOpt.isEmpty()) {
    model.addAttribute("error", "Job not found");
    return "ui/error/404";
}
```
>! agreed, 404 is ok
---

### MED-1 — `@Data` on DTO/response classes in 13+ files
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**Files:** `SubmitChapterRequest.java`, `SubmitChapterResponse.java`, `PrepareChapterRequest.java`, 6 library DTOs, 5 UI form classes, `JobStatusResponse.java`, `JobListResponse.java`  
**Problem:** `@Data` generates unnecessary `equals()`/`hashCode()`/`toString()` on mutable DTOs and request/response objects where these methods will never be used. On `JobStatusResponse.java` and `JobListResponse.java`, the nested static classes also carry `@Data`. Some DTOs are natural record candidates (immutable with static factory methods).

**Fix:** Replace `@Data` with `@Getter @Setter` on mutable DTOs. Convert immutable response types to Java records where appropriate.
>! yes @Data should disappear.
---

### MED-2 — `JobStatusBroadcaster` has no maximum SSE connection limit
**Severity:** 🟡 MEDIUM  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/query/job/JobStatusBroadcaster.java`, lines 22–35  
**Problem:** `register()` has no upper bound on the emitter list. Combined with `SseEmitter(0L)` (no timeout), a connection flood can cause unbounded memory growth. No production monitoring of connection counts.

**Fix:** Add a configurable maximum: `if (emitters.size() >= maxConnections) { /* reject or evict oldest */ }`. Log at INFO when connection count exceeds threshold.
>! same remark as earlier. we need a sensible upper bound
---

### MED-3 — `UiQueryController.parseChapterNumber()` throws uncaught `NumberFormatException`
**Severity:** 🟡 MEDIUM  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiQueryController.java`, lines 287–292  
**Problem:** `Integer.valueOf(readThroughChapterNumber.trim())` without try-catch. A non-numeric request parameter produces an unhandled `NumberFormatException` → HTTP 500 with stack trace.

**Fix:** Wrap in try-catch returning `null` on parse failure.
>! yes, lets fix
---

### MED-4 — `JobsController` status validation gap for "ACTIVE"
**Severity:** 🟡 MEDIUM  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/query/job/JobsController.java`, lines 128–141  
**Problem:** Validation bypasses `"ACTIVE"` at line 128 but the error message at line 135 claims `"ACTIVE"` is valid. The status is passed unchecked to the service layer.

**Fix:** Either add `"ACTIVE"` to the validation set or remove it from the error message.
>! remove from error message
---

### MED-5 — `EventAnnRerunCommandController` misleading `MISSING_UNIVERSE_ID` error
**Severity:** 🟡 MEDIUM  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/EventAnnRerunCommandController.java`, lines 43–50  
**Problem:** The error code `MISSING_UNIVERSE_ID` is returned when the actual fix could be providing `bookId` or `chapterId` instead. A caller providing only `bookId` with no `universeId` bypasses the guard but passes `null` universeId to the service.

**Fix:** Rename error code to `MISSING_SCOPE` with message: "At least one of universeId, bookId, or chapterId is required."
>! ok
---

### MED-6 — `LibraryCommandController` returns empty error bodies
**Severity:** 🟡 MEDIUM  
**Track:** A — Logic & Correctness  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/library/LibraryCommandController.java`, lines 61–67, 107–113, 167–173  
**Problem:** All three catch blocks return `ResponseEntity.badRequest().build()` and `internalServerError().build()` with **no response body**, contradicting the structured `ErrorResponse` pattern used everywhere else.

**Fix:** Return `ErrorResponse.builder().code("...").message(...).build()` in all error paths.
>! ok
---

### MED-7 — `fireEvents` and event publication logic lives in the controller layer
**Severity:** 🟡 MEDIUM  
**Track:** B — Data & Persistence  
**Files:** `StepExecutionCommandController.java:85-86, 135-136, 186-187, 238-239` + all 10 consolidation controllers  
**Problem:** The controller decides whether to fire events and manually publishes `StageCompletedEvent`/`StageTriggeredEvent`. If a new caller (scheduled job, internal trigger) invokes `StageOperation.execute()` directly, it won't publish these events because the logic is in the controller, not the service.

**Fix:** Move `fireEvents` decision into `StageOperation` implementations. The controller should pass `fireEvents` as a parameter, and the operation should publish events itself.

---

### MED-8 — `IngestionUiController.resolveBookSelection()` mixes writes on two aggregates
**Severity:** 🟡 MEDIUM  
**Track:** B — Data & Persistence  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/IngestionUiController.java`, lines 270–345  
**Problem:** Library creation (universe/series/book) and ingestion submission happen in a single non-transactional controller method. If library creation succeeds but ingestion fails, entities persist without associated ingestion — the caller gets a failure response suggesting nothing happened.

**Fix:** Require the book to exist before submission, failing with a clear error if it doesn't. Separate library creation from file upload.

---

### MED-9 — User query text logged without truncation
**Severity:** 🟡 MEDIUM  
**Track:** D — Security & Observability  
**Files:** `UiQueryController.java:195`, `AskController.java:64, 92, 120, 149`  
**Problem:** User-submitted query strings logged at WARN/ERROR level without truncation. Logging philosophy Rule 4: "Book text or chapter raw content must never appear in any log line." Users can paste chapter text into the query input.

**Fix:** Truncate query content to safe preview (e.g., first 100 chars) before logging.

---

### MED-10 — Filename logged without length truncation
**Severity:** 🟡 MEDIUM  
**Track:** D — Security & Observability  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/CommandIngestionController.java`, line 44  
**Problem:** `file.getOriginalFilename()` logged at INFO without truncation. A malicious 5KB filename gets written to logs in full.

**Fix:** Truncate filenames to 100 chars before logging.

---

### MED-11 — Prompt injection risk at controller entry point
**Severity:** 🟡 MEDIUM  
**Track:** D — Security & Observability  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiQueryController.java`, lines 91, 110, 141, 171  
**Problem:** User-supplied `question` parameter flows directly into LLM prompt construction without structural delimiting visible at the controller layer. No size limit on the `question` parameter.

**Fix:** Verify downstream services use structural delimiters (`<input>...</input>`) for user content. Add a size limit on the `question` parameter.

---

### MED-12 — `FileUploadValidator` has unnecessarily public implementation methods
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/validation/FileUploadValidator.java`, lines 79, 100, 115  
**Problem:** `validateFileType`, `validateFileSize`, and `getFileExtension` are `public` but only called from `validateFile` (same class). Internal implementation details leaked through public boundary.

**Fix:** Make these methods `private`.

---

### MED-13 — `FileContentExtractor` has 3 unused public methods
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/extractor/FileContentExtractor.java`, lines 100–134  
**Problem:** `extractBasename`, `validateContentLength`, and `getContentPreview` are `public` with zero callers across the reviewed codebase. Per coding standards: "A helper method extracted from a single callsite is premature."

**Fix:** Delete these three methods.

---

### MED-14 — 7 of 10 consolidation controllers have no `@WebMvcTest` slice tests
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**Files:** Untested: `Book{Object,Location,Concept,Collective,Individual}`, `Chapter{Location,Concept}` consolidation controllers  
**Problem:** Only 3 of 10 controllers have `@WebMvcTest` coverage. The 3 existing tests are copy-paste duplicates. Also missing: `StepExecutionCommandController`, `PrepareCommandController`, `StepQueryController`.

**Fix:** Add `@WebMvcTest` tests for the 7 untested controllers. Consider whether the generic controller refactoring (HIGH-2) would allow a single parameterized test class.

---

### MED-15 — `ErrorResponseFactory` is a single-consumer `@Component`
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/response/ErrorResponseFactory.java`  
**Problem:** Single-implementation, single-consumer `@Component` — only `CommandIngestionController` uses it. `PrepareCommandController` and `JobsController` construct `ErrorResponse` inline, making the factory inconsistent.

**Fix:** Either inline into `CommandIngestionController` or adopt as the standard across all controllers.

---

### MED-16 — `LibraryOptionsController.getBookSelector()` is dead stub
**Severity:** 🟡 MEDIUM  
**Tracks:** A — Logic & Correctness + B — Data & Persistence (cross-track hit)  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/LibraryOptionsController.java`, lines 105–111  
**Problem:** Returns an empty `LibraryHierarchy` with TODO comment. The endpoint always returns an empty book selector regardless of database contents.

**Fix:** Implement the book hierarchy query or remove the endpoint if unused.

---

### MED-17 — `JobStatusResponse` and `JobListResponse` nested classes use `@Data`
**Severity:** 🟡 MEDIUM  
**Track:** E — Structure & Quality  
**Files:** `JobStatusResponse.java:71, 82`, `JobListResponse.java:23, 41`  
**Problem:** Nested static classes (`FailureDetails`, `StatusUpdateDto`, `JobSummary`, `Pagination`) annotated with `@Data`. These are pure data carriers — `equals`/`hashCode` unnecessary.

**Fix:** Replace with `@Getter @Setter` or convert to records.

---

### MED-18 — `UiQueryController` narrow catch misses NPE from null LLM responses
**Severity:** 🟡 MEDIUM  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiQueryController.java`, lines 92–93, 123–124, 154–155, 185–186  
**Problem:** Catches only `SemanticSearchException | EntityLookupException` but not `RuntimeException` (e.g., NPE from null LLM response). A known coding-standards pattern warns: "Null-guard every LLM response access."

**Fix:** Broaden catch to `Exception` in the error-handling path, matching `CommandIngestionController`.

---

### MED-19 — `IngestionUiController` batch upload processes files sequentially on HTTP thread
**Severity:** 🟡 MEDIUM  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/IngestionUiController.java`, lines 130–268  
**Problem:** 50-file batch upload holds the HTTP thread for 50 sequential `@Transactional` database transactions. No early termination on failure — partial submission state not rollback-safe.

**Fix:** Offload individual submissions to a bounded executor and return 202 Accepted immediately with batch tracking. Document that large batches should be split.

---

### MED-20 — `UiOperatorActionsController` replays with null `bookId`
**Severity:** 🟡 MEDIUM  
**Track:** C — Async & Events  
**File:** `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiOperatorActionsController.java`, lines 62–64  
**Problem:** `pipelineCoordinator.findBookIdByChapterId()` can return `null` and is passed to `rerunStage()` without a null check. Book-level stage handlers may NPE when dereferencing null `bookId`.

**Fix:** Add a null check for `bookId` alongside the `jobId` null check.

---

### LOW Findings (consolidated)
| ID | Severity | File | Description |
|----|----------|------|-------------|
| LOW-1 | 🟢 LOW | `CommandIngestionController.java:48` | Redundant `file == null` check (Spring guarantees `@RequestParam` presence) |
| LOW-2 | 🟢 LOW | `JobsController.java:147-168` | No null guard on `summaries.jobs()` / `summaries.pagination()` |
| LOW-3 | 🟢 LOW | `UiQueryController.java:294-311` | `visibilitySummary()` prints "null" strings for missing fields |
| LOW-4 | 🟢 LOW | `StepEventMapper.java:35-57` | `switch` on `StepKey` enum has no default case (coordination risk on enum addition) |
| LOW-5 | 🟢 LOW | `EventAnnRerunCommandController.java:87` | `InvalidUuidException` inner class — exception for expected validation failure |
| LOW-6 | 🟢 LOW | `IngestionUiController.java:140` | `chapterTitles.size()` not validated against `files.size()` |
| LOW-7 | 🟢 LOW | `StepQueryController.java:44` | NPE potential on `def.prerequisites()` or `def.key()` |
| LOW-8 | 🟢 LOW | `JobStatusBroadcaster.java:30, 51-57` | Silent emitter removal on error/exception — no debug logging |
| LOW-9 | 🟢 LOW | `JobStatusBroadcaster.java:23` | No per-connection INFO log for SSE connection counting |
| LOW-10 | 🟢 LOW | `EventAnnRerunCommandController.java:23` | Manual `LoggerFactory.getLogger` + constructor instead of `@Slf4j` + `@RequiredArgsConstructor` |
| LOW-11 | 🟢 LOW | `UiQueryController.java:36, 42-48` | Same — manual logger + constructor |
| LOW-12 | 🟢 LOW | 4 files | Wildcard imports (`CommandIngestionController`, `PrepareCommandController`, `BookConsolidationRedirectController`, `JobsController`) |
| LOW-13 | 🟢 LOW | `StepExecutionCommandController.java:3` | Unused import `com.lorevault.api.graph.event.scene.Scene` |
| LOW-14 | 🟢 LOW | `SubmitChapterResponse.java` | Potentially unused class — zero consumers found |
| LOW-15 | 🟢 LOW | `IngestionUiController.java:387-438` | `BatchUploadResponse`/`BatchUploadItemResult` inline records bloat controller to 439 lines |
| LOW-16 | 🟢 LOW | `CoordinatesBuilder.java` | Misleading name — legacy "coordinates" concept removed, now just builds `SubmitChapterRequest` |
| LOW-17 | 🟢 LOW | StepExecutionCommandController (all steps) | Log format doesn't include `durationMs` on stage completion logs |
| LOW-18 | 🟢 LOW | `LibraryUiController.java:284` | Stack trace logged at ERROR with potential internal path exposure |

---

## Section 3 — Priority Action Table

| ID | Severity | File(s) | Description | Must Fix Before Merge? |
|----|----------|---------|-------------|------------------------|
| CRIT-1 | 🔴 CRITICAL | `pom.xml` + entire web layer | No Spring Security — all 40+ endpoints unprotected | **Yes** |
| CRIT-2 | 🔴 CRITICAL | 16 controllers | Direct Neo4j repository injection in controllers | **Yes** |
| CRIT-3 | 🔴 CRITICAL | `ErrorResponseFactory.java:105` | Exception messages exposed to API clients | **Yes** |
| CRIT-4 | 🔴 CRITICAL | `StageCompletedEvent.java`, `StageTriggeredEvent.java` | Missing `correlationId` on pipeline events | **Yes** |
| CRIT-5 | 🔴 CRITICAL | `BookConsolidationRedirectController.java` | Backward-compatibility code must be deleted | **Yes** |
| CRIT-6 | 🔴 CRITICAL | `UiOperatorActionsController.java:35-80` | Random UUIDs for `stageId`/`jobId`; bypasses orchestration layer | **Yes** |
| HIGH-1 | 🟠 HIGH | `application.yml:71` | Hardcoded PostgreSQL password | **Yes** |
| HIGH-2 | 🟠 HIGH | 10 consolidation controllers | 830 lines of 95%-duplicated copy-paste | **Yes** |
| HIGH-3 | 🟠 HIGH | `JobStatusBroadcaster.java:23` | `SseEmitter(0L)` — unbounded memory growth | **Yes** |
| HIGH-4 | 🟠 HIGH | `StepEventMapper.java:54, 85` | Synchronous event dispatch blocks HTTP threads | **Yes** |
| HIGH-5 | 🟠 HIGH | `JobStatusBroadcaster.java:42-70` | Synchronous broadcast blocks all emitters and executors | **Yes** |
| HIGH-6 | 🟠 HIGH | 5 controllers | Exception messages leaked in HTTP/UI responses | **Yes** |
| HIGH-7 | 🟠 HIGH | `StepEventMapper.java:36-53` | Ambiguous `StageCompletedEvent` constructor overload | **Yes** |
| HIGH-8 | 🟠 HIGH | `CommandIngestionController.java:39` | UUID type bypasses structured error responses | **Yes** |
| HIGH-9 | 🟠 HIGH | All consolidation controllers | TOCTOU race on `findById()` before `execute()` | **Yes** |
| HIGH-10 | 🟠 HIGH | `UiOperatorActionsController.java:33-80` | UI controller bypasses orchestration layer | **Yes** |
| HIGH-11 | 🟠 HIGH | `JobsUiController.java:36-49` | HTTP 500 for job-not-found instead of 404 | **Yes** |
| MED-1 | 🟡 MEDIUM | 13 DTO files | `@Data` on non-entity classes | Recommended |
| MED-2 | 🟡 MEDIUM | `JobStatusBroadcaster.java:22-35` | No max SSE connection limit | Recommended |
| MED-3 | 🟡 MEDIUM | `UiQueryController.java:287-292` | Uncaught `NumberFormatException` | Recommended |
| MED-4 | 🟡 MEDIUM | `JobsController.java:128-141` | "ACTIVE" status validation gap | Recommended |
| MED-5 | 🟡 MEDIUM | `EventAnnRerunCommandController.java:43-50` | Misleading error code | Recommended |
| MED-6 | 🟡 MEDIUM | `LibraryCommandController.java:61-173` | Empty error response bodies | Recommended |
| MED-7 | 🟡 MEDIUM | All step/consolidation controllers | `fireEvents` logic in controller layer | Recommended |
| MED-8 | 🟡 MEDIUM | `IngestionUiController.java:270-345` | Mixed aggregate writes in non-transactional controller | Recommended |
| MED-9 | 🟡 MEDIUM | `UiQueryController.java`, `AskController.java` | User query text logged without truncation | Recommended |
| MED-10 | 🟡 MEDIUM | `CommandIngestionController.java:44` | Filename logged without truncation | Recommended |
| MED-11 | 🟡 MEDIUM | `UiQueryController.java:91, 110, 141, 171` | Prompt injection risk at entry point | Recommended |
| MED-12 | 🟡 MEDIUM | `FileUploadValidator.java:79, 100, 115` | Public implementation methods | Recommended |
| MED-13 | 🟡 MEDIUM | `FileContentExtractor.java:100-134` | 3 unused public methods | Recommended |
| MED-14 | 🟡 MEDIUM | 7 consolidation controllers | No `@WebMvcTest` slice tests | Recommended |
| MED-15 | 🟡 MEDIUM | `ErrorResponseFactory.java` | Single-consumer `@Component` | Recommended |
| MED-16 | 🟡 MEDIUM | `LibraryOptionsController.java:105-111` | Dead `getBookSelector()` stub | Recommended |
| MED-17 | 🟡 MEDIUM | `JobStatusResponse.java`, `JobListResponse.java` | `@Data` on nested DTO classes | Recommended |
| MED-18 | 🟡 MEDIUM | `UiQueryController.java:92-185` | Narrow catch misses LLM NPE | Recommended |
| MED-19 | 🟡 MEDIUM | `IngestionUiController.java:130-268` | Batch upload on HTTP thread | Recommended |
| MED-20 | 🟡 MEDIUM | `UiOperatorActionsController.java:62-64` | Null `bookId` on replay | Recommended |

---

## Section 4 — Test Gaps

- **No `@WebMvcTest` coverage for 7 consolidation controllers:** `BookObject`, `BookLocation`, `BookConcept`, `BookCollective`, `BookIndividual`, `ChapterLocation`, `ChapterConcept` have zero slice tests. ⚠️ Cross-track: these are the same controllers flagged for repository injection (CRIT-2) and copy-paste (HIGH-2).
- **No test for `StepExecutionCommandController`:** The primary pipeline step-execution endpoint is untested.
- **No test for `PrepareCommandController`:** The chapter preparation endpoint is untested.
- **No test for `StepQueryController`:** The step definition query endpoint is untested.
- **No test for SSE disconnection scenarios:** `JobStatusBroadcaster` has no test for slow-client broadcast blocking, emitter timeout, or connection flood. ⚠️ Cross-track hit with HIGH-3, HIGH-5, MED-2.
- **No test for `CommandIngestionController` with invalid UUID:** Current test doesn't cover the UUID-type-mismatch path since the controller uses `UUID` type directly (HIGH-8).
- **No test for `UiQueryController.parseChapterNumber()` with non-numeric input** (MED-3).
- **No test for `JobsUiController` job-not-found 404 path** (HIGH-11).
- **No security integration test:** No test verifying that endpoints require authentication (CRIT-1).
- **No test for `ErrorResponseFactory` exposing internal messages** (CRIT-3).

---

## Section 5 — Positive Notes

The web layer follows a consistent REST API pattern with structured `ErrorResponse` bodies, manual UUID parsing with proper error handling, and clean separation of command vs. query controllers. SSE status broadcasting is integrated with the event pipeline, and `JobStatusBroadcaster` correctly uses `CopyOnWriteArrayList` for thread-safe emitter management. File upload validation and content extraction are properly separated into dedicated classes. The Thymeleaf templates use safe HTML escaping (no `th:utext`), and no raw Cypher string composition or `ObjectInputStream` usage was found anywhere in the web layer. UUID path variables are consistently validated before reaching the service layer in all 15+ controllers.
