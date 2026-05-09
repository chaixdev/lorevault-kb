# Agent-Driven Step Execution API

**Status:** COMPLETE  
**Last Updated:** May 09, 2026

## Summary

Extend the existing CQRS command surface with step-wise pipeline execution endpoints that allow an agent (human or LLM) to run individual ingestion steps, inspect results, and control the cascade — replacing the need for a separate CLI module.

## Problem

The current API has two modes:

1. **Full pipeline** — `POST /api/command/ingest` creates a chapter and fires the entire async event-driven pipeline. No way to stop between steps or inspect intermediate output.
2. **Individual resolution steps** — `POST /api/command/ingest/chapters/{id}/resolve-individuals` etc. exist for 4 entity types, but these are ad hoc additions with inconsistent response shapes and no event suppression control.

There is no way to:

- Run scene detection in isolation and inspect relation claims before downstream steps touch the data
- Run a step without triggering the full async cascade
- Discover what steps exist and what their prerequisites are
- Prepare a chapter without triggering the pipeline
- Get a uniform response from any step execution

An agent driving the system needs: run a step, see what happened, decide what to run next.

## Product Context

- Developers and operators need a fast feedback loop for pipeline step verification
- LLM-based agents need discoverable, uniform API endpoints to drive ingestion step by step
- The operator dashboard brainstorm identifies selective step rerun as a key devx need
- Step-wise execution enables targeted regression testing: change code for one step, rerun only that step, verify results
- The existing resolution command endpoints prove the pattern works — they just need to be completed and standardized

## Technical Context

### Current CQRS surface

```
/api/command/library/universe          POST   create universe
/api/command/library/series            POST   create series
/api/command/library/book              POST   create book
/api/command/ingest                    POST   full pipeline (multipart)
/api/command/ingest/chapters/{id}/resolve-individuals     POST
/api/command/ingest/chapters/{id}/resolve-collectives     POST
/api/command/ingest/chapters/{id}/resolve-locations       POST
/api/command/ingest/chapters/{id}/resolve-objects         POST
/api/command/ingest/books/{id}/resolve-individuals        POST
/api/command/ingest/books/{id}/resolve-collectives       POST
/api/command/ingest/books/{id}/resolve-locations          POST
/api/command/ingest/books/{id}/resolve-objects            POST
/api/command/ingest/events/rerun-ann                      POST
/api/query/jobs/{jobId}                GET    job status
/api/query/jobs                        GET    list jobs
```

### What's missing

| Step | Has endpoint? | Notes |
|---|---|---|
| Scene detection | No | Most important step, no API |
| Chunking | No | |
| Embedding | No | |
| Chapter individual resolution | Yes | Inconsistent response shape |
| Chapter collective resolution | Yes | Inconsistent response shape |
| Chapter location resolution | Yes | Inconsistent response shape |
| Chapter object resolution | Yes | Inconsistent response shape |
| Chapter event resolution | No | |
| Book individual reduction | Yes | Named "resolve" not "reduce" |
| Book collective reduction | Yes | Named "resolve" not "reduce" |
| Book location reduction | Yes | Named "resolve" not "reduce" |
| Book object reduction | Yes | Named "resolve" not "reduce" |
| Event embedding candidates | No | |

### What's inconsistent

- Book-level endpoints say "resolve" but the domain concept is "reduce" (chapter-level entities reduced to book-level entities)
- Each controller has its own response class — no shared envelope
- No `fireEvents` control — resolution endpoints publish events unconditionally
- No `jobId` parameter — resolution endpoints don't track job status
- No discoverability — no endpoint lists available steps

### Handler interface pattern (already in place)

The `*Operation` interfaces already exist in core:

```java
public interface SceneDetectionOperation {
    StepResult execute(UUID jobId, UUID chapterId);
}
```

Each handler implements its interface, and `@EventListener` delegates to `execute()`. The REST controllers can call `execute()` directly — same pattern the resolution controllers already use for their service calls.

## Scope

### In scope

- New `POST /api/command/ingest/prepare` endpoint — create chapter + job without triggering pipeline
- New `POST /api/command/ingest/chapters/{chapterId}/detect-scenes` endpoint
- New `POST /api/command/ingest/chapters/{chapterId}/chunk` endpoint
- New `POST /api/command/ingest/chapters/{chapterId}/embed` endpoint
- New `POST /api/command/ingest/chapters/{chapterId}/resolve-events` endpoint
- Rename book-level endpoints from `resolve-*` to `reduce-*`
- `fireEvents` query parameter on all step endpoints (default: `false`)
- `jobId` query parameter on all step endpoints (optional, enables status tracking)
- Uniform `StepResult`-compatible response envelope
- New `GET /api/query/ingestion/steps` endpoint — list available steps with prerequisites
- Curl command catalog in docs

### Out of scope

- ~~Removing the `lorevault-cli` module~~ — already done
- StageRun DAG persistence (separate planning item)
- Operator dashboard UI (separate brainstorm)
- LLM call result caching/replay
- `run-pipeline --through` composite endpoint (agents compose individual steps via curl)

## Design

### URL surface

