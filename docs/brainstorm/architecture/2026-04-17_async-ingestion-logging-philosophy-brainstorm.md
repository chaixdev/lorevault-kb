# Async Ingestion Logging Philosophy Brainstorm — April 2026

**Date:** April 2026  
**Status:** Brainstorm — not yet accepted  
**Purpose:** Capture the broader logging and correlation strategy for LoreVault's async/event-driven ingestion system before the implementation hardens unevenly across handlers, thread boundaries, and future observability work.

---

## 1. Why This Exists

LoreVault now has enough asynchronous ingestion behavior that ad hoc logging is no longer sufficient.

We already have two important canonical docs:

- `docs/adr/009-structured-logging-philosophy.md`
- `docs/rules/logging-philosophy.md`

Those docs establish accepted direction and contributor rules.

What is still missing is the broader proposal layer that explains:

- how application logs should relate to graph-level observability
- how correlation should work across async event boundaries
- whether and how MDC should be introduced
- how far structured logging should go for a project of this size
- what rollout sequence is appropriate without over-engineering

This document exists to hold that larger design space while implementation is still incomplete.

---

## 2. The Problem We Are Actually Trying To Solve

The immediate trigger is operational ambiguity during ingestion debugging.

Today, a job can appear stuck in an intermediate state even after processing has effectively stopped. We already persist durable graph-level status through `StatusRecord` and `LlmCallRecord`, but runtime diagnosis is still harder than it should be because application logs are inconsistent across:

- stage boundaries
- retries
- async event listeners
- downstream branch fan-out
- success vs skip vs failure outcomes

The goal is **not** to replace the graph audit trail with logs.

The goal is to make it cheap to answer questions like:

- which handler last touched this job?
- did the stage actually start, skip, retry, fail, or finish?
- was the failure in business work, in status persistence, or in event publication?
- are we looking at one job, overlapping jobs, or stale current-status state?
- which lines belong to the same ingestion attempt across thread hops?

---

## 3. Observability Layers And Their Roles

LoreVault should explicitly treat observability as three different layers.

### 3.1 Durable graph audit trail

Mechanisms:

- `IngestionJob`
- `StatusRecord`
- `LlmCallRecord`

Purpose:

- survive restarts
- preserve historical truth
- support API responses and graph debugging
- retain expensive/important evidence about what happened

This is the source of truth for job history.

### 3.2 Application logs

Mechanisms:

- SLF4J log lines emitted by handlers, services, and support boundaries

Purpose:

- fast triage while the system is running
- cheap local debugging during development
- surfacing failures in the exact boundary where they happened
- correlating runtime flow across async fan-out and retries

Logs are the fast signal, not the durable record.

### 3.3 Metrics

Not yet implemented.

Likely future purpose:

- aggregate rate/failure/latency views
- alerting and trend visibility
- understanding system health without reading logs

Metrics should come later. LoreVault does not need to front-load a full observability platform before improving logging discipline.

---

## 4. Core Philosophy

### 4.1 Log once at the right boundary

Each meaningful ingestion boundary should emit one canonical lifecycle log, not a cascade of duplicate error lines as exceptions bubble upward.

For the current pipeline shape, the most important boundaries are:

- handler entry
- stage completion
- stage skip / idempotency exit
- stage failure
- retry attempt / retry exhaustion
- event publication where it is operationally significant

This preserves signal quality and makes grep-based debugging practical.

### 4.2 Logs complement graph records rather than duplicating them

If a detail already lives durably in `StatusRecord` or `LlmCallRecord`, logs should only repeat it when it helps real-time diagnosis.

Examples:

- good to log: `jobId`, `chapterId`, stage, outcome, retryability, counts, duration
- bad to log: raw chapter text, full LLM payloads, large structured response bodies already stored elsewhere

### 4.3 Prefer lifecycle/correlation logs over narration logs

The most useful logs are the ones that define what happened at a boundary:

- `started`
- `completed`
- `skipped`
- `failed`
- `retrying`
- `exhausted`
- `published`
- `received`

LoreVault should avoid chatty narrative logging that tries to describe every internal thought of the system.

### 4.4 Operator debugging should work without a tracing backend

This repo is not yet at the scale where it should require OpenTelemetry, Tempo, Honeycomb, or equivalent infrastructure before a developer can understand a job failure.

The logging strategy should work well with:

