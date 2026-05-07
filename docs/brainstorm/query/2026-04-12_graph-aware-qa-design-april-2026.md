# Graph-Aware Q&A Design — LoreVault

**Date:** April 2026  
**Status:** Brainstorm — not yet accepted  
**Purpose:** Apply the external GraphRAG pattern research to LoreVault's actual schema to produce an actionable implementation plan for entity-aware Q&A.

---

## 1. Where We Are Today

The current Q&A pipeline (`Neo4jSemanticSearch` → `RagService`) is a **pure vector search**:

```
query → embed → db.index.vector.queryNodes(chunk_embedding_idx) → Chunks → LLM
```

The graph is used only for:
- spoiler filtering via `chapter.bookNumber` / `chapter.series` properties on the `Chapter` node co-located with the vector result
- publication coordinate denormalization (universe, series, bookNumber, chapterNumber on `Chapter`)

**What is missing:** The entity ladder built during ingestion is never touched at query time.

LoreVault already has a rich graph that knows:

| Graph fact | Node/Relationship |
|---|---|
| Which individuals appeared in a scene | `Scene -[:MENTIONS]-> IndividualMention` |
| Chapter-level identity consolidation | `IndividualMention -[:REFERS_TO]-> ChapterIndividual` |
| Book-level identity backbone | `ChapterIndividual -[:REFERS_TO]-> BookIndividual` |
| Which locations appeared in a scene | `Scene -[:MENTIONS]-> LocationMention` |
| Chapter-level location consolidation | `LocationMention -[:REFERS_TO]-> ChapterLocation` |
| Book-level location backbone | `ChapterLocation -[:REFERS_TO]-> BookLocation` |
| Temporal ordering of scenes | `Scene -[:TEMPORAL {relationType, certaintyLevel}]-> Scene` |
| Chunk-to-scene parent | `Scene -[:HAS_CHUNK]-> Chunk` |
| Scene-to-chapter parent | `Chapter -[:HAS_SCENE]-> Scene` |

None of this is reachable from today's Q&A path.

---

## 2. The Mapping: External Patterns → LoreVault Graph

### Pattern 1 — Cypher Templates (high-frequency, low-complexity)

**What it is:** Pre-written parameterized Cypher queries, selected by question intent.

**LoreVault fit:** Many reader questions follow narrow, predictable templates:
- "Who is {character}?" → look up `BookIndividual` by normalized name, return `displayName`, `mentionCount`, `firstSeenChapterId`
- "Where does {scene/event} take place?" → look up `BookLocation` by normalized name
- "In which scenes does {character} appear?" → traverse `BookIndividual <- ChapterIndividual <- IndividualMention <- Scene`
- "What chapters feature both {A} and {B}?" → intersection of two individual mention sets

**Spoiler safety:** Template parameters include `$maxBookOrder` derived from `SpoilerVisibility`. All traversals carry a `WHERE bi.bookId IN $allowedBookIds` guard.

**Example template (character lookup):**

```cypher
// Template: "who-is-individual"
MATCH (bi:BookIndividual)
WHERE bi.normalizedName = $normalizedName
  AND bi.bookId IN $allowedBookIds
OPTIONAL MATCH (bi)<-[:REFERS_TO]-(ci:ChapterIndividual)
WITH bi, count(ci) AS chapterCount
RETURN bi.displayName        AS displayName,
       bi.normalizedName      AS normalizedName,
       bi.chapterIndividualCount AS mentionCount,
       bi.firstSeenChapterId  AS firstSeenChapterId,
       chapterCount
```

**LoreVault concrete templates to build first:**

| Template ID | Intent | Parameters |
|---|---|---|
| `individual-lookup` | Who/what is {name} | `normalizedName`, `allowedBookIds` |
| `individual-scenes` | What scenes feature {name} | `normalizedName`, `allowedBookIds` |
| `location-lookup` | Describe {location} | `normalizedName`, `allowedBookIds` |
| `individual-co-occurrence` | Scenes with both {A} and {B} | `nameA`, `nameB`, `allowedBookIds` |
| `individual-first-appearance` | When does {name} first appear | `normalizedName`, `allowedBookIds` |

