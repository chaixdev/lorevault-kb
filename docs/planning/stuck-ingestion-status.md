# Stuck ingestion status sometimes remains in an intermediate state

**Status:** PARKED

## Summary

Ingestion jobs sometimes stop making progress while the UI and persisted job state still show an intermediate status rather than a terminal failure.

The problem is intermittent and currently difficult to reproduce reliably, so the investigation is being parked until it becomes relevant again or enough evidence accumulates.

## Problem

Processing can appear to stop, but the job does not always end in `FAILED`.

This creates ambiguity between:

- a job that is legitimately still progressing
- a job that stopped but failed to persist the terminal state
- a divergence between the current-status pointer and the broader status history

## Product Context

- The operator UI can show a job as still in progress when it is effectively no longer making progress.
- This increases debugging friction and weakens confidence in the operational view.
- It makes it harder to know whether an operator should wait, retry, or investigate deeper.

## Technical Context

The investigation focused on the event-driven ingestion pipeline and its status persistence behavior.

Key areas examined:

- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionJobService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionCompletionCoordinator.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/ChunkingHandler.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/EmbeddingHandler.java`
- async branch handlers and book-reduction follow-up handlers
- `StatusRecord` and the `currentStatus` pointer on `IngestionJob`

Related operational visibility work has already been added in this session:

- async branch/coordinator lifecycle logging
- retry / retry-exhaustion logging in the scene detection LLM path

## Scope

- Preserve the current investigation findings so they can be resumed later.
- Capture the most plausible failure modes and where to look next.
- Keep enough context to avoid redoing the same exploratory work from scratch.

## Out of Scope

- Implementing a fix right now
- Forcing reproducibility before better evidence exists
- Broad architectural refactoring unrelated to this specific issue

## Known Constraints / Prior Findings

### Observed behavior

- A same-job scene-detection retry can legitimately leave the visible status at `SCENE_SEGMENTATION` while retry is still ongoing.
- But if processing has actually stopped and the job remains in an intermediate state, that points to a persistence or coordination problem rather than normal retry behavior.

### Strongest hypotheses found so far

1. **Status persistence / current-status divergence**
   - `StatusRecord` history and the `currentStatus` pointer may diverge.
   - A job may have historical evidence of failure or stalled work while still pointing at an older intermediate status.

2. **Failure path not persisting terminal status cleanly**
   - A terminal failure may not always produce the expected `FAILED` update.
   - Suspected hotspots included `updateJobStatus(...)` and current-status swapping behavior.

3. **Async last-writer-wins race**
   - Multiple async handlers using separate transactions may overwrite or outpace each other in a way that leaves stale visible status.

4. **Overlapping or duplicate job behavior**
   - Active-job checks appear read-based rather than an obviously atomic guard.
   - That leaves open the possibility of overlapping work confusing visible status.

### Things already ruled in / clarified

- The issue is not explained simply by the existence of retries.
- SSE refresh cadence was not the core problem; the more important question was state correctness.
- The issue became harder to investigate because reproduction is sporadic and dev databases were reset multiple times.

### Related implementation/context findings

- A stale locally running Java process once caused confusing UI verification, but that was a separate operational issue, not the core ingestion-state bug.
- SSE branch comparison later showed one useful fix (`ChapterIngestionEvent` extending `IngestionEvent`), but that is also adjacent rather than the root cause here.

## Open Questions

- Under exactly what failure path does the job fail to land in `FAILED`?
- Is the dominant problem status-history persistence, current-status pointer maintenance, or async branch coordination?
- Can duplicate or overlapping jobs for the same chapter contribute to the visible inconsistency?
- Should terminal-state writes be hardened further in the presence of downstream async failures?

## Success Criteria

- We can reproduce the problem with enough evidence to distinguish retry-in-progress from true stuck state.
- We can identify whether the bug is caused by state persistence divergence, async race conditions, or overlapping work.
- We have enough evidence to implement a focused fix rather than broad speculative hardening.

## Links

- Related brainstorm: `../brainstorm/architecture/2026-04-17_async-ingestion-logging-philosophy-brainstorm.md`
- Related pattern: `../patterns/ingestion-job-observability.md`
- Related ADR: `../adr/009-structured-logging-philosophy.md`
- Related rules: `../rules/logging-philosophy.md`
