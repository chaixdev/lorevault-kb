# Conceptual Model Critique — April 2026

**Date:** April 2026
**Status:** Review of pre-implementation conceptual model (~1 year after design, before next implementation phase)
**Source:** Deep architectural review (Oracle consultation + codebase exploration + synthesis)
**Scope:** All concept docs in `docs/concepts/`, compared against current implementation state

---

## Context

LoreVault's conceptual model — entity types, claim bins, event DAG, confidence aggregation, catalog/taxonomy, CDSL — was designed roughly a year before the first line of code. The implementation has since shipped: content hierarchy, ingestion pipeline, triad-based temporal analysis, spoiler-aware search, and RAG. This critique evaluates which parts of the concept remain sound, which should be dropped, and what the correct implementation order should be.

**Team size assumption:** 1 person + AI.

---

## A. Model Coherence

### Entity types: mostly sound

The six entity kinds (Individual, Collective, Object, Location, Concept, Event) are a reasonable upper ontology for narrative fiction. Broad enough without exploding the root taxonomy.

**Weakness: Concept is a junk drawer.** Species-as-Concept works. So do materials, roles, ideologies, magic systems, social classes. But if Concept becomes the fallback for "anything unclear," semantic sharpness erodes. The fix is not more root kinds — it is mandatory subtyping/tagging inside Concept (`species`, `material`, `role`, `belief`, `power`, etc.) and strict rules about when something is a Concept versus an Object, Collective, or Event.

### Claim bins: three is enough, four is not worth it

The concept docs oscillate between 3 and 4 bins:
- `claims.schema.json` has three: Ascription, Relation, Comparison
- Research docs describe four: Attribute, Relation, Comparison, Ability
- `core-domain-model` uses three and notes Ability is just an Ascription with qualifiers

**Verdict: Kill the Ability bin permanently.** "Can fly," "is telepathic," "can wield shardblades" are just properties or capability relations with qualifiers. The fourth bin adds surface area without retrieval value.

### Major gap: fact scoping

Many fiction facts are not timeless binary truths:
- "X is king of Y" — time-bounded
- "X believes Y is dead" — viewpoint-bounded
- "X appears weak" — perception-bounded
- "X was in city Z during event E" — event-scoped

The current concept talks about provenance and publication coordinates but under-specifies **valid-time / event anchoring** for claims. `pubCoords` tells you when the reader learns something, not necessarily when it is true in-world. Publication time and story time are different axes and must stay distinct in both storage and query behavior.

### Other under-thought areas

- **Identity policy:** aliases, titles, disguises, reincarnations, split identities, merged beings — identity mistakes compound; a bad merge is worse than a missed entity
- **N-ary facts:** many facts are not clean binary edges without awkward qualifier bags
- **Evaluation:** no clear gold set for "good extraction" or "useful query answer"
- **Query contract:** what exact user questions must this model answer better than chunk-only RAG?

---

## B. LLM Extraction Realism

Current LLMs can extract **explicit, scene-local entities and direct relations** from prose reasonably well. They are much less reliable at:

- Calibrated numeric certainty
- Source attribution in dialogue / free indirect discourse
- Polarity vs absence
- Implicit comparisons
- Consistent qualifier structure across scenes/books

### False precision is the biggest trap

A model can output `certainty: 0.73`, but that does not mean it knows the difference between 0.73 and 0.58. Do not trust model-generated floats. Use a small enum or 3-level bucket (`explicit`, `implied`, `speculative`) and keep the original evidence span.

### Two-phase vocabulary: right shape, narrow scope

The extract-then-map approach is realistic **only if phase 1 stays simple**:
- "Extract a plain-language relation/property description plus evidence span" — plausible
- "Extract rich structured claims with reliable source, polarity, qualifiers, and ontology-ready wording" — quality drops fast

The model should not be asked to both understand prose and behave like a taxonomy steward simultaneously. If mapping fails often, that is useful signal — it means the catalog is not mature enough yet.

---

## C. Catalog Feasibility

A one-person team does **not** need a catalog microservice, hybrid retrieval layer, and curation UI.

