# Relation Evidence Harvesting and Catalog Discovery

**Status:** NOT STARTED  
**Last Updated:** May 07, 2026

## Summary

A phased solution design for extracting inter-entity relation claims from scene analysis, preserving the LLM's semantic detail through a catalog module that matches, clusters, and promotes relation observations into stable queryable IDs. The first phase is deliberately thin — prompt extension plus persistence plus a dev-console harvest view — so real extraction data drives vocabulary decisions rather than speculative hand-authored taxonomies.

## Problem

The knowledge graph has rich entity nodes but no typed edges between them. Relational questions ("how is X related to Y?", "which factions participated in event Z?") cannot be answered with graph traversal because the only connections are provenance edges (`MENTIONS`, `REFERS_TO`).

Previous planning assumed a curated seed of 5–8 relation types would be defined upfront and the LLM would be forced to emit those IDs. This loses semantic richness: "betrayed", "warned", "trained under", "was disguised as" all get flattened into a small hand-authored set before we know what the text actually produces.

The correct starting point is evidence harvesting: let the LLM describe relations in its own words, preserve those descriptions, and let the vocabulary emerge from accumulated data.

## Product Context

- Relational and structural questions need more than co-mention evidence; they need preserved inter-entity relation claims.
- Vocabulary stability is necessary for query-time traversal, but stability should emerge through candidate matching, clustering, review, and promotion — not from a speculative first-pass taxonomy.
- The catalog module is the bridge between raw claims and future stable typed edges: raw relation phrases remain evidence, while promoted catalog IDs become traversal semantics.
- Q&A validation should inform which relation questions hurt most, but the catalog should also learn from actual extraction output.

## Technical Context

### Current pipeline touchpoints

| Component | Role |
|---|---|
| `scene-analysis.txt` / `scene-analysis-usertemplate.st` | Scene-level extraction prompt — will emit open-ended relation claims |
| Triad analysis pipeline | Normalizes LLM output into structured entity/relation results |
| Entity persistence services | Currently write `MENTIONS` edges; will also write relation claim observations |
| `lorevault-web/src/main/resources/application.yml` | Configuration anchor for catalog seeding |

### Conceptual model references

- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — catalog module design, claim model, three-bin structure
- `docs/brainstorm/query/2026-04-12_graph-aware-qa-design-april-2026.md` — revised first slice, catalog module shape, downstream retrieval implications
- `docs/brainstorm/query/2026-05-05_claims-model-extensions-parked.md` — parked extensions including three-bin taxonomy, hearsay chains, ClaimedEvent
- `docs/brainstorm/query/2026-05-05_event-sourcing-claims-proposed.md` — append-only claim history, per-boundary replay, derived projections
- `docs/planning/minimal-reltype-catalog.md` — catalog module scope, success criteria, open questions

### Catalog module boundary

The catalog module should be an in-process bounded context inside `lorevault-core`, not a separately deployed microservice. It owns:

- Relation type definitions (ID, label, description, aliases, examples, status)
- Candidate matching: `{name, usageHint, subjectKind, objectKind}` → `List<{id, correlation, description}>`
- Provisional observation tracking (raw phrases, frequency, endpoint kinds, evidence examples)
- Promotion and merge decisions

It should **not** own: scene analysis, claim persistence, entity resolution, edge projection, Q&A retrieval routing, or Allen-style temporal relations.

## Phased Implementation

### Phase 0 — Extraction Evidence

**Goal:** Get real relation data out of the LLM and into a queryable store. No catalog matching yet.

**Deliverables:**

1. **Scene analysis prompt extension** — add a structured output section for inter-entity relation claims:
   - `subject` (entity reference, kind + name/slug)
   - `relationName` (short verb phrase, e.g. "betrayed", "trained under", "was disguised as")
   - `relationDescription` / `usageHint` (one-sentence context, e.g. "the general turned against the king and deposed him")
   - `object` (entity reference, kind + name/slug)
   - `evidenceText` (the original sentence or clause)
   - `certainty` (0–1)
   - `source` / `chunkId` / `sceneId` / `pubCoords`

   The prompt should not restrict the LLM to a fixed relation menu. It should ask the LLM to describe the relation in its own words and provide a short canonical suggestion if one feels natural.