**Implementation:** A `CypherTemplateRegistry` bean: `Map<String, String>` keyed by template ID with a `fill(templateId, Map<String,Object> params)` method. `Neo4jClient` executes.

---

### Pattern 2 — Vector-Seeded Graph Expansion (the most impactful near-term step)

**What it is:** Use vector search to find relevant `Chunk` nodes, then traverse `HAS_CHUNK ← Scene → MENTIONS → IndividualMention → ChapterIndividual` to enrich results with entity context.

**LoreVault fit:** This is the `VectorCypherRetriever` pattern applied to LoreVault's graph. It adds entity awareness to the existing vector search with a single Cypher extension.

**The core retrieval query:**

```cypher
// Expand from vector-matched chunks to entity context
CALL db.index.vector.queryNodes('chunk_embedding_idx', $oversampleLimit, $embedding)
YIELD node AS chunk, score

// Navigate up to scene
MATCH (scene:Scene)-[:HAS_CHUNK]->(chunk)
MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)

// Spoiler gate (same as today)
WHERE score > 0.0
  AND ($universe IS NULL OR chapter.universe = $universe)
  AND /* spoiler predicate */

// Expand to entity evidence at scene level
OPTIONAL MATCH (scene)-[:MENTIONS]->(im:IndividualMention)
OPTIONAL MATCH (scene)-[:MENTIONS]->(lm:LocationMention)

// Collect entity signals
WITH chunk, score, scene, chapter,
     collect(DISTINCT im.displayName) AS individualsPresent,
     collect(DISTINCT lm.displayName) AS locationsPresent

RETURN
    chunk.id          AS chunkId,
    score,
    chunk.text        AS text,
    scene.id          AS sceneId,
    scene.contextSummary AS sceneSummary,
    chapter.id        AS chapterId,
    chapter.bookNumber    AS bookNumber,
    chapter.chapterNumber AS chapterNumber,
    individualsPresent,
    locationsPresent
ORDER BY score DESC
LIMIT $topK
```

**What this unlocks for RAG:**
- Context string gains: `[1] ... (Book 2, Ch 5 — featuring: Vin, Kelsier — at: Luthadel)`
- LLM sees entity grounding without needing to infer it from chunk text alone
- Disambiguation: when a name is ambiguous in prose, the entity list confirms who is present

**Implementation path:**
1. Add `individualsPresent` and `locationsPresent` to `Neo4jSemanticSearch.SearchResult`
2. Extend `buildCypher()` with the `OPTIONAL MATCH` expansion
3. Extend `buildContextFromEvidence()` in `RagService` to include entity annotations in the context string

This is a **single-file change** in `Neo4jSemanticSearch.java` plus a minor change in `RagService`. No new services required.

---

### Pattern 3 — Hybrid Dense + Sparse (Vector + Full-Text)

**What it is:** Run both vector search and Neo4j full-text search (Lucene BM25 on `Chapter.raw_text`), normalize scores to [0,1], merge by Reciprocal Rank Fusion.

**LoreVault fit:** `Chapter` already has `raw_text` as a full-text search target (the data model doc notes it as "neo lucene"). Exact-name queries like "Kelsier" return poor vector scores but great BM25 scores. Character names, place names, and proper nouns are the failure mode of pure vector search.

**The gap:** The current schema has full-text indexing on `Chapter.raw_text` but no full-text index on `Chunk.text`. Full-text on `Chapter` returns a chapter node, not a chunk. To use it as a hybrid signal, you would need to:
- Either add a full-text index on `Chunk.text`, OR
- Treat chapter-level BM25 hits as a separate signal lane and merge at the chapter level

**RRF formula (k=60):**

```java
// For each candidate, compute merged score
double rrfScore(int rankVector, int rankFullText, int k) {
    return 1.0 / (k + rankVector) + 1.0 / (k + rankFullText);
}
```

**Recommendation:** Defer hybrid until Pattern 2 (vector-seeded expansion) is shipped. Hybrid adds operational complexity (two index types to maintain, score normalization plumbing) that is not justified until vector-only retrieval proves insufficient for entity-name queries.

**When to revisit:** When users report that questions containing exact character names ("What did Kelsier do at the Pits?") return poor results despite the answer being in the corpus.

---

