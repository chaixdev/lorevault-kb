# Event Entity Extraction — External Research (Verbatim) — April 2026

**Date:** April 2026  
**Status:** Raw preserved research — not canonical truth  
**Purpose:** Preserve high-value external research outputs verbatim from librarian tasks so they remain available for later design and promotion work.

---

## Notes

- This document is intentionally a preservation note, not a cleaned-up proposal.
- The goal is to keep strong external reports intact before they are distilled into a proposal, concept, ADR, or pattern.
- Content below should remain clearly attributable to the originating research task.
- The active working synthesis for these reports lives in `event-entity-extraction-proposal-april-2026.md` in the same folder.

---

## Librarian report 1 — Hierarchical entity graph examples

Origin task: `bg_9e3d2d3a`  
Task status: timed out after inactivity, but the compiled report was recovered from session output.

```text
# External Analogues Survey: Mention → Aggregate → Canonical/Resolved Entity Ladders

This survey covers six strong external analogues for LoreVault's proposed `ResolvedEntity`/`ResolvedEvent` ladder, drawn from production knowledge graph systems, academic pipelines, and narrative graph research. Each section documents the pattern, its provenance strategy, canonical identity approach, query entrypoint design, and relinking/rebuild tradeoffs.

---

## 1. TRACE-KG — The Closest Direct Analogue

**Source:** [arxiv.org/html/2604.03496v1](https://arxiv.org/html/2604.03496v1) (Arizona State University, April 2026)

### The Ladder

TRACE-KG defines an explicit three-tier structure that maps almost exactly onto LoreVault's proposed design:

```
Tier 1 (Leaf):   EntityMention  m ∈ M
                   ├── span(m)         — text span in source chunk
                   ├── chunk(m)        — provenance back-pointer
                   ├── name, description, type_hint
                   └── confidence + justification excerpt

Tier 2 (Scope):  [implicit — chunk/document boundary]

Tier 3 (Canon):  ResolvedEntity  e ∈ E
                   ├── canonical name + description
                   ├── entity class + class group (induced schema)
                   ├── confidence score
                   ├── aggregated provenance → {chunk_id, ...}
                   └── intrinsic properties (stable attributes)
```

**Key design decision:** The final graph `G = (E, R, S)` is built over **resolved entities only**. Mentions are not graph nodes in the final KG — they are intermediate artifacts retained in JSONL for traceability. The resolved entity is the query entrypoint.

**Provenance preservation:** Every resolved entity carries `aggregated_provenance` — a set of chunk identifiers from which its mentions were drawn. Every relation instance also carries provenance. The paper explicitly states: *"Traceability is preserved throughout the pipeline: every node and edge maintains explicit links to supporting chunk identifiers, and all resolution edits are recorded as structured, auditable actions."*

**Relinking/rebuild cost:** TRACE-KG uses **iterative resolution** with constrained action interfaces (LLM function-calling). The resolution stage runs multiple passes until convergence. Critically, intermediate JSONL artifacts are persisted at each stage — so a re-run can resume from any tier without re-extracting mentions. This is staged reduction, not full rebuild.

**Tradeoff explicitly named:** Over-merging semantically distinct entities introduces incorrect relations; under-merging fragments evidence. The system uses embedding-based clustering + LLM-guided selection to balance this. No automatic merge is irreversible — the audit trail is kept.

**Analogy to LoreVault:** TRACE-KG's `EntityMention` ≈ LoreVault's `CharacterMention`; its `ResolvedEntity` ≈ LoreVault's proposed `ResolvedEntity`. The scoped aggregate (Chapter/Book) is implicit in TRACE-KG via chunk provenance, but LoreVault makes it explicit — which is actually stronger for hierarchical querying.

---

## 2. Wikidata Q-Item Architecture — Canonical Identity at Scale

**Sources:** [wikidata.org SPARQL docs](https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service/WDQS_graph_split/Federated_Queries_Examples), [Hernandez et al. reification paper](https://aidanhogan.com/docs/reification-wikidata-rdf-sparql.pdf), [DBpedia/Wikidata integration paper](https://www.semantic-web-journal.net/system/files/swj1518.pdf)

### The Ladder

```
Tier 1 (Evidence):  Statement node  (reified triple)
                      ├── subject QID
                      ├── predicate PID
                      ├── object (QID or literal)
                      ├── qualifiers  (temporal, spatial, rank)
                      └── references  (source URLs, retrieved dates)

Tier 2 (Sitelinks): Wikipedia article per language
                      └── schema:about → QID  (provenance anchor)

Tier 3 (Canon):     Q-item  (e.g., Q42 = Douglas Adams)
                      ├── stable opaque identifier (never changes)
                      ├── labels + aliases (multilingual)
                      ├── instance_of (P31)
                      └── external identifiers (ORCID, LEI, etc.)
