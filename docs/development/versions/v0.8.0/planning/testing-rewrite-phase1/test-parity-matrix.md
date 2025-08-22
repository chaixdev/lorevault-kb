# Test Parity Matrix (legacy → rewrite)

Status: WIP
Owner: Testing Guild
Last updated: 2025-08-20

Legend
- Category: unit | service | controller | adapter | tck | integration | config | drop (placeholder)
- Strategy: how it will be rewritten; whether Spring/context/containers involved

## Mapping

- domain/content/ChapterTest.java → Category: unit
  - Strategy: keep as pure domain unit tests using builders for fixtures; @Tag("unit")
- domain/content/BookTest.java → Category: unit
- domain/content/SeriesTest.java → Category: unit
- domain/content/UniverseTest.java → Category: unit

- service/TextChunkingServiceTest.java → Category: service
  - Strategy: construct service with fakes/mocks only; no Spring; deterministic inputs; @Tag("unit")
- service/content/ChunkEmbeddingServiceTest.java → Category: service
  - Strategy: use FakeEmbeddingPort with seeded vectors; @Tag("unit")
- service/search/SemanticSearchServiceTest.java → Category: service
  - Strategy: FakeEmbeddingPort + FakeSemanticSearchPort; verify filters; @Tag("unit")
- service/ask/RagServiceTest.java (disabled) → Category: service
  - Strategy: replace ChatClient with FakeChatClient; verify prompt assembly and citations; deterministic; @Tag("unit"); parity of all behaviors; re-enable

- service/SceneDetectionServiceTest.java → Category: service
  - Strategy: fakes for parser/localizer; verify pipeline orchestration and business rules
- service/SceneDetectionParsingTest.java → Category: service
  - Strategy: focused parsing tests with small inputs; no Spring
- service/SceneDetectionXmlParsingTest.java → Category: service
  - Strategy: same as above (consider parametrized tests)
- service/SceneCoordinateLocalizerTest.java → Category: service
- service/content/retry/RetryAwareSceneDetectionServiceTest.java → Category: service
  - Strategy: keep detailed scenarios; fakes for retry/backoff; deterministic clock
- service/SceneDetectionDebugTest.java → Category: service
  - Strategy: fold useful assertions into other service tests or drop if debugging-only
- service/SceneDetectionClientModelSelectionTest.java → Category: config (partial), service (avoid Spring)
  - Strategy: split:
    - Fast property binding: ApplicationContextRunner (@Tag("unit"))
    - Minimal @SpringBootTest only if necessary for end-to-end binding (@Tag("integration"))

- controller/IngestionControllerIntegrationTest.java (empty) → Category: controller
  - Strategy: implement @WebMvcTest slice with mocked service
- web/query/ask/AskControllerTest.java → Category: controller
  - Strategy: keep as @WebMvcTest; align request/response DTO assertions

- infrastructure/search/InMemorySemanticSearchAdapterTest.java → Category: adapter + tck
  - Strategy: create SemanticSearchPort TCK under tck/; rewrite adapter tests to run TCK; keep small unit tests for adapter specifics
- integration/EmbeddingModelAdapterTest.java → Category: adapter + tck
  - Strategy: create EmbeddingPort TCK; run TCK against adapter
- integration/EmbeddingModelAdapterIT.java → Category: integration (if it calls external service)
  - Strategy: keep minimal happy-path IT only if external dependency requires it; else replace with TCK

- graph/Neo4jContentPersistenceAdapterIT.java → Category: integration + tck
  - Strategy: create PersistencePort TCK; keep one or two focused ITs using shared Neo4j container base
- graph/Neo4jContentPersistenceAdapterMultiJobIT.java → Category: integration (maybe merge)
  - Strategy: merge scenarios into TCK or single IT with parameterized cases if still valuable

- schema/SchemaBootstrapConfigurationTest.java → Category: integration
  - Strategy: ensure schema bootstrap runs on container; include in base IT smoke
- schema/neo4j/Neo4jSchemaInitializerTest.java → Category: integration
  - Strategy: minimal assertions on constraints/indexes using container
- infrastructure/graph/Neo4jConnectionTest.java → Category: drop or fold
  - Strategy: fold into container base smoke rather than a standalone test

- test/integration/SemanticSearchIntegrationTest.java → Category: drop (placeholder)
- test/integration/SimilarityOrderingIntegrationTest.java → Category: drop (placeholder)

- api/LoreVaultApiApplicationTests.java → Category: config
  - Strategy: keep one ultra-fast contextLoads or drop if redundant; ArchUnit will cover architectural rules

- domain/content/ContentEntityIntegrationTest.java → Category: integration
  - Strategy: if hitting DB, move under adapter or repository IT scope; otherwise convert to unit

## Helpers/fixtures to consolidate
- Multiple container helpers → unify to BaseNeo4jContainerIT (singleton, reuse)
- Test data loaders → centralize under testutil with deterministic fixtures
- Deterministic embeddings → DeterministicEmbeddingTestConfig or FakeEmbeddingPort; prefer fake over Spring config
- MockLlmConfig → prefer FakeChatClient for service tests; avoid Spring

## Deletions/quarantine
- Mark deprecated/empty tests for deletion once parity achieved
- Block deletion until matrix row is marked Parity: Done
