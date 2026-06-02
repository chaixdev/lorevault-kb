# Consolidated Advisory — April 2026

**Date:** April 2026
**Status:** Centralized advice from multi-agent review session
**Sources:** Oracle deep critique, two codebase exploration agents, orchestrator synthesis
**Purpose:** Single reference for all findings, recommendations, warnings, and next steps

---

## How to read this document

This document consolidates advice from multiple analysis sessions into one actionable reference. It is organized by decision area, not by source. Each section states what was found, what is recommended, and why.

**Companion documents:**
- [Conceptual model critique](2026-04-11T1210_concept-model-critique.md) — the structured 8-dimension critique
- [Oracle raw analysis](2026-04-11T1210_oracle-raw.md) — Oracle's unedited reasoning
- [Conceptual Concepts](../../concepts/) — the original concept docs under review

---

## 1. What to build next (staging)

### Recommended order

| Stage | What | Effort | Why this order |
|---|---|---|---|
| 0 | Fix Scene :Event label bug | Half day | Existing Cypher queries match `:Scene:Event` but nodes may lack the Event label. Blocks nothing but is a real correctness issue. |
| 1 | Entity extraction (Individual, Location, Collective) | Medium | The scene analysis LLM prompt already requests entities but `TriadStructuredResult` discards them. Capture what the LLM already returns. Highest value-per-effort ratio. |
| 2 | Entity-to-Scene/Chunk linking with evidence spans | Medium | Gives immediate retrieval value: "where does X appear," "who is in this scene," event participation. |
| 3 | Simple raw claim persistence (ascription + relation only) | Medium | Use the existing three-bin schema. Append-only. Evidence-backed. No aggregation math yet. |
| 4 | Minimal in-app catalog for core IDs | Small | Tiny registry of relation types and property IDs. Repo-versioned. No service, no UI. |
| 5 | Project a small set of high-value edges | Medium | `participated_in`, `located_in`, `member_of`. Only explicit, high-confidence. Keep raw claims off the query path. |
| 6 | Cross-chapter temporal placement | Deferred | Only after entity-linked retrieval is working and users still cannot answer chronology questions. |
| 7 | Richer temporal reasoning (landmarks, arcs, transitive) | Deferred indefinitely | Only if Stage 6 proves insufficient. |

### How this compares to the existing roadmap

`PROJECT-STATUS.md` lists four next-direction candidates:
1. Timeline modeling with Scene-as-Event entities
2. Spoiler-aware search using publication coordinates (already shipped)
3. Entity extraction (Characters, Locations, etc.)
4. Production hardening

The recommended staging **agrees** on starting with Scene-as-Event (Stage 0) and entity extraction (Stage 1-2). It **disagrees** on timing of temporal enrichment — the roadmap implies it's part of the first item, but the recommendation pushes it to Stage 6-7 because entity-linked retrieval delivers more value sooner.

### Why entities before temporal enrichment

The real user question — "What happened to character X between books 3 and 7?" — is not a temporal-algebra problem. It is an entity-linked event retrieval problem:
1. Which scenes involve X?
2. What happened in those scenes?
3. How should those events be ordered?

Without entity-to-scene links, the temporal DAG cannot answer this. With entity-to-scene links, publication order plus existing temporal edges is usually sufficient.

---

## 2. What to kill

### Permanent kills

| Item | Reason |
|---|---|
| **CDSL (Claim DSL)** | The concept docs already note it may be useless. It is. Human-readable claim notation adds no pipeline value. |
| **Ability as separate claim bin** | "Can fly" and "is telepathic" are just ascriptions with qualifiers. The fourth bin adds surface area without retrieval value. Three bins (Ascription, Relation, Comparison) are sufficient. |
| **SubstanceScore triage** | NE density / relation verb prefiltering adds tuning burden and risks dropping subtle but important evidence. Process all chunks. |

### Deferred kills (with revisit triggers)

| Item | Revisit when... |
|---|---|
| **Catalog microservice** | Provisionals pile up faster than manual review can handle |
| **Curation UI** | Same trigger as catalog microservice |
| **Formula-heavy confidence scoring** | Enough adjudicated examples exist to tune alpha/beta/gamma defensibly |
| **Landmark/Arc as first-class node types** | Users need in-universe chronology more than spoiler-aware publication order |
| **BM25 + vector hybrid catalog search** | Catalog exceeds ~100 entries where exact match + aliases is insufficient |
| **Cross-book temporal links** | A series with non-linear cross-book timelines proves the need |

---

## 3. Implementation-level findings (from codebase exploration)

### 3.1 Scene-as-Event: almost done, one real bug

**What exists:**
- `Scene implements Event` (Java interface with `getEventId()`, `getStartOffset()`, `getEndOffset()`)
- Neo4j constraints: `event_id_unique` on `(e:Event)`, index `event_per_chapter_scene_idx` on `(e:Event) ON (e.chapterId, e.sceneIndex)`
- Cypher queries in `TemporalGraphRepository` and `EventGraphRepository` match `(s:Scene:Event)`
- `TemporalEdgeWriteRepository` creates TEMPORAL edges with Allen relations, certainty, weight, evidence

