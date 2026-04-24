# Tighten web transport boundaries and internal type visibility

**Status:** NOT STARTED

## Summary

LoreVault's executable modulith guardrails and strong top-level cycle containment are now in place.

The remaining architecture-hygiene work is narrower: some `web` transport DTOs still expose core-domain vocabulary too directly, some internal types remain broader in visibility than their ownership suggests, and architecture-facing docs still need a tighter ongoing alignment loop with implemented package truth.

This planning item captures that residual follow-up context without keeping the completed parent guardrail tickets active.

## Problem

The repository now has executable architectural guardrails for the current `web -> core` split, and the strongest package-cycle cleanup slice is complete.

What remains is a smaller but still important boundary-hygiene cluster:

- some `web` transport DTOs still expose core-domain shapes directly enough to weaken the edge boundary
- some internal types remain `public` primarily by habit, which makes package boundaries communicative rather than restrictive
- architecture-facing docs can still drift from the actual present package map unless the remaining cleanup work is tracked explicitly

If this residual work is left implicit inside completed planning items, future sessions will either miss it or reopen already-finished architecture tickets just to recover the same context.

## Product Context

- Contributors need the HTTP/UI edge to stay legible so new endpoint work does not gradually re-couple transport code to internal core shapes.
- Tighter type visibility helps architectural rules communicate ownership through code structure rather than documentation alone.
- A smaller, focused follow-up is easier to resume than carrying a completed guardrail project forward as if it were still active.

## Technical Context

Recent architecture-hygiene work already completed these bounded outcomes:

- executable architecture tests now enforce the current `web -> core` dependency direction
- top-level core package cycle containment is green again
- broader strong cycle cleanup and event-boundary containment already landed

The residual concerns called out by the completed guardrail pass were:

- `web` transport DTOs that still expose core-domain types more directly than intended
- broad public visibility for internal types that weakens package-boundary communication
- architecture-facing documentation drift between status/rules/patterns and the actual present package map

Relevant areas likely include:

- `lorevault-web/src/main/java/com/lorevault/api/web/**`
- `lorevault-core/src/main/java/com/lorevault/api/**`
- `docs/PROJECT-STATUS.md`
- `docs/rules/code-organization-guidance.md`
- `docs/rules/lorevault-module-conventions.md`
- `docs/patterns/codebase-topology.md`

## Scope

- Identify the highest-value `web` transport-boundary leaks that still expose core-domain shapes too directly.
- Identify the highest-value internal types whose visibility can be narrowed without disproportionate churn.
- Track documentation-alignment work only where current architecture-facing docs still lag implemented truth.
- Preserve a bounded resume point for this residual hygiene cluster without reopening completed cycle-containment work.

## Out of Scope

- Reopening the completed strong cycle containment work
- Another broad package-shape transformation plan
- A repo-wide visibility sweep with no prioritization
- Introducing new Maven modules or a new architectural style
- Re-documenting already-completed guardrail rollout history in detail

## Known Constraints / Prior Findings

- `lorevault-web -> lorevault-core` remains the only legal cross-module dependency.
- The completed architecture-test rollout should remain stable; this item should not weaken those guardrails.
- Some DTO exposure may be acceptable where the transport contract intentionally mirrors a stable core concept.
- Visibility tightening should prefer high-value ownership seams over blanket `public` removal.
- Documentation should follow implemented truth and avoid preserving stale historical package descriptions as if they were current.

## Open Questions

- Which transport DTO exposures are genuinely harmful boundary leakage versus acceptable thin projection of stable core concepts?
- Which internal types yield the best ownership signal if their visibility is narrowed first?
- Should documentation-alignment updates happen opportunistically inside each bounded implementation slice, or as a dedicated cleanup pass?
- Which of these residual items are best handled alongside nearby feature work versus as standalone cleanup?

## Success Criteria

- Future contributors can distinguish completed architecture guardrail work from the still-open transport/visibility cleanup.
- The highest-value transport-boundary and visibility cleanup candidates are named explicitly in one focused planning item.
- Later implementation work can tighten these seams without reopening the already-completed cycle-containment project.
- Architecture-facing docs remain trustworthy enough that contributors can verify current structure without archaeology.

## Links

- Related planning: `package-shape-transformation-plan.md`
- Related planning: `clarify-ai-package-ownership-for-scene-and-triad-workflows.md`
- Related rules: `../rules/code-organization-guidance.md`
- Related rules: `../rules/lorevault-module-conventions.md`
- Related pattern: `../patterns/codebase-topology.md`
- Related status snapshot: `../PROJECT-STATUS.md`