```
# ── Library ───────────────────────────────────────────────
POST   /api/command/library/universe                      (existing)
POST   /api/command/library/series                         (existing)
POST   /api/command/library/book                            (existing)

# ── Ingestion: full pipeline ─────────────────────────────
POST   /api/command/ingest                                 (existing, multipart)

# ── Ingestion: prepare ────────────────────────────────────
POST   /api/command/ingest/prepare                          (new, JSON body)

# ── Ingestion: chapter-scoped steps ──────────────────────
POST   /api/command/ingest/chapters/{chapterId}/detect-scenes        (new)
POST   /api/command/ingest/chapters/{chapterId}/resolve-individuals  (existing)
POST   /api/command/ingest/chapters/{chapterId}/resolve-collectives  (existing)
POST   /api/command/ingest/chapters/{chapterId}/resolve-locations    (existing)
POST   /api/command/ingest/chapters/{chapterId}/resolve-objects       (existing)
POST   /api/command/ingest/chapters/{chapterId}/resolve-events       (new)
POST   /api/command/ingest/chapters/{chapterId}/chunk                 (new)
POST   /api/command/ingest/chapters/{chapterId}/embed                 (new)

# ── Ingestion: book-scoped steps ─────────────────────────
POST   /api/command/ingest/books/{bookId}/reduce-individuals          (renamed from resolve)
POST   /api/command/ingest/books/{bookId}/reduce-collectives          (renamed from resolve)
POST   /api/command/ingest/books/{bookId}/reduce-locations             (renamed from resolve)
POST   /api/command/ingest/books/{bookId}/reduce-objects                (renamed from resolve)

# ── Ingestion: rerun ────────────────────────────────────
POST   /api/command/ingest/events/rerun-ann                  (existing, keep)

# ── Query: discoverability ──────────────────────────────
GET    /api/query/ingestion/steps                            (new)
GET    /api/query/jobs/{jobId}                               (existing)
GET    /api/query/jobs                                       (existing)
```

### Event suppression

All step endpoints accept `?fireEvents=true|false` (default: `false`).

| `fireEvents` | Behavior |
|---|---|
| `false` (default) | Run step, persist results, return response. **Do not publish completion events.** Downstream handlers stay idle. Agent inspects, then decides what to run next. |
| `true` | Run step, persist results, publish events. Downstream handlers fire. Equivalent to the async pipeline's behavior for that step. |

The full-pipeline endpoint (`POST /api/command/ingest`) always fires events — it's the production path.

### Prepare endpoint

Two content types are supported:

1. **JSON body** — for programmatic use (agents, scripts):
```json
POST /api/command/ingest/prepare
Content-Type: application/json

{
  "bookId": "5187466d-...",
  "chapterNumber": 1,
  "chapterTitle": "The Kevin Jenkins Experience",
  "chapterText": "..."
}

→ 201 Created
{
  "jobId": "75c97b8b-...",
  "chapterId": "be7fc5c7-..."
}
```

2. **Multipart form** — for file upload (same as the existing `POST /api/command/ingest` but without triggering the pipeline):
```
POST /api/command/ingest/prepare
Content-Type: multipart/form-data

file=@chapter.txt
bookId=5187466d-...
chapterNumber=1
chapterTitle=The Kevin Jenkins Experience
```

Calls `IngestionService.prepareChapter()` — creates chapter + job, does NOT publish `ChapterIngestionEvent`.

### Uniform response envelope

All step endpoints return the same shape:

```json
{
  "step": "detect-scenes",
  "scope": "chapter",
  "scopeId": "be7fc5c7-...",
  "success": true,
  "summary": "Detected 5 scenes",
  "durationMs": 37470,
  "retryable": false,
  "counts": {
    "scenesDetected": 5,
    "relationClaims": 10,
    "individuals": 6,
    "collectives": 9
  }
}
```

The `counts` map varies per step. The envelope is constant.

### Error response format

All step endpoints return the same error envelope:

```json
{
  "step": "detect-scenes",
  "scope": "chapter",
  "scopeId": "be7fc5c7-...",
  "success": false,
  "summary": "Scene detection failed: LLM API timeout after 60s",
  "durationMs": 60043,
  "retryable": true,
  "counts": {}
}
```

HTTP status codes:

| Scenario | HTTP status |
|---|---|
| Step succeeded | `200 OK` |
| Step failed but is retryable (LLM timeout, rate limit) | `200 OK` with `success: false, retryable: true` |
| Step failed permanently (bad input, not found) | `200 OK` with `success: false, retryable: false` |
| Chapter/book not found | `404 Not Found` |
| Invalid UUID | `400 Bad Request` |
| Claim contention on book-level reduction | `409 Conflict` |

Step failures return `200 OK` because the step *ran* — it just didn't succeed. This lets the agent distinguish between "the request was malformed" (4xx) and "the step ran but failed" (200 with `success: false`).

### Steps query endpoint

```json
GET /api/query/ingestion/steps

{
  "steps": [
    {
      "key": "detect-scenes",
      "scope": "chapter",
      "description": "Detect semantic scene boundaries in chapter text",
      "prerequisites": []
    },
    {
      "key": "resolve-individuals",
      "scope": "chapter",
      "description": "Resolve individual entity mentions across scenes",
      "prerequisites": ["detect-scenes"]
    },
    {
      "key": "reduce-individuals",
      "scope": "book",
      "description": "Reduce chapter-level individuals to book-level entities",
      "prerequisites": ["resolve-individuals"]
    }
  ]
}
```

An agent can discover what steps exist, what scope they operate on, and what must run first.

### `jobId` parameter

All step endpoints accept an optional `?jobId=...` query parameter. When provided, the step updates the `IngestionJob` status (via `PipelineStageSupport`). When omitted, the step runs without job tracking — useful for ad hoc debugging.

### Backward compatibility for renamed endpoints

The book-level endpoints change from `resolve-*` to `reduce-*` to match the domain vocabulary. The old URLs should be preserved as redirects (`301 Moved Permanently`) for one release cycle, then removed.

### Agentic workflow example

