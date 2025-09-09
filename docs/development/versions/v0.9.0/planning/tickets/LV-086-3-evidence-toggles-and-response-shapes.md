# LV-086-3 — Evidence toggles and stable response shapes [refactor]

Status: NOT IMPLEMENTED

Context

- Timeline responses should remain compact, with optional evidence fields controlled by a flag.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/timeline-apis.md

Problem

- Without strict shapes and toggles, clients risk coupling to unstable internal fields.

Proposal

- Define DTOs and mappers for EventSummary and TemporalNeighbor with optional evidence fields behind includeEvidence.

Scope

- DTOs, mappers, and tests; enforce that evidence fields appear only when includeEvidence=true.

Out of scope

- Additional fields beyond those in research docs

Acceptance criteria

- [ ] EventSummary DTO excludes evidence by default
- [ ] includeEvidence=true includes rationale and offsets fields
- [ ] Tests validate toggling behavior

Quality gates

- [ ] Controller tests pass; response contracts stable

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#086—timeline-query-+-graphrag-nlq-research
- Research: ../../research/timeline-apis.md
