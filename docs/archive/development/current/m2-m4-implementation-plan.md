# M2–M4 Implementation Plan

Date: April 2026  
Status: **Historical execution plan — M2, M3, and M4 complete**  
Baseline at plan creation: M1 merged (`cfb7404`) — ingestion entities annotated `@Node`, mirror classes deleted.  
Final outcome: all 6 content domain entities annotated `@Node`; mirror classes and `Neo4jMapper` deleted; remaining port interfaces deleted; Spring AI upgraded to 1.1.4; package layout flattened into the current feature-oriented structure; 263 tests passing.

This document is preserved as the detailed execution record for the M2-M4 structural program. It is no longer an active plan; use `refactor-roadmap.md` and `PROJECT-STATUS.md` for current direction.

Structured-response note: although Spring AI structured `.entity(...)` mapping is now used in parts of the ingestion flow, XML scene parsing still works in the current codebase and is not causing enough friction to justify immediate cleanup. A full move away from XML can be revisited later, but it is not a current priority.

See `refactor-roadmap.md` for architectural vision and ADRs.

---

## What Was Done (M2/M3 Summary)

**M2 — completed.** All 6 content domain entities now carry `@Node` directly:
- `Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`
- Mirror `*Node` classes deleted
- `Neo4jMapper` deleted
- Repositories typed to domain entities

**M3 — completed.** All 5 remaining port interfaces deleted:
- `ContentPersistencePort` (37 methods) — deleted
- `EmbeddingPort`, `SemanticSearchPort`, `PromptRepositoryPort`, `TemporalEdgePort` — deleted
- `EmbeddingException`, `ContentPersistencePortTCK` — deleted
- All 13 production consumers inject concrete beans/adapters directly
- 7 integration tests migrated from `ContentPersistencePort` to `Neo4jContentPersistenceAdapter`

---

## How To Read This Document

The slices below are kept mostly in their original execution-plan form because they still capture useful implementation detail. Read them as:
- what the codebase looked like before the work landed
- what sequence was chosen to carry out the refactor safely
- which assumptions were made during the execution

Where current reality diverged from the original plan, short notes are added inline.

---

## ~~M2 Mirror Classes~~ (Completed)

~~**6 Node mirror classes** still exist under `infrastructure/persistence/neo4j/model/`:~~

| Mirror class | LOC | Domain entity it mirrors |
|---|---|---|
| `UniverseNode` | 34 | `domain.content.Universe` |
| `SeriesNode` | 41 | `domain.content.Series` |
| `BookNode` | 50 | `domain.content.Book` |
| `ChapterNode` | 69 | `domain.content.Chapter` |
| `SceneNode` | 57 | `domain.content.Scene` |
| `ChunkNode` | 62 | `domain.content.Chunk` |

**Persistence surface that routes through them:**

| Component | LOC | Role |
|---|---|---|
| `ContentPersistencePort` | 83 | 37-method God Port covering all 6 content entity types |
| `Neo4jContentPersistenceAdapter` | 474 | Implements all 37 methods; converts via Neo4jMapper |
| `Neo4jMapper` | 229 | 17 bidirectional domain ↔ Node mapping methods |

**Repositories backed by mirror classes:**

| Repository | Type parameter |
|---|---|
| `UniverseGraphRepository` | `Neo4jRepository<UniverseNode, UUID>` |
| `SeriesGraphRepository` | `Neo4jRepository<SeriesNode, UUID>` |
| `BookGraphRepository` | `Neo4jRepository<BookNode, UUID>` |
| `ChapterGraphRepository` | `Neo4jRepository<ChapterNode, UUID>` |
| `SceneGraphRepository` | `Neo4jRepository<SceneNode, UUID>` |
| `ChunkGraphRepository` | `Neo4jRepository<ChunkNode, UUID>` |
| `EventGraphRepository` | `Neo4jRepository<SceneNode, UUID>` |
| `TemporalGraphRepository` | `Neo4jRepository<SceneNode, UUID>` |
| `TemporalEdgeWriteRepository` | `Neo4jRepository<SceneNode, UUID>` |