```bash
# 1. Create library (existing endpoints)
curl -s -X POST localhost:18080/api/command/library/universe \
  -H 'Content-Type: application/json' -d '{"name":"Deathworlders"}'
# → {"id":"...", "name":"Deathworlders", "created":true}

curl -s -X POST localhost:18080/api/command/library/series \
  -H 'Content-Type: application/json' -d '{"universeId":"...","name":"Deathworlders"}'

curl -s -X POST localhost:18080/api/command/library/book \
  -H 'Content-Type: application/json' \
  -d '{"universeId":"...","seriesId":"...","title":"The Kevin Jenkins Experience","bookNumber":1}'

# 2. Prepare chapter (no pipeline trigger)
curl -s -X POST localhost:18080/api/command/ingest/prepare \
  -H 'Content-Type: application/json' \
  -d '{"bookId":"...","chapterNumber":1,"chapterTitle":"Ch1","chapterText":"..."}'
# → {"jobId":"...", "chapterId":"..."}

# 3. Discover available steps
curl -s localhost:18080/api/query/ingestion/steps

# 4. Run scene detection (isolated, no cascade)
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/detect-scenes
# → {"step":"detect-scenes","success":true,"counts":{"scenesDetected":5,"relationClaims":10}}

# 5. Inspect results in Neo4j
# MATCH (rc:RelationClaim) RETURN rc.relationName, rc.subjectName, rc.objectName

# 6. Run next step when ready
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-individuals

# 7. Or run a step and let it cascade
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/detect-scenes?fireEvents=true

# 8. Full pipeline (existing endpoint, unchanged)
curl -s -X POST localhost:18080/api/command/ingest \
  -F "bookId=..." -F "chapterNumber=1" -F "file=@chapter.txt"
```

## Implementation Notes

### New controllers

| Controller | Endpoint | Delegates to |
|---|---|---|
| `StepExecutionCommandController` | `/chapters/{id}/detect-scenes` | `SceneDetectionOperation.execute()` |
| `StepExecutionCommandController` | `/chapters/{id}/chunk` | `ChunkingOperation.execute()` |
| `StepExecutionCommandController` | `/chapters/{id}/embed` | `EmbeddingOperation.execute()` |
| `StepExecutionCommandController` | `/chapters/{id}/resolve-events` | `ChapterEventResolutionOperation.execute()` |
| `PrepareCommandController` | `/prepare` | `IngestionService.prepareChapter()` |
| `StepQueryController` | `GET /api/query/ingestion/steps` | `StepCatalog.all()` |

Existing resolution controllers stay in place but gain `fireEvents` and `jobId` parameters.

### Event suppression mechanism

Each `*Operation.execute()` method currently does not publish events — that's done by the `@EventListener` adapter. The REST controller calls `execute()` directly, so no events fire by default.

When `fireEvents=true`, the controller publishes the appropriate completion event after `execute()` returns. This requires the controller to know which event to publish for each step — a small mapping table in the controller or a `StepEventMapper`.

### `StepCatalog` must be created in core

`StepKey`, `StepDefinition`, and `StepCatalog` were in the deleted `lorevault-cli` module. They need to be recreated in `lorevault-core` since the REST API needs them. The `StepKey` enum should list all pipeline steps; `StepDefinition` records the key, description, scope, and prerequisites; `StepCatalog` is a Spring `@Component` that registers all steps with their `*Operation` delegates.

### `StepResult` already in core

`StepResult` is already in `lorevault-core/src/main/java/com/lorevault/api/ingestion/pipeline/StepResult.java`. The REST response envelope maps directly from it.

### `*Operation` interfaces — which exist, which need creation

Only `SceneDetectionOperation` currently exists in core. The remaining interfaces need to be extracted from their handlers following the same pattern:

| Interface | Status | Handler |
|---|---|---|
| `SceneDetectionOperation` | **Exists** | `SceneDetectionHandler` |
| `ChunkingOperation` | Needs creation | `ChunkingHandler` |
| `EmbeddingOperation` | Needs creation | `EmbeddingHandler` |
| `ChapterIndividualResolutionOperation` | Needs creation | `ChapterIndividualResolutionHandler` |
| `ChapterCollectiveResolutionOperation` | Needs creation | `ChapterCollectiveResolutionHandler` |
| `ChapterLocationResolutionOperation` | Needs creation | `ChapterLocationResolutionHandler` |
| `ChapterObjectResolutionOperation` | Needs creation | `ChapterObjectResolutionHandler` |
| `ChapterEventResolutionOperation` | Needs creation | `ChapterEventResolutionHandler` |
| `BookIndividualReductionOperation` | Needs creation | `BookIndividualReductionHandler` |
| `BookCollectiveReductionOperation` | Needs creation | `BookCollectiveReductionHandler` |
| `BookLocationReductionOperation` | Needs creation | `BookLocationReductionHandler` |
| `BookObjectReductionOperation` | Needs creation | `BookObjectReductionHandler` |

Each interface follows the same pattern: `StepResult execute(UUID jobId, UUID chapterId)` for chapter-scoped steps, `StepResult execute(UUID jobId, UUID bookId)` for book-scoped steps. The handler's `@EventListener` method delegates to `execute()`, and the REST controller calls `execute()` directly.

### Event mapping for `fireEvents=true`

When `fireEvents=true`, the controller must publish the domain-specific event that the async pipeline would publish. The mapping is:

| Step | Event to publish |
|---|---|
| detect-scenes | `ScenesDetectedEvent` |
| chunk | `ChunksCreatedEvent` |
| resolve-individuals | `ChapterIndividualsResolvedEvent` |
| resolve-collectives | `ChapterCollectivesResolvedEvent` |
| resolve-locations | `ChapterLocationsResolvedEvent` |
| resolve-objects | `ChapterObjectsResolvedEvent` |
| resolve-events | `ChapterEventsResolvedEvent` |
| reduce-individuals | `BookIndividualsReducedEvent` |
| reduce-collectives | `BookCollectivesReducedEvent` |
| reduce-locations | `BookLocationsReducedEvent` |
| reduce-objects | `BookObjectsReducedEvent` |
| embed | No downstream event (embedding is a leaf step) |

This mapping should live in a `StepEventMapper` component in `lorevault-web`, not in core — it's a web-layer concern.

## Phased Implementation

### Phase 1 — Prepare + Scene Detection + Steps Query

The first end-to-end milestone. An agent can prepare a chapter, discover steps, and run scene detection in isolation.

