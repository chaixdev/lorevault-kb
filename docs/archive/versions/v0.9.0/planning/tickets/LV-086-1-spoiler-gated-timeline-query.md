# LV-086-1 — Spoiler-gated timeline query endpoints [user story]

**Status:** DEFERRED

**Reason:** Blocked by event-driven ingestion refactor (LVREF epic). Timeline query features depend on stable event-driven foundation for clean handler integration points, reliable per-stage observability, and consistent event-driven patterns.

**Resume After:** LVREF Phase 3 completion (est. Week 4+)

**Reference:** `../../../../refactor/event-driven-ingestion-refactor-v0100.md`

---

## Original Specification

Context

- We need read endpoints to list ordered Events up to a chapter boundary (spoiler gate).
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/timeline-apis.md

Problem

- Without endpoints, consumers cannot access the timeline or validated ordering behavior.

Proposal

- Implement endpoints:
  - GET /api/timeline/books/{bookId}/events?uptoChapter=N&includeEvidence=bool
  - GET /api/timeline/chapters/{chapterId}/events
- Respect edges-first ordering with sceneIndex fallback and the spoiler gate.

Scope

- Controller, service wiring to ordering service, DTOs.
- Tests for spoiler gate and includeEvidence toggle.

Out of scope

- NLQ summary (covered in LV-086-2)

Technical notes

- Keep response payloads minimal and stable; include optional evidence fields when requested.

Acceptance criteria

- [ ] Book-level endpoint returns ordered events up to chapter N
- [ ] Chapter-level endpoint returns ordered events for the chapter
- [ ] Evidence toggled via includeEvidence

Quality gates

- [ ] Web/controller tests pass and validate ordering and gating

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#086—timeline-query-+-graphrag-nlq-research
- Research: ../../research/timeline-apis.md
