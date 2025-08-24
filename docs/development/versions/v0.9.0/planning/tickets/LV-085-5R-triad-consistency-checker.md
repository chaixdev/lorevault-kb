# LV-085-5R — Triad consistency checker [feature]

Problem

- Triads must be checked for Allen algebra consistency; inverses normalized.

Proposal

- Implement TriadConsistencyChecker that verifies satisfiability of (E-A, A-B, E-B) under canonical 7 with inverses normalized to focal perspective.

Scope

- Library: minimal constraint propagation for triad satisfiability (lookup table acceptable to start)
- Normalizer: ensure inputs are canonical and oriented
- Tests: truth-table style tests for selected triads (satisfiable and unsatisfiable)

Acceptance criteria

- [ ] Checker identifies valid vs invalid triads with clear reason codes
- [ ] Unit tests cover happy path and a few contradictory cases

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