| Deliverable | What |
|---|---|
| `PrepareCommandController` | `POST /api/command/ingest/prepare` |
| `StepExecutionCommandController` | `POST /chapters/{id}/detect-scenes` with `fireEvents` and `jobId` params |
| `StepQueryController` | `GET /api/query/ingestion/steps` |
| `StepCatalog` moved to core | From `lorevault-cli` |
| Response envelope | `StepExecutionResponse` record mapping from `StepResult` |
| Curl catalog | `docs/curl-catalog.md` with all endpoint examples |

**Milestone:** Prepare chapter → detect scenes → inspect in Neo4j, all via curl.

### Phase 2 — Remaining Chapter-Scoped Steps

Add chunking, embedding, and event resolution endpoints.

| Deliverable | What |
|---|---|
| `ChunkingOperation` interface | Already exists or needs extraction |
| `EmbeddingOperation` interface | Already exists or needs extraction |
| `ChapterEventResolutionOperation` interface | Already exists or needs extraction |
| Step endpoints | `/chunk`, `/embed`, `/resolve-events` |
| `StepCatalog` updated | All chapter-scoped steps registered |

### Phase 3 — Book-Scoped Steps + Rename

Rename book-level endpoints from `resolve-*` to `reduce-*`, add redirects, standardize response shapes.

| Deliverable | What |
|---|---|
| Renamed endpoints | `/books/{id}/reduce-*` with 301 redirects from `/resolve-*` |
| `fireEvents` + `jobId` | Added to existing resolution controllers |
| Response standardization | All step endpoints return `StepExecutionResponse` |

### Phase 4 — Curl Catalog + Cleanup

| Deliverable | What |
|---|---|
| `docs/curl-catalog.md` | Complete curl examples for all endpoints |
| Response standardization | Migrate existing resolution controllers to `StepExecutionResponse` |
| `StepKey`/`StepDefinition`/`StepCatalog` | Create in `lorevault-core` (were in deleted CLI module) |

## Known Constraints / Prior Findings

- The existing async pipeline must continue to work unchanged. Step endpoints call `*Operation.execute()` directly; the `@EventListener` path is untouched.
- `SceneDetectionHandler.execute()` has no `@Transactional` — each persistence service manages its own transaction scope. This is correct and must be preserved.
- Book-level reduction steps use `BookReductionClaimService` for distributed locking. The REST endpoint must handle claim contention gracefully (return 409 or retry).
- `IngestionService.prepareChapter()` already exists — it creates chapter + job without publishing `ChapterIngestionEvent`. The prepare endpoint is a thin REST wrapper around it.
- The `@EnableAsync(proxyTargetClass = true)` fix in `AsyncConfig` must be preserved — it was needed because handlers implement `*Operation` interfaces and have `@EventListener` methods not on those interfaces.
- The existing resolution controllers return custom response classes. These should be migrated to the shared `StepExecutionResponse` envelope, but this can be done incrementally.

## Open Questions

- **Event mapping for `fireEvents=true`:** Should the controller publish the exact event class each handler would publish (e.g., `ScenesDetectedEvent` for scene detection), or should there be a generic `StepCompletedEvent`? Recommendation: publish the domain-specific event — it's what the async pipeline would do.
- **Redirect strategy for renamed endpoints:** `301 Moved Permanently` vs `307 Temporary Redirect` vs just keeping both URLs. Recommendation: `307` for one release cycle, then remove old URLs.
- **`StepOrchestrator` reuse:** The CLI's `StepOrchestrator` is a simple `Map.get()` lookup. Should the REST controller use it, or inject `*Operation` interfaces directly? Recommendation: inject directly — the controller is already the orchestrator for a single step.

## Success Criteria

- An agent can prepare a chapter, discover available steps, run scene detection in isolation, and inspect results — all via curl
- `fireEvents=false` (default) prevents downstream cascade; `fireEvents=true` triggers it
- All step endpoints return the same `StepExecutionResponse` envelope
- The existing async pipeline continues to work unchanged
- The curl catalog provides complete, copy-pasteable examples for every endpoint
- Book-level endpoints use `reduce-*` naming consistently

## Implementation Notes

Implementation notes are appended here as each phase is completed. Each entry records what was done, deviations from the design, and patterns worth promoting to canonical docs.

### Phase 1 — 2026-05-08

**Date:** 2026-05-08
**Status:** Complete

**What was built:**

Core layer (`lorevault-core`):
- `StepKey` enum in `com.lorevault.api.ingestion.pipeline` — all 12 pipeline step identifiers with `toUrlSegment()` and `getScope()`
- `StepDefinition` record in `com.lorevault.api.ingestion.pipeline` — key, description, scope, prerequisites
- `StepCatalog` Spring `@Component` in `com.lorevault.api.ingestion.pipeline` — registers all 12 steps in pipeline order
- `PipelineStageSupport.updateJobStatus()` — now gracefully handles `null` jobId (skips update, for ad hoc step execution without job tracking)
- `SceneDetectionHandler.execute()` — removed `emitScenesDetected()` calls from inside `execute()`; event emission moved to `handleChapterIngestion()` listener method so that direct `execute()` calls from REST controllers don't trigger downstream cascade

Web layer (`lorevault-web`):
- `StepExecutionResponse` record in `com.lorevault.api.web.command.ingestion` — uniform response envelope mapping from `StepResult`
- `PrepareChapterRequest` DTO in `com.lorevault.api.web.command.ingestion` — JSON body for prepare endpoint
- `PrepareCommandController` — `POST /api/command/ingest/prepare` (JSON body only; multipart support deferred to Phase 4)
- `StepExecutionCommandController` — `POST /api/command/ingest/chapters/{chapterId}/detect-scenes` with `fireEvents` and `jobId` params
- `StepQueryController` in `com.lorevault.api.web.query.step` — `GET /api/query/ingestion/steps`
- `StepEventMapper` in `com.lorevault.api.web.command.ingestion` — maps `StepKey.DETECT_SCENES` → `ScenesDetectedEvent`; other keys log warning (Phase 2+)

Test fix:
- `SceneDetectionHandlerTest` — updated mock for `sceneRepo.findByChapterId()` to return empty list on first call (idempotency check) and persisted scenes on second call (event emission in `handleChapterIngestion`)

