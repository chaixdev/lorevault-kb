# Multi-Entity Retrieval: External Research Findings
# Pressure-Testing the ShortestPath Proposal

**Date:** April 2026  
**Status:** Brainstorm — research synthesis, not yet an accepted decision  
**Purpose:** Compare external approaches for answering multi-entity, relationship-heavy natural-language questions of the shape *"what was the argument between X and Y about while they were at Z?"* — and pressure-test the LoreVault proposal of `entity extract → shortestPath() → pull chunks → weighted rerank` against peer-reviewed evidence.

**Companion document:** [`graph-aware-qa-design-april-2026.md`](graph-aware-qa-design-april-2026.md) — LoreVault-specific design that should be read alongside this.

---

## The Question Shape Under Analysis

```
"What was the argument between Vin and Kelsier about while they were at the Pits of Hathsin?"
```

Structural anatomy:
- **Entity A** (Person): Vin
- **Entity B** (Person): Kelsier  
- **Location Z**: Pits of Hathsin
- **Implicit event/topic**: an argument — temporal co-location + interaction type implied

This query type has three simultaneous graph anchors and an implicit event that is not named. It requires finding evidence where *all three* are co-present in the same narrative moment — not just any path connecting them in the graph.

---

## Section 1 — Entity Extraction for the "X and Y at Z" Shape

### The standard approach: NER + entity linking

All production-grade GraphRAG systems that handle multi-entity questions use a two-step extraction pipeline:

1. **Named Entity Recognition (NER)** — identify spans: `Vin` (PERSON), `Kelsier` (PERSON), `Pits of Hathsin` (LOCATION)
2. **Entity linking / normalization** — resolve each span to a canonical graph node

**Neo4j GraphRAG Python** (official docs): the `VectorCypherRetriever` and `HybridCypherRetriever` both receive a list of extracted entities as parameters before the Cypher traversal phase. The entity extraction step is considered external to the retriever — callers are expected to supply normalized entity IDs or names.