**Remaining ports and their adapters:**

| Port | LOC | Methods | Adapter | Adapter LOC | Consumers |
|---|---|---|---|---|---|
| `ContentPersistencePort` | 83 | 37 | `Neo4jContentPersistenceAdapter` | 474 | 13 classes |
| `EmbeddingPort` | 15 | 4 | `EmbeddingModelAdapter` | 148 | `EmbeddingService`, `SemanticSearchService`, `SystemHealthService` |
| `SemanticSearchPort` | 58 | 2 | `Neo4jSemanticSearchAdapter`, `InMemorySemanticSearchAdapter` | 165 / 164 | `SemanticSearchService` |
| `PromptRepositoryPort` | 32 | 3 | `PromptRepositoryAdapter` | 61 | `RagService`, `TriadOrchestrationService`, `SceneDetectionClient` |
| `TemporalEdgePort` | 69 | 6 | `Neo4jTemporalEdgeAdapter` | 51 | `DefaultTemporalEdgeService`, `TriadEdgePersistenceService` |

**ContentPersistencePort consumers and the methods they call:**

| Consumer | Methods called |
|---|---|
| `IngestionService` | `findChapterByContentHash`, `createChapter`, `findBookById`, `findChapterById` |
| `IngestionJobService` | `countChunksByChapterId`, `findChaptersByUniverse`, `findChapterById`, `deleteChunksByChapterId`, `deleteScenesByChapterId` |
| `SceneProcessingService` | `findScenesByChapterId`, `deleteScenesByChapterId`, `findChapterById`, `addScenesToChapter` |
| `EmbeddingService` | `findChunksByChapterId`, `findChapterById`, `updateChunks` |
| `TriadBuilderService` | `findScenesByChapterId`, `findChapterIdsUpTo` |
| `EventOrderingService` | `findScenesByChapterId`, `findChapterTemporalEdges`, `findChapterIdsUpTo` |
| `LibraryService` | `findUniverseByName`, `createUniverse`, `findUniverseById`, `findSeriesByNameAndUniverseId`, `createSeries`, `findSeriesById`, `findBookById`, `findBookByTitleAndSeriesId`, `findStandaloneBookByTitleAndUniverseId`, `createBook` |
| `LibraryQueryService` | `findAllUniverses`, `findSeriesByUniverseId`, `findBooksByUniverseId`, `findBooksBySeriesId` |
| `RagService` | `findChunkById`, `findChapterById` |
| `ChunkingHandler` | `chunksExistForChapter`, `countChunksByChapterId`, `findChapterById`, `findScenesByChapterId`, `addChunksToScene` |
| `SceneDetectionHandler` | `findChapterById`, `findScenesByChapterId` |
| `EmbeddingHandler` | `findScenesByChapterId`, `countChunksByChapterId`, `findChapterById` |
| `InMemorySemanticSearchAdapter` | `findAllChunksWithEmbeddings` |

**Large services (>200 LOC) flagged for decomposition:**

| Service | LOC | Notes |
|---|---|---|
| `SceneProcessingService` | 694 | God class — scene parsing, scene saving, coordinate logic |
| `IngestionJobService` | 455 | Job lifecycle + progress tracking + cleanup |
| `EmbeddingService` | 370 | Batch embedding orchestration |
| `RagService` | 309 | RAG query + citation building |
| `SceneDetectionClient` | 268 | LLM call wiring for scene detection |
| `IngestionService` | 267 | Ingestion submission + event publishing |
| `SystemHealthService` | 285 | Multi-component health aggregation |
| `TextChunkingService` | 237 | Chunking algorithm |
| `SceneDetectionService` | 217 | Detection orchestration |
| `LibraryService` | 214 | Hierarchy CRUD via ContentPersistencePort |
| `HealthMetricsCollector` | 205 | Metrics assembly |

