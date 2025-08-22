# LV-083-1 — Dual-write: Scenes → Events on ingestion [user story]

Context

- We need to persist :Event:Scene nodes during chapter ingestion to prepare for timeline features.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/ingestion-changes.md

Problem

- Current ingestion creates Scenes and Chunks, but Events do not yet exist; we need parallel persistence without changing external behavior.

Proposal

- After scene detection, persist an Event per Scene with segmentation fields and semantic title/description.
- Maintain existing Chapter→HAS_SCENE→Scene/Chunk linkage, but attach HAS_SCENE to the new :Event:Scene nodes.

Scope

- Update ingestion pipeline to create :Event:Scene for each scene (sceneIndex, offsets or chunk range).
- Ensure HAS_CHUNK relations attach to :Event:Scene.
- Return created eventIds in ingestion job output (optional, non-breaking field).
- Dev-only: reingestion path for existing chapters; no backfill tooling.

Out of scope

- Temporal edge creation (covered in 0.8.4)
- Public API endpoints for timeline

Technical notes

- Preserve idempotency within a single run; on reingestion, delete chapter subtree before reprocessing (dev script covered in LV-083-2).

Acceptance criteria

- [ ] Ingesting a sample chapter creates one :Event:Scene node per scene
- [ ] Chapter HAS_SCENE edges point to :Event:Scene; HAS_CHUNK edges link Event→Chunk
- [ ] Ingestion job output optionally lists created eventIds

Quality gates

- [ ] Integration test validates counts and relationships
- [ ] No regression in existing web/controller tests

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#083—dual-write-ingestion-scenes-→-events
- Research: ../../research/ingestion-changes.md
