# Cross-chapter temporal linking is analyzed but not materialized

**Status:** PARKED

## Summary

The current scene temporal-linking pipeline can analyze cross-chapter context during triad processing, but that information is not being materialized into cross-chapter `TEMPORAL` edges.

This creates a mismatch between the conceptual pipeline and the implemented behavior: structural cross-chapter reading order is represented in the graph, while inferred cross-chapter temporal precedence is not.

## Problem

The present implementation allows triad analysis to look across a chapter boundary, but the later persistence/read path remains effectively chapter-local.

As a result, cross-chapter temporal information can be present in the triad-analysis phase without becoming durable graph structure that downstream consumers can use.

## Product Context

- Operators and developers can reasonably expect temporal analysis to preserve cross-chapter continuity when the system already reasons over cross-chapter triads.
- The graph currently presents a mixed picture: cross-chapter structural adjacency is visible, but cross-chapter temporal precedence is missing.
- This makes UAT results harder to interpret and weakens confidence that the temporal model matches narrative continuity across chapter boundaries.

## Technical Context

Relevant implementation areas:

- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadBuilderService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadOrchestrationService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalEdgeWriteRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalReadRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/EventOrderingService.java`

Current behavior observed from code inspection:

1. triads are built per chapter, but may include the previous scene from the previous chapter
2. triad analysis therefore has access to some cross-chapter temporal context
3. post-persistence temporal edge application resolves scene IDs from a map built for the current chapter only
4. cross-chapter scene references are therefore not reliably resolvable during temporal edge persistence
5. read-side chapter ordering also explicitly queries only same-chapter `TEMPORAL` edges

The data model and low-level repository write method can support cross-chapter `TEMPORAL` edges, but the current orchestration and read path do not materialize or consume them as a first-class behavior.

## Scope

- Preserve the investigation findings about the current cross-chapter temporal-linking gap.
- Capture the mismatch between triad analysis scope and temporal materialization scope.
- Keep enough context for a later design/implementation pass that aligns the temporal pipeline with intended narrative continuity.

## Out of Scope

- Picking the final redesign now
- Implementing cross-chapter temporal materialization immediately
- Redesigning all temporal read-side behavior in this planning item
- Treating structural reading-order edges as equivalent to inferred temporal precedence

## Known Constraints / Prior Findings

### Confirmed behavior

- Cross-chapter `NEXT_IN_READING_ORDER` is currently materialized successfully.
- Cross-chapter `TEMPORAL` is not appearing in UAT.
- This is not primarily explained by structural edge failure or chapter-number issues.

### Current architecture boundary

- `TriadBuilderService` can provide cross-chapter previous-scene context for triad analysis.
- `TriadEdgePersistenceService` persists from a chapter-scoped `sceneIndex -> persisted scene ID` map.
- That makes persistence effectively chapter-local even when the analysis context was broader.
- `TemporalReadRepository.findChapterEventEdges(...)` explicitly limits read-side temporal precedence to edges whose endpoints are both scenes in the same chapter.

### Interpretation

- This looks less like a narrow bug and more like an implementation/model mismatch.
- The conceptual pipeline suggests that triad analysis results could feed a later temporal-link materialization phase.
- The implemented pipeline instead applies temporal links immediately in a chapter-scoped way.

### Practical implications

- Cross-chapter temporal information may be inferred but dropped before durable graph projection.
- The current graph can therefore look partially coherent: structurally connected across chapters but temporally disconnected across the same boundary.

## Open Questions

- Should temporal-link materialization become a distinct downstream step rather than an immediate chapter-scoped post-persistence action?
- What should be the authoritative identity model for scenes referenced across chapter boundaries?
- Should cross-chapter temporal links be materialized incrementally per chapter, per book, or via a bounded window?
- Should read-side event ordering remain chapter-local, or should there be a separate book-level temporal ordering mode?
- How should retry/reprocessing semantics work once temporal materialization is decoupled from chapter persistence?

## Success Criteria

- The system architecture clearly separates temporal analysis artifacts from temporal graph materialization.
- Cross-chapter temporal information inferred during triad analysis can be preserved into durable graph edges when intended.
- The implemented behavior matches operator and developer expectations about cross-chapter temporal continuity.
- Tests cover the intended cross-chapter temporal behavior at a level stronger than mocked unit interactions.

## Links

- Related planning item:
  - `./scene-temporal-linking-gaps.md`
- Related implementation files:
  - `../../lorevault-api/src/main/java/com/lorevault/api/ai/TriadBuilderService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/ai/TriadOrchestrationService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalReadRepository.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/EventOrderingService.java`
