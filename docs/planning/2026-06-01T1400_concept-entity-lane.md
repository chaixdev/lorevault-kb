# Concept Entity Lane

**Status:** PLANNING  
**Last Updated:** June 1, 2026 (v2 — catalog deferred, plain String conceptType)

## Summary

Add the 6th regular entity resolution lane for Concept using the established `Mention → ChapterEntity → BookEntity` ladder. Concept covers species, technologies, artifact classes, doctrines, roles, and other narrative-significant categories that are not acting groups, locations, objects, individuals, or events.

The prompt already defines Concept as a category (line 47 of `scene-analysis.txt`) but has no extraction output section — no `<concepts>` XML block, no `ConceptExtraction` record, and no persistence.

---

## What's the same (mechanical replication)

The following are pure replication of the established 5-lane pattern. No design decisions needed — follow the existing template.

### 1. Entity nodes (3 levels)

| Level | Class | @Node labels | Key fields |
|-------|-------|-------------|------------|
| Mention | `ConceptMention` in `graph/concept/persistence/` | `ConceptMention`, `EntityMention`, `ConceptNode`, `EntityNode` | `id`, `source`, `displayName`, `normalizedName`, `aliases`, `conceptType` (String, raw LLM label), `description`, `certainty`, `evidence`, `stageId`, `sceneId`, `chapterId`, `bookId`, `resolutionStatus`, `extractionIndex` |
| Chapter | `ChapterConcept` in `graph/concept/persistence/` | `ChapterConcept`, `ChapterEntity`, `ConceptNode`, `EntityNode` | `id`, `displayName`, `normalizedName`, `aliases`, `conceptType`, `chapterId`, `stageId` |
| Book | `BookConcept` in `graph/concept/persistence/` | `BookConcept`, `BookEntity`, `ConceptNode`, `EntityNode` | `id`, `displayName`, `normalizedName`, `aliases`, `conceptType`, `bookId`, `stageId` |

`ConceptMention` implements `Mention` interface. `ChapterConcept` and `BookConcept` are consolidated aggregates — they do NOT implement `Mention` (same as `ChapterCollective`/`BookCollective`). Pattern: identical to `CollectiveMention` / `ChapterCollective` / `BookCollective`.

Note: `ConceptMention.description` is the one deviation from the Collective pattern — Collective has no `description` field. Concepts benefit from definitions; this is an intentional design choice.

### 2. Graph repositories (3 files)

- `ConceptMentionGraphRepository` — extends `Neo4jRepository<ConceptMention, UUID>`
- `ChapterConceptGraphRepository` — extends `Neo4jRepository<ChapterConcept, UUID>`
- `BookConceptGraphRepository` — extends `Neo4jRepository<BookConcept, UUID>`

Pattern: identical to Collective repositories. Include `linkMentionToScene`, `countBySceneIdAndContentIdentity`, constraint/index queries.

### 3. Persistence service (1 file)

`ConceptPersistenceService` in `graph/concept/persistence/`:
```java
@Transactional
public Map<String, UUID> persistExtractedConcepts(
    StageExecutionContext ctx,
    List<Scene> persistedScenes,
    List<TriadAnalysisModels.SceneConceptExtraction> sceneExtractions)
```
Pattern: identical to `CollectivePersistenceService`. Returns `Map<String, UUID>` with normalized alias keys → mention UUID values.

### 4. Extraction models (TriadAnalysisModels)

Add:
- `ConceptExtraction` record: `aliases`, `conceptType`, `description`, `certainty`, `evidence`
- `SceneConceptExtraction(int sceneIndex, List<ConceptExtraction> concepts) implements SceneExtraction`
- Update `SceneExtraction` sealed interface permits list

Pattern: identical to `CollectiveExtraction` / `SceneCollectiveExtraction`.

### 5. Scene relationship outcome — convert to builder

`SceneRelationshipOutcome` currently has 7 fields + 3 convenience constructors. The concept lane adds `sceneConceptExtractions`, and every future lane adds another. Positional constructors are unsustainable.

**Replace canonical constructor + all convenience constructors with a builder:**

