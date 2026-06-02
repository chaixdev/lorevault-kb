# Stuck ingestion status sometimes remains in an intermediate state

**Status:** PARKED - mitigation shipped, revisit if the issue reappears

## Summary

Ingestion jobs sometimes stop making progress while the UI and persisted job state still show an intermediate status rather than a terminal failure.

The problem is intermittent and currently difficult to reproduce reliably. A focused mitigation has now been shipped for the strongest concrete failure path found so far, but the broader investigation remains parked unless the issue reappears or better evidence accumulates.

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
- Record the shipped mitigation so future investigation can distinguish old evidence from still-open behavior.

## Out of Scope

- Implementing a fix right now
- Forcing reproducibility before better evidence exists
- Broad architectural refactoring unrelated to this specific issue

## Known Constraints / Prior Findings

### Observed behavior

- A same-job scene-detection retry can legitimately leave the visible status at `SCENE_SEGMENTATION` while retry is still ongoing.
- But if processing has actually stopped and the job remains in an intermediate state, that points to a persistence or coordination problem rather than normal retry behavior.

### Strongest hypotheses found so far

1. **Embedding completion tracking dead zone**
   - `EmbeddingHandler.completeIngestion(...)` previously wrapped completion tracking in a local `try/catch` and swallowed exceptions.
   - If embeddings were generated successfully but completion tracking failed before `EmbeddingsCompletedEvent` was published, the branch could finish without notifying `IngestionCompletionCoordinator` and without emitting `IngestionFailedEvent`.
   - That created a plausible path to jobs remaining in `EMBEDDING_CHUNKS` or another intermediate state.

2. **Status persistence / current-status divergence**
   - `StatusRecord` history and the `currentStatus` pointer may diverge.
   - A job may have historical evidence of failure or stalled work while still pointing at an older intermediate status.

3. **Failure path not persisting terminal status cleanly**
   - A terminal failure may not always produce the expected `FAILED` update.
   - Suspected hotspots included `updateJobStatus(...)` and current-status swapping behavior.

4. **Async last-writer-wins race**
   - Multiple async handlers using separate transactions may overwrite or outpace each other in a way that leaves stale visible status.

5. **Overlapping or duplicate job behavior**
   - Active-job checks appear read-based rather than an obviously atomic guard.
   - That leaves open the possibility of overlapping work confusing visible status.

## Mitigation Shipped Since Parking

The strongest concrete failure path above has now been mitigated.

- `EmbeddingHandler.completeIngestion(...)` no longer swallows completion-tracking exceptions.
- If completion tracking now fails after embeddings are generated, the exception bubbles back to `PipelineStageSupport`.
- That means the existing failure path emits `IngestionFailedEvent` and marks the job `FAILED` instead of leaving it in an intermediate state with no terminal signal.
- Regression tests were added around both:
  - `EmbeddingHandler` success vs. completion-tracking failure behavior
  - `PipelineStageSupport` failure-event / failed-status behavior

This mitigation is intentionally narrow: it addresses the strongest observed dead zone without claiming that every intermittent stuck-status report is now explained or impossible.

### Things already ruled in / clarified

- The issue is not explained simply by the existence of retries.
- SSE refresh cadence was not the core problem; the more important question was state correctness.
- The issue became harder to investigate because reproduction is sporadic and dev databases were reset multiple times.

### Related implementation/context findings

- A stale locally running Java process once caused confusing UI verification, but that was a separate operational issue, not the core ingestion-state bug.
- SSE branch comparison later showed one useful fix (`ChapterIngestionEvent` extending `IngestionEvent`), but that is also adjacent rather than the root cause here.

## Open Questions

- If the issue reappears, does it still involve missing terminal status writes after the embedding dead-zone mitigation?
- Is the dominant remaining problem status-history persistence, current-status pointer maintenance, or async branch coordination?
- Can duplicate or overlapping jobs for the same chapter contribute to the visible inconsistency?
- Should terminal-state writes be hardened further in the presence of downstream async failures?

## Success Criteria

- We can tell whether the shipped embedding mitigation removed the originally strongest failure path.
- If the problem reappears, we can reproduce it with enough evidence to distinguish retry-in-progress from true stuck state.
- We can identify whether the remaining bug is caused by state persistence divergence, async race conditions, or overlapping work.

## Links

- Related brainstorm: `../brainstorm/architecture/2026-04-17_async-ingestion-logging-philosophy-brainstorm.md`
- Related pattern: `../patterns/ingestion/ingestion-job-observability.md`
- Related ADR: `../adr/009-structured-logging-philosophy.md`
- Related rules: `../rules/logging-philosophy.md`
