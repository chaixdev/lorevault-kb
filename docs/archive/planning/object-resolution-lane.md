# Object Entity Resolution Lane

**Status:** NOT STARTED  
**Last Updated:** April 29, 2026

## Summary

Complete the Object cardinal entity lane by extending the already-shipped Object mention extraction slice into the established `Mention → ChapterEntity → BookEntity` resolution ladder. Object should become the third regular entity lane after Individual and Location, while Event remains excluded because it follows a distinct temporal/event-resolution path.

This work must follow the existing Individual/Location lane shape. It must not generalize Event into the regular entity ladder, and it must not introduce a shared lane abstraction before the existing concrete lane pattern proves insufficient.

## Problem

Object mentions are currently extracted from scene analysis, persisted as `ObjectMention` nodes, and linked from scenes with `Scene-[:MENTIONS]->ObjectMention`. The lane stops there.

This leaves objects without chapter-scoped or book-scoped identity anchors. Queries and future graph traversal can see scene-local evidence for objects such as weapons, artifacts, tools, documents, vessels, or named relics, but cannot reliably use a `ChapterObject` or `BookObject` as a canonical graph anchor.

## Product Context

- Object-centric questions such as “where does the sword appear?”, “which scenes mention the logbook?”, or “what artifacts matter in this chapter?” cannot be grounded against canonical object nodes.
- Retrieval can use scene-level object evidence, but it cannot navigate through a stable chapter/book object identity backbone.
- Collective and Concept lanes are planned next; completing Object first gives the project a third regular lane and a stronger template for entity-type-specific extraction and matching rules.
- Typed semantic edges between cardinal entities will need canonical Object targets when relationships such as ownership, use, containment, or transport are introduced later.

## Technical Context

The established pattern is documented in `docs/patterns/ingestion/entity-resolution-ladder.md`:

- `SceneDetectionHandler` persists scenes and writes mention evidence before consolidation.
- Regular entity lanes listen to `ScenesDetectedEvent` and independently run chapter resolution followed by book reduction.
- Chapter resolution groups mention evidence into `ChapterEntity` nodes.
- Book reduction groups chapter entities into thin `BookEntity` identity anchors.
- Book reduction is delete-and-rebuild and uses the persisted `BookReductionClaim` guard for same-book serialization.
- Completed regular lanes publish a book-reduced event that participates in ingestion completion fan-in.

Object already has the evidence layer:

| File | Current role |
|---|---|
| `lorevault-core/src/main/resources/prompts/scene-analysis.txt` | Defines Object as an extracted scene entity category |
| `TriadAnalysisModels` | Defines `ObjectExtraction` and `SceneObjectExtraction` |
| `SceneRelationshipAnalysisService` | Normalizes triad object output into scene-level extractions |
| `SceneDetectionHandler` | Calls `ObjectPersistenceService` after scene persistence |
| `ObjectPersistenceService` | Saves `ObjectMention` and links it to `Scene` |
| `ObjectMention` | Flat persisted mention record with Object-specific metadata |
| `ObjectMentionGraphRepository` | Saves object mentions and creates scene mention links |

The missing ladder is:

```text
Scene -[:MENTIONS]-> ObjectMention -[:REFERS_TO]-> ChapterObject -[:REFERS_TO]-> BookObject
```

## Scope

### 1. Domain and repository model

Add Object aggregate entities in `lorevault-core/src/main/java/com/lorevault/api/content/association/`:

- `ChapterObject`
- `BookObject`
- `ChapterObjectGraphRepository`
- `BookObjectGraphRepository`

The node labels should follow the broad-label convention used by the existing aggregate entities:

- `@Node(primaryLabel = "ChapterObject", labels = "ChapterEntity")`
- `@Node(primaryLabel = "BookObject", labels = "BookEntity")`

Recommended state:

- `ChapterObject`
  - `id`
  - `chapterId`
  - `displayName`
  - `normalizedName`
  - `aliases`
  - `type`
  - `material`
  - `purpose`
  - `description`
  - `mentionCount`
  - `createdAt`, `updatedAt`
- `BookObject`
  - `id`
  - `bookId`
  - `displayName`
  - `normalizedName`
  - `aliases`
  - `type`
  - `material`
  - `purpose`
  - `description`
  - `chapterObjectCount`
  - `representativeChapterObjectId`
  - `firstSeenChapterId`
  - `createdAt`, `updatedAt`

