# LV-087-2 — Replace System.out with structured logging [refactor]

Context

- Several production classes use `System.out.println`, notably `Neo4jContentPersistenceAdapter` for timing/size prints.
- This bypasses SLF4J logging configuration and makes test output noisy.

Problem

- Unstructured `System.out` cannot be filtered by level and pollutes logs.
- Hidden performance overhead and potential leaks in hot paths.

Proposal

- Replace `System.out.println` occurrences with `log.debug` or `log.info` as appropriate.
- Remove redundant timing logs or guard them behind debug.

Scope

- Update `Neo4jContentPersistenceAdapter`:
  - `findChunksByChapterId` replace prints with `log.debug` and consider removing measurements.
  - `updateChunks`, `findAllChunksWithEmbeddings` likewise.
- Search for other `System.out` in `src/main` and replace with SLF4J (class-level `@Slf4j` or logger).

Out of scope

- Changing business logic or repository queries.

Technical notes

- Ensure classes have Lombok `@Slf4j` or define `private static final Logger log = LoggerFactory.getLogger(...)`.
- Keep log statements concise; avoid large payload dumps.

Acceptance criteria

- [ ] No `System.out.println` remains in `src/main/java`.
- [ ] Equivalent information, if still useful, is logged via SLF4J with appropriate level.
- [ ] Tests are not flakier/noisier due to logging changes.

Quality gates

- [ ] Build and tests green on JDK 21
- [ ] No new ArchUnit violations

Links

- Example class: ../../../lorevault-api/src/main/java/com/lorevault/api/infrastructure/persistence/neo4j/adapter/Neo4jContentPersistenceAdapter.java