### Pattern 4 — Template Selection via Intent Classification

**What it is:** The question is classified by an LLM (or rule-based classifier) to select the right template or retrieval strategy.

**LoreVault fit:** A simple two-lane router:

```
question → router → ENTITY_LOOKUP?  → CypherTemplate (Pattern 1)
                  → NARRATIVE_QA?   → VectorCypherExpansion (Pattern 2)
```

**Intent signals:**
- Starts with "Who is", "What is", "Where is", "Describe" → entity lookup intent
- Contains a known normalized name with no surrounding context → entity lookup
- Otherwise → narrative Q&A

**Implementation:** A `QuestionIntentClassifier` that returns an enum `{ENTITY_LOOKUP, NARRATIVE_QA, AMBIGUOUS}`. Start with keyword rules; optionally upgrade to a lightweight LLM call with a short system prompt.

**The router lives in `RagService.ask()`:**

```java
QuestionIntent intent = intentClassifier.classify(request.getQuestion());
return switch (intent) {
    case ENTITY_LOOKUP -> handleEntityLookup(request);
    case NARRATIVE_QA  -> handleNarrativeQa(request);   // existing path + entity expansion
    case AMBIGUOUS     -> handleNarrativeQa(request);   // fall through to vector
};
```

---

### Pattern 5 — Context-Scoped Entity Injection (Spoiler-Safe Graph Enrichment)

**What it is:** Before generating the answer, look up entity summaries from the graph and inject them into the LLM context as structured background — but only for entities the reader has already encountered.

**The spoiler-safe guard:**

```cypher
// Only inject entity background visible to this reader
MATCH (bi:BookIndividual)
WHERE bi.normalizedName IN $entityNames
  AND bi.bookId IN $allowedBookIds  // ← spoiler gate: only books reader has reached
RETURN bi.displayName, bi.normalizedName, bi.chapterIndividualCount, bi.firstSeenChapterId
```

**`allowedBookIds` derivation from `SpoilerVisibility`:**

```java
// Compute from SpoilerVisibility.seriesProgress
Set<UUID> allowedBookIds = bookRepo.findBooksUpToProgress(
    seriesProgress.getSeries(),
    seriesProgress.getReadThroughBookNumber()
);
```

**Context injection format:**

```
[Character Background]
- Vin: appears in 47 chapters across this book; first seen in Chapter 1
- Kelsier: appears in 23 chapters; first seen in Chapter 2

[Story Context]
[1] <chunk text> (Book 1, Ch 3 — featuring: Vin, Kelsier — at: Luthadel)
...
```

This pattern enriches the LLM's context without exposing spoiler facts, because `BookIndividual` currently holds only structural data (`mentionCount`, `firstSeenChapterId`) — not revealed narrative facts.

**Future extension:** When `BookIndividual` gains richer profile fields (traits, roles, relationships), the same guard selects which fields to inject based on the reader's progress.

---

## 3. Implementation Priority

| Priority | Pattern | Effort | Value |
|---|---|---|---|
| **1 — Ship now** | Vector-seeded entity expansion (Pattern 2) | Single Cypher extension + DTO field additions | High: entity grounding in every RAG answer |
| **2 — Next** | Cypher template registry (Pattern 1) for `individual-lookup` + `location-lookup` | New `CypherTemplateRegistry` bean + 2–3 queries | High: answers character/location questions correctly |
| **3 — After templates** | Intent router (Pattern 4) | `QuestionIntentClassifier` + branch in `RagService` | Medium: routes to the right strategy per question |
| **4 — Later** | Spoiler-safe entity injection (Pattern 5) | `allowedBookIds` resolver + context builder extension | Medium: richer LLM context, requires book-id mapping |
| **5 — Defer** | Hybrid dense+sparse (Pattern 3) | New full-text index + RRF fusion utility | Low until proven needed |

---

## 4. What the Expanded `SearchResult` Should Look Like

Current:
```java
record SearchResult(UUID chunkId, double score, String snippet,
                    UUID chapterId, Integer bookNumber, Integer chapterNumber)
```