**The bug:** `Scene` is annotated `@Node("Scene")` with `@DynamicLabels List<String> labels`, but no code populates the `"Event"` label into that list at creation time. The Neo4j schema initializer creates constraints for `:Event` and some queries match `:Scene:Event`, but scenes in the database may only carry the `:Scene` label.

**Fix:** Add `"Event"` to Scene's dynamic labels in constructors / `@PersistenceCreator`.

### 3.2 Temporal property naming inconsistency

- Java `TemporalEdge` has field `temporalRelation` (enum `TemporalRelation`)
- Cypher `upsertTemporalEdge` stores as `t.type = $type` (a String parameter)
- Default edge creation uses `t.type = 'R:temporal.meets'` and `t.confidence = 0.5`
- `TriadEdgePersistenceService` passes type as String, certainty as String, maps certainty to weight

**Recommendation:** Standardize on either the Java field name or the Cypher property name. Consider accepting `TemporalRelation` enum directly in the repository method.

### 3.3 Event interface is skeletal

Only 3 methods: `getEventId()`, `getStartOffset()`, `getEndOffset()`. If other event types are ever added (or if timeline code needs to generically access `chapterId`/`sceneIndex`), the interface needs extension.

**Recommendation:** Consider adding `getChapterId()` and `getSceneIndex()` if timeline code should treat Event generically. Or leave minimal if Scene remains the only Event type.

### 3.4 Scene analysis prompt already requests entities — code ignores them

The `scene-analysis.txt` system prompt explicitly asks the LLM to extract entities (individuals, collectives, objects, locations, concepts, events) for each scene. But `TriadStructuredResult` only captures `timelineMarker`, `previousToCurrent`, and `currentToNext` — the entity data is discarded.

**This is the single biggest low-hanging fruit.** Expanding `TriadStructuredResult` to capture entity mentions would give you entity extraction almost for free, using an LLM call you already make.

### 3.5 Triad analysis pipeline (complete map)

```
TriadBuilderService.buildTriadsForChapter(chapter)
  → resolves cross-chapter previous scene (last scene of prior chapter)
  → produces SceneTriad(previous, current, next) objects

TriadOrchestrationService.analyzeTriads(chapter, triads)
  → for each triad:
    → builds user variables from scene-analysis-usertemplate.xml
    → loads system prompt from scene-analysis.txt
    → calls SceneDetectionClient.detectSceneAnalysisTriad(...)
    → validates TriadStructuredResult (temporal relations must be present)
    → inverts prev→curr relation via TriadRelationInverter
    → produces TriadAnalysis records

TriadEdgePersistenceService.applyTriadAnalyses(analyses)
  → for each analysis:
    → maps certainty string to weight (Explicit=0.9, StronglyImplied=0.7, WeaklyImplied=0.5, Heuristic=0.3)
    → calls TemporalEdgeWriteRepository.upsertTemporalEdge(from, to, type, certainty, weight, "ai-scene-analysis-triad", evidence, ...)
```

### 3.6 Default temporal edge creation

Two Cypher methods in `TemporalEdgeWriteRepository`:

**In-chapter defaults:** For each book, creates `MEETS@0.5` edges between consecutive scenes within each chapter. Guarded by `WHERE NOT EXISTS { MATCH (later)-[:TEMPORAL*1..50]->(earlier) }` to prevent cycles.

**Cross-chapter defaults:** Links last scene of chapter N to first scene of chapter N+1 within the same book. Same cycle guard pattern with path length up to 500.

### 3.7 Event ordering (topological sort)

`EventOrderingService.orderChapterEvents(chapterId)`:
- Builds adjacency list from `[:MEETS|TEMPORAL]` edges within a chapter
- Runs Kahn's algorithm with tie-breaking by `sceneIndex` then UUID
- Falls back to comparator ordering if cycles detected

`EventOrderingService.orderBookEventsUpToChapter(bookId, uptoChapterNumber)`:
- Simply **concatenates** per-chapter orderings by chapter number
- No global topological sort across chapters

### 3.8 Cross-chapter and cross-book temporal links