2. **Relation claim persistence** — store each extracted relation claim as a Neo4j node or relationship property alongside existing mention evidence:
   - Raw `relationName` and `relationDescription` preserved exactly as extracted
   - Normalized provisional key: `R:provisional.<normalized_phrase>` (lowercase, underscores, stripped articles)
   - Subject and object entity references (may be unresolved at this stage)
   - Full provenance: `sourceId`, `certainty`, `evidenceText`, `pubCoords`, `chunkId`, `sceneId`
   - Append-only: never mutate or overwrite existing claims

3. **Dev-console harvest view** — surface observed relation phrases grouped by:
   - Normalized phrase
   - Frequency across ingested content
   - Subject kind → object kind pairs
   - Sample evidence snippets (2–3 per phrase)
   - Source chapters/scenes

   This is the primary operator-facing output for Phase 0. It answers: "what does the LLM actually produce?"

**Decision point after Phase 0:**

- If the LLM produces rich, varied relations → proceed to Phase 1 (catalog matching)
- If the LLM produces noise, very few relations, or mostly duplicates → tune the prompt and re-run before investing in catalog infrastructure
- If the LLM produces useful relations but with inconsistent phrasing for the same semantic relation → Phase 1 candidate matching is exactly the right next step

**Out of scope for Phase 0:**

- Catalog matching or candidate scoring
- Stable `REL` edge projection
- Graph-aware retrieval changes
- Q&A routing changes
- Confidence aggregation over competing claims
- `MENTIONS` split (separate concern, can proceed in parallel)

---

### Phase 1 — Thin Catalog Module

**Goal:** Match extracted relation phrases against a growing catalog, store candidates and provisional keys, and give operators a review surface for promotion.

**Deliverables:**

1. **Candidate matching contract** — define the request/response shape:

   ```json
   // Request
   {
     "name": "betrayed",
     "usageHint": "the general turned against the king and deposed him",
     "subjectKind": "Individual",
     "objectKind": "Individual"
   }

   // Response
   [
     {
       "id": "R:turned_against",
       "correlation": 0.9,
       "description": "Previously aligned party consciously acted against the other party"
     },
     {
       "id": "R:cheated_on",
       "correlation": 0.3,
       "description": "Violation of romantic exclusivity or relationship integrity"
     }
   ]
   ```

2. **Matching implementation** — start simple:
   - Exact match on normalized phrase against known catalog entries
   - Lemma/stem fuzzy match for near-synonyms
   - Optional: embedding similarity if cheap to compute
   - No LLM judging step in v1 (too slow, too expensive for per-relation calls)

3. **Catalog entry storage** — promoted relation types stored with:
   - `id` (e.g. `R:turned_against`)
   - `label`, `description`, `synonyms[]`
   - `subjectTypes[]`, `objectTypes[]` (applied during promotion, not during extraction)
   - `status` (`active | provisional | deprecated | merged`)
   - `inverseId?` (deferred to later phases)

4. **Provisional observation enrichment** — each extracted relation claim now also stores:
   - Candidate matches with correlation scores
   - Whether a high-confidence match was selected (and which `relTypeId`)
   - If no match cleared the threshold: `R:provisional.<normalized_phrase>`

5. **Review surface** — extend the dev-console harvest view with:
   - Cluster view: group provisional phrases by semantic similarity
   - Promote action: operator selects a provisional phrase cluster and creates a canonical `relTypeId`
   - Merge action: operator merges two provisional phrases into one canonical entry
   - Deprecate action: operator marks a provisional as noise

**Decision point after Phase 1:**

- Do the first promoted clusters make semantic sense? → If yes, proceed to Phase 2 projection
- Are there too many near-duplicate provisionals? → Improve matching (add embeddings, adjust threshold)
- Are there too few matches? → The catalog is still sparse; run more content through extraction first

