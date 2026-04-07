# Tickets for v0.9.0 (Scene → Event / Timeline)

This folder contains functionally scoped tickets grouped by patch versions (0.8.2–0.8.6) for the v0.9.0 milestone.

Conventions

- ID format: `LV-<version>-<n>` (e.g., LV-082-1). Versions map: 0.8.2 → 082, 0.8.3 → 083, etc.
- Labels: `[user story]`, `[bugfix]`, `[refactor]`, `[chore]`, `[research]`. Use multiple if needed.
- Structure: Each ticket includes Context, Problem, Proposal, Scope, Out-of-scope, Technical Notes, Acceptance Criteria, Quality Gates, Links.
- Cross-links: Reference related research/planning docs with relative paths and link sibling tickets.
- Evidence: Where applicable, note sample data or fixtures used for verification.

Template

```markdown
# LV-XYZ-N — Title [label]

Context
- Why this matters. Link to planning/research.

Problem
- What gap/issue we are solving.

Proposal
- The approach at a high level.

Scope
- Bulleted list of what will be done.

Out of scope
- Explicit non-goals for this ticket.

Technical notes
- Data model, APIs, constraints, edge cases.

Acceptance criteria
- [ ] Clear, testable checks.

Quality gates
- [ ] Lint/Build green; [ ] Tests for X; [ ] ArchUnit unaffected; [ ] Coverage thresholds met.

Links
- Planning: ./../v0.9.0-scene-to-event-entity-plan.md (or relative path)
- Research: ./../../research/<doc>.md
- Sibling tickets: LV-XYZ-M
```

Version buckets

- 0.8.2 — Model and storage readiness (LV-082-1..3)
- 0.8.3 — Dual-write ingestion and reingestion support (LV-083-1..3)
- 0.8.4 — Default temporal edges and ordering (LV-084-1..3)
- 0.8.5 — LLM upgrades and neighbors API (LV-085-1..3)
- 0.8.6 — Timeline queries and NLQ POC (LV-086-1..4)
