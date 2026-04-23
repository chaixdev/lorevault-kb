# Logging Philosophy

**Applies to:** All ingestion pipeline handlers, async event listeners, and service boundaries.

This document defines the rules for application-level logging in LoreVault. It is the companion to ADR 009 and the Ingestion Observability pattern.

---

## The Three Tiers of Observability

LoreVault has three distinct observability layers. Do not conflate them.

| Tier | Mechanism | Purpose |
|---|---|---|
| **Graph audit trail** | `StatusRecord` + `LlmCallRecord` nodes in Neo4j | Durable, queryable job history; survives restarts |
| **Application logs** | SLF4J log lines | Fast operational triage; not durable |
| **Metrics** | Not yet implemented | Aggregated counts and rates |

Application logs are for triage, not for duplicating the graph audit trail. Do not log information that is already captured in a `StatusRecord` unless it aids immediate debugging.

---

## Rule 1 — Log Once at the Right Boundary

Each pipeline stage emits **exactly one `started` log** and **exactly one terminal log** (`completed`, `skipped`, or `failed`).

`PipelineStageSupport.runStage()` is the canonical failure boundary. It logs the failure once. **Callers must not re-log the same exception** after delegating to `runStage`.

```java
// CORRECT — one started, one terminal
log.info("[CHUNKING] Starting for job={}, chapter={}", jobId, chapterId);
stageSupport.runStage(this, "CHUNKING", jobId, chapterId, () -> {
    // ... work ...
    log.info("[CHUNKING] Completed: {} chunks from {} scenes", totalChunks, scenes.size());
    return null;
}, e -> false);

// WRONG — re-logging after runStage already logged it
try {
    stageSupport.runStage(...);
} catch (Exception e) {
    log.error("CHUNKING failed", e); // duplicate — runStage already logged this
}
```

---

## Rule 2 — Mandatory Fields on Stage Boundary Logs

Every `started` and terminal log line for a pipeline stage **must include** `jobId` and `chapterId`. Prefer including the stage name as a bracketed prefix or as a field.

Minimum required fields:

| Field | Type | Notes |
|---|---|---|
| `jobId` | UUID | Always present for pipeline stages |
| `chapterId` | UUID | Always present for pipeline stages |
| stage outcome | implicit in message | `started`, `completed`, `skipped`, `failed` |

Recommended additional fields on terminal logs:

| Field | Example | Notes |
|---|---|---|
| count of work done | `chunkCount=47` | Helps detect silent zero-output failures |
| duration | `durationMs=1842` | Optional; useful for LLM stages |
| retry indicator | `retryable=true` | On failure logs |

---

## Rule 3 — Lifecycle Events

Every handler entry point must emit these log lines:

| Event | Level | When |
|---|---|---|
| `started` | `INFO` | First line of the handler, before any branching |
| `completed` | `INFO` | Terminal success path |
| `skipped` | `WARN` | Any early return (idempotency guard, empty input) |
| `failed` | `ERROR` | Terminal failure — emitted by `PipelineStageSupport`, not the caller |

A `skipped` log is required whenever a handler returns early without doing its primary work. This prevents silent no-ops from being invisible in logs.

```java
// CORRECT — skipped is explicit
if (!existingScenes.isEmpty()) {
    log.warn("[SCENE_DETECTION] Skipping: {} existing scenes found for chapter={}", 
             existingScenes.size(), chapterId);
    emitScenesDetected(...);
    return null;
}
```

---

## Rule 4 — What Must Never Appear in Logs

The following must **never** appear in any log line at any level:

- Book text or chapter raw content (`rawText`, `chapterText`, or any substring thereof)
- AI response payloads (LLM output, scene detection JSON, triad analysis JSON)
- Full API keys or secrets (even partially — use `key=...abc` at most)
- User-submitted filenames beyond a safe length (truncate to 100 chars)

LLM call payloads are captured in `LlmCallRecord` graph nodes. Do not duplicate them in logs.

---

## Rule 5 — Log Levels

| Level | Use for |
|---|---|
| `ERROR` | Terminal failures that require operator attention; unrecoverable states |
| `WARN` | Retryable failures; skipped stages; unexpected-but-handled conditions |
| `INFO` | Stage started/completed; significant state transitions |
| `DEBUG` | Internal loop progress (e.g., per-scene chunk counts); verbose detail useful only during development |

Do not use `INFO` for per-item loop progress inside a stage. Use `DEBUG`.

```java
// WRONG — INFO inside a loop
for (Scene scene : scenes) {
    log.info("Processing scene {}", scene.getId()); // noisy at INFO
}

// CORRECT
log.debug("[CHUNKING] Processing scene {}", scene.getId());
```

---

## Rule 6 — Failure Classifier Errors

`PipelineStageSupport.runStage()` catches exceptions from the retry classifier itself and logs them at `DEBUG`. Do not promote these to `WARN` or `ERROR` — a classifier failure is not a pipeline failure.

---

## Rule 7 — Idempotency Guards Must Log at WARN

When a handler detects existing work and skips re-processing (idempotency guard), log at `WARN` with the count of existing items. This makes re-submissions visible without being alarming.

```java
log.warn("[SCENE_DETECTION] Skipping detection: {} scenes already exist for chapter={}", 
         existingScenes.size(), chapterId);
```

---

## Recommended Log Line Shape

For pipeline stage boundaries, prefer this shape:

```
[STAGE_NAME] <outcome>: jobId=<uuid>, chapterId=<uuid>[, key=value ...]
```

Examples:

```
[SCENE_DETECTION] Starting: jobId=abc123, chapterId=def456
[SCENE_DETECTION] Completed: jobId=abc123, chapterId=def456, sceneCount=7
[CHUNKING] Skipping: jobId=abc123, chapterId=def456, existingChunks=47
[EMBEDDING] Failed: jobId=abc123, chapterId=def456, retryable=true
```

---

## Relationship to Graph Observability

Application logs and the `StatusRecord` chain serve different purposes:

- **Logs** are for immediate triage. They are ephemeral and not queryable after rotation.
- **StatusRecord nodes** are the durable audit trail. They survive restarts and are queryable via the API.

When a stage completes, the `StatusRecord` update is the source of truth. The log line is the fast signal. Do not skip the `StatusRecord` update in favor of a log line, or vice versa.

See [Ingestion Job Observability](../patterns/ingestion/ingestion-job-observability.md) for the graph observability layer.

---

## Sources

These rules are derived from:

- Stripe canonical log lines: https://brandur.org/canonical-log-lines
- PostHog logging best practices: https://posthog.com/docs/logs/best-practices
- structlog best practices: https://structlog.readthedocs.org/en/stable/logging-best-practices.html
- OneUptime event metadata design: https://oneuptime.com/blog/post/2026-01-30-event-metadata-design/view
