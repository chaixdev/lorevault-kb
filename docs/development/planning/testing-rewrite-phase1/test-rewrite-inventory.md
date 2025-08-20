# Test Suite Inventory (pre-rewrite)

Status: WIP
Owner: Testing Guild
Last updated: 2025-08-20

Purpose: Capture what existing tests assert today to drive parity during the full rewrite.

## Scope scanned
- Module: lorevault-api
- Locations: src/test/java, src/test/resources

## Summary counts (approx)
- Test classes: ~40
- IT classes (suffix `IT`): 3
- Integration-style with Spring context: several base classes (`IntegrationTestBase`, `Neo4jIntegrationTestBase`) and slice-like tests
- Disabled tests: 2 (`RagServiceTest`, `PromptLoaderServiceTest`)
- Test resources: 6 (scene detection XMLs, sample chapters)

## Notable categories
- Domain unit tests: `domain/content/*Test.java` (Chapter, Book, Series, Universe)
- Service unit tests: scene detection, chunk embedding, text chunking, semantic search, RAG
- Adapter tests: InMemory semantic search, embedding model adapter, Neo4j persistence (IT)
- Web/controller tests: `AskControllerTest`, `IngestionControllerIntegrationTest`
- Schema/infra tests: schema bootstrap, neo4j schema initializer, connection
- Utilities: vector math, prompt loader

## Disabled tests
- service/ask/RagServiceTest.java — @Disabled("Temporarily disabled to stabilize build; ChatClient mock contract to be updated")
- service/shared/PromptLoaderServiceTest.java — @Disabled("Temporarily disabled to stabilize build; to be fixed in test refactor roadmap")

## Spring context / containers usage
- @SpringBootTest in:
  - test/IntegrationTestBase.java
  - service/SceneDetectionClientModelSelectionTest.java (twice with different properties)
- Testcontainers Neo4j presence in:
  - test/container/(Neo4jTestContainer|SharedNeo4jTestContainer).java
  - test/(IntegrationTestBase|Neo4jIntegrationTestBase|SharedNeo4jTestContainer).java
  - graph/*IT.java

## IT classes (suffix IT)
- graph/Neo4jContentPersistenceAdapterIT.java — persistence workflows
- graph/Neo4jContentPersistenceAdapterMultiJobIT.java — multi-job scenario
- integration/EmbeddingModelAdapterIT.java — embedding adapter behavior

## Representative assertions/themes (brief)
- Domain entities: construction, invariants, relationships
- Scene detection pipeline: parsing, XML handling, coordinate localization, retries, business rules
- Embeddings: chunk generation, deterministic embedding config, vector math
- Search: semantic ranking, ordering, integration flows
- RAG: prompt assembly and ChatClient interactions (currently disabled)
- Web: AskController endpoints behavior
- Schema: indexes/constraints setup, bootstrap

## Gaps and risks
- Mixed use of @SpringBootTest for what should be pure service tests
- Multiple container helpers with duplication; unclear reuse strategy
- Determinism not enforced uniformly (Clock/UUID)
- Sparse tagging (@Tag) and taxonomy inconsistent
- Some heavy tests disabled or flaky themes identified

## Raw file list
- See workspace search inventory; detailed per-file notes to be appended as we rewrite.

## Related docs
- Parity Matrix: `docs/development/planning/testing-rewrite-phase1/test-parity-matrix.md`
- Rewrite Plan: `docs/development/planning/testing-rewrite-phase1/test-rewrite-plan.md`

## Next steps
- Fill out parity matrix per file with Category/Strategy and mark Parity status as we land replacements.
- Start scaffolding: deterministic testutil, fakes, base container IT.