- **Cross-chapter (within book):** Two mechanisms exist:
  1. Default MEETS edges between last/first scenes of adjacent chapters
  2. Triad analysis can cross one chapter boundary (TriadBuilderService includes previous chapter's last scene)
- **Cross-book:** No code creates temporal links across different books. All cross-chapter logic is book-scoped.

### 3.9 No transitive closure or global graph algorithms

- No stored transitive closure or precomputed reachability in Java code
- Only bounded variable-length path existence checks in Cypher (write-time cycle guards)
- No SCC detection, transitive reduction, or full-graph reachability

---

## 4. Conceptual model advice

### 4.1 Entity types: keep six, discipline Concept

The six entity kinds (Individual, Collective, Object, Location, Concept, Event) cover narrative fiction well. Keep them. But enforce mandatory subtyping inside Concept (`species`, `material`, `role`, `belief`, `power`) with strict rules about when something is a Concept versus an Object, Collective, or Event. Without this discipline, Concept becomes a junk drawer.

### 4.2 Three-bin claims are enough

Ascription, Relation, Comparison. The Ability bin adds nothing — collapse it permanently.

### 4.3 Fact scoping is the biggest conceptual gap

`pubCoords` captures when the reader learns something, not when it is true in-world. Many fiction facts are time-bounded, viewpoint-bounded, or event-scoped. Publication time and story time must stay distinct in both storage and query behavior. This is not solved by the current concept docs and will need addressing when claims are implemented.

### 4.4 Identity policy needs upfront design

Aliases, titles, disguises, reincarnations, split identities, merged beings — identity mistakes compound. A bad merge is worse than a missed entity. Bias toward under-merging. Design the identity model before implementing entity resolution, not after.

### 4.5 LLM extraction: keep it simple

- Do not trust model-generated certainty floats. Use enum buckets: `explicit`, `implied`, `speculative`.
- Phase 1 extraction should produce plain-language descriptions + evidence spans. Do not ask the LLM to be a taxonomy steward.
- Extract-then-map is the right shape, but only with a very small target vocabulary.
- If catalog mapping fails often, that signals catalog immaturity, not extraction failure.

### 4.6 Catalog: boring in-app data

Start as a small table/file-backed registry inside the Spring app. Versioned in the repo. If it fits in a markdown file, it is too small for a service. Exact match + aliases + simple fuzzy matching before any embedding-based search.

### 4.7 Confidence: count before you compute

Start with support count, contradiction count, strongest source tier, earliest evidence, explicit-vs-implied class. Map to coarse statuses (`supported`, `contested`, `weak`, `denied`). The sigmoid endorsement formula can come later when you have enough adjudicated examples to tune it defensibly. Confidence math should follow evaluation, not precede it.

### 4.8 Graph architecture: keep claims off the hot path

Neo4j is the right home for content hierarchy, scene/event backbone, projected entity relations, and spoiler-aware traversal. Raw claims are tolerable at MVP scale but must stay off the primary query path. If every user query has to traverse provenance-heavy subgraphs, the model stops paying for itself.

---

## 5. Warnings and watch-outs

### Identity mistakes compound
A bad merge is worse than a missed entity. Bias toward under-merging. Do not attempt full autonomous entity resolution across books on day one.

### Publication time is not story time
Keep them separate in the model and in query semantics. `pubCoords` tells you when the reader learns something. Story-time anchoring for claims is a separate axis that the current concept under-specifies.

### Projection can destroy trust
If weak claims become graph facts too early, users will stop believing the system. Project only explicit, high-confidence edges.

### False precision in certainty scores
LLMs output `0.73` but don't know the difference between `0.73` and `0.58`. Use enum buckets, not floats.

### Semantic sprawl is worse than scale
The node/edge count for a 20-book series is fine for Neo4j. The risk is a graph that is technically manageable but mentally expensive and operationally noisy.

### Triad analysis provides modest incremental value
Within a chapter, publication order already gives a strong MEETS assumption. The real value gap is cross-chapter temporal placement and entity-anchored retrieval — both require entities first.

---

## 6. Escalation triggers

These are signals that a deferred item needs attention:

| Signal | Action |
|---|---|
| Provisionals pile up faster than manual review | Add lightweight in-app catalog tooling |
| Users need in-universe chronology more than publication order | Invest in cross-chapter temporal placement |
| Provenance-heavy claim storage slows core queries | Move raw claims off Neo4j hot path |
| Chronology-heavy questions remain unanswered after entity linking | Evaluate Landmark/Arc nodes and richer temporal reasoning |
| Catalog exceeds ~100 entries | Consider embedding-based catalog search |
| Cross-book timelines prove necessary | Add cross-book temporal linking |

---

## 7. Open questions (not yet answered)

These emerged from the review but were not resolved:

1. **Query contract:** What exact user questions must this model answer better than chunk-only RAG? Without this, there is no evaluation criteria for whether entity/claim extraction is working.

2. **Gold set:** There is no labeled evaluation data for extraction quality. When entity extraction is implemented, what does "correct" look like? Manual review of first N chapters may be the practical answer.

3. **N-ary facts:** Many narrative facts don't fit clean binary edges. The qualifier bag approach works but is awkward. Is there a better pattern for "X gave Y to Z at location W during event E"?

4. **Event interface scope:** Should `Event` stay skeletal (3 methods) anticipating Scene as the only implementation, or should it be extended for future event types? The answer depends on whether non-Scene events (standalone, cross-scene) are ever needed.

5. **Cross-chapter ordering strategy:** Currently, cross-chapter ordering is naive concatenation by chapter number. Is a global topological sort across chapters needed? Or is chapter-number ordering sufficient when combined with entity-linked retrieval?

---

## Related Documents

- [Conceptual model critique](2026-04-11T1210_concept-model-critique.md) — structured 8-dimension analysis
- [Oracle raw analysis](2026-04-11T1210_oracle-raw.md) — unedited Oracle reasoning
- [Concepts](../../concepts/) — original concept docs under review
- [Project Status](../../PROJECT-STATUS.md) — current implementation state
