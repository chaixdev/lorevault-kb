# Agent-Driven Step Execution API

**Status:** NOT STARTED  
**Last Updated:** May 08, 2026

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

- Removing the `lorevault-cli` module (separate cleanup)
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

### `StepCatalog` moves to core

`StepKey`, `StepDefinition`, and `StepCatalog` currently live in `lorevault-cli`. They move to `lorevault-core` since the REST API now needs them. The CLI module's copy is removed when the CLI module is deleted.

### `StepResult` already in core

`StepResult` is already in `lorevault-core/src/main/java/com/lorevault/api/ingestion/pipeline/StepResult.java`. The REST response envelope maps directly from it.

### `*Operation` interfaces already in core

All handler interfaces (`SceneDetectionOperation`, etc.) are already extracted. The REST controllers inject these interfaces and call `execute()` directly — same pattern the existing resolution controllers use with their service classes.

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
| Delete `lorevault-cli` module | Remove module, POM reference, `application.yml` |
| `StepKey`/`StepCatalog`/`StepOrchestrator` | Remove CLI-specific copies from `lorevault-cli`; core versions remain |

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

## Links

- [CLI Stage Runner planning](cli-stage-runner.md) — the CLI module that will be removed; this doc replaces its step-execution goals
- [Relation Evidence Harvesting](relation-evidence-harvesting.md) — Phase 0 validated with step-wise execution; Phase 1 needs this API
- [Ingestion pipeline pattern](../patterns/ingestion/ingestion-pipeline.md) — established pipeline step documentation
- [Handler design contract](../rules/handler-design-contract.md) — handler ownership and retry safety rules
- [Ingestion concurrency model](../patterns/ingestion/ingestion-concurrency-model.md) — threading and ordering guarantees