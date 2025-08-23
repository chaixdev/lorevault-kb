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

## Dev Notes

### Chosen approach (Aug 2025)

**Current state limitations:**

- No Universe/Series/Book nodes in graph; chapters matched by string-based PublicationCoordinates (universe, series, bookTitle, bookNumber)
- Risk of misspellings creating disconnected graphs
- Cross-chapter linking relies on coordinate text matching

**Proper workflow design (for future implementation):**

1. **Create publication hierarchy first:** Require Universe/Series/Book entities to exist before chapter ingestion
2. **Use stable IDs:** Pass explicit `bookId` in chapter submission requests instead of relying on bookTitle matching
3. **Validation:** Return API errors if referenced bookId not found
4. **Chapter sequence:** Use `(:Chapter)-[:NEXT_CHAPTER]->(:Chapter)` relationship for robust cross-chapter linking

**LV-084-1 implementation approach:**

- **In-chapter edges:** Use existing sceneIndex ordering within each chapter
- **Cross-chapter edges:** During sequential ingestion of chapter k+1, create edge from last(k) → first(k+1)
- **Matching strategy:** For now, use deterministic bookId derived from coordinates: `UUID.nameUUIDFromBytes((universe + "/" + series + "/" + bookTitle + "#" + bookNumber).getBytes())`
- **Edge properties (exact):**
  - temporalRelation = MEETS
  - certainty = HEURISTIC
  - weight = 0.5
  - source = "CHAPTER_SEQUENCE"
  - rationale = "chapter sequence"

**Technical implementation:**

- New service: `DefaultTemporalEdgeService`
- New repo: `TemporalEdgeWriteRepository` with @Query MERGE operations
- Wire into `IngestionWorkflowService` after scene persistence
- Add derived `bookId` to `ChapterNode` via `Neo4jMapper`
- Integration tests with Testcontainers for idempotency and property verification