```java
@Builder
public record SceneRelationshipOutcome(
    List<SceneAnalysisResult> analyses,
    List<SceneIndividualExtraction> sceneIndividualExtractions,
    List<SceneCollectiveExtraction> sceneCollectiveExtractions,
    List<SceneObjectExtraction> sceneObjectExtractions,
    List<SceneLocationExtraction> sceneLocationExtractions,
    List<SceneEventExtraction> sceneEventExtractions,
    List<SceneConceptExtraction> sceneConceptExtractions,  // NEW
    List<SceneRelationClaimExtraction> sceneRelationClaimExtractions
) {}
```

All 4 call sites (`buildOutcome`, `SceneDetectionHandler` fallback, and 2 test references) switch to `.builder().<fields>().build()`. The builder defaults missing `List` fields to `null`, so existing callers that don't set `sceneConceptExtractions` compile immediately.

For `buildOutcome()`, replace positional constructor call with:
```java
SceneRelationshipOutcome.builder()
    .analyses(...)
    .sceneIndividualExtractions(...)
    ...
    .sceneConceptExtractions(coalesce(concepts, SceneConceptExtraction::new))
    .sceneRelationClaimExtractions(...)
    .build();
```

For `SceneDetectionHandler` empty-scene fallback, replace 5-arg constructor with:
```java
SceneRelationshipOutcome.builder().build()
```
The builder produces `null` for all List fields — same effective result as the current `List.of()` chain but without the positional maintenance burden.

### 6. Normalization (SceneRelationshipAnalysisService)

Add:
- `TriadConceptExtraction` record: `aliases`, `conceptType`, `description`, `certainty`, `evidence`
- Update `TriadCurrentSceneEntities` canonical constructor to include `concepts` field. **Update both convenience constructors** — they use `List.of()` positional delegation and adding a 6th entity field shifts all positions.
- Note: `description` is present in `TriadConceptExtraction` but absent in `TriadCollectiveExtraction`. This is an intentional deviation — concepts benefit from definitions.
- `normalizeConcepts()` method — maps `TriadConceptExtraction → ConceptExtraction` with line-based `certainty`/`evidence` extraction. Identical pattern to `normalizeCollectives()` except maps `description` as well.
- Integration in `analyzeChapterTriads()`: new `Map<Integer, List<ConceptExtraction>> extractedConceptsBySceneIndex` variable + new `mergeIfNotEmpty(...)` call within the extraction loop

### 7. Handler wiring (SceneDetectionHandler)

In `SceneDetectionHandler.execute()` (`orchestration/scene/SceneDetectionHandler.java`):
- Add `ConceptPersistenceService` as constructor dependency (14 → 15 constructor params — largest constructor in the codebase; a builder or parameter object refactor is deferred)
- Call `conceptPersistenceService.persistExtractedConcepts(ctx, scenes, outcome.sceneConceptExtractions())`
- Pass `conceptIds` map to `relationClaimPersistenceService.persistExtractedRelationClaims()`
- Update empty-scene fallback from positional constructor to `SceneRelationshipOutcome.builder().build()`

### 8. claim-entity linking (RelationClaimPersistenceService)

Add `Map<String, UUID> conceptIds` parameter to `persistExtractedRelationClaims()`.
Add `"Concept" -> conceptIds` case to the switch in `resolveMentionId()`.

Note: `"Event"` currently falls through to `default → null` (Event claim-entity linking is deferred). Adding Concept's case leaves Event still unhandled — this asymmetry is intentional but should be documented in the code as `// TODO: Event claim-entity linking (Phase 4)`.

### 9. Consolidation services (2 services)

| Service | File | Pattern |
|---------|------|---------|
| `ChapterConceptConsolidationService` | `graph/concept/consolidation/chapter/` | Identical to `ChapterCollectiveConsolidationService` |
| `BookConceptConsolidationService` | `graph/concept/consolidation/book/` | Identical to `BookCollectiveConsolidationService` |

Both use shared `ConsolidationEngine<S>`. Chapter service groups by `normalizedName`; book service groups by `normalizedName + bookId`. Alias-aware connected-components clustering.

### 10. StageOperation handlers (2 handlers)

| Handler | @ForStage | Pattern |
|---------|-----------|---------|
| `ChapterConceptConsolidationHandler` | `CHAPTER_CONCEPT_CONSOLIDATION` | Identical to `ChapterCollectiveConsolidationHandler` |
| `BookConceptConsolidationHandler` | `BOOK_CONCEPT_CONSOLIDATION` | Identical to `BookCollectiveConsolidationHandler` |

