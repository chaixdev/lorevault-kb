# LV-087-5 — Neo4j repo queries: avoid full scans and in-memory filtering [refactor]

Context

- `Neo4jContentPersistenceAdapter.findChaptersByUniverse` currently uses `chapterRepo.findAll().stream().filter(...)`.

Problem

- Pulling all data and filtering in-memory is inefficient and can degrade as data grows.

Proposal

- Add repository methods to push filtering into the database (e.g., `findByUniverse(String)` or explicit Cypher query methods).

Scope

- Introduce `ChapterGraphRepository#findByUniverse(String)`.
- Update adapter to call the new repository method.
- Add a focused test to verify the repository method and adapter behavior.

Out of scope

- Broader query optimization or index creation (covered by separate tickets if needed).

Technical notes

- Ensure appropriate indexes/constraints exist for `universe` if used frequently.

Acceptance criteria

- [ ] Adapter no longer loads all chapters for filtering.
- [ ] New repository method covered by unit/integration tests.

Quality gates

- [ ] Build and tests green on JDK 21
- [ ] No new ArchUnit violations

Links

- Adapter: ../../../lorevault-api/src/main/java/com/lorevault/api/infrastructure/persistence/neo4j/adapter/Neo4jContentPersistenceAdapter.java
- Repository: ../../../lorevault-api/src/main/java/com/lorevault/api/infrastructure/persistence/neo4j/repository/ChapterGraphRepository.java