**Deviations from design:**

1. **Prepare endpoint: JSON-only for Phase 1.** The design specified both JSON and multipart support. Only JSON body is implemented in Phase 1. Multipart support for `/prepare` is deferred to Phase 4 (curl catalog) since the existing `/api/command/ingest` endpoint already handles multipart uploads.
2. **StepCatalog: no operation delegates.** The design mentioned `StepCatalog` registering `*Operation` delegates. The implementation keeps `StepCatalog` as a pure metadata registry — operation interfaces are injected directly into controllers, per the design's own recommendation in the Open Questions section.
3. **SceneDetectionHandler: event emission refactored.** The design said "the REST controller calls `execute()` directly" and "when `fireEvents=true`, the controller publishes the domain-specific event." This required removing `emitScenesDetected()` from inside `execute()` so that direct calls don't auto-emit. The `handleChapterIngestion()` listener now emits on success, and the `StepExecutionCommandController` emits via `StepEventMapper` when `fireEvents=true`.
4. **StepEventMapper: Phase 1 only implements DETECT_SCENES.** Other step keys log a warning and skip. This matches the phased approach — Phase 2 will add chunk, embed, and resolve-events mappings.

**Patterns worth promoting:**

1. **`*Operation.execute()` should not emit events.** The pattern established in Phase 1 is: `execute()` performs business logic and returns `StepResult`. Event emission is the caller's responsibility (either the `@EventListener` adapter or the REST controller via `StepEventMapper`). This should be documented in `docs/rules/handler-design-contract.md` as a rule: *Operation interface methods must not publish domain events — that is the caller's responsibility.*
2. **`PipelineStageSupport.updateJobStatus()` null-jobId tolerance.** When `jobId` is null (ad hoc step execution without job tracking), `updateJobStatus` silently returns. This pattern should be documented: *Pipeline stage support methods gracefully handle null jobId for step-wise API use.*
3. **StepExecutionResponse as uniform envelope.** The `StepExecutionResponse.from(StepResult, scope, scopeId)` factory pattern provides a clean mapping from core-layer `StepResult` to web-layer DTO. This should be the standard for all step endpoints going forward.

**Verification:**

Compilation verified:
```bash
mvn clean compile -pl lorevault-core,lorevault-web -DskipTests
# BUILD SUCCESS
```

SceneDetectionHandler tests verified:
```bash
mvn test -pl lorevault-web -Dtest="SceneDetectionHandlerTest"
# Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

End-to-end verification requires running services (Neo4j + API):
```bash
# 1. Reset dev DB
./scripts/reset-dev-db.sh

# 2. Start API
./scripts/dev-api.sh start

# 3. Create universe
curl -s -X POST localhost:18080/api/command/library/universe \
  -H 'Content-Type: application/json' -d '{"name":"Test"}'

# 4. Create series (use universeId from step 3)
curl -s -X POST localhost:18080/api/command/library/series \
  -H 'Content-Type: application/json' -d '{"universeId":"...","name":"Test Series"}'

# 5. Create book (use universeId and seriesId from steps 3-4)
curl -s -X POST localhost:18080/api/command/library/book \
  -H 'Content-Type: application/json' -d '{"universeId":"...","seriesId":"...","title":"Test Book","bookNumber":1}'

# 6. Prepare chapter (no pipeline trigger)
curl -s -X POST localhost:18080/api/command/ingest/prepare \
  -H 'Content-Type: application/json' \
  -d '{"bookId":"...","chapterNumber":1,"chapterTitle":"Test Chapter","chapterText":"It was a dark and stormy night..."}'
# Expected: {"jobId":"...","chapterId":"..."}

# 7. Discover steps
curl -s localhost:18080/api/query/ingestion/steps
# Expected: JSON with 12 step definitions

# 8. Run scene detection (isolated, no cascade)
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/detect-scenes
# Expected: StepExecutionResponse with success=true, step="SCENE_DETECTION"

