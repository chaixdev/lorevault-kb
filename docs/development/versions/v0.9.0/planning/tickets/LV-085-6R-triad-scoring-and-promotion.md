# LV-085-6R — Triad scoring and edge promotion [feature]

Problem

- We need a principled way to aggregate triad evidence and set edge statuses.

Proposal

- Implement TriadScorer that assigns scores per edge based on triad consistency, relation agreement, and confidence weights.
- Implement PromotionService that updates edges:
  - score >= threshold1 -> CONFIRMED
  - conflicting evidence -> CONTESTED (retain quotes)
  - otherwise -> PROPOSED

Scope

- Services: TriadScorer, PromotionService
- Config: thresholds and weights in application-dev.yml
- Tests: deterministic unit tests with synthetic triads

Acceptance criteria

- [ ] Scores are deterministic given fixed inputs
- [ ] Edge statuses updated idempotently; reruns don’t oscillate

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
