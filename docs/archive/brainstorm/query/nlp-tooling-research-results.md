# NLP Tooling Research Results: Query-Time Entity Extraction for LoreVault

## Date: 2026-04-16
## Status: Complete
## Input: Research brief prepared during query-time entity extraction evaluation

---

## Executive Summary

**Recommendation: Java-native Aho-Corasick trie + OpenNLP noun-phrase chunker. No Python sidecar needed.**

For LoreVault's bounded entity space (20–200 names per book, all pre-stored in Neo4j), a two-layer Java-native approach delivers **2–4ms total latency** — 90%+ headroom within the 50ms budget. This eliminates Docker sidecar overhead, network serialization, and operational complexity, aligning with the project's "reduce indirection" principle.

Production GraphRAG systems (Microsoft GraphRAG, Neo4j GraphRAG Python, LlamaIndex, LangChain) all use either vector ANN search or LLM extraction — approaches designed for *open/unknown* entity spaces that cost 100ms–2s per query. None use dictionary lookup because their entity sets are unbounded. Our entity set is fully known and small — the exact scenario where Aho-Corasick excels.

---

## 1. Library Comparison

### 1a. Python NLP Libraries

| Library | Latency (warm) | OOV/Fictional Names | Model Size | Docker Image | Notes |
|---|---|---|---|---|---|
| **spaCy `en_core_web_sm`** | P50=3ms, P99=8ms (full pipeline); PhraseMatcher <1ms | NER useless for fictional; PROPN detection works | 12MB | ~350–500MB (python:3.12-slim + spaCy) | Best Python option |
| **Stanza** | 20–80ms | Same NER limitation | 4–6GB (PyTorch) | Bloated | **Fails budget** |
| **NLTK** | 15–30ms warm; cold `pos_tag()` ~1s | Same NER limitation | Small | Small | Marginal; cold-start risk |

**Key finding:** Statistical NER is useless for fictional names. Grammar-based PROPN detection + dictionary matching is the optimal Python approach. Best Python strategy: spaCy EntityRuler (Aho-Corasick internally, <1ms) + PROPN fallback (~2ms total).

### 1b. Java NLP Libraries

| Library | Latency (warm JVM) | OOV Handling | Size | License | Notes |
|---|---|---|---|---|---|
| **OpenNLP 2.5.x** | 1–3ms POS+chunk | Grammar-based NP extraction | ~10MB models | Apache-2.0 | `ThreadSafeChunkerME` for concurrency |
| **Stanford CoreNLP** | 27–100ms (parser required for NP) | Same | Heavy | **GPL** | **Too heavy + wrong license** |
| **LingPipe** | N/A | N/A | N/A | N/A | Dead since 2011 |
| **Aho-Corasick** (`robert-bor/aho-corasick:0.6.3`) | ~0.02ms per query | Perfect (dictionary-based) | Tiny | Apache-2.0 | Deterministic, zero false negatives on known names |

**Key finding:** Java-native matches spaCy latency. OpenNLP POS+Chunk at 1–3ms is comparable to spaCy sm at 3ms. Aho-Corasick at 0.02ms is unbeatable for known-entity matching.

### 1c. Python Sidecar Overhead

| Component | Latency | Notes |
|---|---|---|
| Docker Compose HTTP round-trip | 2–5ms | Not the bottleneck |
| FastAPI framework overhead | ~5ms | Slightly faster than Flask for async |
| Total sidecar budget | 7–21ms typical | Achievable but unnecessary |
| GraalPy for spaCy | **Not viable** | C extension chain untested, fragile |

---

## 2. Production GraphRAG Validation

All four production systems were analyzed at source-code level.

| System | Query-Time Strategy | Entity Lookup | Latency | Trie/Dictionary? |
|---|---|---|---|---|
| **Microsoft GraphRAG** | Embed full query → ANN search on entity description vectors | Cosine similarity rank | 100–500ms (embedding + ANN) | **No** |
| **Neo4j GraphRAG Python** | Embed → ANN (VectorRetriever) OR LLM → Cypher (Text2Cypher) | ANN rank / Cypher execution | 50–200ms (embed) or 300–2000ms (LLM) | **No** |
| **LlamaIndex** | LLM → keyword list → exact string match (old); Embed → ANN (new, preferred) | Exact string / ANN rank | 300–2000ms (LLM) or 50–200ms (embed) | **No** |
| **LangChain** | LLM → proper noun list → exact triplet lookup (GraphQA); LLM → Cypher (CypherQA) | Exact string / Cypher | 300–2000ms | **No** |

**Why none use dictionary lookup:** Their entity spaces are **open and unbounded** — they don't know all entities at build time. Our entity space is **closed and fully known**. This is the fundamental architectural difference that makes Aho-Corasick the right choice for us.

**Fuzzy matching:** Neo4j's `FuzzyMatchResolver` (RapidFuzz `WRatio`, threshold 0.8) exists but is **build-time only** for deduplicating ingested entities — never used at query time in any system.

### Source evidence