**Out of scope for Phase 1:**

- Stable `REL` edge projection (Phase 2)
- Graph-aware retrieval routing (Phase 2)
- Full curation UI (Phase 1 only needs a dev-console surface)
- Property catalog (`P:*`) or Action catalog (`A:*`)

---

### Phase 2 — Stable Projection and Retrieval

**Goal:** Project promoted relation types as stable `REL` edges and wire them into graph-aware retrieval.

**Deliverables:**

1. **REL edge projection** — for each relation claim where a canonical `relTypeId` has been promoted:
   ```text
   (:Entity)-[:REL {
     relTypeId: 'R:turned_against',
     claimId: 'C:...',
     confidence: 0.85,
     pubUniverse, pubSeries, pubBookNumber, pubChapterNumber, pubOrdinal, pubKey
   }]->(:Entity)
   ```
   Edges carry `pubCoords` for spoiler gating. Projection is replayable from the claim log.

2. **Provisional edge policy** — provisional observations may optionally be materialized as diagnostic edges:
   ```text
   (:Entity)-[:PROVISIONAL_REL {
     relationName: 'betrayed',
     provisionalRelTypeId: 'R:provisional.betrayed',
     claimId: 'C:...',
     pubCoords
   }]->(:Entity)
   ```
   These are not part of the stable query contract. They exist for operator inspection and experimental retrieval only.

3. **Graph-aware retrieval integration** — once enough relation types are promoted, wire `REL` edges into the retrieval expansion path:
   - Fixed routing table maps question surface patterns to promoted `relTypeId` values
   - Expansion traverses `REL` edges by `relTypeId`, collects secondary entity chunks
   - Merged candidate set feeds into vector similarity filtering
   - Spoiler gating on `pubCoords` filters edges at the reader's boundary

4. **Q&A validation against enriched graph** — run the question set from `qa-retrieval-quality-validation.md` against the graph with `REL` edges present. Classify improvements and remaining gaps.

**Decision point after Phase 2:**

- Do promoted `REL` edges measurably improve answer quality for relational questions? → If yes, continue promoting more relation types
- Are there question types that still fail? → Identify whether the gap is missing relation types, missing retrieval logic, or answer assembly

**Out of scope for Phase 2:**

- Per-boundary claim replay (Phase 3 / event-sourcing claims)
- `ClaimedEvent` / hearsay chain (parked)
- Property catalog or Action catalog

---

### Phase 3 — Event-Sourcing Claims (Future)

This phase is documented in `docs/brainstorm/query/2026-05-05_event-sourcing-claims-proposed.md`. It depends on Phase 2 producing stable `REL` edges and is not in scope for the current implementation cycle.

Key ideas carried forward:

- Claims are append-only; projected edges are derived views
- Per-boundary replay reconstructs relation state at the reader's progress position
- Invalidatable projections tied to the stage-run DAG mechanism

---

## Known Constraints / Prior Findings

- The entity taxonomy has six kinds (Individual, Collective, Object, Location, Concept, Event); relation endpoint kinds reference this taxonomy
- The Concept lane must exist before relation types involving Concept targets can be used
- The `MENTIONS` conflation problem (direct occurrence vs. indirect reference) is a separate concern that can proceed in parallel but should not block Phase 0
- Oracle's advisory recommends expanding from evidence, not speculatively; the catalog module makes this evidence loop explicit
- The existing stage-run DAG invalidation mechanism provides the projection replay trigger
- Provisional keys (`R:provisional.*`) must not become stable edge labels — they are observations, not accepted taxonomy

## Open Questions

