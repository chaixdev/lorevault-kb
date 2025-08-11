# Neo4j Migration Progress (v0.4.0 Pivot)

Scope: Replace RDBMS (Postgres/JPA/Flyway) with Neo4j graph persistence while keeping feature set at v0.4.0 (no embeddings, no semantic/vector search). Semantic search endpoint returns 501 until v0.5.0.

## Legend
- [x] Done
- [ ] Pending
- [~] In Progress
- [!] Blocked / Decision Needed

## Completed
- [x] Remove Postgres service from docker-compose and add Neo4j
- [x] Remove Postgres/JPA/Flyway/pgvector dependencies from `lorevault-api/pom.xml`
- [x] Clean `application.properties` (drop datasource/JPA/Flyway/vector config; add Neo4j config)
- [x] Neutralize LLM plan & add placeholder note
- [x] Replace semantic search implementation with 501 placeholder; adjust tests & DTO (removed score)
- [x] Add Neo4j node models (`ChapterNode`, `SceneNode`, `ChunkNode`, `IngestionJobNode`, `StatusRecordNode`)
- [x] Add graph repositories
- [x] Add `ContentPersistencePort` abstraction
- [x] Implement `Neo4jContentPersistenceAdapter`
- [x] Implement mapping utilities (e.g., `GraphModelMapper`) for Chapter/Scene/Chunk/Job/StatusRecord

## In Progress
- [~] Design mapping layer (entity ↔ node) & transitional strategy (will be removed soon; Option 2 selected to finish full cutover now)
- [~] Refactor of `IngestionService` (submitChapter + job/status updates now using port; listJobs now graph-backed; removing remaining JPA dependencies next)
- [~] Temporary reintroduced JPA starter to restore compilation during dual persistence (scheduled for full removal in current iteration)
- [~] Replace JPA usage in services with graph port (SceneDetectionService & ScenePersistenceService migrated; IngestionService partial; ChapterProcessor migrated)
- [~] Chunking path still partially legacy (chunks not yet persisted to graph)
- [~] Chunk persistence migrated to graph in `IngestionService` (chunk creation now writes ChunkNodes)
- [~] Final JPA removal prep (all ingestion path operations now use graph port: scenes + chunks + jobs)

## Pending Tasks
- [x] Remove deprecated `ChunkService` and all JPA repositories/entities (physically deleted)
- [x] Drop JPA dependency from pom and clean exclusions
- [~] Introduce Neo4j Testcontainer & adjust tests (base class added)
- [~] Write adapter & integration tests (chapter, scenes, chunks, job lifecycle) (initial + multi-job/status tests added)
- [x] Add minimal Neo4j constraints
- [~] Documentation & README updates (README + functional + information viewpoints updated)
- [x] Delete legacy scripts/config (removed flyway properties)
- [x] Final cleanup: remove legacy entity classes / repositories / scripts (mapper + stragglers removed)
- [x] Remove temporary reintroduced JPA dependency once all repositories replaced (none present)

## Nice-To-Have (Defer if Time-Pressed)
- [x] Custom Cypher queries for efficient status record retrieval (jobs + status + scenes + chunks optimized)
- [ ] Relationship property modeling for ordering (HAS_SCENE.index, HAS_CHUNK.index) instead of node fields
- [ ] Add constraint automation via a lightweight startup initializer bean

## Decisions / Assumptions
- Chosen path: Option 2 (complete migration; eliminate JPA instead of adding in-memory DB)
- Semantic search real implementation deferred to v0.5.0 (embeddings inside Neo4j)
- No data migration required (clean break)
- Mapping layer used only as temporary bridge; final state will drop JPA entities entirely
- Minimal indexes: uniqueness on `Chapter.contentHash`, `Chapter.id`, and optional composite on coordinates in future release

## Next Immediate Step
Run full test suite + smoke run (boot app) to validate clean state; then tag v0.4.0-migration-complete.

---
(Keep this file updated after each migration step.)