After Pattern 2:
```java
record SearchResult(UUID chunkId, double score, String snippet,
                    UUID chapterId, Integer bookNumber, Integer chapterNumber,
                    UUID sceneId, String sceneSummary,
                    List<String> individualsPresent,   // from IndividualMention.displayName
                    List<String> locationsPresent)     // from LocationMention.displayName
```

Current canonical entity lanes have since expanded to Object and Collective as well. If this proposal is revived, the entity-expansion fields should be generalized rather than limited to Individual/Location.

The `sceneSummary` field (`Scene.contextSummary`) is particularly valuable: it is an AI-generated one-line description of the scene. Including it in the context header gives the LLM a stable anchor without needing to re-read the full chunk.

---

## 5. What to Do About Missing Graph Links

### Problem: `Chunk` has no `Scene` relationship in the vector index traversal

The current Cypher in `Neo4jSemanticSearch.buildCypher()` does:
```cypher
OPTIONAL MATCH (chapterDirect:Chapter)-[:HAS_CHUNK]->(chunk)
OPTIONAL MATCH (chapterViaScene:Chapter)-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(chunk)
```

This means it can reach the `Scene` via the second pattern. The `Scene` node is reachable; the query just does not currently return it or traverse further from it.

**No data migration needed for existing mention links.** The graph links already exist. The expansion query can continue from `Scene` to the implemented scene-local mention families, currently Individual, Location, Object, and Collective.

### Problem: mention evidence may not exist for all scenes

Triad extraction runs during ingestion, but only for scenes that passed scene detection. If a chapter was ingested before the entity pipeline existed, those scenes have no mention nodes.

**Handling:** `OPTIONAL MATCH` already handles this — if no mentions exist, `individualsPresent` returns an empty list. The query does not fail.

---

## 6. Proposed First Commit Scope (Entity Expansion MVP)

**Files to change:**

1. `Neo4jSemanticSearch.java`
   - Extend `SearchResult` record to include `sceneId`, `sceneSummary`, and entity-presence fields
   - Extend `buildCypher()` with `OPTIONAL MATCH` traversal to the implemented mention families
   - Map new fields in the `mappedBy` lambda

2. `RagService.java`
   - Extend `buildContextFromEvidence()` to include scene summary and entity lists in the context annotation per chunk

3. `SemanticSearchDtos.java` (if `SearchResultDto` mirrors `SearchResult`)
   - Add matching fields

**Files to add:**

- None in this slice — no new services or beans

**Tests to add/update:**

- Unit test: `buildCypher()` includes `OPTIONAL MATCH` when entity expansion is enabled
- Integration test: a chapter with known mention nodes returns the expected scene-local entity presence fields

---

## 7. Open Questions

1. **Feature flag or always-on?**  
   Entity expansion adds 1–2 extra `OPTIONAL MATCH` hops to every vector query. Measure query latency before and after. If < 20ms overhead, ship always-on. Otherwise, gate on a config flag `lorevault.search.entity-expansion.enabled`.

2. **Normalize display names or pass raw?**  
   `IndividualMention.displayName` is the raw extracted name. The same character can have multiple display names across scenes. For context injection, it may be better to traverse up to `ChapterIndividual.displayName` or `BookIndividual.displayName` for a stable canonical form. The extra hop is cheap.

3. **What about Chunks with no Scene parent?**  
   The `chapterDirect` path in the current query suggests some chunks may be attached directly to a Chapter. If those exist, they will return empty entity lists. Worth auditing the data to confirm whether this path is still in use.

4. **BookIndividual scope vs. search scope?**  
   For Pattern 5 (entity injection), the `allowedBookIds` list must come from `SpoilerVisibility`. There is currently no `BookGraphRepository` query that maps `(series, maxBookOrder) → List<UUID>`. This needs to be added before Pattern 5.

5. **Should `AskController` accept an `entityExpansion: boolean` flag?**  
   For the router (Pattern 4), the entity lookup path returns structured data, not prose. The current `AskResponse` shape (`answer: String, citations: List`) is not ideal for structured entity queries. A second response shape may be needed — or entity lookup can return its results formatted as prose by the LLM.

---

## 8. Relationship to Project-Status Next Steps

From `PROJECT-STATUS.md`:

> **1. Entity-aware Q&A improvements**  
> Improve query behavior against at least two entity types instead of building a character-only vertical