- **Matching implementation:** Should Phase 1 use lexical/fuzzy matching, embeddings, an LLM judging step, or a staged combination? Recommendation: start with exact + lemma matching, add embeddings only if cheap.
- **Correlation threshold:** What score is high enough to attach a canonical `relTypeId` automatically vs. storing only candidates? Start conservative (0.85+) and adjust based on Phase 0 data.
- **Provisional edge policy:** Should provisional observations be materialized as `PROVISIONAL_REL` edges for diagnostics, or kept only in the claim/catalog observation store until promotion? Recommendation: materialize for dev-console visibility but exclude from production retrieval.
- **Storage:** Should canonical catalog entries live in Neo4j as `CatalogRelType` nodes, in YAML/JSON seed files, or both? Recommendation: start with in-code/YAML for v1, migrate to Neo4j nodes when the catalog grows past manual review scale.
- **`inverseId`:** Should inverse relation types be populated in Phase 1 (e.g. `member_of` ↔ `has_member`) or deferred? Recommendation: defer. Directionality is stored on the edge; inverse derivation is a render concern.
- **`MENTIONS` split:** Can indirect scene references be captured in a single bucket, or do they require a curated vocabulary? This is separable from relation extraction and can proceed in parallel.

## Success Criteria

### Phase 0

- Scene analysis emits open-ended relation claims with enough context for later semantic mapping
- Relation claims are persisted append-only with raw phrase, usage hint, evidence, provenance, `pubCoords`
- A dev-console harvest view shows observed relation phrases, frequencies, endpoint kinds, and evidence examples
- At least one full book has been processed through the extended pipeline

### Phase 1

- The catalog module exposes a candidate-matching contract and returns correlation-scored candidates
- Unknown or low-confidence relation descriptions receive provisional keys rather than stable edge labels
- An operator can review, promote, merge, and deprecate provisional observations through the dev console
- The first batch of promoted canonical relation types exists (expected: 5–15 types after 2–3 books)

### Phase 2

- Promoted relation types are projected as stable `REL` edges with `pubCoords` for spoiler gating
- Graph-aware retrieval uses `REL` edges to expand entity context for at least one question pattern
- Q&A validation shows measurable improvement on relational questions compared to baseline

## Links

- `docs/planning/minimal-reltype-catalog.md` — catalog module scope, success criteria, open questions
- `docs/planning/qa-retrieval-quality-validation.md` — Q&A validation planning
- `docs/brainstorm/query/2026-04-12_graph-aware-qa-design-april-2026.md` — revised first slice, catalog module shape, retrieval implications
- `docs/brainstorm/query/2026-05-05_claims-model-extensions-parked.md` — parked extensions (ClaimedEvent, hearsay, three-bin taxonomy)
- `docs/brainstorm/query/2026-05-05_event-sourcing-claims-proposed.md` — event-sourcing claims (Phase 3)
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — core domain model, claim bins, catalog module
- `docs/patterns/ingestion/entity-resolution-ladder.md` — entity resolution pattern
- `docs/patterns/ingestion/triad-analysis.md` — triad normalization pipeline

---

## Implementation Notes

### Phase 0 — Architecture Survey (May 7, 2026)

**Current extraction pipeline flow:**

```
SceneDetectionHandler.handleChapterIngestion()
  → detectAndPersistScenes()           (scene segmentation)
  → defaultTemporalEdgeService.createAllDefaults()  (NEXT_IN_READING_ORDER edges)
  → sceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals()  (triad analysis)
  → sceneTemporalRelationshipPersistenceService.applyTemporalRelationships() (TEMPORAL edges)
  → individualPersistenceService.persistExtractedIndividuals()
  → collectivePersistenceService.persistExtractedCollectives()
  → objectPersistenceService.persistExtractedObjects()
  → locationPersistenceService.persistExtractedLocations()
  → eventPersistenceService.persistExtractedEvents()
  → emit ScenesDetectedEvent
```

**Key insertion point:** After the five entity persistence calls and before `ScenesDetectedEvent`, a new `relationClaimPersistenceService.persistExtractedRelationClaims()` call would fit naturally. The relation claims reference entity mentions that have already been persisted.

**Current scene analysis prompt structure:**

The LLM is asked to extract per-triad:
1. Temporal relationships (`previous_to_current`, `current_to_next`) with `temporalType`, `certainty`, `evidence`
2. Current scene entities in 5 categories: `individuals`, `collectives`, `objects`, `locations`, `events`