> Source: [Neo4j GraphRAG Python user guide — Retrievers](https://neo4j.com/docs/neo4j-graphrag-python/current/user_guide_rag.html)

**Microsoft GraphRAG** (Local Search): resolves entity names via the entity community membership index before traversal. Entity resolution is the pipeline's first phase; the retrieval phase receives resolved node IDs.

> Source: [Microsoft GraphRAG — Local Search](https://microsoft.github.io/graphrag/query/local_search/)

**LangChain4j + Neo4j**: `Neo4jText2CypherRetriever` passes the raw question to an LLM which generates Cypher directly — entity extraction is implicit in the LLM call. This is **not recommended** without strong guardrails (see Section 5).

> Source: [LangChain4j Neo4j docs — Text2CypherRetriever](https://docs.langchain4j.dev/integrations/embedding-stores/neo4j/)

### The coreference / alias problem

The NovelHopQA paper (arXiv:2506.02000v2, 2026) identifies **entity confusion and coreference errors** as one of the two dominant failure modes in narrative RAG. In fiction, the same character may be referred to as:
- "Vin", "the girl", "the young allomancer", "she"

NER on the question alone does not help when the answer evidence uses a different surface form. The entity linking step must normalise **both** the query entity and the candidate chunk entities.

**LoreVault relevance:** LoreVault's `IndividualMention → ChapterIndividual → BookIndividual` ladder already provides this normalisation. The `BookIndividual.normalizedName` is the correct lookup key. The extraction step for a question is: extract surface names from the question text → match against `BookIndividual.normalizedName` within the allowed book scope.

### Critical failure mode: over-merging

The "State of Agentic GraphRAG" (Feb 2026) identifies **entity over-merge as the #1 failure mode** in graph construction: "hallucinated edges become shortcuts to the wrong neighbourhood." For LoreVault, this means if `Vin` and `the girl` are merged into a single `Individual` node incorrectly, any traversal from that node will silently return wrong evidence.

**Recommendation:** Resolve entities from the question against known `BookIndividual` and `BookLocation` nodes rather than extracting fresh from the question text. This leverages the already-normalised graph and avoids hallucinated entity injection.

---

## Section 2 — Graph Anchoring Approaches

Once entities are extracted, the question is: **where in the graph do you anchor traversal?**

Three approaches appear in the literature:

### Approach A — Anchor at the entity node directly

```cypher
MATCH (a:BookIndividual {normalizedName: $nameA})
MATCH (b:BookIndividual {normalizedName: $nameB})
MATCH (z:BookLocation   {normalizedName: $nameZ})
// ... then traverse
```

**Pro:** Precise, deterministic, schema-safe.  
**Con:** Requires complete entity extraction and linking before the query runs. Fails silently if an entity is misspelled or if the graph only has ChapterIndividual (not BookIndividual) coverage.

### Approach B — Anchor at chunks via vector, then ascend to entity nodes

```cypher
CALL db.index.vector.queryNodes('chunk_embedding_idx', 20, $embedding) YIELD node AS chunk, score
MATCH (scene:Scene)-[:HAS_CHUNK]->(chunk)
MATCH (scene)-[:MENTIONS]->(im:IndividualMention)
// ... then link im to ChapterIndividual/BookIndividual
```

**Pro:** Tolerates fuzzy/incomplete entity extraction; vector search finds scenes where all three entities co-occur naturally.  
**Con:** Vector search may not surface the specific scene if the argument is described in indirect prose (e.g., "their disagreement").

**Neo4j's canonical `VectorCypherRetriever` pattern** uses exactly Approach B: vector similarity finds seed nodes, then Cypher traversal expands outward 1–2 hops:

```cypher
-- From neo4j.com/developer/genai-ecosystem/graphrag-python/
WITH node AS chunk
MATCH (chunk)<-[:FROM_CHUNK]-(entity)-[relList:!FROM_CHUNK]-{1,2}(nb)
UNWIND relList AS rel
WITH collect(DISTINCT chunk) AS chunks, collect(DISTINCT rel) AS rels
RETURN apoc.text.join([c in chunks | c.text], '\n') + ...
```

> Source: [Neo4j Developer Guide — GraphRAG Python](https://neo4j.com/developer/genai-ecosystem/graphrag-python/)

### Approach C — Anchor at the scene (direct co-occurrence query)

```cypher
MATCH (scene:Scene)-[:MENTIONS]->(imA:IndividualMention)
MATCH (scene)-[:MENTIONS]->(imB:IndividualMention)
MATCH (scene)-[:MENTIONS]->(lm:LocationMention)
WHERE imA.normalizedRef = $nameA
  AND imB.normalizedRef = $nameB
  AND lm.normalizedRef  = $nameZ
MATCH (scene)-[:HAS_CHUNK]->(chunk)
RETURN chunk.text, scene.contextSummary
```

**Pro:** Directly retrieves what the question asks — scenes where all three are simultaneously present.  
**Con:** Requires complete entity mention extraction to have been run during ingestion. Requires `normalizedRef` to be populated and normalised consistently.

**The E²RAG paper (EACL 2026, arXiv:2506.05939)** validates Approach C through their dual-graph architecture: entity subgraph + event subgraph, with a bipartite mapping between them. The key finding is that scene co-occurrence correctly captures *"the shared temporal and contextual facet"* that the question is asking about — which single-entity node traversal cannot.

---

## Section 3 — Path Retrieval vs. Scene Co-occurrence vs. Subgraph vs. Community

This is the core question: **is `shortestPath()` the right primitive?**

The research evidence is unambiguous.

### 3.1 Shortest-Path Retrieval

**What it does:**
```cypher
MATCH path = shortestPath((a:BookIndividual {normalizedName:$nameA})-[*]-(b:BookIndividual {normalizedName:$nameB}))
RETURN path
```

**What the research says:**

The GTSQA paper (arXiv:2511.04473v2, 2026) directly evaluates shortest-path methods as retrieval primitives for knowledge-graph QA:

> *"Shortest paths from seed entities to answer nodes is often a bad approximation... shortest path triples are not as good a target for retrieval as the ground-truth triples."*

Their analysis shows:
- Shortest-path methods learn *relation sequences* — they trace the topologically shortest route through the graph
- For multi-entity questions, the answer usually requires a *subgraph* where multiple entities satisfy simultaneous constraints, not a linear path between two of them
- Path-based methods fail most when: (a) seed-to-answer distance is large, (b) the answer requires common neighbors satisfying multiple constraints at once (exactly the "X and Y at Z" shape)

The S-Path-RAG paper (WWW 2026, arXiv:2603.23512) makes `shortestPath()` work — but only by adding:
- a semantic edge weighting function (trained on query-path relevance)
- a path verifier that filters out topologically short but semantically irrelevant paths
- k-shortest path enumeration + beam search rather than a single path

**Raw, unweighted Cypher `shortestPath()`** — as originally proposed — is the version that fails.

**The concrete failure mode for LoreVault:**

Given `Vin` and `Kelsier`, the shortest path in LoreVault's graph might be:
```
BookIndividual:Vin → ChapterIndividual:Vin_ch3 → IndividualMention:Vin_scene7
  → Scene:scene7 ← IndividualMention:Kelsier_scene7 ← ChapterIndividual:Kelsier_ch3
  ← BookIndividual:Kelsier
```

That path traverses through their *first co-occurring scene*. If their argument at the Pits of Hathsin happens in Chapter 15, that is not the shortest path. The shortest path reliably finds the *first* connection, not the *contextually relevant* one.

Adding `Location Z` makes this worse: `shortestPath()` between three nodes is not directly expressible in Cypher — you would need two separate shortest paths and hope they share a scene, which they may not.

### 3.2 Scene Co-occurrence Retrieval

**What it does:** Find scenes where all query entities are simultaneously present via direct co-occurrence matching (Approach C above).

**What the research says:**

The temporal co-occurrence paper (arXiv:2603.18420, 2026) directly validates scene co-occurrence as the correct retrieval unit for narrative questions:

> *"A 15-chunk sliding window captures the local narrative neighbourhood: passages that participate in the same scene, the same argument."*

The paper establishes that the relevant evidence unit for "X and Y argument at Z"-type questions is *the scene* — not the individual entity mentions, not a path between them, and not isolated chunks.

**E²RAG (EACL 2026)** reinforces this with their narrative-specific finding:

> *"Standard KG-RAG collapses distinct temporal or contextual facets of the same character into a single node — fatal for fiction where characters evolve."*

Their solution is a **dual-graph** (entity graph + event/scene graph with bipartite mapping), which allows retrieval to be anchored at the event/scene level rather than the entity level. For the "argument" query, retrieval anchors at the scene node, then expands to entities and chunks.

**Advantage for LoreVault:** LoreVault already has `Scene` as a first-class node with `[:MENTIONS]` edges to `IndividualMention` and `LocationMention`. The scene co-occurrence Cypher is expressible directly against the existing graph. No new nodes or relationships are needed.

### 3.3 Subgraph Retrieval

**What it does:** Extract a bounded subgraph around the seed entities (all nodes within k hops), then let the LLM reason over it.

**What the research says:**

SubgraphRAG (ICLR 2025) and the GTSQA analysis both show that subgraph extraction outperforms path-based methods on complex multi-entity questions. The key metric is: *"does the ground-truth answer appear in the retrieved context?"* SubgraphRAG achieves this at higher rates than path traversal.

The reasoning bottleneck paper (arXiv:2603.14045v2, Mar 2026) adds an important counterpoint:

> *"77–91% of questions have the gold answer in the retrieved context, but only 35–78% get a correct answer — reasoning failure dominates."*

This means even if subgraph retrieval surfaces the right evidence, the LLM may fail to extract the answer from a large, unstructured subgraph. **Context size management is critical** — smaller, better-targeted subgraphs outperform large sprawling ones.

**LoreVault implication:** Subgraph retrieval is more powerful than shortest-path, but it needs bounded expansion (not unbounded `[*]` Cypher traversal). The vector-seeded expansion described in `graph-aware-qa-design-april-2026.md` (Pattern 2) is a controlled subgraph — it is the right primitive.

### 3.4 Community / Summary Retrieval

**What it does:** Pre-computed community summaries (e.g., via Leiden algorithm) capture the thematic neighborhood of a group of entities. Retrieval fetches the relevant community report first, then refines locally.

**What the research says:**

Microsoft GraphRAG's community detection achieves an **89–91% win rate vs. 28–34% for traditional RAG** on complex relational queries. DRIFT Search (the most relevant mode) uses:
- Phase A: community reports → broad initial answer + follow-up questions
- Phase B: local entity neighborhood expansion → refinement
- Phase C: hierarchical Q&A ranked by confidence

> Source: [Microsoft GraphRAG — DRIFT Search](https://microsoft.github.io/graphrag/query/drift_search/)

**The graphrag.com Pattern Catalog** describes the Global Community Summary Retriever:

```cypher
MATCH (c:__Community__) WHERE c.level = $level RETURN c.full_content
```

> Source: [GraphRAG Pattern Catalog — Global Community Summary Retriever](https://graphrag.com/reference/graphrag/global-community-summary-retriever/)

**LoreVault fit:** Community detection requires a significant up-front graph analysis step (Leiden algorithm on the entity graph) and storage of pre-computed community summaries. LoreVault's graph is currently too sparse at the `BookIndividual` layer for community detection to produce meaningful summaries. This is a **Phase 4 investment** at the earliest.

### 3.5 Verdict Table

| Approach | Complexity | Handles "X+Y at Z" correctly? | LoreVault readiness | Verdict |
|---|---|---|---|---|
| Raw `shortestPath()` | Low | ❌ No — topologically wrong path | Ready (but wrong) | **Do not use** |
| Semantic shortest-path (S-Path-RAG) | High | ✅ Yes, with trained scorer | Not ready (needs training data) | Future only |
| Scene co-occurrence | Medium | ✅ Yes — direct match | **Ready now** | **Recommended primary** |
| Vector-seeded subgraph expansion | Medium | ✅ Yes — finds co-occurrence via vector | **Ready now** | **Recommended default** |
| Community/DRIFT | High | ✅ Yes, for broad questions | Not ready (sparse graph) | Phase 4 |
| Free-form Text2Cypher | Medium | ⚠️ Unpredictable | Available but unsafe | Fallback only |

---

## Section 4 — Final Evidence Ranking

### The right unit to rank

All production references agree: **rank chunks, not graph nodes**.

Graph nodes (scenes, entities, locations) are retrieval intermediaries. The final evidence presented to the LLM must be grounded in text chunks with stable citation anchors. This is consistent with:
- Neo4j GraphRAG Python's `VectorCypherRetriever` (returns chunk text + graph context)
- The reasoning bottleneck paper's finding that context size directly impacts answer quality
- LoreVault's existing citation model (`chunkId`, `bookNumber`, `chapterNumber`)

### Recommended ranking signal stack

Starting with the current vector score as the primary signal, add these deterministic graph-derived boosts:

| Signal | Cypher derivation | Boost weight |
|---|---|---|
| Vector similarity | `score` from `db.index.vector.queryNodes` | Primary (1.0×) |
| Entity name overlap | Count of `IndividualMention.normalizedRef` in scene that match query entities | +0.3 per matched entity |
| Location name overlap | Count of `LocationMention.normalizedRef` in scene that matches query location | +0.4 (location is high-precision for "X+Y at Z") |
| Scene support count | Number of graph-matched entities present in this scene | +0.1 per additional supporting entity |
| Scene summary relevance | Optional: cosine similarity between query embedding and `Scene.contextSummary` embedding | +0.2 if available |
| Diversity penalty | If multiple chunks from the same `Scene` are in top-K, penalise duplicates | −0.3 per duplicate scene |

**Fusion formula:**

```java
double finalScore(SearchResult r, Set<String> queryEntities, Set<String> queryLocations) {
    double score = r.vectorScore();
    score += r.individualsPresent().stream()
        .filter(queryEntities::contains).count() * 0.3;
    score += r.locationsPresent().stream()
        .filter(queryLocations::contains).count() * 0.4;
    score += Math.min(r.individualsPresent().size() - queryEntities.size(), 0) * 0.1;
    // diversity penalty applied after deduplication across scenes
    return score;
}
```

Keep all weight constants in `application.yml` under `lorevault.search.ranking.*` for tuning without redeployment.

### RRF is for symmetric lane fusion — do not use it here

Reciprocal Rank Fusion (RRF) is appropriate when **two independently-ranked lists of the same unit type** (e.g., two chunk lists) are merged. The research on RRF (and the graphrag.com hybrid retrieval docs) explicitly note that RRF degrades when the two lists rank different entity types.

For LoreVault's initial hybrid: vector score is the primary list (chunk-ranked), and graph signals are **boosts on that list**, not a second independent ranked list. A weighted boost is more appropriate than RRF at this stage.

**When RRF is appropriate:** After Pattern 3 (hybrid dense + sparse) is added — when there is a full-text BM25 chunk list alongside the vector chunk list. Both are chunk-ranked. That is the correct symmetric case for RRF.

---

## Section 5 — Java/Spring/Neo4j Stack Mapping

### LangChain4j primitives

| Need | LangChain4j component | Maven artifact |
|---|---|---|
| Vector store | `Neo4jEmbeddingStore` | `dev.langchain4j:langchain4j-community-neo4j` |
| Hybrid search | `Neo4jEmbeddingStore` with `fullTextIndexName` + `fullTextQuery` builder params | same |
| Graph-aware retrieval | Custom `ContentRetriever` backed by `Neo4jClient` + Cypher templates | Spring Data Neo4j |
| LLM-generated Cypher (unsafe) | `Neo4jText2CypherRetriever` | `dev.langchain4j:langchain4j-community-neo4j-retriever` |
| Spring Boot autoconfiguration | Spring Boot starter | `dev.langchain4j:langchain4j-community-neo4j-spring-boot-starter` |

> Source: [LangChain4j Neo4j integration docs](https://docs.langchain4j.dev/integrations/embedding-stores/neo4j/)  
> Source: [Neo4j + LangChain4j blog — April 2026](https://neo4j.com/blog/developer/langchain4j-graphrag-vector-stores-retrievers/)

### Safe vs. unsafe Text2Cypher

The `Neo4jText2CypherRetriever` exposes the database schema to the LLM. When the LLM generates a Cypher query, it:
- Has access to node labels and relationship types
- Can produce arbitrary read queries against the database
- Does not enforce spoiler guards unless they are embedded in the system prompt

**Production risk:** Spoiler safety cannot be reliably enforced via LLM-generated Cypher. A single prompt-injection attack can strip the `WHERE bookId IN $allowedBookIds` guard.

The safe pattern (from `ugwun/lanchain4j-contentretriever` and the associated Medium tutorial) is:

```java
// SAFE: deterministic ContentRetriever backed by @Query repositories
@Repository
public interface SceneContentRetriever {
    @Query("""
        MATCH (scene:Scene)-[:MENTIONS]->(im:IndividualMention)
        MATCH (scene)-[:MENTIONS]->(lm:LocationMention)
        WHERE im.normalizedRef IN $individualNames
          AND lm.normalizedRef IN $locationNames
          AND scene.bookId IN $allowedBookIds
        MATCH (scene)-[:HAS_CHUNK]->(chunk)
        RETURN chunk.text, scene.contextSummary
    """)
    List<ChunkWithContext> findScenesByEntitiesAndLocation(
        List<String> individualNames,
        List<String> locationNames,
        Set<UUID> allowedBookIds
    );
}
```

This is the recommended pattern for LoreVault: **parameterized Cypher templates in Spring Data Neo4j repositories, not LLM-generated Cypher**.

### The canonical traversal pattern (translated to Java/Neo4jClient)

Neo4j's official VectorCypherRetriever pattern (Python) maps to `Neo4jClient` in Spring:

```java
// In Neo4jSemanticSearch.java — extending the existing buildCypher() method
private static final String MULTI_ENTITY_CO_OCCURRENCE_QUERY = """
    CALL db.index.vector.queryNodes('chunk_embedding_idx', $oversampleLimit, $embedding)
    YIELD node AS chunk, score
    MATCH (scene:Scene)-[:HAS_CHUNK]->(chunk)
    MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)
    WHERE chapter.bookId IN $allowedBookIds
    
    // Entity co-occurrence filter (when entities are provided)
    WITH chunk, score, scene, chapter
    OPTIONAL MATCH (scene)-[:MENTIONS]->(im:IndividualMention)
    OPTIONAL MATCH (scene)-[:MENTIONS]->(lm:LocationMention)
    
    WITH chunk, score, scene, chapter,
         collect(DISTINCT im.normalizedRef) AS individualsInScene,
         collect(DISTINCT lm.normalizedRef) AS locationsInScene
    
    // Boost score by entity overlap
    WITH chunk, score, scene, chapter, individualsInScene, locationsInScene,
         score
         + size([x IN individualsInScene WHERE x IN $queryIndividuals]) * 0.3
         + size([x IN locationsInScene WHERE x IN $queryLocations]) * 0.4
         AS boostedScore
    
    RETURN
        chunk.id          AS chunkId,
        boostedScore      AS score,
        chunk.text        AS text,
        scene.id          AS sceneId,
        scene.contextSummary AS sceneSummary,
        chapter.id        AS chapterId,
        chapter.bookNumber    AS bookNumber,
        chapter.chapterNumber AS chapterNumber,
        individualsInScene,
        locationsInScene
    ORDER BY boostedScore DESC
    LIMIT $topK
    """;
```

This is Approach B from Section 2 (vector-seeded, ascending to scene, with co-occurrence filter applied as a boost rather than a hard filter). It degrades gracefully: if no entities are extracted from the question, `$queryIndividuals` and `$queryLocations` are empty lists and the score is pure vector similarity.

---

## Section 6 — Verdict on ShortestPath for LoreVault

**The proposed approach:** `entity extract → shortestPath() → pull chunks → weighted rerank`

**Verdict: Do not use raw `shortestPath()` as the graph traversal primitive for narrative Q&A.**

### Evidence

1. **GTSQA (arXiv:2511.04473v2):** Shortest path is "often a bad approximation" for ground-truth answer subgraphs. Path-based retrievers consistently underperform subgraph-based ones on multi-entity questions.

2. **S-Path-RAG (WWW 2026):** Shortest-path CAN be made to work, but only with a trained semantic edge weighting function and a path verifier. These components do not exist in LoreVault and require training data to build.

3. **Concrete LoreVault failure mode:** `shortestPath(Vin, Kelsier)` finds their *first* co-occurring scene (minimum graph hops), not the scene where their argument occurs. The argument at the Pits of Hathsin may be Chapter 15; their first co-occurrence may be Chapter 2. `shortestPath()` returns Chapter 2's evidence.

4. **Three-anchor failure:** The "X and Y at Z" shape has three anchors (two persons + one location). `shortestPath()` takes two endpoints. There is no single Cypher `shortestPath()` call that handles three anchors simultaneously. The only workarounds — two separate shortest paths, or a multi-anchor Steiner tree approximation — are significantly more complex and still path-centric (subject to the above failures).

5. **E²RAG (EACL 2026):** For narrative fiction, collapsing characters to single nodes (which path traversal does) "destroys temporal and causal context." A character's node in LoreVault represents their entire presence across the book — traversing it via shortest-path ignores the temporal dimension entirely.

### What to use instead

| Query shape | Recommended primitive | Cypher pattern |
|---|---|---|
| Single entity lookup | Template query on `BookIndividual` or `BookLocation` | `MATCH (bi:BookIndividual {normalizedName: $name})` |
| "What scenes feature X?" | Direct ladder traversal | `BookIndividual ← ChapterIndividual ← IndividualMention ← Scene` |
| "What scenes feature X and Y?" | Scene co-occurrence | Double `MATCH (scene)-[:MENTIONS]->(im)` with entity filter |
| "What happened at X and Y at Z?" | **Vector-seeded expansion + entity boost** | Vector seed → Scene → mentions filter + boost |
| "General narrative question" | Vector search (current path) | Existing `db.index.vector.queryNodes` |

The vector-seeded expansion with entity co-occurrence boost (Section 5 above) handles the "X and Y at Z" shape correctly:
- Vector similarity surfaces scenes *semantically* related to the question (including the argument)
- Entity/location overlap boost promotes scenes where all three named entities are present
- No path traversal between entity nodes is needed — the scene is the anchor, not the path

---

## Section 7 — Recommended Approach for LoreVault

### The recommended retrieval architecture for "X and Y at Z" questions

```
Step 1: Extract entity names from question text
        → Match against BookIndividual.normalizedName and BookLocation.normalizedName
        → Collect: queryIndividuals = ["vin", "kelsier"], queryLocations = ["pits-of-hathsin"]

Step 2: Run vector search (existing path)
        → db.index.vector.queryNodes on chunk_embedding_idx
        → Oversample: topK * 3 (e.g., 30 candidates for top-10 result)

Step 3: Expand each candidate chunk to its Scene
        → MATCH (scene:Scene)-[:HAS_CHUNK]->(chunk)
        → Collect IndividualMention and LocationMention for each scene

Step 4: Apply entity co-occurrence boost
        → For each candidate, compute boostedScore = vectorScore
                                                    + (matched query individuals × 0.3)
                                                    + (matched query locations × 0.4)

Step 5: Apply diversity penalty
        → If multiple chunks from the same Scene are in top-K, keep highest-scored, penalise duplicates

Step 6: Build enriched context string per chunk
        → "[Book X, Ch Y — Scene: {contextSummary} — Individuals: {list} — Locations: {list}]"
        → Append chunk text

Step 7: Pass to LLM with enriched context
        → System prompt: ground your answer in the provided passages; cite by [chunk N]
```

This is an incremental extension of the existing RAG path. It does not require new graph nodes, new relationships, or a new service layer.

### When to add scene co-occurrence as a hard filter

Once the entity mention pipeline (IndividualMention normalization) is production-stable and covers all ingested chapters, add a **second Cypher template** for the "X and Y at Z" intent:

```cypher
// Template: "co-occurrence-with-location"
// Fires when: at least 2 individuals AND 1 location are extracted from the question
MATCH (scene:Scene)-[:MENTIONS]->(imA:IndividualMention)
MATCH (scene)-[:MENTIONS]->(imB:IndividualMention)
MATCH (scene)-[:MENTIONS]->(lm:LocationMention)
MATCH (chapter:Chapter)-[:HAS_SCENE]->(scene)
WHERE imA.normalizedRef = $nameA
  AND imB.normalizedRef = $nameB
  AND lm.normalizedRef  = $nameZ
  AND chapter.bookId IN $allowedBookIds
  AND imA <> imB
MATCH (scene)-[:HAS_CHUNK]->(chunk)
RETURN chunk.text, scene.contextSummary, chapter.bookNumber, chapter.chapterNumber
ORDER BY chapter.bookNumber, chapter.chapterNumber
```

This template returns results ranked by narrative order (which is often what the reader wants). Use it as the **primary lane** when all three entity types are present in the question, with the vector-seeded expansion as the fallback when fewer entities are identified.

### Staged implementation plan

| Phase | Action | Effort | Prerequisite |
|---|---|---|---|
| **Phase 1 (now)** | Add entity co-occurrence boost to existing vector search | Small — extend `buildCypher()` and `SearchResult` | IndividualMention + LocationMention exist |
| **Phase 2** | Add `QuestionIntentClassifier` to extract entity names from questions | Medium — new bean, regex + optional LLM | Phase 1 |
| **Phase 3** | Add `co-occurrence-with-location` Cypher template as a dedicated lane | Medium — new template + router branch | IndividualMention.normalizedRef populated |
| **Phase 4** | Evaluate: do phases 1–3 solve "X+Y at Z" questions adequately? | Analysis — compare answer quality | Phases 1–3 |
| **Phase 5 (if needed)** | Consider community detection on entity graph for thematic questions | Large — Leiden algorithm, community summaries | Phase 4 result |

**Do not implement shortest-path at any phase.** There is no evidence basis for it outperforming the scene co-occurrence approach for this query shape, and multiple papers directly contradict it.

---

## Summary of External Evidence

| Paper / Source | Key Finding | Relevance |
|---|---|---|
| GTSQA (arXiv:2511.04473v2) | Shortest paths are "often a bad approximation" for multi-entity QA | Directly against `shortestPath()` proposal |
| S-Path-RAG (WWW 2026, arXiv:2603.23512) | Semantic path scoring required for paths to work | ShortestPath only viable with trained scorer |
| SubgraphRAG (ICLR 2025) | Subgraph extraction outperforms path-based on KGQA benchmarks | Supports bounded expansion over path traversal |
| E²RAG (EACL 2026, arXiv:2506.05939) | Scene/event anchoring outperforms entity-node anchoring in narrative fiction | Directly supports scene co-occurrence for fiction |
| NovelHopQA (arXiv:2506.02000v2) | Coreference errors + evidence misalignment are dominant failure modes in narrative RAG | Motivates entity normalisation via ladder |
| Temporal co-occurrence (arXiv:2603.18420) | 15-chunk window captures scenes participating in "the same argument" | Validates scene as the retrieval unit |
| Reasoning bottleneck (arXiv:2603.14045v2) | LLM reasoning failure dominates (77–91% recall, 35–78% accuracy) | Keep context bounded and well-structured |
| Microsoft GraphRAG DRIFT | Community → local expansion beats pure vector on relational queries | Future direction; not ready without community detection |
| Neo4j VectorCypherRetriever | 1–2 hop neighborhood expansion from vector-matched nodes | Canonical production pattern for Neo4j |
| LangChain4j Neo4j docs | `Neo4jText2CypherRetriever` unsafe without guardrails | Don't use for spoiler-sensitive LoreVault |

---

## Primary External References

- Neo4j GraphRAG Python user guide: https://neo4j.com/docs/neo4j-graphrag-python/current/user_guide_rag.html
- Neo4j Developer Guide (VectorCypherRetriever): https://neo4j.com/developer/genai-ecosystem/graphrag-python/
- Neo4j + LangChain4j blog: https://neo4j.com/blog/developer/langchain4j-graphrag-vector-stores-retrievers/
- LangChain4j Neo4j docs: https://docs.langchain4j.dev/integrations/embedding-stores/neo4j/
- Microsoft GraphRAG DRIFT Search: https://microsoft.github.io/graphrag/query/drift_search/
- GraphRAG Pattern Catalog: https://graphrag.com/reference/graphrag/global-community-summary-retriever/
- GTSQA paper: https://arxiv.org/html/2511.04473v2
- S-Path-RAG paper: https://arxiv.org/abs/2603.23512
- E²RAG paper: https://arxiv.org/abs/2506.05939 / https://www.aclanthology.org/2026.eacl-long.90/
- NovelHopQA: https://arxiv.org/html/2506.02000v2
- Reasoning Bottleneck in GraphRAG: https://arxiv.org/abs/2603.14045v2
- Temporal co-occurrence: https://arxiv.org/pdf/2603.18420
- Safe LangChain4j ContentRetriever: https://medium.com/@cyrilsadovsky/langchain4j-spring-boot-contentretriever-tutorial-212da8b5c50d