**Spring AI at plan time:** `1.0.0`, with upgrade to `1.1.4` planned and later completed.  
**XML parsing note:** this plan assumed `TriadXmlParser` would be removed as part of the structured-output cleanup. In the current codebase, structured `.entity(...)` mapping is used in `SceneDetectionClient`, but XML scene parsing still remains where it is working acceptably; finishing that cleanup is deferred.  
**Package count at plan time:** ~42 packages → target 12 (implemented result later converged to 10 top-level feature packages)

---

## Migration Strategy

Historical note: this sequencing was largely followed successfully. The key enduring idea was to do invasive persistence simplification before package flattening, so behavioural changes stayed easier to verify.

**Core principle:** Keep `ContentPersistencePort` interface stable throughout M2–M3. Services do not change their injection point — only the adapter internals change beneath them. Delete the port only after all consumers are migrated off it in M3.

**Sequence rationale:**
1. Move infra first (Node classes → domain @Node, repositories, adapter internals) — no service changes needed.
2. Remove the adapter/mapper once direct SDN access is proven to compile and tests pass.
3. Kill remaining ports one-by-one, injecting concrete beans or repositories directly.
4. Upgrade Spring AI; replace EmbeddingModelAdapter with `Neo4jVectorStore` / `TokenCountBatchingStrategy`.
5. Flatten packages last — no behavioural change, easy to verify.

---

## M2 — Content Entities → @Node

**Goal:** Annotate the 6 content domain entities with `@Node`, update repositories to return domain types directly, gut Neo4jMapper and Neo4jContentPersistenceAdapter of mirror-class references. Delete all 6 mirror Node classes and Neo4jMapper entirely.

`ContentPersistencePort` interface stays unchanged. All 13 consumers keep working without modification.

### Slice 2.1 — Annotate domain content entities

Files to change:

- `domain/content/Universe.java` — add `@Node("Universe")`, `@Id`, `@GeneratedValue` as per M1 pattern (see `IngestionJob.java`)
- `domain/content/Series.java` — same
- `domain/content/Book.java` — same
- `domain/content/Chapter.java` — same; field `embedding` → `@CompositeProperty` or `float[]` as needed
- `domain/content/Scene.java` — same; check relationship annotations for HAS_SCENE
- `domain/content/Chunk.java` — same; `embedding float[]` field kept; check HAS_CHUNK relationship

Cross-check each domain entity's fields against its corresponding Node mirror class to ensure no field is dropped. Relationship annotations (`@Relationship`) that live on the mirror must move to the domain entity.

Validation: `mvn compile` must pass. No behaviour change yet — adapters still use mirror classes.

### Slice 2.2 — Migrate repositories to domain types

For each repository, change type parameter from `Neo4jRepository<XxxNode, UUID>` to `Neo4jRepository<Xxx, UUID>`. Update any custom `@Query` methods whose projections reference Node-class field names if they differ from domain entity field names.

Repositories to change:

- `UniverseGraphRepository` → `Neo4jRepository<Universe, UUID>`
- `SeriesGraphRepository` → `Neo4jRepository<Series, UUID>`
- `BookGraphRepository` → `Neo4jRepository<Book, UUID>`
- `ChapterGraphRepository` → `Neo4jRepository<Chapter, UUID>`
- `SceneGraphRepository` → `Neo4jRepository<Scene, UUID>`
- `ChunkGraphRepository` → `Neo4jRepository<Chunk, UUID>`
- `EventGraphRepository` → `Neo4jRepository<Scene, UUID>`
- `TemporalGraphRepository` → `Neo4jRepository<Scene, UUID>`
- `TemporalEdgeWriteRepository` → `Neo4jRepository<Scene, UUID>`

Validation: `mvn compile`. Run full test suite — the adapter still compiles (it still uses mapper) but repositories now return domain types; adapter's mapper calls will fail to compile → that is the signal to move to slice 2.3.

### Slice 2.3 — Rewrite Neo4jContentPersistenceAdapter without mapper

Rewrite the 37 implementations in `Neo4jContentPersistenceAdapter` to work directly with the repositories (which now return domain types). Remove all `Neo4jMapper` calls. The adapter no longer needs to convert — repositories hand back domain objects directly.