There is **no inter-entity relationship section** in the current prompt. The new `<relations>` section will be additive.

**Current data model (TriadAnalysisModels.java):**

- `TriadStructuredResult` — top-level LLM output record
- `TriadCurrentSceneEntities` — holds the 5 entity lists
- Per-entity extraction records: `TriadIndividualExtraction`, `TriadCollectiveExtraction`, `TriadObjectExtraction`, `TriadLocationExtraction`, `TriadEventExtraction`
- `SceneRelationshipOutcome` — aggregated result across all triads
- `SceneRelationshipAnalysis` — per-triad analysis with temporal edges

**Current Neo4j schema — no inter-entity edges:**

The graph currently has:
- `Scene -[:MENTIONS]-> *Mention` (provenance only)
- `*Mention -[:REFERS_TO]-> Chapter* -[:REFERS_TO]-> Book*` (resolution ladder)
- `Chapter -[:HAS_INDIVIDUAL]-> ChapterIndividual`, etc.
- `Book -[:HAS_INDIVIDUAL]-> BookIndividual`, etc.
- `Scene -[:TEMPORAL]-> Scene` (temporal edges)
- `Scene -[:NEXT_IN_READING_ORDER]-> Scene` (structural ordering)

There are **no edges between entity nodes of different types** (no `IndividualMention -[:LOCATED_AT]-> LocationMention`, etc.).

**Design decisions for Phase 0:**

1. **Relation claim node model:** `RelationClaim` node with properties:
   - `id` (UUID)
   - `relationName` (raw LLM phrase, e.g. "betrayed", "trained under")
   - `relationDescription` / `usageHint` (one-sentence context)
   - `provisionalRelTypeId` (normalized key, e.g. `R:provisional.betrayed`)
   - `subjectKind` (Individual/Collective/Object/Location/Concept/Event)
   - `subjectName` (raw name from LLM)
   - `objectKind` (Individual/Collective/Object/Location/Concept/Event)
   - `objectName` (raw name from LLM)
   - `certainty` (0.0–1.0)
   - `evidenceText` (original sentence/clause)
   - `source` ("ai-scene-analysis")
   - `sceneId`, `chapterId`, `bookId`
   - `pubCoords` (universe, series, bookNumber, chapterNumber, sceneIndex, pubOrdinal, pubKey)
   - `createdAt`, `updatedAt`

2. **Relationship model:**
   - `(Scene)-[:MENTIONS]->(RelationClaim)` — provenance, same pattern as all other mentions
   - `(RelationClaim)-[:RELATES_SUBJECT]->(*Mention)` — link to subject entity mention (if resolved)
   - `(RelationClaim)-[:RELATES_OBJECT]->(*Mention)` — link to object entity mention (if resolved)
   - Subject/object links are best-effort at Phase 0: if the LLM names match a persisted mention, link them; if not, store the raw names only.

3. **Aggregate label:** `RelationClaim` gets the `Mention` aggregate label, consistent with the existing pattern (`IndividualMention` has `Mention`, etc.). This allows generic mention queries to include relation claims.

4. **Prompt extension:** Add a `<relations>` section to `scene-analysis.txt` after `<current_scene_entities>`, asking the LLM to list inter-entity relationships observed in the current scene with subject, relation name, description, object, certainty, and evidence.

5. **Persistence service:** New `RelationClaimPersistenceService` in `lorevault-core/.../ingestion/infrastructure/`, following the same pattern as `IndividualPersistenceService` but writing `RelationClaim` nodes and `RELATES_SUBJECT`/`RELATES_OBJECT` edges.

6. **Dev-console harvest view:** New tab or section in the operator dashboard showing:
   - Grouped relation phrases (by `provisionalRelTypeId`)
   - Frequency counts
   - Subject kind → object kind distribution
   - Sample evidence snippets
   - Source chapters/scenes
   This can be a simple Cypher aggregation query exposed via a new controller endpoint and rendered as an HTMX table fragment.

