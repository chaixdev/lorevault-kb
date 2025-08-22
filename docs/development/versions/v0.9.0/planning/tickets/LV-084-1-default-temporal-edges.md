# LV-084-1 — Default TEMPORAL edges (MEETS@Heuristic) [user story]

Context

- To build a skeleton timeline, link consecutive scenes/events with default TEMPORAL edges.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/event-model.md, ../../research/ingestion-changes.md

Problem

- Without edges, ordering relies only on sceneIndex, which is insufficient for graph-native traversal and upgrades.

Proposal

- Create TEMPORAL edges Ei→Ej for consecutive events within a chapter with properties: temporalRelation=MEETS, certainty=Heuristic, source=CHAPTER_SEQUENCE, weight=0.5, rationale="chapter sequence".
- Add cross-chapter default edge from last of chapter k to first of k+1.

Scope

- Edge creation step in ingestion or a post-processing service.
- Store properties and ensure no duplicates.

Out of scope

- LLM-based upgrades (0.8.5)

Technical notes

- Maintain DAG property for precedence; avoid introducing cycles.

Acceptance criteria

- [ ] Consecutive events within a chapter are connected with default edges with exact properties
- [ ] Cross-chapter last→first edge created
- [ ] No duplicate edges created on repeated runs

Quality gates

- [ ] Integration tests cover in-chapter and cross-chapter defaults

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#084—skeleton-timeline-edges-default-meets@heuristic
- Research: ../../research/event-model.md