Both implement `StageOperation`, call consolidation service, return `StepResult`.

### 11. StageKey (2 new values)

```java
CHAPTER_CONCEPT_CONSOLIDATION,   // in CHAPTER_STAGES set
BOOK_CONCEPT_CONSOLIDATION,      // in BOOK_LEVEL_STAGES set
```

### 12. StageDag (3 new edges)

```
CHAPTER_CONCEPT_CONSOLIDATION → BOOK_CONCEPT_CONSOLIDATION → INGESTION_COMPLETE
```

Plus: `SCENE_SEGMENTATION → CHAPTER_CONCEPT_CONSOLIDATION` (fan-out from root).

### 13. Command controllers (2 controllers)

- `ChapterConceptConsolidationCommandController` — manual rerun endpoint for chapter-level
- `BookConceptConsolidationCommandController` — manual rerun endpoint for book-level

Pattern: identical to `ChapterCollectiveConsolidationCommandController` / `BookCollectiveConsolidationCommandController`.

### 14. Schema/index (Neo4j)

Constraints on `ConceptMention`, `ChapterConcept`, `BookConcept` following existing pattern. Index on `normalizedName`, `conceptType`, `chapterId`, `bookId`.

Note: existing entity lanes have `(chapterId, normalizedName)` indexes only. Adding a `conceptType` index is forward-looking (future catalog-driven queries by type) and deviates from the minimal-schema pattern — defer to v2.

### 16. Tests

- `ConceptPersistenceServiceTest` — mock-based, same pattern as `CollectivePersistenceServiceTest`
- `ChapterConceptConsolidationServiceTest` — unit test with ConsolidationEngine
- `BookConceptConsolidationServiceTest` — unit test
- `SceneRelationshipAnalysisServiceTest` — verify `normalizeConcepts()` / `TriadConceptExtraction`
- Update `RelationClaimNormalizationTests` — verify concept alias resolution in claims (new test file or update existing)
- Update `StageDispatcherTest` — add `H_CHAPTER_CONCEPT_CONSOLIDATION`, `H_BOOK_CONCEPT_CONSOLIDATION` stub handler classes
- Update `StageDispatcherWiringTest` — add concept handler `.class` entries to `HANDLER_CLASSES` (15 → 17 handlers), update `allStageKeysCovered()` (16 → 18 keys), update `handlerListIsComplete()` assertion
- Update `StageDagTest` — verify concept stages in DAG
- Update `AssociationEntityLabelTest` — EntityNode label on ConceptMention, ChapterEntity/BookEntity on chapter/book nodes
- Update `MentionRecordTest` — Mention contract

---

## What's different (needs design decisions)

### D1. Concept type vocabulary — plain String, catalog deferred

`ConceptMention` stores `conceptType` as a plain `String` — the raw LLM-extracted label (e.g., `"species"`, `"technology"`, `"doctrine"`). No catalog resolution for v1. A `// TODO: resolve conceptType through ObjectKindCatalogService when catalog ships` comment marks the insertion point in `ConceptPersistenceService`.

V2: replace the raw string with `catalogId` + `definitionKey` when the ObjectKind catalog is designed and shipped. The planning for that is parked at `docs/planning/2026-06-01T1415_catalog-objectkind.md`.

`ChapterConcept` and `BookConcept` also store `conceptType` as plain String. No catalog module dependency from concept lane code.

### D2. Extraction boundaries and over-extraction risk

Concepts are uniquely prone to over-extraction because ordinary nouns ("captain", "war", "laser") appear everywhere.

**Recommendation: Accept over-extraction, manage through prompt tuning.** The prompt is the vocabulary boundary. Instruct the LLM to extract only narrative-significant categories. No mechanical quality filter — thresholds are hard to get right and arbitrary. Over-extraction is cheaper than under-extraction.

### D3. Normalization and identity key

**Cross-kind multiplicity is intentional.** "Battlestar Galactica" can appear as a Location (setting), an Object (vehicle), and a Concept (political symbol) — three different subgraphs, same name, different kinds. The architecture explicitly avoids creating a single canonical entity node. Each consolidation lane produces its own typed subgraph. The `EntityNode` label enables cross-kind querying when needed, but consolidation stays per-kind.