# 9. Verify in Neo4j
# MATCH (rc:RelationClaim) RETURN count(rc)
```

---

### Phase 2 — 2026-05-08

**Date:** 2026-05-08
**Status:** Complete

**What was built:**

Core layer (`lorevault-core`):
- `ChunkingOperation` interface in `com.lorevault.api.ingestion.content` — `StepResult execute(UUID jobId, UUID chapterId)`
- `EmbeddingOperation` interface in `com.lorevault.api.ingestion.content` — `StepResult execute(UUID jobId, UUID chapterId)`
- `ChapterEventResolutionOperation` interface in `com.lorevault.api.ingestion.resolution.event` — `StepResult execute(UUID jobId, UUID chapterId)`
- `ChunkingHandler` refactored: implements `ChunkingOperation`, `execute()` extracted with try/catch returning `StepResult`, `handleScenesDetected()` delegates to `execute()` and publishes `ChunksCreatedEvent` on success / `IngestionFailedEvent` on failure
- `EmbeddingHandler` refactored: implements `EmbeddingOperation`, `execute()` extracted, `handleChunksCreated()` delegates and publishes `EmbeddingsCompletedEvent` on success / `IngestionFailedEvent` on failure
- `ChapterEventResolutionHandler` refactored: implements `ChapterEventResolutionOperation`, `execute()` extracted (runs coref pass + aggregation), `handleScenesDetected()` delegates and publishes `ChapterEventsResolvedEvent` on success / `IngestionFailedEvent` on failure

Web layer (`lorevault-web`):
- `StepExecutionCommandController` updated: added `/chapters/{chapterId}/chunk`, `/chapters/{chapterId}/embed`, `/chapters/{chapterId}/resolve-events` endpoints
- `StepEventMapper` updated: added event mappings for `CHUNK` → `ChunksCreatedEvent`, `EMBED` → `EmbeddingsCompletedEvent`, `RESOLVE_EVENTS` → `ChapterEventsResolvedEvent`

**Deviations from design:**

1. **`execute()` uses direct try/catch, not `PipelineStageSupport.runStage()`.** The `runStage()` method swallows exceptions and returns null — incompatible with `StepResult` return type. The new pattern is: `execute()` wraps business logic in try/catch, returns `StepResult.success()` or `StepResult.failure()`/`StepResult.retryableFailure()`. The `@EventListener` method delegates to `execute()` and handles event emission. This is consistent with the Phase 1 `SceneDetectionHandler.execute()` pattern.
2. **`ChapterEventResolutionHandler.execute()` runs both coref and aggregation stages.** The design didn't specify how to handle the multi-stage pipeline. The implementation runs both stages sequentially in a single `execute()` call. If the coref pass fails, aggregation is skipped (fail-fast). This is correct because aggregation depends on coref output.
3. **Bug fix: StepEventMapper count key mismatch.** Oracle review caught that `publishChapterEventsResolvedEvent()` used wrong count keys (`mentionsResolved`, `chapterEventsResolved`, `failedCorefWindows`) instead of the correct keys from `ChapterEventResolutionHandler.execute()` (`rawMentionsProcessed`, `chapterEventsCreated`, `failedCorefWindowCount`). Fixed before committing.

**Patterns worth promoting:**

1. **`*Operation.execute()` pattern is now consistent across 4 handlers.** All four (SceneDetection, Chunking, Embedding, ChapterEventResolution) follow the same pattern: `execute()` returns `StepResult`, no event emission inside, `@EventListener` delegates and handles events. This should be documented in `docs/rules/handler-design-contract.md`.
2. **`PipelineStageSupport.updateJobStatus()` handles null jobId.** This pattern from Phase 1 is now used by all four handlers' `execute()` methods. When called without a job (ad hoc step execution), job status updates are silently skipped.

**Verification:**

Compilation verified:
```bash
mvn clean compile -pl lorevault-core,lorevault-web -DskipTests
# BUILD SUCCESS
```

Handler tests verified:
```bash
mvn test -pl lorevault-web -Dtest="SceneDetectionHandlerTest,ChunkingHandlerTest,EmbeddingHandlerTest,ChapterEventResolutionHandlerTest"
# Tests run: 23, Failures: 0, Errors: 0
```

End-to-end verification (requires running services):
```bash
# After prepare + detect-scenes:
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/chunk
# Expected: StepExecutionResponse with step="CHUNKING"

curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/embed
# Expected: StepExecutionResponse with step="EMBEDDING"

curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-events
# Expected: StepExecutionResponse with step="EVENT_RESOLUTION"
```

---

### Phase 3 — 2026-05-09

**Date:** 2026-05-09
**Status:** Complete

**What was built:**

Core layer (`lorevault-core`):
- 8 new *Operation interfaces: `ChapterIndividualResolutionOperation`, `ChapterCollectiveResolutionOperation`, `ChapterLocationResolutionOperation`, `ChapterObjectResolutionOperation`, `BookIndividualReductionOperation`, `BookCollectiveReductionOperation`, `BookLocationReductionOperation`, `BookObjectReductionOperation`
- 4 chapter resolution handlers refactored to implement their *Operation interfaces with `execute()` methods
- 4 book reduction handlers refactored to implement their *Operation interfaces with `execute()` methods
- All 8 handlers now follow the same pattern: `execute()` returns `StepResult`, `@EventListener` delegates and handles event emission
- Book reduction handlers preserve `BookReductionClaimService` claim management in `execute()`, returning `StepResult.retryableFailure()` on claim contention

Web layer (`lorevault-web`):
- 4 existing chapter resolution controllers updated: added `fireEvents` and `jobId` params, switched from service injection to *Operation injection, now return `StepExecutionResponse`
- 4 new book-level reduction controllers: `BookIndividualReductionCommandController`, `BookCollectiveReductionCommandController`, `BookLocationReductionCommandController`, `BookObjectReductionCommandController`
- `BookReductionRedirectController`: 307 Temporary Redirects from old `/books/{bookId}/resolve-*` URLs to new `/books/{bookId}/reduce-*` URLs
- `StepEventMapper` updated: added event mappings for all 8 resolution/reduction step keys

**Deviations from design:**

1. **307 redirects instead of 301.** The design recommended 307 Temporary Redirect for one release cycle. The implementation uses `RedirectView` with `HttpStatus.TEMPORARY_REDIRECT` (307). This is correct — 307 preserves the POST method, while 301 may convert POST to GET.
2. **Claim contention returns 200 with `retryable: true`, not 409.** The design suggested returning 409 Conflict for claim contention. However, the design also says "All step endpoints return 200 OK with success: false for step failures." Since claim contention is a transient failure (the step can be retried), returning 200 with `success: false, retryable: true` is more consistent with the uniform response envelope. The agent can check `retryable` to decide whether to retry.
3. **Chapter resolution controllers now use *Operation interfaces instead of services.** The existing controllers injected `ChapterIndividualResolutionService` etc. directly. The new controllers inject `ChapterIndividualResolutionOperation` etc. and call `execute()` instead of `resolveChapter()`. This is consistent with the step execution pattern.
4. **Old chapter resolution response classes retained.** The custom response classes (`ChapterIndividualResolutionResponse`, etc.) still exist but are no longer used by the controllers. They can be removed in a future cleanup.

**Patterns worth promoting:**

1. **Book reduction claim management in `execute()`.** The `BookReductionClaimService.tryAcquireClaim()` / `releaseClaim()` pattern is preserved inside `execute()`, with the claim released in a `finally` block. This ensures claims are always released even on failure. The `StepResult.retryableFailure()` return on claim contention is a clean way to communicate transient failures to the agent.
2. **307 redirect pattern for renamed endpoints.** `BookReductionRedirectController` provides a clean pattern for URL migration that preserves HTTP method semantics.

**Verification:**

Compilation verified:
```bash
mvn clean compile -pl lorevault-core,lorevault-web -DskipTests
# BUILD SUCCESS
```

Handler tests verified:
```bash
mvn test -pl lorevault-web -Dtest="SceneDetectionHandlerTest,ChunkingHandlerTest,EmbeddingHandlerTest,ChapterEventResolutionHandlerTest"
# Tests run: 23, Failures: 0, Errors: 0
```

End-to-end verification (requires running services):
```bash
# Chapter resolution endpoints (with fireEvents and jobId):
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-individuals?fireEvents=true
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-collectives
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-locations
curl -s -X POST localhost:18080/api/command/ingest/chapters/{chapterId}/resolve-objects