Pattern (per method):
```java
// Before
@Override
public Optional<Chapter> findChapterById(UUID id) {
    return chapterRepo.findById(id).map(mapper::toDomain);
}

// After
@Override
public Optional<Chapter> findChapterById(UUID id) {
    return chapterRepo.findById(id);
}
```

For write methods:
```java
// Before
@Override
public Chapter createChapter(Chapter chapter) {
    ChapterNode node = mapper.toNode(chapter);
    return mapper.toDomain(chapterRepo.save(node));
}

// After
@Override
public Chapter createChapter(Chapter chapter) {
    return chapterRepo.save(chapter);
}
```

Do all 37 methods. Do not change method signatures — the port contract is unchanged.

Validation: `mvn compile`. Run full test suite.

### Slice 2.4 — Delete Neo4jMapper and all 6 mirror Node classes

With the adapter no longer referencing the mapper or Node classes, delete:

- `Neo4jMapper.java`
- `UniverseNode.java`
- `SeriesNode.java`
- `BookNode.java`
- `ChapterNode.java`
- `SceneNode.java`
- `ChunkNode.java`

Validation: `mvn compile && mvn test`. All 263+ tests must pass.

**M2 definition of done:** `mvn test` green, 0 references to `*Node` classes (except `@Node` annotation), `Neo4jMapper` deleted.

---

## M3 — Kill All Ports and Adapters

**Goal:** Delete all 5 ports and their adapters. Services inject concrete repositories or Spring beans directly. `ContentPersistencePort` is deleted last, after all 13 consumers are migrated.

### Slice 3.1 — Kill TemporalEdgePort (smallest, most isolated)

Consumers: `DefaultTemporalEdgeService`, `TriadEdgePersistenceService`.

Steps:
1. Inline the 6 methods from `Neo4jTemporalEdgeAdapter` directly into `DefaultTemporalEdgeService` and `TriadEdgePersistenceService` (or move them to a dedicated `TemporalEdgeRepository` with `@Query` methods).
2. Remove `TemporalEdgePort` injection from both services; inject repository directly.
3. Delete `TemporalEdgePort.java` and `Neo4jTemporalEdgeAdapter.java`.

Validation: `mvn test`.

### Slice 3.2 — Kill PromptRepositoryPort

Consumers: `RagService`, `TriadOrchestrationService`, `SceneDetectionClient`.

Steps:
1. Replace `PromptRepositoryAdapter` with a `@Component PromptRepository` class (same logic, no interface). Annotate with `@Component`, keep method names.
2. Change consumers from injecting `PromptRepositoryPort` to injecting `PromptRepository` (concrete class).
3. Delete `PromptRepositoryPort.java` and `PromptRepositoryAdapter.java`.

Validation: `mvn test`.

### Slice 3.3 — Kill EmbeddingPort

Consumers: `EmbeddingService`, `SemanticSearchService`, `SystemHealthService`.

Steps:
1. `EmbeddingModelAdapter` wraps a raw `RestTemplate` to call the embedding provider. After M4 this will be replaced by Spring AI's `EmbeddingModel` bean — but for now, keep the implementation, drop the interface.
2. Rename `EmbeddingModelAdapter` → `EmbeddingClient` (or similar concrete name).
3. Change consumers to inject `EmbeddingClient` directly.
4. Delete `EmbeddingPort.java`.

Note: This slice deliberately keeps the raw RestTemplate implementation intact; M4 will replace it with Spring AI's managed `EmbeddingModel`.

Validation: `mvn test`.

### Slice 3.4 — Kill SemanticSearchPort

Consumers: `SemanticSearchService`.

Two adapters exist: `Neo4jSemanticSearchAdapter` (165 LOC) and `InMemorySemanticSearchAdapter` (164 LOC). The in-memory adapter holds a `ContentPersistencePort` reference for `findAllChunksWithEmbeddings` — that must be resolved first (it will be resolved when ContentPersistencePort is fully removed in slice 3.5).

