# Scene-localization anchor mismatches should surface as expected business failures

**Status:** NOT STARTED

## Summary

When scene coordinate localization cannot find an LLM-provided anchor string in chapter text, the pipeline currently throws a raw `RuntimeException`.

This should be treated as an expected business failure mode of scene processing, converted into a typed domain/business exception, and handled through existing ingestion failure pathways without generic runtime semantics.

## Problem

A known and foreseeable content-localization mismatch currently bubbles up as a generic runtime error:

- `Error localizing scene 4: Failed to localize scene 4 because start anchor '...' was not found`

This has two issues:

- it classifies an expected pipeline outcome as an unexpected technical crash
- it weakens failure intent, making operational triage and policy decisions (retryability, user messaging, metrics) less explicit

## Product Context

- Operators should see clear, explainable ingestion failures rather than opaque runtime errors.
- Expected model/data mismatch scenarios should map to stable failure categories.
- Better failure typing improves trust in ingestion status transitions and incident diagnostics.

## Technical Context

Relevant failure path observed in logs:

- `SceneProcessingService.localizeSceneCoordinates(...)` throws `RuntimeException` on missing anchor text.
- The exception propagates through scene detection flow and eventually marks ingestion failed via generic error handling.

Likely affected files:

- `lorevault-core/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
- existing AI/ingestion exception types in `lorevault-core/src/main/java/com/lorevault/api/ai/**` and `.../ingestion/**`

Operational example captured (2026-04-21):

- `Triad-based scene detection pipeline failed: Error localizing scene 4 ... start anchor ... was not found`

## Scope

- Define or reuse a typed business exception for scene-localization anchor mismatches.
- Replace raw runtime throw sites for this condition with the typed business exception.
- Ensure retryability semantics are explicit (default expectation: non-retryable content/localization mismatch unless policy says otherwise).
- Ensure ingestion failure reporting preserves useful context (scene index, anchor fragment, chapter/job correlation) without overexposing raw payloads.
- Add/adjust tests for exception mapping and resulting ingestion failure behavior.

## Out of Scope

- Reworking the full scene localization algorithm.
- Prompt redesign for scene extraction.
- Broad ingestion error taxonomy redesign beyond this specific failure class.
- UI redesign for failure presentation.

## Known Constraints / Prior Findings

- Anchor mismatch is expected in real-world LLM outputs and should not be modeled as an impossible state.
- Existing pipeline infrastructure already supports controlled failure propagation (`PipelineStageSupport` + `IngestionFailedEvent`).
- Retry behavior must stay deliberate: anchor-not-found usually indicates a deterministic content mismatch, not transient transport/API failure.
- Failure details should remain diagnosable while avoiding noisy stack-trace-first operator experience.

## Open Questions

- Should this map to an existing exception type (if one already encodes localization/business failure), or should a dedicated `SceneLocalizationException` be introduced?
- What exact payload should be retained on failure events/status properties (scene index, anchor hash/preview, coordinates) to balance observability and log hygiene?
- Should this failure always be non-retryable, or should policy allow bounded retry under specific parsing/localization uncertainty conditions?

## Success Criteria

- Missing-anchor localization path no longer throws bare `RuntimeException`.
- Failure propagates as a typed business exception with clear semantics.
- Ingestion status transitions to terminal failure through standard pipeline handling with meaningful structured reason.
- Automated tests cover the anchor-mismatch path and confirm expected retryability and failure mapping.

## Links

- Related planning: `stuck-ingestion-status.md`
- Related planning: `cross-chapter-temporal-linking-materialization-gap.md`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