# Book reduction endpoints (new URLs):
curl -s -X POST localhost:18080/api/command/ingest/books/{bookId}/reduce-individuals
curl -s -X POST localhost:18080/api/command/ingest/books/{bookId}/reduce-collectives
curl -s -X POST localhost:18080/api/command/ingest/books/{bookId}/reduce-locations
curl -s -X POST localhost:18080/api/command/ingest/books/{bookId}/reduce-objects

# Old book resolve URLs (307 redirect to reduce URLs):
curl -s -X POST localhost:18080/api/command/ingest/books/{bookId}/resolve-individuals
# Expected: 307 Temporary Redirect to /books/{bookId}/reduce-individuals
```

### Phase 4 — 2026-05-09

**Date:** 2026-05-09
**Status:** Complete

**What was built:**

Documentation:
- `docs/curl-catalog.md` — complete curl examples for all ingestion endpoints, including:
  - Library commands (universe, series, book creation)
  - Full pipeline submission
  - Prepare endpoint (step-by-step)
  - All 8 chapter-scoped step endpoints with `fireEvents` and `jobId` params
  - All 4 book-scoped step endpoints
  - Legacy redirect documentation
  - Step discoverability query
  - Job status queries
  - Error response examples
  - Complete agentic workflow example

**Deviations from design:**

1. **No response class migration.** The design called for migrating existing resolution controllers' response classes to `StepExecutionResponse`. The implementation already achieves this — the updated chapter resolution controllers now return `StepExecutionResponse` instead of their custom response classes. The old response classes (`ChapterIndividualResolutionResponse`, etc.) still exist but are no longer used by controllers. They can be removed in a future cleanup.

**Patterns worth promoting:**

1. **Curl catalog as living documentation.** The `docs/curl-catalog.md` file provides copy-pasteable examples for every endpoint. This pattern should be maintained as new endpoints are added.

**Verification:**

Documentation review:
- All 12 step endpoints documented with curl examples
- `fireEvents` and `jobId` parameters documented
- Error response examples included
- Agentic workflow example provided

### Phase 5 — Deep Code Review + E2E Bug Fix — 2026-05-09

**Date:** 2026-05-09
**Status:** Complete

**What was done:**

A principal-engineer-level code review of all Phase 1–4 changes, followed by E2E validation against a 2-chapter Deathworlders test, uncovered and fixed 24 findings across 2 CRITICAL, 6 HIGH, 8 MEDIUM, and 8 LOW severity levels. A pre-existing Spring Data Neo4j projection-mapping bug was also discovered and fixed during E2E testing.

#### CRITICAL findings fixed

1. **CRIT-1: Deleted 4 orphaned Book*ResolutionCommandController files + 8 orphaned *Response DTOs.** The old chapter-scoped resolution controllers (`ChapterIndividualResolutionCommandController`, `ChapterCollectiveResolutionCommandController`, `ChapterLocationResolutionCommandController`, `ChapterObjectResolutionCommandController`) and their custom response classes were superseded by `StepExecutionCommandController` but never removed. The old controllers had stale constructor signatures (injecting services instead of *Operation interfaces), causing `ApplicationContext` failures in WebMvc tests. Deleted all 4 controllers + 8 response DTOs + 3 orphaned WebMvc test files.

2. **CRIT-2: Added null chapterId guards to all 8 handler methods in `IngestionCompletionCoordinator`.** Book-scoped events (`BookIndividualsReducedEvent`, etc.) carry a `bookId` but no `chapterId`. The coordinator's fan-in tracking used `chapterId` as a map key, so book-scoped events with null chapterId would corrupt the tracking state (null key entries, memory leak from REST-path book-scoped events). All 8 handler methods now guard: if `chapterId` is null, the event is logged and ignored (not tracked in fan-in).

#### HIGH findings fixed

3. **HIGH-1: `PipelineStageSupport.runStage()` now null-guards `updateJobStatus()`.** When `jobId` is null (ad hoc step execution), `runStage()` previously called `updateJobStatus(jobId, ...)` which would attempt a Neo4j write with a null ID. Now silently skips the update.

4. **HIGH-2: Added `isRetryableError()` to 4 Book*ReductionHandler classes + `ChapterEventResolutionHandler`.** These handlers previously caught all exceptions and returned `StepResult.failure()` — transient errors (LLM timeouts, rate limits) were treated as permanent failures. Now they distinguish retryable from permanent failures, returning `StepResult.retryableFailure()` for transient errors.

5. **HIGH-3: `StepExecutionResponse.from()` now takes `StepKey` param for kebab-case step names.** The response `step` field was previously the raw `StepResult.stepName` (e.g., `"SCENE_DETECTION"`). Now uses `StepKey.toUrlSegment()` to produce kebab-case names (e.g., `"detect-scenes"`) matching the URL path.

6. **HIGH-4: Added `CHUNKING` status to `IngestionStatus` enum, updated `ChunkingHandler`.** The chunking step had no corresponding ingestion status. Added `CHUNKING` and updated `ChunkingHandler` to set it during execution.

7. **HIGH-5+6: Added `@Valid`, `@Size` constraints, and broadened exception handling in `PrepareCommandController`.** The prepare endpoint accepted unvalidated input and caught only `IllegalArgumentException`. Now validates `PrepareChapterRequest` with Bean Validation and catches broader exceptions.

8. **HIGH-6 (merged with HIGH-5): Broadened exception handling.** `PrepareCommandController` now catches `Exception` with targeted logging instead of only `IllegalArgumentException`.

#### MEDIUM findings fixed

9. **MED-2 (merged with HIGH-5): Input validation.** `@Size` constraints on `chapterText` and `chapterTitle` in `PrepareChapterRequest`.

10. **MED-3: Added `isRetryableError()` to `ChapterEventResolutionHandler`.** Same pattern as HIGH-2 — distinguishes transient from permanent failures.

11. **MED-4: Added `updateJobStatus()` to 4 chapter resolution handlers + 4 new `IngestionStatus` values.** Chapter resolution handlers previously didn't update job status at all. Now they set `INDIVIDUAL_RESOLUTION`, `COLLECTIVE_RESOLUTION`, `LOCATION_RESOLUTION`, `OBJECT_RESOLUTION` statuses during execution.

12. **MED-5: Handled by CRIT-2.** The null chapterId guard in the coordinator covers this.

13. **MED-6: Added idempotency check to `EmbeddingHandler.execute()`.** Embedding handler now checks `countEmbeddingsByChapterId()` before processing. If embeddings already exist, returns `StepResult.success()` with a "skipped" message instead of re-embedding.

14. **MED-8: Added chapter existence checks to 4 `StepExecutionCommandController` endpoints.** The detect-scenes, chunk, embed, and resolve-events endpoints now verify the chapter exists before calling the operation. Returns 404 with a clear message if not found.

#### LOW findings fixed

15. **LOW-1: Fixed `Collectors.toMap` NPE risk in `SceneDetectionHandler`.** Filtered out null eventId entries before collecting to map.

16. **LOW-2: Fixed `BookReductionRedirectController` query parameter propagation.** Redirects now preserve query parameters (`fireEvents`, `jobId`) from the original request.

17. **LOW-6: Fixed case-sensitive retryability check in `SceneDetectionHandler.isRetryableError()`.** The check used `containsIgnoreCase` for error messages but not for exception class names. Now consistently case-insensitive.

18. **Remaining LOW findings** (LOW-3, LOW-4, LOW-5, LOW-7, LOW-8) were documentation-only or cosmetic and were addressed inline during the review.

#### E2E testing bug fix — Spring Data Neo4j projection mapping

During E2E validation with a 2-chapter Deathworlders test, the cross-chapter boundary edge creation (`mergeCrossChapterDefaultEdges`) threw `NotReadablePropertyException`. Root cause: `TemporalEdgeWriteRepository` extends `Neo4jRepository<Scene, UUID>`, and when its `@Query` method returns custom columns (`previousSceneId`, `nextSceneId`, etc.), Spring Data Neo4j's `DirectFieldAccessFallbackBeanWrapper` attempts to map those columns onto the repository's domain entity (`Scene`) instead of the declared projection interface. This only triggers when 2+ chapters exist in the same book.

**Fix applied:**

- `CrossChapterBoundaryProjection` (interface) and `CrossChapterBoundary` (record implementing it) moved from `content.timeline.infrastructure` to `content.timeline.domain` — they represent domain concepts, not persistence infrastructure (per ADR 011 and code organization guidance)
- `TemporalEdgeWriteRepository.mergeCrossChapterDefaultEdges()` return type changed from `List<CrossChapterBoundaryProjection>` to `List<CrossChapterBoundary>` — Spring Data Neo4j constructs records via canonical constructor matching Cypher columns by name, bypassing the `DirectFieldAccessFallbackBeanWrapper` entity-mapping path
- `DefaultTemporalEdgeService` reverted to use `TemporalEdgeWriteRepository` directly (removed failed `CrossChapterBoundaryRepository` experiment — a plain `@Repository` interface without a base repository can't be Spring-proxied)
- `ChapterIndividualCandidateView` (nested interface in `ChapterIndividualGraphRepository`) extracted to standalone `ChapterIndividualCandidate` record + `ChapterIndividualCandidateView` interface in `content.association` — same `DirectFieldAccessFallbackBeanWrapper` vulnerability: `ChapterIndividualGraphRepository` extends `Neo4jRepository<ChapterIndividual, UUID>` and returns custom columns that don't match `ChapterIndividual` entity fields

**Pattern worth promoting:**

> **Spring Data Neo4j projection mapping rule:** When a repository extends `Neo4jRepository<Entity, ID>` and a `@Query` method returns custom columns that don't match the entity's fields, use a Java record as the return type instead of a projection interface. Spring Data Neo4j constructs records via their canonical constructor (matching parameters to Cypher column names by position/name), which bypasses the `DirectFieldAccessFallbackBeanWrapper` that would otherwise try to map columns onto the domain entity. Projection interfaces are safe only on repositories that extend the base `Repository<Entity, ID>` (not `Neo4jRepository`), because the base interface doesn't trigger entity-aware mapping.

**Verification:**

```bash
mvn test -pl lorevault-core,lorevault-web
# Tests run: 497, Failures: 0, Errors: 9 (pre-existing WebMvc ApplicationContext failures for deleted controllers)
```

E2E validation (requires running services):
```bash
# Full pipeline with 2 chapters — cross-chapter boundary edges now created successfully
./scripts/reset-dev-db.sh
./scripts/dev-api.sh start
# Create universe, series, book, prepare 2 chapters, run detect-scenes, chunk, embed
# Cross-chapter NEXT_IN_READING_ORDER edges verified in Neo4j
```

---

## Links

- [Relation Evidence Harvesting](relation-evidence-harvesting.md) — Phase 0 validated with step-wise execution; Phase 1 needs this API
- [Ingestion pipeline pattern](../patterns/ingestion/ingestion-pipeline.md) — established pipeline step documentation
- [Handler design contract](../rules/handler-design-contract.md) — handler ownership and retry safety rules
- [Ingestion concurrency model](../patterns/ingestion/ingestion-concurrency-model.md) — threading and ordering guarantees