Steps:
1. Confirm production always uses `Neo4jSemanticSearchAdapter` (check `@Profile` or `@ConditionalOnProperty`). If so, `InMemorySemanticSearchAdapter` is test/dev-only.
2. Inline `Neo4jSemanticSearchAdapter` logic into `SemanticSearchService` or convert it to a `@Component SemanticSearchRepository`.
3. Change `SemanticSearchService` to inject concrete classes.
4. Delete `SemanticSearchPort.java`, `Neo4jSemanticSearchAdapter.java`.
5. Update `InMemorySemanticSearchAdapter` for tests — it can remain as a test-only `@TestComponent` that no longer implements the now-deleted port.

Validation: `mvn test` including integration.

### Slice 3.5 — Kill ContentPersistencePort (largest, last)

After M2, the adapter's internals already use domain @Node entities and repositories directly. The port is now a thin delegation layer. Migrate consumers one group at a time, then delete the port.

**Migration order (least coupled → most coupled):**

a) **Library group** — `LibraryService`, `LibraryQueryService`  
   Inject repositories (`UniverseGraphRepository`, `SeriesGraphRepository`, `BookGraphRepository`) directly. Remove `ContentPersistencePort` injection. These services only use 14 of the 37 port methods, all in the hierarchy group.

b) **Read services** — `TriadBuilderService`, `EventOrderingService`  
   Inject `ChapterGraphRepository`, `SceneGraphRepository` directly.

c) **Query** — `RagService`  
   Inject `ChunkGraphRepository`, `ChapterGraphRepository` directly.

d) **Search adapter** — `InMemorySemanticSearchAdapter`  
   Inject `ChunkGraphRepository` directly (for `findAllChunksWithEmbeddings`).

e) **Handlers** — `ChunkingHandler`, `SceneDetectionHandler`, `EmbeddingHandler`  
   Inject repositories directly. Handler logic stays the same, port reference removed.

f) **Content services** — `SceneProcessingService`, `EmbeddingService`  
   Inject `SceneGraphRepository`, `ChunkGraphRepository`, `ChapterGraphRepository` directly.

g) **Ingestion** — `IngestionService`, `IngestionJobService`  
   Most complex — these have write paths, transactions, and event publishing. Inject repositories. Preserve `@Transactional` boundaries.

