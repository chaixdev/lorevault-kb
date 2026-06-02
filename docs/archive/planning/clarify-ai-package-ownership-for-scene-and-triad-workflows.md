# Clarify AI package ownership for scene and triad workflows

**Status:** IN PROGRESS

## Summary

LoreVault still has a concentrated cluster of scene-detection and triad-analysis classes whose package placement does not match the repository's current ownership rules.

The main issue is not whether these classes use LLMs at all. It is that several workflow, orchestration, and pipeline-result types still live under `ai` even though the durable package guidance says `ai` should stay narrow and generic while feature-owned ingestion workflow belongs in `ingestion`.

This planning item exists to capture that specific remaining ownership problem in one place so a future session does not need to rediscover it by mining broader architecture and package-shape documents that are otherwise nearly complete.

## Problem

Today the `ai` package still contains a mixed set of responsibilities:

- true AI integration concerns
- ingestion-owned workflow orchestration
- deterministic scene/triad preparation logic
- pipeline DTO/result carriers used outside generic AI infrastructure

That mixed placement makes it harder to answer basic maintenance questions such as:

- which code is genuinely reusable AI infrastructure versus feature-owned chapter-processing logic
- which types are safe to extend under `ai`
- where future scene/triad work should be added without deepening legacy ownership drift

The confusion is especially visible around classes and families such as:

- `lorevault-core/src/main/java/com/lorevault/api/ai/application/SceneRelationshipAnalysisService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/application/TriadBuilderService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/infrastructure/SceneDetectionClient.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/domain/SceneWithCoordinates.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/domain/SceneDetectionResult.java`
- remaining `SceneDetection*` / `TriadAnalysis*` types that still blur generic AI concerns with ingestion-owned workflow semantics

The repo already says package placement should follow semantic ownership rather than implementation technique. The remaining `ai` package shape in this area still violates that rule enough to confuse follow-up work.

## Product Context

- Contributors need to place new scene-processing and temporal-analysis code without guessing whether `ai` is meant to be a generic integration package or a catch-all for LLM-adjacent workflow code.
- Clearer ownership boundaries reduce the chance that future ingestion work keeps deepening historical `ai ↔ ingestion` coupling.
- Architectural review and future refactor sessions are easier when the remaining package-ownership issue is tracked directly instead of buried inside broader finished items.

## Technical Context

Current canonical guidance says:

- `docs/rules/code-organization-guidance.md`
  - `ai` means **generic interaction with LLM APIs and AI infrastructure**
  - scene-detection workflow code that is part of chapter processing belongs in `ingestion`
  - code should not live under `ai` merely because it calls an LLM

Current present-state topology says:

- `docs/patterns/codebase-topology.md`
  - `ai ↔ ingestion` is known technical debt
  - contributors should not deepen that coupling

Relevant current implementation shape:

- clearly AI-owned:
  - `ai/infrastructure/SceneDetectionClient.java`
- mixed AI-adjacent orchestration:
  - `ai/application/SceneRelationshipAnalysisService.java`
  - `ingestion/application/scene/SceneDetectionService.java`
- not obviously AI-owned despite current/legacy placement history:
  - `ai/application/TriadBuilderService.java`
  - `ai/domain/SceneWithCoordinates.java`
  - `ai/domain/SceneDetectionResult.java`

Recent bounded cleanup already reduced part of the problem:

- triad workflow/result contracts were moved out of old AI-owned placement into `ingestion.application.result.TriadAnalysisModels`
- broader strong cycle cleanup is complete and reflected in `../PROJECT-STATUS.md`

What remains is a narrower ownership/placement clarification task for the scene/triad workflow cluster itself.

## Scope

- Identify the remaining scene- and triad-related classes whose package placement still conflicts with the current ownership rules.
- Clarify which of those classes are:
  - generic AI infrastructure
  - ingestion-owned workflow orchestration
  - feature-local DTO/result carriers
  - deterministic builders/helpers that should not remain under `ai`
- Create a bounded follow-up path so future implementation can move or reshape only the still-confusing ownership cluster.
- Preserve links back to the broader completed package-cycle and package-shape work without reopening those full topics.

## Out of Scope

- Reopening the completed architecture-cycle cleanup item
- Repo-wide package reshuffling
- A broad redesign of scene detection, triad analysis, or timeline persistence
- Deciding every final package move in this planning item itself
- Reworking unrelated `ai` consumers outside the scene/triad workflow cluster

## Known Constraints / Prior Findings

- `ai` is intentionally supposed to stay narrow.
- Some classes in the current cluster do directly call LLM infrastructure, but LLM usage alone is not enough to justify `ai` ownership.
- The strongest remaining confusion is around workflow-orchestration and pipeline-result types, not the generic client boundary itself.
- Some broader coupling work has already completed, so this item should stay focused on the still-ambiguous class set rather than reabsorbing finished cycle-removal work.
- Future moves should avoid move-twice churn by keeping generic AI integration separate from feature-owned ingestion workflow.

## Open Questions

- Which remaining classes in the scene/triad cluster are truly generic enough to stay in `ai`?
- Which classes should move to `ingestion.application`, `ingestion.domain`, or another ingestion-owned area?
- Should feature-local scene/triad result carriers stay near the producing workflow, or be grouped under a tighter ingestion-owned result package?
- Is there any remaining class in this cluster whose current name implies the wrong ownership even if the package eventually changes?
- What is the smallest bounded implementation slice that improves clarity without reopening the full ingestion/AI architecture?

## Success Criteria

- A future contributor can quickly find one planning item that explains the remaining `ai` package ownership problem for scene and triad workflows.
- The still-confusing class set is explicitly named instead of being implied only through broader cycle/planning documents.
- A later brainstorm or implementation pass can choose a bounded move/ownership strategy without redoing the discovery work.
- The planning item stays focused on ownership clarity rather than drifting into a full package-taxonomy rewrite.

## Links

- Related rules: `../rules/code-organization-guidance.md`
- Related pattern: `../patterns/codebase-topology.md`
- Related pattern: `../patterns/ingestion/triad-analysis.md`
- Related planning: `package-shape-transformation-plan.md`
- Related status snapshot: `../PROJECT-STATUS.md`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api/ai`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion`
