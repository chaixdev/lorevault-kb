# LVREF-085-0 Follow-up: Restore LlmCallRecord persistence for triad-based scene analysis

Status: proposed

## Problem

During the LV-085-0 triad refactor, scene triads are analyzed in-memory (Pass 2 triad) via `TriadOrchestrationService` and `SceneDetectionClient.detectScenesPass2Triad(...)`. While the LLM call logging infrastructure exists (`LlmCallLoggingService`, `LlmCallRecord`, Neo4j mappings), we lost guaranteed persistence linkage between each triad LLM call and the ingestion job/status due to the in-memory flow and lack of explicit logging at the triad step level.

We need to restore LLM call persistence for observability, auditability, and debugging. Each triad Pass 2 call must produce an `LlmCallRecord` linked to the current ingestion job and, when possible, to the current `StatusRecord`.

## Goals (implementation-agnostic)

- Every triad Pass 2 LLM request is logged with an `LlmCallRecord`.
- Each record links to the active `IngestionJob` (OF_JOB) and, if available, the current `StatusRecord` (OF_STATUS).
- Logging does not materially alter the triad orchestration flow, latency, or error semantics.
- Input privacy controls are respected (store rendered prompt and bodies per `LoreVaultLlmLoggingProperties`).
- Minimal code footprint with clear seams for future analysis/metrics.

## Non-goals

- Changing triad parsing or temporal edge persistence behavior.
- Introducing new storage backends or external telemetry systems.

## Context

- Triad orchestration entrypoint: `TriadOrchestrationService.analyzeChapterTriads(jobId, chapter)`
- Triad LLM call site: `SceneDetectionClient.detectScenesPass2Triad(jobId, systemPrompt, userVars)` → delegates to `executeSceneDetectionCall(jobId, step, ...)` which already calls `LlmCallLoggingService.logCall(...)` with the provided `jobId`.
- Retry-aware pipeline: `RetryAwareSceneDetectionService.performFullSceneDetection(...)` invokes triad orchestration with the `jobId`.
- LLM logging infra persists records to Neo4j and attaches OF_JOB/OF_STATUS when IDs are provided.

Observation: The building blocks exist and are partially wired. The gap is ensuring that all triad calls consistently carry the `jobId` and use a distinct `step` value for analytics, and optionally enriching step metadata.

## Acceptance criteria

- When running ingestion with triad Pass 2 enabled, each triad call emits one `LlmCallRecord` in the graph with `jobId` populated and relationships set.
- The `step` is standardized as `scene-detection-pass2-triad` for the triad variant.
- Logging respects configuration flags (enabled/disabled, truncate sizes, body persistence) and includes token estimates and latency.
- Unit/integration tests verify persistence of `LlmCallRecord` for triad calls and correct linkage to `IngestionJob` and `StatusRecord` when available.

## Solution alternatives (per LLM_DEVELOPMENT_PLAN)

### Approach A: Standardize a triad-specific step and reuse existing logging hook (low complexity)

- Description: Ensure `SceneDetectionClient.detectScenesPass2Triad` passes `step = "scene-detection-pass2-triad"` to `executeSceneDetectionCall(...)`. Keep current `jobId` threading from `RetryAwareSceneDetectionService` → `TriadOrchestrationService` → `SceneDetectionClient` intact. Add a focused test.
- Files: `SceneDetectionClient.java` (constant/parameter), tests in `src/test/java/.../LlmCallRecordPersistenceIntegrationTest.java` or new service test.
- Pros: Minimal change, uses existing logging infrastructure, predictable semantics.
- Cons: Requires discipline to always provide jobId to triad orchestration callers.
- Complexity: Low
- Integration impact: Minimal
- CQRS impact: Command side only

### Approach B: Introduce a TriadMetricsLogger decorator around SceneDetectionClient (moderate complexity)

- Description: Create a decorator that wraps triad calls and guarantees `jobId` propagation and step naming. The decorator can enrich context (triad indices, scene UUIDs) in the inputPreview or metadata fields.
- Files: New `TriadLoggingSceneDetectionClient` + wiring in Spring config; tests.
- Pros: Strong boundary for logging concerns; avoids leakage when adding more triad variants.
- Cons: Extra indirection, more wiring and tests.
- Complexity: Medium
- Integration impact: Moderate
- CQRS impact: Command side only

### Approach C: Event-sourced logging via ApplicationEvent (higher complexity)

- Description: Publish an event `TriadLlmCallCompleted` including jobId, step, prompts, and response. An async listener persists `LlmCallRecord`. Useful if we want to decouple logging from call path, at cost of eventual consistency.
- Files: New event + listener, Spring config, tests.
- Pros: Decouples logging, allows async processing and potential batching.
- Cons: Eventual consistency, more moving parts.
- Complexity: High
- Integration impact: Significant
- CQRS impact: Command side only

## Recommendation

Adopt Approach A now: it’s the smallest change with clear value and aligns with current patterns. We can evolve to B if we later need richer triad telemetry.

## Test plan (sketch)

- Extend `LlmCallRecordPersistenceIntegrationTest` with a triad scenario:
  - Create a job and status; enable logging; stub `ChatClient` to return a small triad XML; call `SceneDetectionClient.detectScenesPass2Triad(jobId, systemPrompt, userVars)`; verify a persisted `LlmCallRecord` with step = `scene-detection-pass2-triad`, job/status linkage, and response hash/truncation behavior.
- Add a service-level test on `RetryAwareSceneDetectionService.performFullSceneDetection` that asserts triad calls produce records using a fake `SceneDetectionClient` or spy.

## Follow-ups

- Consider adding triad context (previous/current/next scene IDs and indices) into inputPreview metadata when safe.
- Dashboard/query for LLM calls per job and step including timing distribution.