```

**Canonical identity:** The QID is the query entrypoint. All retrieval systems resolve a label/alias → QID, then traverse properties and references from there. The QID never changes even if the entity is renamed or merged. Sitelinks are the provenance layer connecting the canonical node to human-verified sources.

**Provenance preservation:** Wikidata uses **statement reification** — each claim is a first-class node with qualifiers (e.g., `start_time`, `end_time`) and references (source URLs). This means a statement like "Douglas Adams was born in Cambridge" carries its own evidence chain without polluting the canonical Q-item node.

**Relinking cost:** When two Q-items are merged (e.g., duplicate entities discovered), Wikidata creates a **redirect** from the deprecated QID to the surviving one. All sitelinks and statements are migrated. The redirect is permanent and queryable. This is the production-scale answer to "what happens when canonical identity changes."

**Query entrypoint design:** SPARQL queries always start from the Q-item. The statement layer is only traversed when provenance or qualifiers are needed. This two-speed access pattern — fast canonical lookup, slower provenance drill-down — is a deliberate architectural choice.

**Tradeoff:** Wikidata's reification scheme is verbose and query-expensive. The WDQS graph split (2024–2026) was partly motivated by the cost of querying across canonical and provenance layers simultaneously. LoreVault should note: if provenance queries are rare, keeping them in a separate traversal path (not inline on the canonical node) is the right call.

---

## 3. Linked Art / CIDOC-CRM — Event-Centric Provenance with Canonical Entities

**Sources:** [linked.art/model/provenance](https://linked.art/model/provenance), [linked.art/api/1.0/endpoint/event](https://linked.art/api/1.0/endpoint/event), [cidoc-crm.org](https://cidoc-crm.org)

### The Ladder

CIDOC-CRM and its Linked Art application profile use a fundamentally **event-centric** model where canonical entities (Person, HumanMadeObject, Place) are stable nodes, and all change/provenance is captured as events:

```
Tier 1 (Evidence):  ProvenanceActivity  (E7_Activity)
                      ├── part → Acquisition (title transfer)
                      ├── part → Payment
                      ├── timespan
                      ├── took_place_at → Place
                      └── carried_out_by → Person/Group