You do need a catalog conceptually, but it should start as **boring in-app data**: a small table/file-backed registry of relation types, core properties, and maybe concept subtypes. Inside the Spring app, versioned in the repo, editable without new infrastructure.

BM25 + vector hybrid search is overkill initially. With 30-80 relation/property entries, exact match + aliases + simple fuzzy matching is enough. Embeddings become useful only when the catalog is large enough that humans cannot manage the drift manually.

**Rule of thumb:** if the catalog is small enough to review in a markdown file, it is too small for a service.

---

## D. Confidence Formula Realism

The proposed formula is elegant and premature:

```
support = Sigma(sourceReliability x certainty x evidenceQuality)
deny    = Sigma(sourceReliability x certainty x evidenceQuality)
confRaw = alpha * ln(1+support) - beta * ln(1+deny) + gamma * avg(sourceReliability)
confidence = sigmoid(confRaw)
```

With one fictional universe, you will not have enough labeled review data to tune `alpha`, `beta`, `gamma`, source reliabilities, projection thresholds, and evidence-quality penalties defensibly. You will have numbers, but they will mostly encode intuitions, not validated behavior.

### Start with cheap, interpretable signals instead

- Support count
- Contradiction count
- Strongest source tier
- Earliest evidence
- Explicit vs implied evidence class

Map those into coarse statuses: `supported`, `contested`, `weak`, `denied`. That is testable by spot review. A sigmoid-based endorsement model can come later if you accumulate enough adjudicated examples to justify it.

**Confidence math should follow evaluation, not precede it.**

---

## E. Graph Explosion Risk

For one 20-book series, the raw scale is manageable:
- Low thousands of scenes
- Tens of thousands of chunks
- Hundreds to low thousands of canonical entities
- Tens to low hundreds of thousands of raw claims
- Similar order of magnitude for evidence/projection edges if you stay sparse

Neo4j can handle that.

### The real risk: write amplification and semantic sprawl

If every claim becomes a node, plus support/deny aggregates, plus projected edges, plus entity-resolution evidence, plus temporal edges — you get a graph that is technically manageable but mentally expensive and operationally noisy.

### Neo4j is the right home for:
- Content hierarchy
- Scene/event backbone
- Projected entity/event relations
- Spoiler-aware query traversal

### Tolerable for MVP:
- Raw claims at small scale

### Critical rule:
Raw claims must stay **off the hot query path**. The moment every user query has to traverse provenance-heavy subgraphs, the model stops paying for itself.

---

## F. Event DAG: Sparsity vs Utility

### The sparse DAG idea is good

The current implementation reflects the right instinct: local edges, no dense transitive closure, scene-as-event, certainty carried on temporal relationships.

### But triad-only is not enough for real user questions

"What happened to character X between books 3 and 7?" — this is mostly not a temporal-algebra problem. It is an **entity-linked event retrieval** problem:

1. Which scenes/events involve X?
2. What happened in those scenes?
3. How should those events be ordered for the reader?

Without entity-to-scene/event links and event summaries, the DAG adds little. In practice, the best answer path is:
- Filter by publication range
- Retrieve scenes/events involving X
- Use explicit temporal edges where available
- Otherwise fall back to publication order / scene order

That is much more robust than betting the product on long-range Allen composition over uncertain edges.

### Landmarks and Arcs are especially suspect

Analytically nice, but not the next thing users need. Defer indefinitely.

### Current triad analysis provides modest value

Within a chapter, publication order already gives you a strong MEETS assumption. The triad analysis occasionally upgrades that to OVERLAPS or DURING (for flashbacks, parallel scenes), but for most narrative fiction, consecutive scenes within a chapter *are* temporal neighbors. The real value gap is:

1. **Cross-chapter temporal placement** — "where does this flashback scene actually sit on the story timeline?"
2. **Entity-anchored retrieval** — "show me all scenes where Character X appears, ordered by story time" (requires entities first)

---

## G. Kill List

### Kill now, probably forever

| Item | Reason |
|---|---|
| **CDSL** | The docs already say it may be useless. They are right. |
| **Ability as separate bin** | Collapse into Ascription. Adds surface area without retrieval value. |
| **SubstanceScore triage** | Adds tuning burden and risks dropping subtle but important evidence. |