**Files to create/modify:**

| Action | File | Purpose |
|---|---|---|
| Modify | `lorevault-core/.../resources/prompts/scene-analysis.txt` | Add `<relations>` section |
| Modify | `lorevault-core/.../resources/prompts/scene-analysis-usertemplate.st` | No change needed (user template provides scene text, not extraction schema) |
| Create | `lorevault-core/.../ingestion/triad/TriadAnalysisModels.java` (add records) | `TriadRelationClaimExtraction`, add `relations` field to `TriadCurrentSceneEntities` |
| Modify | `lorevault-core/.../ingestion/triad/SceneRelationshipAnalysisService.java` | Add `normalizeRelationClaims()` method |
| Create | `lorevault-core/.../ingestion/infrastructure/RelationClaimPersistenceService.java` | Persist `RelationClaim` nodes and edges |
| Create | `lorevault-core/.../content/relation/RelationClaim.java` | `@Node` record for relation claims |
| Create | `lorevault-core/.../content/relation/RelationClaimGraphRepository.java` | Neo4j repository for relation claims |
| Modify | `lorevault-core/.../ingestion/scene/SceneDetectionHandler.java` | Call `relationClaimPersistenceService.persistExtractedRelationClaims()` after entity persistence |
| Modify | `lorevault-core/.../config/Neo4jSchemaInitializer.java` | Add `RelationClaim` constraints and indexes |
| ~~Create~~ | ~~`lorevault-web/.../web/ui/UiRelationHarvestController.java`~~ | ~~Harvest view endpoint~~ — deferred; inspect directly in Neo4j for Phase 0 |
| ~~Create~~ | ~~`lorevault-web/.../resources/templates/ui/relations.html`~~ | ~~HTMX fragment for harvest table~~ — deferred; inspect directly in Neo4j for Phase 0 |

### Phase 0 — Implementation (May 7, 2026)

**Status: Core pipeline implemented. Dev-console harvest view deferred — inspect directly in Neo4j.**

**What was built:**

1. **RelationClaim data model** (`lorevault-core/.../content/relation/RelationClaim.java`):
   - Java 21 record with `@Node(primaryLabel = "RelationClaim", labels = "Mention")`
   - 18 properties: id, relationName, relationDescription, provisionalRelTypeId, subjectKind, subjectName, objectKind, objectName, certainty, evidenceText, source, sceneId, chapterId, bookId, extractionIndex, resolutionStatus, createdAt, updatedAt
   - `certainty` uses String values ("Explicit", "StronglyImplied", "WeaklyImplied") matching existing certainty enum
   - `bookId` is null at creation time (filled later during book-level processing, same as other mentions)

2. **RelationClaimGraphRepository** (`lorevault-core/.../content/relation/RelationClaimGraphRepository.java`):
   - `Neo4jRepository<RelationClaim, UUID>` with Cypher methods for `linkClaimToScene`, `linkSubjectMention`, `linkObjectMention`

3. **Neo4j schema** (`lorevault-core/.../config/Neo4jSchemaInitializer.java`):
   - `RELATION_CLAIM_ID_UNIQUE` constraint on `RelationClaim.id`
   - `RELATION_CLAIM_CHAPTER_RELTYPE_INDEX` on `(chapterId, provisionalRelTypeId)` for harvest aggregation queries
   - `RELATION_CLAIM_BOOK_RELTYPE_INDEX` on `(bookId, provisionalRelTypeId)` for book-level aggregation
   - `RelationClaim` added to the `Mention` aggregate label backfill

4. **Scene analysis prompt** (`lorevault-core/.../resources/prompts/scene-analysis.txt`):
   - Added `**relations**` section after entity extraction instructions
   - Added `<relations>` XML output template with `<subject>`, `<relationName>`, `<relationDescription>`, `<object>`, `<certainty>`, `<evidence>` elements
   - Two example relations (`trusted`, `opposed`) with different entity kinds and certainty levels
   - Explicit instruction: "Describe relations in your own words — there is no predefined relation type menu"
   - Section is optional: empty `<relations/>` if no inter-entity relations are evident