This mirrors Location’s richer aggregate state more closely than Individual because Object evidence already carries aliases plus descriptive metadata. The additional Object-specific fields should be treated as representative metadata, not as canonical facts.

### 2. Object chapter resolution

Add `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/object/` with:

- `ChapterObjectResolutionService`
- `ChapterObjectResolutionHandler`
- `ChapterObjectResolutionResult`

The handler must mirror the existing regular lane branch shape:

1. listen to `ScenesDetectedEvent`
2. call `ChapterObjectResolutionService.resolveChapter(chapterId)`
3. publish `ChapterObjectsResolvedEvent`

Chapter resolution should:

- count `ObjectMention` nodes by `chapterId`
- delete existing `ChapterObject` state for the chapter
- group resolvable mentions deterministically
- create one `ChapterObject` per group
- link `ObjectMention-[:REFERS_TO]->ChapterObject`
- mark linked mentions as `chapter-resolved`
- publish `ChapterObjectsResolvedEvent` even when a chapter has no object mentions, using a successful zero-count result so downstream completion does not hang

#### Object v1 matching rule

Use deterministic `normalizedName` grouping as the v1 merge authority.

Object aliases and descriptive fields should be preserved on `ChapterObject` and `BookObject`, but should not aggressively bridge groups in v1. This keeps the lane inside the established deterministic ladder while avoiding false merges for generic object language such as “sword”, “door”, “key”, “book”, or “ship”.

Object-specific metadata should influence representative selection and diagnostics, not graph identity, unless a later accepted design explicitly introduces stronger object matching.

V1 must not introduce LLM-backed merge adjudication, embedding-assisted identity matching, or prompt-time merge decisions for Object resolution.

### 3. Object book reduction

Add:

- `BookObjectReductionService`
- `BookObjectPersistenceService`
- `BookObjectReductionHandler`
- `BookObjectResolutionResult`

The book reduction handler must mirror Individual/Location:

1. listen to `ChapterObjectsResolvedEvent`
2. call `BookObjectReductionService.resolveBook(bookId)`
3. publish `BookObjectsReducedEvent`

Book reduction should:

- use `BookReductionClaimService` before destructive book-level rebuilds
- gather `ChapterObject` candidates for the book
- group by `normalizedName`
- preserve a representative chapter object and first-seen chapter reference
- delete-and-rebuild `BookObject` nodes for the book
- recreate `ChapterObject-[:REFERS_TO]->BookObject` links
- emit `BookObjectsReducedEvent` even when no chapter objects exist, using a successful zero-count result

### 4. Events and ingestion completion

Add immutable event classes under `lorevault-core/src/main/java/com/lorevault/api/ingestion/events/`:

- `ChapterObjectsResolvedEvent`
  - event type: `CHAPTER_OBJECTS_RESOLVED`
  - fields matching the regular chapter-resolution events: `bookId`, `processed`, `mentionCount`, `chapterObjectCount`
- `BookObjectsReducedEvent`
  - event type: `BOOK_OBJECTS_REDUCED`
  - fields matching the regular book-reduction events: `bookId`, `processed`, `chapterObjectCount`, `bookObjectCount`

Update `IngestionCompletionCoordinator` so `BookObjectsReducedEvent` is a required completion branch once the Object lane exists.

The completion contract should then wait for:

- `EmbeddingsCompletedEvent`
- `BookIndividualsReducedEvent`
- `BookLocationsReducedEvent`
- `BookObjectsReducedEvent`
- `ChapterEventsResolvedEvent`
- `BookEventCandidatesGeneratedEvent`

Object resolution should not be optional post-processing after implementation. If the lane is implemented, terminal ingestion correctness should include it.

### 5. Manual rerun command endpoints

Add manual command endpoints in `lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/` following Individual/Location naming and response shape:

- `ChapterObjectResolutionCommandController`
- `BookObjectResolutionCommandController`
- `ChapterObjectResolutionResponse`
- `BookObjectResolutionResponse`

Expected endpoint shape:

- `POST /api/command/ingest/chapters/{chapterId}/resolve-objects`
- `POST /api/command/ingest/books/{bookId}/resolve-objects`

These endpoints are rerun tools only. They must not replace the automatic `ScenesDetectedEvent` branch.

### 6. Schema and indexes

Update `Neo4jSchemaInitializer` with Object aggregate constraints and indexes equivalent to the existing Individual/Location aggregate support.

Required coverage should include:

- unique `ChapterObject.id`
- unique chapter/name scope for `ChapterObject`
- lookup index for `ChapterObject(chapterId, normalizedName)`
- unique `BookObject.id`
- unique book/name scope for `BookObject`
- lookup index for `BookObject(bookId, normalizedName)`

