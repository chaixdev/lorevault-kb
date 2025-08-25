# LV-083-3 — Ingestion verification tests for Events [user story]

**Status: COMPLETE** - Implemented in SceneEventDualWriteIntegrationTest with comprehensive coverage of dual-label verification, relationship validation, and property checks. The test validates event==scene counts and all required relationships.

Context

- After enabling dual-write, we need tests to ensure Event creation mirrors Scenes and linkages are correct.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/tests-and-qa.md

Problem

- Without verification, regressions in linkages or counts might go unnoticed.

Proposal

- Add integration tests that ingest a small fixture and assert event count equals scene count per chapter and that relationships are correctly set.

Scope

- Integration tests: ingest chapter(s), verify :Event:Scene count, HAS_SCENE and HAS_CHUNK relationships from Chapter.
- Include cross-chapter fixture to prepare for 0.8.4 verifications later.

Out of scope

- Temporal edges checks (covered in 0.8.4)

Technical notes

- Reuse existing test infrastructure and sample content; add minimal fixtures if needed.

Acceptance criteria

- [ ] For a book with 2 chapters, events==scenes per chapter
- [ ] Chapter HAS_SCENE points to Event; Event HAS_CHUNK points to chunks
- [ ] Tests pass in CI and are stable

Quality gates

- [ ] Coverage thresholds unchanged and met

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#083—dual-write-ingestion-scenes-→-events
- Research: ../../research/tests-and-qa.md
