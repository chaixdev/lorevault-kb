# Adopt Micrometer `Timer.Sample` for Pipeline Stage Timing

**Date:** May 23, 2026
**Status:** Parked — learning goal, independent maturity milestone
**Category:** Observability / Dev Maturity
**Prerequisite:** `StageDispatcher` (submission-flow-cleanup issue #7) — timing moves to one place

## Goal

Replace 13 copies of `System.currentTimeMillis()` manual wall-clock timing in handler `execute()` methods with Micrometer `Timer.Sample`. Achieve functional parity with current behavior (`StepResult.durationMs`, CLI response, log output) before plugging in any broader observability ecosystem (Prometheus, CloudWatch). This is a self-contained stepping stone toward AWS Phase D observability.

## Current State

Every handler computes elapsed time manually:

```java
long start = System.currentTimeMillis();
// ... work ...
long elapsed = System.currentTimeMillis() - start;
StepResult result = StepResult.success("CHUNKING", summary, elapsed);
```

13 copies of the same pattern. `elapsed` flows into `StepResult` → `StageCompletedEvent` → CLI response. The coordinator ignores it.

## Proposed

After the dispatcher centralizes `onTrigger`, replace raw timing with Micrometer in the dispatcher itself:

```java
Timer.Sample sample = Timer.start(meterRegistry);
// ... execute handler ...
long elapsedMs = sample.stop(Timer.builder("ingestion.stage.duration")
    .tag("stage", event.getStage().name())
    .description("Wall-clock duration of pipeline stage execution")
    .register(meterRegistry)) / 1_000_000;

StepResult result = StepResult.success(event.getStage().name(), summary, elapsedMs);
```

## Parity Check

| Concern | Status |
|---------|--------|
| `StepResult.durationMs` still populated | ✅ `stop()` returns nanos |
| CLI response includes elapsed | ✅ same `StepResult` flow |
| Handler logs show elapsed | ✅ dispatcher logs it |
| No metrics backend needed | ✅ Spring auto-creates `SimpleMeterRegistry` |
| Viewable without Prometheus | ✅ `/actuator/metrics/ingestion.stage.duration` |
| Migration cost when backend arrives | ✅ Zero — registry just swaps in |

## Dependencies

- `spring-boot-starter-actuator` — already on classpath
- Micrometer — pulled transitively by actuator
- `SimpleMeterRegistry` — Spring Boot auto-configures it

## Files Affected

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/orchestration/StageDispatcher.java` — 1 `Timer.Sample` replaces 13 `System.currentTimeMillis()` blocks (after issue #7)
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/pipeline/StepResult.java` — no change (already accepts `long durationMs`)

## Learning Goals

- Micrometer `Timer.Sample` / `Timer.builder()` API
- `/actuator/metrics` endpoint — inspect timer output without a backend
- Tag naming conventions (`ingestion.stage.duration` with `stage` tag)

## Estimated Effort

~20 minutes. Single change in the dispatcher after issue #7 is done. Purely additive — no behavior change.

## Relationship to AWS Path

When the pipeline moves to AWS Step Functions + CloudWatch (Phase D), `Timer.Sample` is replaced by distributed tracing (X-Ray segments). But the Micrometer instrumentation serves as a bridge: the pattern is already in place when the metrics backend arrives, and the code already separates timing from business logic.