### Kill until proven necessary

| Item | Revisit trigger |
|---|---|
| **Catalog microservice** | Provisionals pile up faster than manual review can handle |
| **Curation UI** | Same trigger as catalog microservice |
| **Formula-heavy confidence scoring** | Enough adjudicated examples exist to tune parameters |
| **Landmark/Arc as first-class nodes** | Users need in-universe chronology more than spoiler-aware publication order |

---

## H. Staging Critique

### Proposed order (from user):
1. Events as first cardinal entity
2. DAG enrichment (more temporal edges, landmarks, arcs)
3. Add other cardinal entities (Individual, Location, etc.)
4. Claims as an aggregate soft link

### Problems with this order

The first step is fine but already mostly done. The second step (DAG enrichment) should not come before entity extraction — entities are where the value is. The third step is too late; entities should come second. Claims should come after entities are linked to scenes.

### Recommended order

1. **Stabilize Scene-as-Event** — fix the Neo4j label mismatch (Scene nodes don't carry :Event label despite queries expecting it), standardize temporal property naming, possibly extend Event interface. This is a half-day task, not a stage.

2. **Entity extraction** — Individual, Location, Collective first. The scene analysis LLM prompt already requests entities but the code ignores them (`TriadStructuredResult` only captures temporal relations). Low-hanging fruit: capture what the LLM already returns.

3. **Entity-to-Scene/Chunk linking** — connect extracted entities to their scenes with evidence spans. Gives immediate value for "where does X appear" and "who is in this scene."

4. **Simple raw claim persistence** — use the existing three-bin schema. Start with ascriptions and relations only. Store source role, evidence span, coarse certainty bucket. Keep append-only.

5. **Minimal in-app catalog** — tiny registry for core relation/property IDs. In-process, repo-versioned. Manual review beats service architecture.

6. **Project a small set of high-value edges** — `participated_in`, `located_in`, `member_of`, maybe a few durable properties. Only explicit, high-confidence edges onto the main graph.

7. **Richer temporal reasoning** — only after entity-linked retrieval is working and users still cannot answer chronology-heavy questions. That is the point to test whether cross-chapter placement, landmarks, or arcs are actually needed.

---

## Current Implementation State (for reference)

### What exists (built and working):
- Content hierarchy: Universe -> Series -> Book -> Chapter -> Scene -> Chunk
- `Scene implements Event` (Java interface)
- Neo4j constraints/indexes for `:Event` label
- TEMPORAL edges with full Allen relations, certainty, weight, evidence
- Triad analysis pipeline (TriadOrchestrationService -> SceneDetectionClient -> TriadEdgePersistenceService)
- Cross-chapter default MEETS edges (last scene of ch N -> first scene of ch N+1)
- Per-chapter topological sort (Kahn's algorithm in EventOrderingService)
- Cycle guards via bounded path-existence Cypher checks
- Spoiler-aware query gating
- Vector search and RAG

### What does NOT exist:
- Cardinal entity types beyond Scene/Chunk
- Claim nodes or persistence
- Catalog/taxonomy
- Entity resolution
- Confidence aggregation
- Edge materialization from claims
- Cross-book temporal links

### Known implementation gaps in existing code:
- Scene annotated `@Node("Scene")` but does not actually add `:Event` label to DB nodes (DynamicLabels not populated)
- Some Cypher queries match `(s:Scene:Event)` — may return nothing if Event label missing
- Property naming mismatch: Java `temporalRelation` (enum) vs Cypher `type` (string)
- Event interface is skeletal (3 methods only)
- Scene analysis prompt requests entity extraction but code discards the results

---

## Summary

The conceptual model is intellectually strong but over-scoped for a one-person team. The foundation in code is solid. The winning path forward is:

1. Keep the sparse event backbone
2. Add high-value entity types next (Individual, Location, Collective)
3. Store simple evidence-bearing claims
4. Drop most of the ontology/governance machinery until real query pain proves it is needed

The architecture should stay simple enough for one person to reason about and maintain, while preserving the option to grow into richer claims and confidence later.

---

## Related Documents

- `docs/concepts/` — the concept docs reviewed here
- `docs/PROJECT-STATUS.md` — current implementation state


