# LV-085-1 — LLM temporal upgrades for edges [user story]

Context

- Default MEETS@Heuristic edges should be upgraded when explicit temporal cues exist.
- Now that Pass 2 is triad-based (LV-085-0), we have two local votes per adjacency (i,i+1) and richer certainty/evidence to inform upgrades.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md; Research: ../../research/Narrative event DAG.md

Problem

- Default edges lack semantic richness; we need stronger relations with evidence and certainty where possible, while respecting triad overlap confirmation.

Proposal

- Consume triad votes and apply an overlap policy:
  - Agreement -> Confirmed primary relation; Divergence -> Contested (retain counter-vote for audit).
  - Promote from MEETS@Heuristic to stronger Allen relations when certainty and lexical/evidence thresholds are met (e.g., Explicit cues, clear "before/after/overlaps/during").
- Persist rationale and evidence (short quotes) without duplicating edges; preserve idempotency.

Scope

- Implement upgrade rules using triad outputs (relation + certainty + evidence) and overlap state.
- Update mapping/weighting to reflect higher-confidence relations.
- Keep existing neighbor-only storage; no transitive edges.

Out of scope

- Public API changes (neighbors API covered in LV-085-3)

Technical notes

- Treat inverse agreements (e.g., overlaps vs overlapped_by) as agreement for confirmation purposes.
- If a confirmed upgrade would introduce a cycle, downgrade to Contested (work with LV-084-2 cycle guard).

Acceptance criteria

- [ ] On curated fixture, some edges upgraded beyond MEETS with non-Heuristic certainty based on triad evidence.
- [ ] Overlap agreement produces Confirmed; disagreement produces Contested; counter-vote preserved for audit.
- [ ] Re-running upgrade is idempotent (no duplicates, no regressions).

Quality gates

- [ ] Unit tests for promote/no-promote/overlap agree/diverge/re-run cases.

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/Narrative event DAG.md