After all consumers migrated:
- Delete `ContentPersistencePort.java`
- Delete `Neo4jContentPersistenceAdapter.java`
- Delete `Neo4jMapper.java` (already done in M2 — confirm it's gone)
- Delete `infrastructure/persistence/neo4j/adapter/` package if empty

Validation: `mvn compile && mvn test`. Zero references to `ContentPersistencePort`.

**M3 definition of done:** `mvn test` green, `application/port/` package deleted, all 5 adapters deleted, services inject repositories or concrete classes directly.

---

## M4 — Spring AI Upgrade + Structured Output + Package Flatten

Historical note: most of this milestone landed, but not every cleanup item was completed exactly as originally phrased below. In particular, the package-flattening outcome ended up at 10 top-level feature packages, and XML parsing cleanup is not fully finished because the remaining XML path is still working well enough.

### Slice 4.1 — Spring AI upgrade (1.0.0 → 1.1.4)

Steps:
1. Bump `spring-ai-bom` version in `pom.xml` to `1.1.4`.
2. Address any breaking API changes (consult Spring AI migration guide).
3. `mvn compile`. Fix compilation errors before anything else.
4. `mvn test`. Fix test failures.

Key improvements unlocked by 1.1.4:
- `Neo4jVectorStore` + `TokenCountBatchingStrategy` → replaces `EmbeddingModelAdapter`/`EmbeddingClient` raw RestTemplate
- `entity(MyRecord.class)` structured output → replaces `TriadXmlParser`
- `RetrievalAugmentationAdvisor` → simplifies parts of `RagService` (optional, not required)
- Micrometer token usage metrics → replaces `estimateTokens()` heuristic

### Slice 4.2 — Replace EmbeddingClient with Neo4jVectorStore

After slice 3.3 (EmbeddingPort dead), `EmbeddingClient` wraps a raw `RestTemplate`. Replace it with Spring AI's managed `EmbeddingModel` bean (already wired in `SpringAiConfig.java` as `@Qualifier("embeddingModel") EmbeddingModel`).

Steps:
1. In `EmbeddingService`, replace `EmbeddingClient.embedBatch(...)` with `embeddingModel.embed(...)` / `embeddingModel.embedAll(...)`.
2. Delete `EmbeddingClient.java` (formerly `EmbeddingModelAdapter`).
3. Evaluate whether `Neo4jVectorStore` can manage the HNSW index and vector writes directly, removing the manual embedding storage code in `EmbeddingService`.
4. `EmbeddingConfig.java` — the `RestTemplate` bean may no longer be needed; verify and remove if so.

Validation: `mvn test`. Run a live embedding smoke test.

### Slice 4.3 — Replace TriadXmlParser with structured output

Historical note: the original plan treated XML removal as part of M4. In practice, this became non-urgent. Structured response mapping via `.entity(...)` is already in use, but the remaining XML path works and is not creating enough operational friction to force immediate removal.

Prerequisite: confirm OpenRouter/Nebius provider supports `response_format: json_schema`.

Steps:
1. Define a Java record matching the triad analysis structure (timeline marker + two temporal relations).
2. In `TriadOrchestrationService`, change `.call()` to `.entity(TriadResult.class)` (Spring AI 1.1 generates JSON Schema from the record automatically).
3. Update prompts — remove XML-specific instructions, add JSON constraints if needed.
4. Delete `TriadXmlParser.java`.
5. Test with live or recorded provider responses.

Current status: partially realized in spirit, but not completed as a full XML-removal effort. Treat this slice as deferred cleanup rather than active roadmap work.

Validation: `mvn test`. Run a live triad detection smoke test.

### Slice 4.4 — Package flattening (layered → small feature-oriented package set)

Target structure (from `refactor-roadmap.md`):

```
com.lorevault.api/
├── config/       # Spring @Configuration beans
├── content/      # Universe, Series, Book, Chapter, Scene, Chunk — all @Node + repositories
├── library/      # LibraryService, LibraryQueryService
├── ingestion/    # IngestionJob, handlers, IngestionService, IngestionJobService
├── timeline/     # Temporal edges, event ordering
├── ai/           # SceneDetectionService/Client, TriadOrchestration, EmbeddingService
├── search/       # SemanticSearchService, RagService
├── health/       # SystemHealthService, HealthMetricsCollector, RetryableHealthChecker
├── web/          # REST controllers
├── web.command/  # CQRS command controllers
├── web.query/    # CQRS query controllers
└── support/      # Cross-cutting utilities (DTOs, events, validators)
```

This is a pure package-rename operation — no behaviour change. Use IDE/compiler-assisted batch rename to avoid errors.

Steps:
1. Create new packages.
2. Move files one feature area at a time (content → ingestion → ai → search → ...).
3. Fix import statements (compiler errors guide you).
4. Delete now-empty old packages.
5. Verify no leftover `infrastructure/`, `service/`, `application/`, `handler/` top-level packages remain.

Validation: `mvn compile && mvn test`.

**Original M4 definition of done:** `mvn test` green, Spring AI on 1.1.4, TriadXmlParser deleted, ~12 packages, ~55 total files.

Actual outcome: Spring AI upgrade and package flattening landed; the implemented package layout converged to 10 top-level feature packages; the exact file-count target was not pursued literally; XML cleanup remains deferred.

---

## Commit Conventions

- Prefix: `refactor(m2):`, `refactor(m3):`, `refactor(m4):`
- Each slice = at least one commit; large slices get multiple commits split by concern
- No commit leaves the build broken
- Co-author: `Co-authored-by: Sisyphus <clio-agent@sisyphuslabs.ai>`

## Resolved Decisions

### Decision 1 — Relationship fields on domain entities ✅
**Verdict: Annotate all 6 domain entities directly. No mapping records.**

Field-by-field audit:
- **Universe**: 1:1 scalar match with `UniverseNode` — add `@Node("Universe")`, `@Id`, done.
- **Series**: scalars match; `SeriesNode`'s `@Relationship UniverseNode universe` is navigation sugar on top of the `universeId` UUID already present on the domain entity — not needed.
- **Book**: same as Series — `universeNode` and `seriesNode` relationship objects are navigation sugar on top of UUIDs already on the domain.
- **Chunk**: scalar fields align exactly; associations are modeled via `SceneHasChunk` externally on the Node — verify whether `SceneHasChunk` carries relationship properties; if bare, replace with direct `@Relationship`.
- **Chapter**: shape differs — `ChapterNode` carries denormalized flat display fields (`universe`, `series`, `bookTitle`, `bookNumber`, `chapterNumber`), `@Relationship BookNode book`, `@CreatedDate`/`@LastModifiedDate`, `@Property("rawText")`, `@PersistenceCreator`. Resolution: absorb the denormalized fields into domain `Chapter` (they are genuine facts about the chapter, not infrastructure). `PublicationCoordinates` can remain as a domain value object; SDN flattens its fields automatically via `@TargetNode` or inline properties. No mapping record.
- **Scene**: field names differ (`startCharacterOffset` vs `startOffset`/`endOffset`) — rename in domain. `SceneNode` has `@DynamicLabels List<String> labels` and `chapterId` UUID for query efficiency — add both to domain `Scene`. Annotate directly, no mapping record.

SDN annotations to move from Node classes to domain entities: `@Node`, `@Id`, `@Relationship`, `@Property`, `@CreatedDate`, `@LastModifiedDate`, `@DynamicLabels`, `@PersistenceCreator`.

Reference pattern: `IngestionJob.java` — domain class with `@Node`, `@Id`, `@Relationship`, `@CreatedDate`.

---

### Decision 2 — InMemorySemanticSearchAdapter fate ✅
**Verdict: Delete in M3.4. Replace its TCK test with `FakeSemanticSearchPort`.**

- `@ConditionalOnProperty(name="lorevault.search.provider", havingValue="memory", matchIfMissing=false)` — never Spring-activated by default.
- No test sets `lorevault.search.provider=memory` via Spring property sources — it is never managed by Spring in the test suite.
- Only consumer: `InMemorySemanticSearchAdapterTckTest` constructs it directly as a plain object.
- Its `matchesFilters()` is a stub (always returns `true`); results return `null` for `chapterId`/`bookNumber`/`chapterNumber` — functionally inferior to `Neo4jSemanticSearchAdapter`.
- `FakeSemanticSearchPort` already exists in `SemanticSearchServiceTest` — use that pattern for the TCK replacement.
- 164 LOC with no production or meaningful test value → delete.

---

### Decision 3 — Provider JSON Schema support for `.entity()` ✅
**Verdict: No blocker. Use default prompt-injection mode unconditionally.**

Spring AI 1.1.x `.entity(Record.class)` has two modes:
- **Default (prompt injection)**: `BeanOutputConverter.getFormat()` appends the JSON schema as text in the user message. No `response_format` field sent. Works with any LLM including Groq and Google. **Use this.**
- **Native (opt-in)**: Requires `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` in the advisor chain. Sends `response_format: { type: "json_schema", strict: true }`. Groq supports this only for `openai/gpt-oss-20b` and `openai/gpt-oss-120b` in strict mode. **Do not enable.**

M4.3 can proceed unconditionally with default mode. No provider check needed.

---

### Decision 4 — Neo4jVectorStore / Neo4j 5.26 HNSW compatibility ✅
**Verdict: Fully supported. No blocker.**

- Spring AI `Neo4jVectorStore` minimum requirement: Neo4j 5.15.
- Spring AI's own CI tests run against Neo4j 5.24.
- Neo4j 5.26 is above both — fully compatible.
- Only relevant 1.1.x change: a filter expression key validation bug fix in `Neo4jVectorFilterExpressionConverter` — not breaking, no API change.
- HNSW index creation Cypher stable since Neo4j 5.11.