This document directly addresses that slice. Pattern 2 (vector-seeded expansion) + Pattern 1 (templates for individual-lookup and location-lookup) = entity-aware Q&A for both `Individual` and `Location` entity types.

The two entity types are already symmetric (`BookIndividual` / `BookLocation`, same ladder shape), so building both templates in one shot is low-cost.

---

## 9. Refined Strategic Direction After Wider Search

After broader internal exploration, external pattern research, Oracle review, and creative brainstorming, the current recommendation is sharper than the earlier implementation-first plan.

### Recommended default retrieval posture

LoreVault should treat **spoiler-gated chunk retrieval as the hard entry point** for Q&A, then use the graph to improve retrieval quality, ranking, and answer planning.

That means the default posture is:

1. retrieve spoiler-safe chunks first
2. use those chunks and their scenes as graph anchors
3. expand to nearby Entities, Locations, and timeline context in bounded ways
4. rerank chunks and context deterministically
5. synthesize the answer from chunk-grounded evidence

This is now the recommended near-term direction over graph-first retrieval or free-form Text2Cypher.

### Why this is the preferred first move

The current graph is valuable, but still intentionally thin in the newest areas:

- `BookIndividual` and `BookLocation` are continuity structures, not rich profile nodes
- chunk citations are still the best current grounding unit for answers
- spoiler gating is already implemented in the vector retrieval path
- chunk search already provides a working recall mechanism that can seed graph context safely

So the graph should first act as a **retrieval amplifier and context structure**, not as the primary answer source.

---

## 10. Strategy Comparison

### Strategy A — Vector-first retrieval with bounded graph expansion

**Status:** Recommended first implementation path

This is the strongest consensus from the local code review, Oracle, and the external GraphRAG references.

Core shape:

1. run spoiler-safe vector retrieval on `Chunk`
2. treat returned chunks as seeds
3. expand to `Scene`, nearby `IndividualMention` / `LocationMention`, and optionally thin chapter/book aggregates
4. rerank or enrich the evidence set
5. answer from chunk-grounded evidence plus graph annotations

Why it fits LoreVault now:

- smallest delta from the current Q&A pipeline
- preserves existing citation and spoiler controls
- easy to A/B test against current vector-only behavior
- lets graph structure improve answers without overcommitting to a graph-native query stack

### Strategy B — Small catalog of parameterized graph query templates

**Status:** Recommended second lane

This is still a strong idea, but it should complement the default vector-first path rather than replace it.

Best fits:

- explicit entity lookup questions
- location lookup questions
- scene/entity co-occurrence questions
- narrow timeline or relationship questions once enough structure exists

This lane should stay intentionally bounded. The LLM or router should select a known template and extract parameters; it should not generate arbitrary Cypher.

### Strategy C — Routed hybrid retrieval

**Status:** Likely target state later, not first

This means maintaining multiple retrieval lanes and routing by question type:

- broad descriptive questions → vector-first hybrid
- explicit entity/location lookups → template lane
- future timeline/path questions → timeline-specific lane

This becomes attractive after initial metrics show which questions remain weak under Strategy A.

### Strategy D — Free-form LLM-generated Cypher

**Status:** Fallback or research lane only

The wide-net search does **not** support using this as the primary production strategy yet.

Main concerns:

- query correctness and schema drift
- weak determinism
- harder latency predictability
- more fragile spoiler safety
- poor explainability compared to vector-first and template approaches

If explored later, it should be limited to a read-only fallback path with schema redaction, query validation/correction, and strict observability.

---

## 11. Ranking And Fusion Guidance

### The ranking principle

LoreVault should rank **chunks as the final evidence unit**.

Graph nodes are useful for:

- identifying better chunk candidates
- grouping evidence
- enriching chunk context
- helping the LLM reason about who/where/when

But current graph nodes should not replace chunks as the core cited evidence.

### Recommended retrieval-time ranking signals

Start with vector score, then add deterministic graph-derived signals such as:

- overlap between query-mentioned entity names and scene entity names
- overlap between query-mentioned location names and scene locations
- number of supporting graph hits attached to the same scene/chapter
- bounded path proximity from query-linked entities
- diversity penalties so one scene/chapter does not dominate the whole result set