Within the Concept lane, same-name-different-type (e.g., "Dragon" as species vs "Dragon" as artifact-class) is possible but rare. `conceptType` distinguishes subtypes for query purposes.

**Recommendation: Name-only key for v1.** Same `NameNormalizer.normalize()` as all other lanes. `conceptType` is an attribute, not part of the consolidation identity key. If same-name-different-type collisions become a query concern, `conceptType` can be added to the identity key in v2.

### D4. Prompt design — dedicated extraction section or relation-only?

**Context:** The prompt already allows `Concept` as an entityType in `<relation>` blocks (subject/object). Should concepts also get a dedicated `<concepts>` extraction section under `<current_scene_entities>`, or should they only appear in relations?

**Existing pattern:** All 5 other entity types have dedicated extraction sections (`<individuals>`, `<collectives>`, `<objects>`, `<locations>`, `<events>`). The LLM extracts them independently of whether they appear in relations.

**Options:**

| Option | Pros | Cons |
|--------|------|------|
| **A) Full extraction section** (`<concepts>` with `<concept_type>`, `<aliases>`, `<description>`) | Same pattern as all other types. Enables concept mentions to be first-class graph nodes. claim-entity linking can resolve concept aliases. | Risk of over-extraction (see D2). |
| **B) Relation-only** — concepts only appear as subject/object in relation claims, never extracted independently | Zero over-extraction risk. | Concepts can't be queried independently. No concept anchors for retrieval. Defeats the purpose of the Concept lane. |
| **C) Extracted but optional** — `<concepts>` section exists but the prompt emphasizes it's optional and only for narrative-significant concepts | Balances extraction with noise control. | Same as A but with softer guardrails. |

**Recommendation: Option A — full extraction section.** Completing the implementation across the pipeline — concepts get the same treatment as all other entity types. The prompt should follow the same structure as `<collectives>` since the extraction shape is similar (aliases + concept_type + description).

---

## Implementation order (by dependency)

| Phase | Files | Depends on |
|-------|-------|-----------|
| **P1) Extraction models** | `TriadAnalysisModels.java` — add ConceptExtraction, SceneConceptExtraction, update SceneExtraction sealed interface. Convert SceneRelationshipOutcome to `@Builder` (replace canonical + 3 convenience constructors). | None |
| **P2) Prompt + parsing** | `scene-analysis.txt` — add `<concepts>` section with `<concept_type>` (prompt-guided, lowercase kebab-case). `SceneRelationshipAnalysisService.java` — add TriadConceptExtraction, normalizeConcepts(), integrate into analyzeChapterTriads(). Switch `buildOutcome()` to builder calls. | P1 |
| **P3) Entity nodes + repositories** | 6 files: `ConceptMention` (stores `conceptType String`), `ChapterConcept`, `BookConcept` + 3 repositories. | None (parallel with P1) |
| **P4) Persistence service** | `ConceptPersistenceService` — // TODO: resolve conceptType through ObjectKindCatalogService when catalog ships. For v1, store raw LLM label. Returns `Map<String, UUID>`. | P1, P3 |
| **P5) Handler wiring** | `orchestration/scene/SceneDetectionHandler.java` — add ConceptPersistenceService dep + wire conceptIds to RelationClaimPersistenceService | P4 |
| **P6) claim-entity linking** | `RelationClaimPersistenceService` — add conceptIds param + `"Concept" -> conceptIds` switch case | P4 |
| **P7) StageKey + StageDag** | `StageKey.java`, `StageDag.java` — add 2 stage keys + 3 edges + fan-in | None (parallel with P1) |
| **P8) Consolidation services** | `ChapterConceptConsolidationService`, `BookConceptConsolidationService` | P3 |
| **P9) Handlers** | `ChapterConceptConsolidationHandler`, `BookConceptConsolidationHandler` | P7, P8 |
| **P10) Command controllers** | `ChapterConceptConsolidationCommandController`, `BookConceptConsolidationCommandController` | P8 |
| **P11) Schema/index** | Neo4j constraints and indexes for concept nodes | P3 |
| **P12) Pipeline coordinator** | `IngestionPipelineCoordinator` — rerun wiring | P7, P9 |
| **P13) Tests** | 10+ test files across core and web | All phases |

### Parallelization opportunities