- local console logs
- `logs/lorevault-api.log`
- simple grep/search
- the operator UI's live SSE console

If distributed tracing arrives later, the logging shape should be compatible with it rather than blocked by it.

---

## 5. Canonical Lifecycle Events To Surface In Logs

For async ingestion, these are the events that matter most operationally.

### 5.1 Stage boundaries

Each stage invocation should surface:

- `started`
- `completed`
- `skipped`
- `failed`

This aligns with the current rules doc and should remain the minimum floor.

### 5.2 Retry boundaries

Retry behavior should be visible as first-class operational events.

Important states:

- `retrying`
- `retry_scheduled`
- `retry_exhausted`
- `retry_classifier_error` (debug-only, if retained)

The key need is to distinguish:

- a terminal failure
- a retryable failure that is still in progress
- a retry sequence that has ended and should now produce `FAILED`

### 5.3 Event boundaries

Because LoreVault is event-driven, there is real value in logging selected event handoff points:

- event published
- event received
- event ignored/skipped

But this must be selective.

We do **not** want every event emission to become noisy boilerplate. Only event boundaries that help explain async flow or missing downstream work should get canonical logs.

Best candidates:

- publish from the end of a stage into the next major branch
- receipt at async listeners
- completion coordination boundaries
- operator-triggered replay/reset actions

### 5.4 Operator actions

Manual actions from the operator UI should be logged clearly.

Examples:

- retrigger chapter location resolution
- rebuild book locations
- reset downstream derived state

These are especially important because they create system state changes outside the default happy-path ingestion flow.

---

## 6. Correlation Model

### 6.1 Minimum identifiers

Every ingestion boundary log should carry enough context to correlate one logical execution path.

Minimum practical fields:

- `jobId`
- `chapterId`
- `stage`
- `outcome`

Often useful additional fields:

- `bookId`
- `seriesId`
- `universeId`
- `eventType`
- `attempt`
- `retryable`
- `durationMs`
- `count` or stage-specific totals

### 6.2 Correlation versus causation

LoreVault should distinguish between:

- **correlation ID** — identifies the broader logical execution lineage
- **causation ID** — identifies the immediate event or action that triggered this step

For current project scope, there is a strong case for:

- using `jobId` as the primary correlation anchor for ingestion work
- optionally adding a lightweight event/action identifier later for causation-sensitive debugging

This is probably enough for now:

- `jobId` as the main cross-stage anchor
- `eventType` to show what kind of transition happened
- retry `attempt` where relevant

Only introduce separate explicit `correlationId` / `causationId` fields if real debugging shows that `jobId + eventType + attempt` is insufficient.

### 6.3 Relationship to overlapping jobs

If duplicate or overlapping jobs can exist for the same chapter, logs must make that visible rather than flattening all lines under `chapterId`.

That means:

- `jobId` must remain the primary correlation key
- `chapterId` alone is not enough
- operator-facing diagnostics should make concurrent jobs easy to spot

---

## 7. MDC: Appropriate, But Only With Discipline

### 7.1 Why MDC is attractive here

Mapped Diagnostic Context is appealing because LoreVault has async fan-out and repeated identifiers that we do not want every callsite to hand-thread manually forever.

MDC could help ensure that log lines consistently carry:

- `jobId`
- `chapterId`
- `stage`
- possibly `eventType` / `attempt`

This is especially helpful when a handler invokes several lower-level services that should inherit the same contextual identity.

### 7.2 Why MDC is not a free win

MDC becomes dangerous when teams assume it magically survives all thread and async boundaries.

In LoreVault, this is exactly where mistakes would happen:

- `@Async` listeners
- executor thread pools
- event publication and downstream handlers
- nested retries

If MDC is introduced carelessly, it will create false confidence and inconsistent logs.

### 7.3 Proposed stance

Adopt MDC as a convenience layer, not as the sole carrier of required identifiers.

That means:

- boundary logs should still be written intentionally with required fields
- MDC should enrich logs, not replace explicit correctness
- async propagation must be configured deliberately if MDC is used across executors

### 7.4 Practical rollout path

Reasonable staged approach:

1. standardize boundary log schema first
2. ensure important handlers already log explicit identifiers
3. add MDC only after the boundaries are stable
4. propagate MDC across async executors using the Spring Boot 3.5 / Spring Framework 6.2 era context-propagation mechanisms that fit the repo best
5. verify MDC behavior under async fan-out before treating it as reliable