### On fusion algorithms

If LoreVault later merges multiple ranked lists, use one of these approaches:

- **simple weighted rerank** when vector search is the clear primary signal and graph data is auxiliary
- **Reciprocal Rank Fusion (RRF)** when multiple ranked lists materially overlap

Important caveat from the research: RRF is strongest when the competing lists overlap on the same evidence units. If one list is graph-node-centric and the other is chunk-centric, first project graph relevance back onto chunks/scenes before fusing.

### Recommended first ranking experiment

For the first hybrid slice, keep ranking simple:

```text
finalScore =
    vectorScore
  + entityMatchBoost
  + locationMatchBoost
  + supportCountBoost
  - duplicationPenalty
```

Keep weights config-driven and log all components for later tuning.

---

## 12. How Vector Results Should Use The Graph

The current best use of the graph is not “find the answer instead of chunks.”

It is one of these three jobs:

### 1. Context inflation

Given a strong chunk hit, automatically pull its narrative neighborhood:

- owning `Scene`
- `Scene.contextSummary`
- present Individuals
- present Locations
- optionally previous/next scenes or temporal neighbors

This gives the LLM the who/where/when structure that vector similarity alone cannot provide.

### 2. Deterministic reranking

Graph signals can improve retrieval ordering without changing the answering model:

- an entity-heavy question should reward chunks from scenes where the relevant entity is actually present
- a location-heavy question should reward chunks in scenes mentioning the location
- related chunks from the same scene or timeline neighborhood can be grouped instead of treated as isolated text fragments

### 3. Answer planning support

The graph can provide a lightweight structured header for each evidence item, for example:

```text
[Chunk 3] Book 2, Chapter 5
Scene summary: ...
Individuals present: ...
Locations present: ...
```

This helps the LLM reason about evidence ordering and relevance while keeping the answer grounded in visible chunks.

---

## 13. How Chunk Similarity Can Guide Graph Search

This is now one of the clearest promising patterns for LoreVault.

Yes — chunk similarity can and should act as a **hinting mechanism** for graph exploration.

Recommended flow:

1. vector search returns top spoiler-safe chunks
2. chunks identify seed scenes
3. scenes identify seed Individuals and Locations
4. bounded expansion gathers nearby graph context
5. that context is used to rerank or enrich the original chunk results

This is safer and more stable than asking an LLM to infer graph traversal targets from the raw question alone.

It also maps well to LoreVault's current data model, where the graph's most reliable retrieval hooks are close to scenes and chunks.

---

## 14. Revised Near-Term Recommendation

The current recommended staged plan is now:

### Phase 1 — ship a bounded hybrid on top of the current RAG path

- keep spoiler-gated vector retrieval as the default entry point
- enrich top chunk results with scene/entity/location context
- rerank chunks with deterministic graph-derived signals
- keep chunks as the core cited evidence

### Phase 2 — add a small entity-template lane

Start with a narrow template registry for questions such as:

- who is `{individual}` so far?
- where is `{location}` mentioned so far?
- which scenes mention `{individual}`?
- which scenes connect `{individual A}` and `{individual B}`?

### Phase 3 — evaluate before expanding the architecture

Measure:

- answer quality
- citation quality
- latency impact
- spoiler safety
- which question types still fail under the bounded hybrid

### Phase 4 — only then consider a routed multi-lane system

If needed, add explicit routing between:

- vector-first narrative QA
- entity lookup templates
- future timeline/path templates
- eventual fallback Text2Cypher

---

## 15. May 2026 Revision: Relation Discovery Before Relation Routing

The earlier entity-expansion MVP remains useful for improving the current RAG path, but it is not the right starting point for typed inter-entity semantics. The relation layer should begin as **evidence harvesting plus catalog discovery**, not as a fixed hand-authored routing table.

The first relation slice should answer this question:

> Can LoreVault extract rich inter-entity relation claims from scenes, preserve the LLM's semantic detail, and use a catalog module to accumulate candidate meanings without prematurely flattening prose into a small predefined taxonomy?

This changes the order of work:

1. The scene-analysis LLM emits open-ended relation claims: `subject`, `relationName`, `usageHint` / `relationDescription`, `object`, evidence text, certainty, and publication coordinates.
2. A catalog module receives `{name, usageHint, subjectKind, objectKind}` and returns candidate relation IDs with correlation scores and descriptions.
3. High-confidence matches can attach a known `relTypeId`; otherwise the claim is retained with a provisional key such as `R:provisional.turned_against`.
4. Provisional observations accumulate across ingestion runs, then get clustered, reviewed, merged, or promoted into canonical catalog entries.
5. Stable graph-aware routing is introduced only after enough promoted relation types exist to traverse reliably.

This preserves semantic richness while still creating a path toward queryable typed edges.

### Catalog module shape

Conceptually, the catalog owns relation vocabulary intelligence. It can be queried like:

```json
{
  "name": "betrayed",
  "usageHint": "the general turned against the king and deposed him",
  "subjectKind": "Individual",
  "objectKind": "Individual"
}
```

and respond with candidates:

```json
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

For now this should be a module / bounded context in the modulith, not a separately deployed microservice. It owns relation definitions, aliases, examples, embeddings or semantic matching, candidate scoring, provisional observations, and promotion / merge decisions. It should not own scene analysis, claim persistence, entity resolution, edge projection, Q&A routing, or Allen-style temporal relations.

### Revised first relation slice

The first relation slice is now:

1. **Prompt extension** — ask scene analysis to extract open-ended inter-entity relation claims without forcing a fixed relation menu.
2. **Relation claim persistence** — store raw relation phrase, usage hint, subject/object references, evidence, certainty, source/chunk/scene provenance, and `pubCoords` append-only.
3. **Catalog candidate matching** — call the catalog module with the extracted phrase and context; store candidate IDs, correlation scores, and whether a high-confidence match was selected.
4. **Provisional observation harvest** — assign `R:provisional.<normalized_phrase>` when no confident match exists; aggregate observed phrases by normalized form, endpoint kinds, frequency, examples, and candidate cluster.
5. **Review-ready output** — surface relation clusters and examples for later curation. Promotion to stable `R:*` IDs is an explicit follow-up, not a prerequisite for retaining evidence.

Projected query edges should distinguish exploratory/provisional observations from stable canonical relations. A provisional edge, if materialized at all, is a derived debug/query-assist view:

```text
(:Entity)-[:PROVISIONAL_REL {
  relationName,
  provisionalRelTypeId,
  claimId,
  pubCoords
}]->(:Entity)
```

Stable `REL {relTypeId}` edges are replayed from claims after catalog promotion. This avoids rewriting the canonical evidence store when provisional phrases later merge into a canonical relation.

### Downstream retrieval implication

Fixed routing tables can return later, but they should route over **promoted catalog IDs**, not speculative first-pass names. Until then, graph-aware retrieval should either ignore provisional relations or use them only in diagnostic / experimental modes where fuzzy matching and cluster uncertainty are visible.

### Multi-bin fan-out remains correct behavior

The same semantic fact can live in different claim bins depending on how the source text framed it:

- "He's a police officer" → **Ascription** (`P:occupation`, value = police_officer concept)
- "He was part of the force" → **Relation** (raw phrase: `part of`, possible catalog candidate later)
- "He carried a badge" → relation-like evidence that may support occupation or affiliation after catalog/claim aggregation

**Store it where you find it. Retrieve it from everywhere — but only promote stable traversal semantics after observed data earns them.**

---

## Primary References

- [RAG Retrieval Chain](../../patterns/search/rag-retrieval-chain.md) — current RAG chain
- [Spoiler-Aware Retrieval](../../patterns/search/spoiler-aware-retrieval.md) — spoiler filter mechanism
- [Entity Resolution Ladder](../../patterns/ingestion/entity-resolution-ladder.md) — entity resolution ladder (Individual, Location, Object, and Collective lanes)
- `lorevault-api/src/main/java/com/lorevault/api/search/Neo4jSemanticSearch.java` — vector search impl
- `lorevault-api/src/main/java/com/lorevault/api/search/RagService.java` — RAG orchestration
- External: Neo4j GraphRAG Pattern Catalog (graphrag.com/reference/graphrag/)
- External: neo4j-graphrag-python user guide (neo4j.com/docs/neo4j-graphrag-python/current/)