5. **Triad analysis models** (`lorevault-core/.../ingestion/triad/TriadAnalysisModels.java`):
   - Added `RelationClaimExtraction` record with: subjectKind, subjectName, relationName, relationDescription, provisionalRelTypeId, objectKind, objectName, certainty, evidence
   - Added `SceneRelationClaimExtraction` record with: sceneIndex, List<RelationClaimExtraction>
   - Added `sceneRelationClaimExtractions` field to `SceneRelationshipOutcome` (all constructors updated)

6. **Triad analysis service** (`lorevault-core/.../ingestion/triad/SceneRelationshipAnalysisService.java`):
   - Added `TriadRelationClaimExtraction` inner record (subject, relationName, relationDescription, object, certainty, evidence)
   - Added `List<TriadRelationClaimExtraction> relations` to `TriadCurrentSceneEntities` (all constructors updated)
   - Added `normalizeRelationClaims()` method with:
     - `parseEntityRef()` — splits "Kind: Name" into kind/name parts
     - `generateProvisionalRelTypeId()` — lowercases, replaces spaces with underscores, strips non-alphanumeric, prefixes "R:provisional."
     - `normalizeCertainty()` — maps to "Explicit"/"StronglyImplied"/"WeaklyImplied" (default)
     - `truncate()` — caps evidence at 500 chars, description at 1000 chars
   - Wired into `analyzeChapterTriadsWithIndividuals()` alongside existing entity extraction maps

7. **RelationClaimPersistenceService** (`lorevault-core/.../ingestion/infrastructure/RelationClaimPersistenceService.java`):
   - Follows the exact same pattern as `IndividualPersistenceService`
   - `persistExtractedRelationClaims(List<Scene>, List<SceneRelationClaimExtraction>)`
   - Creates `RelationClaim` records, saves via repository, links to scene via `linkClaimToScene()`
   - Subject/object mention linking is best-effort at Phase 0 (stored as raw names; linking to resolved mentions is a future enhancement)

8. **SceneDetectionHandler** (`lorevault-core/.../ingestion/scene/SceneDetectionHandler.java`):
   - Added `RelationClaimPersistenceService` as constructor parameter
   - Calls `persistExtractedRelationClaims()` after the five entity persistence calls, inside the `if (!scenes.isEmpty())` block

**What was deferred:**

- Dev-console harvest view (controller + template) — inspect directly in Neo4j for Phase 0
- Subject/object mention linking (`RELATES_SUBJECT`/`RELATES_OBJECT` edges) — the repository methods exist but are not called yet; linking requires matching LLM-extracted names to persisted mention IDs, which needs a name-resolution step
- `pubCoords` on RelationClaim nodes — the fields are in the data model but not yet populated from scene/chapter metadata during persistence; this will be added when the first book is processed and the pubCoords derivation is wired

**Neo4j inspection queries for Phase 0:**

```cypher
// List all relation claims
MATCH (rc:RelationClaim) RETURN rc.relationName, rc.provisionalRelTypeId, rc.subjectKind, rc.subjectName, rc.objectKind, rc.objectName, rc.certainty, rc.evidenceText LIMIT 50

// Aggregate by provisional relation type
MATCH (rc:RelationClaim)
RETURN rc.provisionalRelTypeId, count(*) AS count, collect(DISTINCT rc.subjectKind) AS subjectKinds, collect(DISTINCT rc.objectKind) AS objectKinds, collect(rc.evidenceText)[0..3] AS samples
ORDER BY count DESC

// Find claims for a specific scene
MATCH (s:Scene)-[:MENTIONS]->(rc:RelationClaim)
WHERE s.id = $sceneId
RETURN rc.relationName, rc.subjectKind + ': ' + rc.subjectName AS subject, rc.objectKind + ': ' + rc.objectName AS object, rc.certainty, rc.evidenceText
```