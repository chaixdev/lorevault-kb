# LV-085-1 — LLM temporal upgrades for edges [user story]

Context

- Default MEETS@Heuristic edges should be upgraded when explicit temporal cues exist.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/ingestion-changes.md

Problem

- Default edges lack semantic richness; we need stronger relations with evidence and certainty where possible.

Proposal

- Add a step (in-pipeline or background) that evaluates consecutive event pairs and upgrades temporalRelation and certainty when confident; persist rationale and offsets.

Scope

- Implement upgrade rules and thresholds; update weight via mapping; ensure idempotency.

Out of scope

- Public API exposure (neighbors covered separately)

Technical notes

- Keep MEETS edge if confidence < threshold; never remove the only link between events.

Acceptance criteria

- [ ] On curated fixture, some edges upgraded to BEFORE/DURING/OVERLAPS/etc with non-Heuristic certainty
- [ ] Rationale and evidence offsets persisted when available
- [ ] Re-running upgrade does not duplicate edges or degrade links

Quality gates

- [ ] Unit tests for upgrade/no-upgrade/re-run cases

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/ingestion-changes.md