Any repository `WHERE` clause introduced for Object resolution must have matching index coverage.

All repository Cypher must remain parameterized. New constraints and indexes should follow the same SDN/Neo4j schema initializer conventions as the Individual and Location aggregate entries.

### 7. Scene-analysis and Java mapping review

Object extraction already exists, but the implementation should review and tighten the scene-analysis prompt and Java mapping so Object-specific semantics remain consistent end to end.

The prompt should continue to distinguish:

- Object: inanimate physical things, weapons, vehicles, artifacts, documents, tools, structures
- Location: spatial references, rooms, cities, regions, planets, coordinates
- Concept: abstract categories, species, titles, roles, doctrines, technologies as categories
- Collective: organizations, factions, teams, governments, crews

The Java mapping should preserve:

- `aliases` for named object surface forms
- `type` for broad object kind
- `material`, `purpose`, and `description` as scene-local evidence fields

The mapper should avoid flattening all object evidence into only `displayName` and `type`. `displayName` remains the identity display surface; descriptive fields remain evidence carried forward into representative aggregates.

### 8. Tests

Add or update tests in `lorevault-web/src/test/java/com/lorevault/api/` following the existing lane coverage pattern.

Unit and handler tests:

- `ChapterObjectResolutionServiceTest`
- `BookObjectReductionServiceTest`
- `ChapterObjectResolutionHandlerTest`
- `BookObjectReductionHandlerTest`
- `ChapterObjectResolutionCommandControllerWebMvcTest`
- `BookObjectResolutionCommandControllerWebMvcTest`

Integration and schema tests:

- `ObjectResolutionIT`
- `Neo4jSchemaInitializerChapterObjectIndexesIT`
- `Neo4jSchemaInitializerBookObjectIndexesIT`

Existing tests to update:

- `ObjectPersistenceServiceTest` for any prompt/mapping adjustments
- `MentionRecordTest` if aggregate assumptions expand
- `AssociationEntityLabelTest` to include `ChapterObject` and `BookObject`
- `Neo4jSchemaInitializerAggregateLabelBackfillTest` if new broad-label backfills are added
- `IngestionCompletionCoordinatorTest` for the six-branch completion contract

Verification targets:

- focused Object lane unit tests
- focused command controller WebMvc tests
- focused schema initializer tests
- `mvn compile -DskipTests`
- relevant integration tests under the documented integration-test profile when graph-backed behavior is touched

### 9. Documentation updates after implementation

After the Object lane is implemented and accepted, promote durable truth into canonical docs:

- update `docs/patterns/ingestion/entity-resolution-ladder.md` to list Object as a first-class regular lane
- update the event-chain diagram and completion contract to include `BookObjectsReducedEvent`
- update `docs/PROJECT-STATUS.md` from “Object mention extraction slice landed” to Object resolution/reduction shipped
- update `docs/planning/collective-and-concept-resolution-lanes.md` so Collective/Concept are planned after the completed Object template
- remove or mark this planning item done once canonical docs reflect accepted truth

## Out of Scope

- Event entity resolution changes; Event remains a special-case temporal/event lane.
- Collective and Concept implementation.
- LLM-backed object identity merging.
- Embedding-assisted object candidate generation.
- Cross-book or cross-series object identity resolution.
- Typed semantic edges such as ownership, use, possession, containment, transport, or object-event participation.
- Claim-backed canonical fact modeling for Object attributes.
- In-place graph migrations for local development data; local graph data is disposable and should be rebuilt by re-ingestion when schema changes.

## Known Constraints / Prior Findings

- The Object evidence layer is already implemented and should not be replaced by a different extraction pipeline.
- The established regular lane pattern must not be deviated from: evidence first, chapter resolution from `ScenesDetectedEvent`, book reduction from the chapter-resolved event, and book-reduced completion fan-in.
- Object is closer to Location than Individual in persisted evidence richness because it has aliases and descriptive metadata.
- Object matching is more over-merge-prone than Location matching because generic object terms are common. V1 should therefore use `normalizedName` as merge authority and carry aliases/descriptive fields as metadata rather than transitive merge keys.
- Empty Object lanes must still publish completion events. A chapter with zero objects is a successful branch result, not a missing branch.
- Book-level destructive rebuilds must use the persisted `BookReductionClaim` pattern; JVM-local locking would violate current transaction/concurrency guidance.
- Repository Cypher must remain parameterized and any high-cardinality lookup fields must be indexed.
- Result containers and event payloads should prefer immutable forms where practical, but existing ingestion events currently extend `IngestionEvent`; Object events should match the nearby event style unless the whole event hierarchy is changed separately.
- Async handlers must use the established `ingestionTaskExecutor` qualifier and should not introduce new executors.
- Implementation should mirror concrete Individual/Location classes rather than introducing a shared abstract entity-lane framework in this slice.

