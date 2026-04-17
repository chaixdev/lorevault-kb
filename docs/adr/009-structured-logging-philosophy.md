# ADR 009: Adopt a Structured Logging Philosophy for the Ingestion Pipeline

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault adopts a structured, boundary-anchored logging philosophy for the ingestion pipeline and all async handlers.

Application log lines must carry consistent structured fields (job ID, chapter ID, stage, outcome) rather than free-form bracketed prefixes. Each pipeline stage emits exactly one `started` log and one terminal log (`completed`, `skipped`, or `failed`). Failures are logged once — at the boundary where they are caught — not re-logged as they propagate.

The concrete rules are in `docs/rules/logging-philosophy.md`.

## Context

The ingestion pipeline is async and event-driven. A chapter submission triggers a chain of Spring application events across `SceneDetectionHandler → ChunkingHandler → EmbeddingHandler` and parallel resolution branches. Failures can occur at any stage, retries are possible, and LLM calls add latency and cost.

Before this decision, log lines used an informal `[STAGE] message` prefix style with no consistent field set. This made it hard to:
- correlate log lines for a single job across stages
- distinguish a retryable failure from a terminal one
- know whether a stage was skipped vs. completed
- avoid logging book text or AI response content accidentally

Two approaches were considered:

**Option A — Informal prefix style (status quo)**  
Keep `log.info("[STAGE] message")` as-is. Low friction, no migration needed. Downside: no consistent fields, no correlation, hard to query, easy to accidentally log sensitive content.

**Option B — Structured boundary logging**  
Require a consistent field set on every stage boundary log. Log once per boundary. Propagate job/chapter IDs through every handler. Define explicit rules for what must never appear in logs. Downside: requires discipline and a written rule set.

## Why Option B

- The pipeline already has a graph-level observability layer (`StatusRecord` chain). Application logs should complement it, not duplicate it. The right role for log lines is fast operational triage — not a second copy of the graph audit trail.
- Correlation without a consistent `jobId` field is impractical once more than one job runs concurrently.
- The pipeline processes book text and LLM responses. Without an explicit rule, sensitive content will eventually appear in logs.
- The `log once at the right boundary` principle (derived from Stripe's canonical log line pattern) prevents duplicate noise from exception re-logging across layers.

## Implications

- All pipeline handlers must emit a `started` log and a terminal log per invocation.
- `PipelineStageSupport.runStage()` is the canonical failure boundary; it logs the failure once. Callers must not re-log the same exception.
- Book text, AI response payloads, and full API keys must never appear in log lines.
- The `[STAGE]` prefix style is not prohibited but should be replaced with structured field logging as handlers are touched.
- See `docs/rules/logging-philosophy.md` for the full rule set and field schema.