- Microsoft GraphRAG: `map_query_to_entities()` — pure vector ANN, no dictionary ([source](https://github.com/microsoft/graphrag/blob/main/packages/graphrag/graphrag/query/context_builder/entity_extraction.py))
- Neo4j GraphRAG Python: no query-time entity extraction; `FuzzyMatchResolver` is build-time only ([source](https://github.com/neo4j/neo4j-graphrag-python/blob/main/src/neo4j_graphrag/experimental/components/resolver.py))
- LlamaIndex: LLM keyword extraction → exact string lookup; newer VectorContextRetriever embeds whole query ([source](https://github.com/run-llama/llama_index/blob/main/llama-index-core/llama_index/core/indices/property_graph/sub_retrievers/llm_synonym.py))
- LangChain: LLM extracts proper nouns → exact triplet lookup; CypherQA has no extraction step ([source](https://github.com/langchain-ai/langchain-community/blob/main/libs/community/langchain_community/chains/graph_qa/base.py))

---

## 3. Integration Strategy Ranking

| Rank | Strategy | Total Latency | Complexity | Recommendation |
|---|---|---|---|---|
| **1** | **Java-native: Aho-Corasick + OpenNLP chunker** | **2–4ms** | Low | ✅ **Recommended** |
| 2 | Python sidecar: spaCy EntityRuler + PROPN | 7–21ms | Medium (Docker container, HTTP) | Viable but unnecessary |
| 3 | Java-native: OpenNLP only (no trie) | 1–3ms | Low | Misses known-entity fast path |
| 4 | GraalPy (run spaCy in JVM) | Unknown | High | ❌ Not viable (C extensions) |

---

## 4. Recommended Architecture

### Two-layer extraction pipeline (Java-native)

```
User Question: "Where does Kelsier meet Vin in the Final Empire?"
        │
        ▼
  ┌─────────────────────────────────┐
  │  Layer 1: Aho-Corasick Trie     │  ← <1ms
  │  (all names + aliases, case-    │
  │   insensitive, pre-built per    │
  │   book from Neo4j)              │
  │                                 │
  │  Found: "Kelsier", "Vin",       │
  │         "the Final Empire"      │
  └─────────────┬───────────────────┘
                │
                ▼
  ┌─────────────────────────────────┐
  │  Layer 2: OpenNLP NP Chunker    │  ← 1–3ms (only if Layer 1 found 0 matches
  │  (fallback for unknown proper   │     AND query has capitalized words)
  │   nouns not in dictionary)      │
  │                                 │
  │  Extract NPs → Levenshtein-1    │
  │  against name dictionary        │
  └─────────────┬───────────────────┘
                │
                ▼
  ┌─────────────────────────────────┐
  │  EntityAnchors                  │
  │  - individuals: [Kelsier, Vin]  │
  │  - locations: [Final Empire]    │
  └─────────────────────────────────┘
```

### Component design

```java
// Insertion point: RagService.ask() → EntityAnchorResolver.resolve(question, bookId)

public record EntityAnchors(
    List<ResolvedIndividual> individuals,
    List<ResolvedLocation> locations
) {}

EntityAnchors resolve(String question, UUID bookId);
```

**Components:**
- `KnownEntityTrie` — Aho-Corasick automaton built from `BookIndividual.normalizedName` + `BookLocation.normalizedName` + aliases. Rebuilt on book entity changes.
- `OpenNlpNounPhraseExtractor` — POS tag + NP chunk using OpenNLP `en-pos-maxent` + `en-chunker` models. Thread-safe via `ThreadSafeChunkerME`.
- `QueryEntityExtractor` — Orchestrates: trie scan first → NP fallback if zero matches → Levenshtein-1 fuzzy match against dictionary for NP results.
- `EntityNameRepository` — Loads entity names + aliases from Neo4j, cached per book.

### Dependencies

```xml
<dependency>
    <groupId>org.ahocorasick</groupId>
    <artifactId>ahocorasick</artifactId>
    <version>0.6.3</version>
</dependency>
<dependency>
    <groupId>org.apache.opennlp</groupId>
    <artifactId>opennlp-tools</artifactId>
    <version>2.5.7</version>
</dependency>
```

### Re-ranking integration

In `SemanticSearchService`: boost search result score by `entityBoostPerMatch` (default 0.05) × entity overlap count between resolved anchors and each result's `individualsPresent`/`locationsPresent`.

---

## 5. Latency Budget

| Step | Latency | When |
|---|---|---|
| Aho-Corasick trie scan | <1ms | Always |
| OpenNLP POS + chunk | 1–3ms | Only on zero AC matches + capitalized words |
| Levenshtein-1 against 200 names | <1ms | Only on NP fallback path |
| **Total (happy path — AC hit)** | **<1ms** | ~80–90% of queries |
| **Total (fallback path)** | **2–4ms** | ~10–20% of queries |
| **50ms budget headroom** | **90%+** | |

---

## 6. Risk Assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| AC misses partial names ("Kel" for "Kelsier") | Medium | Levenshtein-1 fallback on NP chunks; consider prefix matching in trie |
| AC matches substring in unrelated word | Low | Verify match boundaries (word-boundary check post-match) |
| OpenNLP model cold-start latency | Low | Load models at app startup, not first query |
| Entity list changes mid-session | Low | Rebuild trie on entity CRUD operations; trie build is ~1ms for 200 patterns |
| Multi-word entity names ("the Lord Ruler") | Medium | Include multi-word names + aliases in trie; AC handles multi-word patterns natively |

### What we lose vs. Python NLP sidecar

- **Nothing meaningful.** spaCy's best strategy for our use case (EntityRuler + PROPN) is functionally identical to AC + OpenNLP, just in Python with sidecar overhead.
- The only capability Python adds is richer linguistic features (dependency parsing, etc.) — none of which we need for 5–30 word question entity extraction.

### Fallback if Java-native proves insufficient

If edge cases surface that need more sophisticated NLP:
1. First try: expand alias coverage in Neo4j (cheapest fix)
2. Second try: add spaCy sidecar for the specific failing queries (targeted, not wholesale)
3. Do NOT add a sidecar preemptively — YAGNI