## Open Questions

- Should Object aggregates preserve all distinct aliases/descriptive snippets from grouped mentions, or only representative values in v1?
- Should object `type` be normalized to a controlled vocabulary during extraction, or kept as free text until a later quality pass?
- Should vehicles and vessels always remain Objects, or can they become Locations when they are used as spatial settings? The prompt already draws this boundary, but UAT should validate examples.
- Should generic unnamed objects be reduced to `BookObject` when they recur by normalized name, or should a later pass distinguish named/significant objects from incidental props?

None of these questions block the initial implementation if v1 keeps deterministic normalized-name grouping and representative metadata.

## Success Criteria

- `ObjectMention`, `ChapterObject`, and `BookObject` form the full ladder: `Scene-[:MENTIONS]->ObjectMention-[:REFERS_TO]->ChapterObject-[:REFERS_TO]->BookObject`.
- Object chapter resolution runs automatically after `ScenesDetectedEvent` and is safe to rerun.
- Object book reduction runs automatically after `ChapterObjectsResolvedEvent`, uses the persisted book-reduction claim guard, and is safe to rerun.
- `BookObjectsReducedEvent` is part of ingestion completion fan-in.
- Chapters with no object mentions still publish `ChapterObjectsResolvedEvent` and `BookObjectsReducedEvent` with zero counts.
- V1 Object matching is deterministic: `normalizedName` is the merge authority, with no LLM calls, embedding candidate generation, or prompt-backed merge adjudication.
- Manual rerun endpoints exist for chapter Object resolution and book Object reduction.
- Object aggregate constraints/indexes are initialized by `Neo4jSchemaInitializer`, follow the existing SDN/Neo4j label conventions, and are covered by focused tests.
- Tests cover service behavior, handler event publication, completion fan-in, schema/index initialization, manual command endpoints, and at least one graph-backed Object resolution flow.
- Canonical docs are updated after implementation is accepted.

## Implementation Notes

- April 29, 2026: Planning investigation confirmed Object currently has extraction and persistence only. `ObjectMention` is saved and linked to scenes, but no `ChapterObject`, `BookObject`, Object resolution services, Object reduction services, Object events, Object fan-in branch, or Object manual rerun endpoints exist yet.
- April 29, 2026: Architecture recommendation is to complete Object before Collective/Concept so the next two cardinal lanes can follow a complete third regular-lane template rather than a half-finished Object slice.
- April 29, 2026: No required deviation from the established regular entity lane architecture has been identified. The only entity-specific choice captured here is conservative v1 Object matching by `normalizedName`, with aliases and Object descriptive fields carried as metadata instead of merge authority.
- April 29, 2026: Implementation follows strict deterministic grouping by `normalizedName` for both chapter resolution and book reduction, while carrying aliases/type/material/purpose/description as representative metadata only.
- April 29, 2026: Empty-lane handling is implemented as successful zero-count branch completion for both `ChapterObjectsResolvedEvent` and `BookObjectsReducedEvent`, preventing ingestion completion fan-in hangs when a chapter/book has no object evidence.

## Links

- `docs/patterns/ingestion/entity-resolution-ladder.md` — established regular entity resolution ladder
- `docs/patterns/ingestion/ingestion-pipeline.md` — ingestion fan-out/fan-in constraints
- `docs/rules/coding-standards.md` — Java, Spring, SDN, async, transaction, and testing guidance
- `docs/rules/development-workflow.md` — planning/proposal/canonical-doc workflow
- `docs/planning/collective-and-concept-resolution-lanes.md` — follow-on cardinal entity lanes
- `docs/planning/minimal-reltype-catalog.md` — future typed semantic edge catalog
- `docs/planning/qa-retrieval-quality-validation.md` — retrieval validation that can use Object anchors
- `lorevault-core/src/main/java/com/lorevault/api/content/mention/ObjectMention.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/ObjectPersistenceService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/individual/ChapterIndividualResolutionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/location/ChapterLocationResolutionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/completion/IngestionCompletionCoordinator.java`
