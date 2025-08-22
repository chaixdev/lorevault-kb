# LV-084-3 — Ordering service with edges-first, sceneIndex fallback [user story]

Context

- Clients need a deterministic order of events per chapter and across chapters; prefer graph edges when present.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/timeline-apis.md

Problem

- Without a consistent ordering policy, responses may be unstable or ambiguous.

Proposal

- Implement an ordering service that returns Events ordered by TEMPORAL edges when available; fall back to sceneIndex for gaps/ambiguity.

Scope

- Service method(s) to order within a chapter and up to chapter N within a book.
- Handle missing edges gracefully; ensure deterministic result.

Out of scope

- Public exposure of endpoints (0.8.6)

Technical notes

- Prefer edge-based topological order within chapter; if disconnected, sort by sceneIndex.

Acceptance criteria

- [ ] For chapters with default edges, ordered list equals sceneIndex order
- [ ] For chapters with upgraded edges, order reflects edges
- [ ] Book-level ordering concatenates chapters by publication coordinates

Quality gates

- [ ] Integration tests for ordering behavior in both modes

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#084—skeleton-timeline-edges-default-meets@heuristic
- Research: ../../research/timeline-apis.md