- **P1 + P3 + P7** can run in parallel (no dependencies between them)
- **P2** depends on P1 (extraction models must exist before service can consume them)
- **P4** depends on P1, P3 (extraction models + entity nodes)
- **P5 + P6** depend on P4 (persistence service must exist before handler wiring)
- **P8 + P11** can run in parallel after P3 (entity nodes + schema)
- **P9 + P10 + P12** can run in parallel after P7 and P8 (stages + consolidation services)
- **P13** runs after all implementation phases

---

## Files to create (~23 files)

### Core module — graph entities (P3)

```
lorevault-core/src/main/java/com/lorevault/api/graph/concept/
├── persistence/
│   ├── ConceptMention.java
│   ├── ConceptMentionGraphRepository.java
│   ├── ChapterConcept.java
│   ├── ChapterConceptGraphRepository.java
│   ├── BookConcept.java
│   ├── BookConceptGraphRepository.java
│   └── ConceptPersistenceService.java
├── consolidation/
│   ├── chapter/
│   │   ├── ChapterConceptConsolidationService.java
│   │   ├── ChapterConceptConsolidationOperation.java
│   │   ├── ChapterConceptConsolidationResult.java
│   │   └── ChapterConceptConsolidationHandler.java
│   └── book/
│       ├── BookConceptConsolidationService.java
│       ├── BookConceptConsolidationOperation.java
│       ├── BookConceptConsolidationResult.java
│       └── BookConceptConsolidationHandler.java
```

### Web module — controllers (P10)

```
lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/
├── ChapterConceptConsolidationCommandController.java
└── BookConceptConsolidationCommandController.java
```

## Files to modify (~13 files)

```
lorevault-core/src/main/java/com/lorevault/api/
├── orchestration/
│   ├── triad/
│   │   ├── TriadAnalysisModels.java              — add ConceptExtraction, SceneConceptExtraction
│   │   └── SceneRelationshipAnalysisService.java — add TriadConceptExtraction, normalizeConcepts()
│   ├── pipeline/
│   │   ├── StageKey.java                         — add 2 enum values + classification sets
│   │   └── StageDag.java                         — add 3 edges + fan-in
│   │   └── IngestionPipelineCoordinator.java     — rerun wiring
│   └── scene/
│       └── SceneDetectionHandler.java            — add ConceptPersistenceService dep + wire
├── graph/
│   ├── event/scene/Scene.java                    — unchanged (wire via SceneDetectionHandler)
│   └── relation/RelationClaimPersistenceService.java — add conceptIds param + switch case

lorevault-core/src/main/resources/prompts/
└── scene-analysis.txt                         — add <concepts> extraction section
```

## Resolved

1. **Concept type vocabulary scope:** The prompt provides example categories (species, technology, doctrine, role, artifact-class) but the LLM is free to produce whatever it deems appropriate. The catalog (`ObjectKindCatalogService`) handles dedup and clustering — it's the vocabulary limiter, not the prompt.

2. **Quality filter:** Dropped. Premature optimization — thresholds are arbitrary and risk losing narrative nuance. Over-extraction is acceptable; the catalog is the natural vocabulary boundary.

3. **Event vs Concept overlap:** Prompt tuning. Instruct the LLM to make a judgment call when something could be both (e.g., "The Winter War" as Event vs Concept). The architecture already handles multi-faceted entities across subgraphs — as long as the extraction's facet is clear, cross-kind multiplicity is correct behavior.

4. **Cross-kind identity:** Separate subgraphs — no merging across kinds. Each consolidation lane produces its own typed view. Future: cross-kind relation claims between subgraph views of the same real-world entity. E.g., `(ObjectMention{name:"Hephaestus"}) -[:RELATES_SUBJECT]-> (RelationClaim{name:"is meeting place of"}) -[:RELATES_OBJECT]-> (LocationMention{name:"Hephaestus"})` — the Object and Location facets of the same spaceship connected through a typed relation. Out of scope for v1 but a natural extension of the architecture.

---

## Links

- Original planning: `docs/planning/2026-04-30T1237_concept-resolution-lane.md`
- Entity resolution ladder pattern: `docs/patterns/ingestion/entity-resolution-ladder.md`
- claim-entity linking pattern: `docs/patterns/ingestion/claim-entity-linking.md`
- Coding standards: `docs/rules/coding-standards.md`
- Unified consolidation: `docs/archive/planning/2026-05-27T0015_unified-entity-consolidation.md`