Tier 2 (Lifecycle): Production → [Object] → Destruction
                      (object's full event chain)

Tier 3 (Canon):     HumanMadeObject / Person / Group
                      ├── stable URI (dereferenceable endpoint)
                      ├── identified_by → Name/Identifier
                      ├── classified_as → Type (Getty AAT)
                      └── subject_of → TextualWork (descriptions)
```

**Key design decision:** The canonical entity node is **immutable** — it holds stable identity. All change (ownership, location, condition) is modeled as events that *reference* the canonical node, not as mutations of it. This is the CIDOC-CRM principle of **endurant vs. perdurant** separation: entities endure through time; events perdure (happen and are done).

**Provenance preservation:** Every provenance event is a first-class node with its own URI, time, place, and participants. The chain of events is queryable independently of the canonical entity. The Linked Art API exposes a dedicated `ProvenanceActivity` endpoint — provenance is not a property of the object, it is a separate graph of events that reference the object.

**Query entrypoint:** The canonical entity (e.g., `https://linked.art/example/object/spring`) is the entrypoint. From it, you can traverse `produced_by`, `transferred_title_of` (via provenance events), `current_owner`, etc. The provenance chain is navigated by following event references, not by reading the canonical node directly.

**Relinking cost:** Because events reference canonical entities by URI, relinking is cheap — you update the event's reference, not the canonical node. If a canonical entity is split or merged, the event chain is re-pointed. The canonical node itself rarely changes structure.

**Analogy to LoreVault:** CIDOC-CRM's `E7_Activity` ≈ LoreVault's `ChapterMention` or `SceneEvent`. The canonical `Person` node ≈ `ResolvedEntity`. The key insight: **events are the evidence layer; canonical entities are the query entrypoint**. LoreVault's proposed ladder follows this pattern correctly.

---

## 4. GOLEM Knowledge Graph — Fiction-Specific Character Stoff Pattern

**Source:** [ceur-ws.org/Vol-3834/paper80.pdf](https://ceur-ws.org/Vol-3834/paper80.pdf) (GOLEM project, 2024)

### The Ladder

GOLEM (Graph Ontologies for Literary Evolution Models) models fanfiction narratives and explicitly distinguishes two character representations:

```
Tier 1 (Instance):  gc:G1_Character  (crm:E89_Propositional_Object)
                      └── character as it appears in ONE specific story
                          (story-scoped, carries narrative context)

Tier 2 (Canon):     gc:G0_Character-Stoff  (crm:E28_Conceptual_Object)
                      └── the abstract "character idea" that appears
                          across all versions/books/fandoms
                          (cross-story canonical identity)
```

The German term **Stoff** (literary material/substance) is used deliberately — it refers to the underlying conceptual entity that persists across all instantiations of a character across different works.

**Canonical identity:** `G0_Character-Stoff` is the query entrypoint for cross-story questions ("How does Hermione appear across all Harry Potter fanfics?"). `G1_Character` is the entrypoint for story-specific questions ("What does Hermione do in this specific fic?").

**Provenance preservation:** The `G1_Character` instance carries story-specific attributes (role, relationships, narrative arc within that story). The `G0_Character-Stoff` aggregates across instances. The relationship between them is explicit: `G1_Character` → `is_instance_of` → `G0_Character-Stoff`.

**Relinking cost:** Adding a new story creates new `G1_Character` instances and links them to existing `G0_Character-Stoff` nodes. The canonical Stoff node is not rebuilt — it is extended by new instance links. This is **incremental extension**, not rebuild.

**Tradeoff:** The Stoff node can become a "god node" if too many instances link to it without further structure. GOLEM mitigates this by keeping the Stoff node lightweight (conceptual identity only) and pushing all narrative detail to the instance layer.

**Analogy to LoreVault:** `G0_Character-Stoff` ≈ LoreVault's `ResolvedEntity`. `G1_Character` ≈ LoreVault's `BookAggregate` or `ChapterMention`. The GOLEM pattern is the closest fiction-domain analogue to LoreVault's proposed ladder.

---

## 5. E²RAG (Entity-Event RAG) — Narrative Graph with Deliberate Non-Merging

**Source:** [aclanthology.org/2026.eacl-long.90.pdf](https://aclanthology.org/2026.eacl-long.90.pdf) (EACL 2026)

### The Ladder

E²RAG takes a **deliberately different** approach to the merge question — it argues that for narrative fiction, merging entity mentions is harmful:

```
Entity subgraph  Gent = (Vent, Eent)
  └── Each node = one entity MENTION with context-specific description
      (deliberately NOT merged across story positions)

Event subgraph   Gevt = (Vevt, Eevt)
  └── Each node = one event with trigger, description, chunk_id

Bipartite edges  B ⊆ Vent × Vevt
  └── (mention, event) edge when mention appears in event's chunk
```

**Key design decision:** *"Instead of collapsing duplicates, we first extract both entities and their events... we never merge mentions that arise in different parts of the story, each entity node carries its own context-specific description."*

**Why this matters for LoreVault:** E²RAG is the counter-argument to aggressive canonical merging. For narrative fiction where a character's state evolves (Frodo at the Shire vs. Frodo at Mount Doom), merging all mentions into one canonical node loses temporal/causal context. E²RAG's answer is to keep mentions distinct and use the bipartite event graph for retrieval.

**The tradeoff explicitly stated:** E²RAG achieves better temporal-causal consistency in RAG but at the cost of cross-story identity queries. You cannot easily ask "who is this character across all books" without a separate canonical layer. This is precisely the gap that LoreVault's `ResolvedEntity` fills — E²RAG shows what you lose if you skip it, and what you gain if you keep the mention layer.

**Analogy to LoreVault:** E²RAG's mention nodes ≈ LoreVault's `CharacterMention`. E²RAG has no canonical layer — which is the gap LoreVault's `ResolvedEntity` is designed to fill. E²RAG's event nodes ≈ LoreVault's `SceneEvent` or `ChapterEvent`.

---

## 6. Stardog EntityMatch — Non-Destructive Canonical Grouping

**Source:** [docs.stardog.com/entity-resolution](https://docs.stardog.com/entity-resolution/)

### The Pattern

Stardog's entity resolution deliberately avoids merging source nodes. Instead it creates a **canonical grouping node**:

```
:Sebestian_Vincent  ──entityMatch──>  :MatchGroup_001
:Sebestn_Vincent    ──entityMatch──>  :MatchGroup_001
:S_Vincent          ──entityMatch──>  :MatchGroup_001

:MatchGroup_001  a  stardog:EntityMatch
  ├── hasEntityMatchInfo → score nodes (pairwise similarity)
  └── metadata (timestamp, query, user, config)
```

**Key design decision:** *"Entity resolution does not modify the existing data and writes the results to the provided target graph."* The source nodes are untouched. The canonical grouping is a separate overlay graph.

**Query entrypoint:** You query the `EntityMatch` node to find all equivalent entities, then traverse to whichever source node you need. The match group is the canonical identity node; the source nodes are the evidence/provenance layer.

**Relinking cost:** Because the canonical grouping is in a separate named graph, it can be rebuilt without touching source data. Re-running entity resolution produces a new target graph. This is the cleanest separation of canonical identity from source provenance.

**Tradeoff:** The `EntityMatch` node is a thin grouping node — it carries no synthesized attributes. If you want a canonical description or merged properties, you must compute them separately. Stardog leaves that to the user (via SPARQL UPDATE). LoreVault's `ResolvedEntity` is richer — it should carry synthesized canonical attributes, not just a grouping pointer.

---

## Cross-Cutting Principles and Tradeoffs

### Principle 1: Canonical Node = Query Entrypoint, Not Storage Node

Every strong analogue (Wikidata Q-item, Linked Art Person, GOLEM Stoff, TRACE-KG ResolvedEntity) uses the canonical node as the **entry point for queries** while keeping evidence/provenance in lower layers. The canonical node is lightweight — it holds stable identity and synthesized attributes, not raw evidence.

**LoreVault implication:** `ResolvedEntity` should be the entrypoint for cross-book/cross-series queries. `CharacterMention` and `BookAggregate` are the evidence/provenance layer. This is the correct direction.

### Principle 2: Provenance Must Be a First-Class Graph Citizen

Wikidata uses statement reification. Linked Art uses event nodes. TRACE-KG uses chunk provenance on every node. GOLEM uses instance-to-Stoff links. In every case, provenance is not a property — it is a traversable graph structure.

**LoreVault implication:** The `[:MENTIONED_IN {chapter, page, context}]` edge pattern is correct. Do not flatten provenance into node properties.

### Principle 3: Incremental Extension, Not Full Rebuild

GOLEM adds new `G1_Character` instances without rebuilding `G0_Character-Stoff`. Wikidata adds new statements without changing Q-items. Stardog writes resolution results to a separate graph without touching source data.

**LoreVault implication:** When a new book is ingested, new `CharacterMention` nodes and a new `BookAggregate` are created. The `ResolvedEntity` is extended (new `MENTIONED_IN` edges added), not rebuilt. Full rebuild of `ResolvedEntity` is only needed when the resolution decision itself changes (e.g., two previously separate entities are merged).

### Principle 4: The Non-Merge Argument (E²RAG) Is Real

E²RAG shows that aggressive merging loses temporal/causal narrative context. The correct answer is **not** to skip the canonical layer, but to **keep both layers**. The mention layer preserves temporal context; the canonical layer enables cross-story identity queries.

**LoreVault implication:** `CharacterMention` nodes should carry their narrative context (chapter position, emotional state, relationships at that point). `ResolvedEntity` should carry only stable cross-story identity. This is the correct separation.

### Principle 5: Rebuild Cost Is Proportional to Merge Decision Scope

| System | Rebuild trigger | Rebuild scope |
|---|---|---|
| Wikidata | Q-item merge/split | Redirect + statement migration |
| TRACE-KG | Resolution decision changes | Re-run EntRes from clustering stage |
| Stardog | Re-run ER | New target graph (source untouched) |
| GOLEM | New story added | New G1 instances only |
| Linked Art | Provenance event added | New event node + reference |

The pattern: **canonical node changes are expensive; evidence layer additions are cheap**. Design the system so that new ingestion (new book, new chapter) only adds to the evidence layer. Canonical node changes (merges, splits, reclassifications) are rare and deliberate.

---

## Summary Table for LoreVault Comparison

| Analogue | Mention Layer | Scoped Aggregate | Canonical Node | Query Entrypoint | Provenance Strategy | Rebuild Cost |
|---|---|---|---|---|---|---|
| **TRACE-KG** | EntityMention (JSONL) | Chunk/document | ResolvedEntity | ResolvedEntity | Chunk IDs on every node | Re-run EntRes stage |
| **Wikidata** | Statement (reified) | Sitelink (per-wiki) | Q-item | Q-item | Statement reification + references | Redirect + migration |
| **Linked Art** | ProvenanceActivity | Object lifecycle | Person/Object URI | Canonical URI | Event chain (first-class nodes) | New event node |
| **GOLEM** | G1_Character (story-scoped) | Story/fandom | G0_Character-Stoff | Stoff node | Instance-to-Stoff links | New G1 instances |
| **E²RAG** | EntityMention (kept distinct) | Event chunk | *(none — gap)* | Mention node | Bipartite event edges | N/A |
| **Stardog** | Source nodes (untouched) | *(none)* | EntityMatch group | Match group | Separate named graph | Re-run ER |
| **LoreVault (proposed)** | CharacterMention | BookAggregate | ResolvedEntity | ResolvedEntity | MENTIONED_IN edges | Extend on ingest |

---

## Key Takeaways for LoreVault's ResolvedEntity Decision

1. **The ladder is well-established.** TRACE-KG, GOLEM, and Linked Art all independently converge on the same three-tier structure. LoreVault is not inventing a novel pattern — it is implementing a well-validated one in a fiction domain.

2. **ResolvedEntity as query entrypoint is correct.** Every production system uses the canonical node as the query entrypoint. The evidence layer is traversed only when provenance is explicitly needed.

3. **The scoped aggregate (BookAggregate) is the missing middle in most systems.** TRACE-KG uses chunks implicitly; Wikidata uses sitelinks. LoreVault's explicit Book/Series/Universe hierarchy is actually *stronger* than most analogues for hierarchical scope queries ("who appears in Book 2 but not Book 3?").

4. **Keep mentions alive.** E²RAG proves that discarding mentions loses temporal/causal narrative context. TRACE-KG keeps them as JSONL artifacts. LoreVault should keep `CharacterMention` nodes as live graph nodes, not just ingestion artifacts.

5. **Rebuild cost is manageable if the canonical layer is thin.** The expensive case is when `ResolvedEntity` carries synthesized attributes that must be recomputed on merge/split. Keep the canonical node's synthesized content minimal and derivable from the evidence layer on demand.
```

---

## Librarian report 2 — Event graph design analogues

Origin task: `bg_f81fbe39`  
Task status: timed out after inactivity, but the compiled report was recovered from session output.

```text
# External Event-Graph Modeling Patterns: A Pressure-Test Report for LoreVault's ResolvedEvent Architecture

---

## Executive Summary

Across NLP event coreference research, temporal knowledge graph engineering, and narrative timeline systems, a consistent **three-tier separation** emerges: raw evidence units → interpreted/resolved events → canonical identity nodes. LoreVault's ladder ending in `ResolvedEvent` is structurally well-grounded. However, the field reveals several pressure points: the canonical top node is only stable as a query entrypoint *if* it is decoupled from the evidence provenance chain, and the interpreted DAG below it must tolerate quasi-identity (partial coreference) rather than demanding strict identity. The evidence-vs-interpretation split is not just a design preference — it is a hard requirement in every serious system reviewed.

---

## Pattern 1: The Mention → Cluster → Canonical Event Ladder (ECR Literature)

**Source:** Cross-Document Event Coreference Resolution (ECB+ benchmark, X-AMR, ACCI framework)
- Papers: [arxiv.org/abs/2404.08656](https://arxiv.org/abs/2404.08656) (X-AMR, LREC-COLING 2024), [nature.com/articles/s41598-025-32765-6](https://www.nature.com/articles/s41598-025-32765-6) (ACCI, 2025)

### What the pattern is

Every serious ECR system separates three layers:

```
TextSpan (mention)
    ↓  extracted from document
EventMention  [scoped to document/scene, carries local temporal args]
    ↓  clustered by coreference resolution
EventCluster  [canonical real-world event identity]
```

**Mentions are never promoted into the canonical layer.** They remain as evidence. The cluster node is the stable identity that participates in cross-document temporal reasoning.

In X-AMR specifically, each mention gets an **Event Identifier (EID)** computed from `(roleset, ARG-0, ARG-1)` — optionally extended with `ARG-Loc` and `ARG-Time`. Two mentions are coreferent if their EIDs match. The canonical event is the cluster, not any individual mention. Crucially:

> *"We generate EID using the roleset, ARG-0, and ARG-1. To evaluate the influence of location and time, we produce EIDlt by incorporating ARG-Loc and ARG-Time."*

This is structurally identical to LoreVault's `EventMention` carrying scene-relative temporal semantics, with `ResolvedEvent` as the cluster identity.

### Relevance to LoreVault

✅ **Confirms**: Evidence mentions (scene-scoped) must stay outside the canonical DAG. The canonical node (`ResolvedEvent`) is the right query entrypoint.

⚠️ **Pressure point**: EID computation in X-AMR is deterministic from arguments. LoreVault's resolution step must be similarly principled — if `ResolvedEvent` identity is fuzzy or LLM-generated without a stable key, the entrypoint becomes unreliable when lower layers are rebuilt.

---

## Pattern 2: Quasi-Identity and Event Hoppers (CMU Dissertation)

**Source:** Zheng et al., CMU LTI PhD Dissertation 2024 — *"Graph Based Event Coreference and Sequencing"*
- [lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf](https://www.lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf)

### What the pattern is

The dissertation introduces the **Quasi-Identity** problem: some event mentions are *related but not fully identical* — they share spatiotemporal continuity without being the same event instance. The solution is **Event Hoppers**: a relaxed coreference cluster that allows partial identity.

```
EventHopper  [relaxed canonical node — tolerates partial identity]
    ↑ AFTER relation between hoppers (temporal ordering)
    ↑ member mentions may not be strictly identical
```

The key insight: **strict identity is too brittle for real narrative events**. The Haitian cholera example in the dissertation shows an event whose identity "continuously evolves over space and time." A canonical node that demands strict identity will fail to aggregate mentions of the same evolving event.

### Relevance to LoreVault

✅ **Confirms**: `ResolvedEvent` as a top-level canonical node is correct. But it should be modeled as a *hopper* (tolerating quasi-identity), not a strict identity cluster.

⚠️ **Pressure point**: If LoreVault's `ResolvedEvent` requires all contributing `EventMention`s to be strictly identical, it will either over-split (creating too many resolved events for the same narrative event) or under-merge (collapsing distinct events). The hopper model suggests `ResolvedEvent` should carry a **confidence/identity-type attribute** (strict vs. quasi).

---

## Pattern 3: The Three-Tier Episodic/Semantic/Community Graph (Zep/Graphiti)

**Source:** Zep: A Temporal Knowledge Graph Architecture for Agent Memory (arXiv 2501.13956, Jan 2025)
- [arxiv.org/html/2501.13956v1](https://arxiv.org/html/2501.13956v1)

### What the pattern is

Graphiti implements a **three-tier hierarchical graph** that is the closest production analog to LoreVault's ladder:

```
Episode Subgraph (𝒢_e)
    Raw input units — messages, text, JSON
    Non-lossy store; never modified
    ↓ episodic edges connect to semantic layer
Semantic Entity Subgraph (𝒢_s)
    Resolved entities and facts
    Bi-temporal: t_valid/t_invalid (narrative time) + t_created/t_expired (ingestion time)
    ↓ community edges connect to community layer
Community Subgraph (𝒢_c)
    High-level cluster summaries
    Stable query entrypoints
```

The **bi-temporal model** is critical:
- `T` (narrative timeline): when facts held true in the story world
- `T'` (transactional timeline): when facts were ingested into the system

> *"Zep implements a bi-temporal model, where timeline T represents the chronological ordering of events, and timeline T' represents the transactional order of Zep's data ingestion."*

Episodes are **never modified** — they are the evidence layer. Semantic entities are resolved and can be invalidated (edge invalidation when contradictions arise). Communities are the stable top-level query nodes.

The episodic edges maintain **bidirectional indices**: semantic artifacts can be traced back to source episodes, and episodes can retrieve their derived entities. This is the provenance chain.

### Relevance to LoreVault

✅ **Directly validates** the architecture: raw scenes (episodes) → interpreted events (semantic entities) → resolved canonical events (communities/resolved nodes). The episode layer must be non-lossy and immutable.

✅ **Validates** `ResolvedEvent` as query entrypoint: Graphiti's community nodes are the stable search targets, not the raw episodes.

⚠️ **Pressure point**: Graphiti's edge invalidation model means the semantic layer *can change* when new evidence arrives. LoreVault's `Event` DAG (interpreted layer) must similarly support invalidation/reinterpretation without touching the `ResolvedEvent` identity. If `ResolvedEvent` is rebuilt from scratch when lower layers change, it loses its value as a stable entrypoint.

⚠️ **Pressure point**: The bi-temporal model is non-negotiable for correctness. LoreVault needs to distinguish *when an event happened in the story* from *when LoreVault learned about it*. Without this, temporal queries across scenes will conflate narrative time with ingestion order.

---

## Pattern 4: Narrative Containers and Epoch Abstractions (NarrativeTime / TimeML)

**Source:** NarrativeTime: Dense Temporal Annotation on a Timeline (LREC-COLING 2024)
- [aclanthology.org/2024.lrec-main.1054](https://aclanthology.org/2024.lrec-main.1054/)

**Source:** Pustejovsky & Stubbs (2011) — Narrative Containers (referenced in medical timeline dissertation)

### What the pattern is

NarrativeTime replaces pairwise temporal link (TLINK) annotation with a **holistic timeline** — a structure from which all pairwise relations can be unambiguously inferred. The key insight:

> *"A timeline contains all the information needed for ordering all event pairs."*

The **Narrative Container** concept (Pustejovsky & Stubbs 2011, widely cited) is a timex (time expression) that acts as a *default interval containing events*. It is an epoch abstraction:

```
NarrativeContainer (epoch/frame)
    ↓ contains
EventMention_1, EventMention_2, ...  [all within this temporal scope]
```

Containers solve the **underspecification problem**: when you can't determine the exact order of two events, you can still assert they both fall within the same container. This is a sparse temporal graph strategy — you don't need all pairwise relations, just containment.

NarrativeTime also introduces **timeline branches** for counterfactual/hypothetical events — events that "maybe didn't/won't happen" get placed on separate branches, not the main timeline.

### Relevance to LoreVault

✅ **Validates** the scene-as-container model: a scene (chapter/passage) is a natural narrative container. `EventMention`s extracted from a scene inherit the scene's temporal scope. This is exactly the "scene-relative temporal semantics" LoreVault describes.

✅ **Validates** sparse temporal graph: you don't need to resolve all pairwise temporal relations between `ResolvedEvent` nodes. Containment within scenes/epochs is sufficient for most queries.

⚠️ **Pressure point**: Hypothetical/counterfactual events need to be on separate branches. If LoreVault's `EventMention` extraction doesn't distinguish factual from hypothetical events, the temporal DAG will be polluted with non-actual events. NarrativeTime handles this via event type definitions with vagueness built in.

---

## Pattern 5: Event-Centric TKG Construction Pipeline (MDPI Survey)

**Source:** Event-Centric Temporal Knowledge Graph Construction: A Survey (Mathematics 2023, 11(23), 4852)
- [mdpi.com/2227-7390/11/23/4852](https://www.mdpi.com/2227-7390/11/23/4852)

### What the pattern is

The survey identifies the canonical three-step pipeline for event-centric TKG construction:

```
Step 1: Event Extraction
    → Identify event triggers and arguments from text
    → Output: EventMention nodes with attributes (who, what, where, when, why, how)

Step 2: Temporal Relation Extraction
    → Allen's interval relations (before, after, during, overlaps, etc.)
    → Output: Temporal edges between EventMentions

Step 3: Knowledge Graph Construction
    → Coreference resolution → canonical event nodes
    → Timeline/DAG assembly
    → Output: Event-centric TKG
```

The survey notes that **only two of the surveyed systems** implement the full pipeline end-to-end. Most systems handle only one or two steps. This is a known gap — and it means most real-world event graphs are assembled from modular components with explicit handoff points between layers.

The OWL Time Ontology is the standard for temporal attributes: `time:Instant`, `time:Interval`, `time:TemporalEntity`, `time:before`/`time:after` relations.

The survey also notes the **granularity problem**: an event-centric KG must accommodate different levels of event granularity. A "war" and a "battle" are both events but at different scales. This requires hierarchical event typing.

### Relevance to LoreVault

✅ **Confirms** the modular pipeline: LoreVault's ladder (scene → mention → event → resolved event) maps cleanly onto the three-step pipeline. Each layer is a legitimate handoff point.

⚠️ **Pressure point**: The granularity problem is real. LoreVault's `Event` nodes need a type hierarchy or granularity attribute. A `ResolvedEvent` for "The Battle of Helm's Deep" and one for "Aragorn drew his sword" are at incompatible granularities — the DAG will be incoherent without explicit granularity handling.

---

## Pattern 6: LOME — Modular Pipeline with Explicit Mention/Cluster Separation

**Source:** LOME: Multilingual Information Extraction (arXiv 2101.12175)
- [arxiv.org/pdf/2101.12175](https://arxiv.org/pdf/2101.12175)

### What the pattern is

LOME is a production NLP pipeline that explicitly separates:

1. **FrameNet parser** → detects event triggers and arguments (mention-level)
2. **Coreference resolution** → clusters coreferent mentions (cluster-level)
3. **Temporal relation extraction** → orders events (DAG-level)
4. **Entity typing** → assigns types to canonical entities

The architecture uses **CONCRETE** as the inter-module communication schema — a standardized data format that carries both raw spans and derived annotations. This is the key design insight: the schema must support both the evidence layer (raw spans) and the interpretation layer (clusters, types, relations) simultaneously.

> *"Our system is designed to be modular: each component is trained independently and tuned on task-specific data. To communicate between modules, we use CONCRETE."*

The coreference module operates on **predetermined mention spans** from the FrameNet parser — it does not re-detect mentions. This means the mention layer is fixed before coreference runs, and coreference only assigns cluster membership.

### Relevance to LoreVault

✅ **Confirms** the separation: mention detection (scene-level) must be complete before coreference/resolution runs. `EventMention` extraction is a prerequisite for `ResolvedEvent` construction, not concurrent with it.

✅ **Validates** the modular handoff: each layer can be rebuilt independently as long as the schema contract is maintained. This directly supports the hypothesis that `ResolvedEvent` can remain stable while lower layers are rebuilt.

⚠️ **Pressure point**: LOME's CONCRETE schema carries provenance — every cluster node knows which mention spans it was derived from. If LoreVault's `ResolvedEvent` loses its back-links to `EventMention`s when lower layers are rebuilt, it becomes an orphaned canonical node with no evidence trail. The provenance chain must survive rebuilds.

---

## Pattern 7: Temporal Anchor Memory for Sparse Graphs (MATA, ICLR 2026)

**Source:** MATA: Memory-Augmented Temporal Anchors for Sparse-Time Dynamic Knowledge Graph Embedding (ICLR 2026 under review)
- [openreview.net/pdf?id=9CwDDoag8I](https://openreview.net/pdf?id=9CwDDoag8I)

### What the pattern is

MATA addresses the **sparse temporal supervision** problem in TKGs: real-world temporal graphs have irregular, missing timestamps. The solution is **learnable temporal anchors** — a differentiable memory of `K` anchor vectors, each corresponding to a learned time point. Given a query timestamp, the system interpolates over anchors:

```
Temporal Anchor Memory M = {(t_i, a_i)}^K
    ↓ attention-weighted interpolation
Time-aware entity embedding at arbitrary t_q
```

This is the "epoch abstraction" pattern at the embedding level: instead of requiring dense temporal coverage, you maintain a sparse set of anchor points and interpolate between them.

The notation: `G_t = (E_t, R_t, T)` — a TKG snapshot at time `t`, where each fact is `(h, r, o, t)`.

### Relevance to LoreVault

✅ **Validates** sparse temporal graph design: LoreVault's temporal DAG does not need dense pairwise temporal relations. A sparse set of anchor events (major plot points, chapter boundaries) can serve as temporal anchors from which other events are positioned.

⚠️ **Pressure point**: If LoreVault's `ResolvedEvent` nodes don't carry explicit temporal coordinates (even approximate ones), temporal queries will degrade to graph traversal without time-awareness. The anchor pattern suggests maintaining a small set of well-dated `ResolvedEvent`s as temporal scaffolding.

---

## Synthesis: Pressure-Testing the ResolvedEvent Entrypoint

### What the field agrees on

| Claim | Evidence |
|---|---|
| Evidence mentions must stay outside the canonical DAG | ECR literature (X-AMR, ACCI), LOME, Graphiti — unanimous |
| A canonical/resolved event node is the right query entrypoint | Graphiti communities, ECR clusters, LOME coreference clusters — unanimous |
| The canonical layer must survive lower-layer rebuilds | Graphiti edge invalidation, LOME modular design — explicit |
| Bi-temporal modeling is required | Graphiti (T vs T'), NarrativeTime (narrative vs annotation time) |
| Sparse temporal graphs are acceptable | NarrativeTime containers, MATA anchors, TKG survey |
| Granularity hierarchy is required | TKG survey, ChronoGrapher sub-event model |

### Where the field pushes back on strict ResolvedEvent identity

| Risk | Source | Mitigation |
|---|---|---|
| Strict identity is too brittle | CMU quasi-identity / event hoppers | `ResolvedEvent` should carry an identity-type attribute (strict vs. quasi/hopper) |
| Canonical node becomes orphaned if provenance is lost | LOME CONCRETE schema, Graphiti episodic back-links | `ResolvedEvent` must maintain back-links to `EventMention`s even after lower-layer rebuilds |
| Factual/hypothetical conflation | NarrativeTime timeline branches | `EventMention` must carry a factuality/modality flag before being promoted |
| Granularity incoherence | TKG survey, ChronoGrapher | `ResolvedEvent` needs a granularity/scale attribute |
| Temporal anchor sparsity | MATA | Maintain a small set of well-dated `ResolvedEvent`s as temporal scaffolding |

### The core verdict

**`ResolvedEvent` as the clean query entrypoint is well-supported by the field — with one non-negotiable condition**: it must be an *identity node with stable provenance back-links*, not a derived summary that gets recomputed from scratch when lower layers change. The Graphiti model is the clearest production analog: community nodes (≈ `ResolvedEvent`) are stable query targets, but they maintain edges back to semantic entities (≈ `Event`) which maintain edges back to episodes (≈ `Scene`/`EventMention`). When a semantic entity is invalidated, the community node is updated — not replaced.

The field also consistently shows that **the interpreted Event DAG** (the layer between `EventMention` and `ResolvedEvent`) is the most volatile layer — it is where temporal relations, causal links, and sub-event hierarchies live, and it is the layer most likely to be rebuilt as new evidence arrives. This is exactly why `ResolvedEvent` must be decoupled from it: the canonical identity should survive DAG restructuring below it.

---

## Key Sources for Further Reading

| Source | URL | Why relevant |
|---|---|---|
| X-AMR (LREC-COLING 2024) | https://aclanthology.org/2024.lrec-main.920 | EID-based canonical event identity from mention arguments |
| Zep/Graphiti (arXiv 2501.13956) | https://arxiv.org/abs/2501.13956 | Production three-tier episodic/semantic/community graph with bi-temporal model |
| CMU Event Coreference Dissertation (2024) | https://www.lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf | Quasi-identity, event hoppers, graph-based coreference |
| NarrativeTime (LREC-COLING 2024) | https://aclanthology.org/2024.lrec-main.1054 | Timeline-based annotation, narrative containers, factuality branches |
| Event-Centric TKG Survey (Mathematics 2023) | https://www.mdpi.com/2227-7390/11/23/4852 | Full pipeline survey, granularity problem, OWL Time Ontology |
| LOME (arXiv 2101.12175) | https://arxiv.org/abs/2101.12175 | Modular pipeline, CONCRETE schema, mention/cluster separation |
| MATA (ICLR 2026) | https://openreview.net/forum?id=9CwDDoag8I | Sparse temporal anchor memory for irregular TKGs |
```