This avoids building logging policy on top of context propagation that has not been proven in the codebase.

---

## 8. Structured Shape: How Far To Go

LoreVault should prefer structured, machine-parsable logs, but remain pragmatic.

### 8.1 Near-term recommendation

Prefer consistent key/value logging at canonical boundaries.

Examples:

- `stage=SCENE_SEGMENTATION outcome=started jobId=... chapterId=...`
- `stage=EMBEDDING_CHUNKS outcome=failed jobId=... chapterId=... retryable=true`

This can coexist temporarily with the current bracket-prefix style during migration.

### 8.2 Do not over-rotate into logging-framework complexity yet

Avoid prematurely introducing all of the following at once:

- custom appenders
- heavyweight JSON logging pipelines
- a large bespoke logging abstraction
- tracing-specific infrastructure before basic discipline exists

LoreVault needs consistency first, not observability theater.

### 8.3 Migration principle

When touching a handler or support boundary for real work, move it closer to the canonical structured shape instead of doing a repo-wide logging rewrite.

---

## 9. Data Hygiene And Safety

The ingestion pipeline handles narrative text and LLM outputs. Logging discipline must explicitly protect against accidental leakage.

Never log:

- raw chapter text
- scene JSON payloads
- triad analysis JSON payloads
- full LLM request/response bodies
- secrets or full API keys

Use caution with:

- filenames
- user-entered questions in the operator UI
- exception messages that may embed payload fragments

If a payload must be diagnosable, prefer:

- counts
- identifiers
- truncated safe labels
- durable storage in a graph record already designed for that purpose

---

## 10. Recommended Rollout Sequence

### Phase 1 — Boundary consistency

- align all major ingestion handlers to the current rules doc
- ensure explicit `started/completed/skipped/failed`
- ensure `jobId` and `chapterId` are always present
- eliminate duplicate failure re-logging

### Phase 2 — Retry visibility

- add canonical retry and retry-exhaustion logs
- make retry vs terminal failure unambiguous
- verify that terminal `FAILED` status always has a matching operational log trail

### Phase 3 — Async handoff visibility

- add selected publish/receive logs at high-value event boundaries
- add operator-action logs for manual replay/reset controls
- confirm completion-coordination logs are easy to follow

### Phase 4 — MDC/context propagation

- add MDC enrichment where it reduces manual repetition
- configure async propagation intentionally
- verify behavior in real thread-pool usage

### Phase 5 — Metrics later, if justified

- promote the most useful counters and timings into metrics
- avoid building dashboards before the logging/event vocabulary is stable

---

## 11. Open Questions

These are the main unresolved areas that should remain exploratory for now.

### 11.1 Should selected event publication/receipt boundaries be logged universally or only at major fan-out points?

Too much event logging will create noise. Too little will obscure async gaps.

### 11.2 Should `jobId` remain the only primary correlation anchor, or do we also need explicit `correlationId` and `causationId` fields?

Current instinct: start with `jobId`, `eventType`, and `attempt`; only add more if debugging proves the need.

### 11.3 Should LoreVault standardize on MDC + structured arguments now, or keep explicit fields in messages until the async propagation story is verified?

Current instinct: explicit first, MDC second.

### 11.4 Should the live SSE operator feed reuse the same event vocabulary as logs?

There is strong value in shared vocabulary (`started`, `failed`, `retrying`, `completed`), but logs and SSE may still need different payload shapes.

---

## 12. Relationship To Existing Canonical Docs

This brainstorm should not be read as replacing the current accepted docs.

Current canonical truth remains:

- `docs/adr/009-structured-logging-philosophy.md`
- `docs/rules/logging-philosophy.md`
- `docs/patterns/ingestion-job-observability.md`

Those docs answer:

- what decision was accepted
- what contributors should do repeatedly
- how graph-level observability works today

This brainstorm answers a different question:

- how the broader logging/correlation/rollout strategy should evolve across async ingestion boundaries without over-building too early

---

## 13. Likely Promotion Path Later

If this area stabilizes further, likely promotion targets are:

- **ADR update or successor ADR** if MDC/context propagation becomes a real architecture-level decision
- **Rules update** if the boundary vocabulary or mandatory fields change
- **Pattern update** if async logging/event correlation becomes part of the present-state ingestion mechanism that is hard to infer from code

Until then, this document should remain a working proposal.
