# LV-084-2 — Cycle guard for precedence edges [refactor]

Context

- Temporal precedence edges should not introduce cycles (for BEFORE/MEETS relations). We need guardrails during edge creation.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md

Problem

- Naive edge creation across chapters could create cycles if data anomalies or duplicates occur.

Proposal

- Implement a check before creating edges to ensure adding Ei→Ej doesn't create a cycle; if it would, skip and log.

Scope

- Guard in the edge creation service; unit tests for cycle detection logic.

Out of scope

- Graph-wide repair jobs

Technical notes

- Use bounded DFS/BFS in chapter scope; cross-chapter links are only last→first, which simplifies checks.

Acceptance criteria

- [ ] Edge creation skips and logs when a cycle would be introduced
- [ ] Unit test simulates a potential cycle and passes

Quality gates

- [ ] No performance regression in edge creation on sample datasets

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#084—skeleton-timeline-edges-default-meets@heuristic
