External Analogues Survey: Mention → Aggregate → Canonical/Resolved Entity Ladders
This survey covers six strong external analogues for LoreVault's proposed ResolvedEntity/ResolvedEvent ladder, drawn from production knowledge graph systems, academic pipelines, and narrative graph research. Each section documents the pattern, its provenance strategy, canonical identity approach, query entrypoint design, and relinking/rebuild tradeoffs.
---
1. TRACE-KG — The Closest Direct Analogue
   Source: arxiv.org/html/2604.03496v1 (https://arxiv.org/html/2604.03496v1) (Arizona State University, April 2026)
   The Ladder
   TRACE-KG defines an explicit three-tier structure that maps almost exactly onto LoreVault's proposed design:
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
   Key design decision: The final graph G = (E, R, S) is built over resolved entities only. Mentions are not graph nodes in the final KG — they are intermediate artifacts retained in JSONL for traceability. The resolved entity is the query entrypoint.
   Provenance preservation: Every resolved entity carries aggregated_provenance — a set of chunk identifiers from which its mentions were drawn. Every relation instance also carries provenance. The paper explicitly states: "Traceability is preserved throughout the pipeline: every node and edge maintains explicit links to supporting chunk identifiers, and all resolution edits are recorded as structured, auditable actions."
   Relinking/rebuild cost: TRACE-KG uses iterative resolution with constrained action interfaces (LLM function-calling). The resolution stage runs multiple passes until convergence. Critically, intermediate JSONL artifacts are persisted at each stage — so a re-run can resume from any tier without re-extracting mentions. This is staged reduction, not full rebuild.
   Tradeoff explicitly named: Over-merging semantically distinct entities introduces incorrect relations; under-merging fragments evidence. The system uses embedding-based clustering + LLM-guided selection to balance this. No automatic merge is irreversible — the audit trail is kept.
   Analogy to LoreVault: TRACE-KG's EntityMention ≈ LoreVault's CharacterMention; its ResolvedEntity ≈ LoreVault's proposed ResolvedEntity. The scoped aggregate (Chapter/Book) is implicit in TRACE-KG via chunk provenance, but LoreVault makes it explicit — which is actually stronger for hierarchical querying.
---
2. Wikidata Q-Item Architecture — Canonical Identity at Scale
   Sources: wikidata.org SPARQL docs (https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service/WDQS_graph_split/Federated_Queries_Examples), Hernandez et al. reification paper (https://aidanhogan.com/docs/reification-wikidata-rdf-sparql.pdf), DBpedia/Wikidata integration paper (https://www.semantic-web-journal.net/system/files/swj1518.pdf)
   The Ladder
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
   Canonical identity: The QID is the query entrypoint. All retrieval systems resolve a label/alias → QID, then traverse properties and references from there. The QID never changes even if the entity is renamed or merged. Sitelinks are the provenance layer connecting the canonical node to human-verified sources.
   Provenance preservation: Wikidata uses statement reification — each claim is a first-class node with qualifiers (e.g., start_time, end_time) and references (source URLs). This means a statement like "Douglas Adams was born in Cambridge" carries its own evidence chain without polluting the canonical Q-item node.
   Relinking cost: When two Q-items are merged (e.g., duplicate entities discovered), Wikidata creates a redirect from the deprecated QID to the surviving one. All sitelinks and statements are migrated. The redirect is permanent and queryable. This is the production-scale answer to "what happens when canonical identity changes."
   Query entrypoint design: SPARQL queries always start from the Q-item. The statement layer is only traversed when provenance or qualifiers are needed. This two-speed access pattern — fast canonical lookup, slower provenance drill-down — is a deliberate architectural choice.
   Tradeoff: Wikidata's reification scheme is verbose and query-expensive. The WDQS graph split (2024–2026) was partly motivated by the cost of querying across canonical and provenance layers simultaneously. LoreVault should note: if provenance queries are rare, keeping them in a separate traversal path (not inline on the canonical node) is the right call.
---
3. Linked Art / CIDOC-CRM — Event-Centric Provenance with Canonical Entities
   Sources: linked.art/model/provenance (https://linked.art/model/provenance), linked.art/api/1.0/endpoint/event (https://linked.art/api/1.0/endpoint/event), cidoc-crm.org (https://cidoc-crm.org)
   The Ladder
   CIDOC-CRM and its Linked Art application profile use a fundamentally event-centric model where canonical entities (Person, HumanMadeObject, Place) are stable nodes, and all change/provenance is captured as events:
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
   Key design decision: The canonical entity node is immutable — it holds stable identity. All change (ownership, location, condition) is modeled as events that reference the canonical node, not as mutations of it. This is the CIDOC-CRM principle of endurant vs. perdurant separation: entities endure through time; events perdure (happen and are done).
   Provenance preservation: Every provenance event is a first-class node with its own URI, time, place, and participants. The chain of events is queryable independently of the canonical entity. The Linked Art API exposes a dedicated ProvenanceActivity endpoint — provenance is not a property of the object, it is a separate graph of events that reference the object.
   Query entrypoint: The canonical entity (e.g., https://linked.art/example/object/spring) is the entrypoint. From it, you can traverse produced_by, transferred_title_of (via provenance events), current_owner, etc. The provenance chain is navigated by following event references, not by reading the canonical node directly.
   Relinking cost: Because events reference canonical entities by URI, relinking is cheap — you update the event's reference, not the canonical node. If a canonical entity is split or merged, the event chain is re-pointed. The canonical node itself rarely changes structure.
   Analogy to LoreVault: CIDOC-CRM's E7_Activity ≈ LoreVault's ChapterMention or SceneEvent. The canonical Person node ≈ ResolvedEntity. The key insight: events are the evidence layer; canonical entities are the query entrypoint. LoreVault's proposed ladder follows this pattern correctly.
---
4. GOLEM Knowledge Graph — Fiction-Specific Character Stoff Pattern
   Source: ceur-ws.org/Vol-3834/paper80.pdf (https://ceur-ws.org/Vol-3834/paper80.pdf) (GOLEM project, 2024)
   The Ladder
   GOLEM (Graph Ontologies for Literary Evolution Models) models fanfiction narratives and explicitly distinguishes two character representations:
   Tier 1 (Instance):  gc:G1_Character  (crm:E89_Propositional_Object)
   └── character as it appears in ONE specific story
   (story-scoped, carries narrative context)
   Tier 2 (Canon):     gc:G0_Character-Stoff  (crm:E28_Conceptual_Object)
   └── the abstract "character idea" that appears
   across all versions/books/fandoms
   (cross-story canonical identity)
   The German term Stoff (literary material/substance) is used deliberately — it refers to the underlying conceptual entity that persists across all instantiations of a character across different works.
   Canonical identity: G0_Character-Stoff is the query entrypoint for cross-story questions ("How does Hermione appear across all Harry Potter fanfics?"). G1_Character is the entrypoint for story-specific questions ("What does Hermione do in this specific fic?").
   Provenance preservation: The G1_Character instance carries story-specific attributes (role, relationships, narrative arc within that story). The G0_Character-Stoff aggregates across instances. The relationship between them is explicit: G1_Character → is_instance_of → G0_Character-Stoff.
   Relinking cost: Adding a new story creates new G1_Character instances and links them to existing G0_Character-Stoff nodes. The canonical Stoff node is not rebuilt — it is extended by new instance links. This is incremental extension, not rebuild.
   Tradeoff: The Stoff node can become a "god node" if too many instances link to it without further structure. GOLEM mitigates this by keeping the Stoff node lightweight (conceptual identity only) and pushing all narrative detail to the instance layer.
   Analogy to LoreVault: G0_Character-Stoff ≈ LoreVault's ResolvedEntity. G1_Character ≈ LoreVault's BookAggregate or ChapterMention. The GOLEM pattern is the closest fiction-domain analogue to LoreVault's proposed ladder.
---
5. E²RAG (Entity-Event RAG) — Narrative Graph with Deliberate Non-Merging
   Source: aclanthology.org/2026.eacl-long.90.pdf (https://aclanthology.org/2026.eacl-long.90.pdf) (EACL 2026)
   The Ladder
   E²RAG takes a deliberately different approach to the merge question — it argues that for narrative fiction, merging entity mentions is harmful:
   Entity subgraph  Gent = (Vent, Eent)
   └── Each node = one entity MENTION with context-specific description
   (deliberately NOT merged across story positions)
   Event subgraph   Gevt = (Vevt, Eevt)
   └── Each node = one event with trigger, description, chunk_id
   Bipartite edges  B ⊆ Vent × Vevt
   └── (mention, event) edge when mention appears in event's chunk
   Key design decision: "Instead of collapsing duplicates, we first extract both entities and their events... we never merge mentions that arise in different parts of the story, each entity node carries its own context-specific description."
   Why this matters for LoreVault: E²RAG is the counter-argument to aggressive canonical merging. For narrative fiction where a character's state evolves (Frodo at the Shire vs. Frodo at Mount Doom), merging all mentions into one canonical node loses temporal/causal context. E²RAG's answer is to keep mentions distinct and use the bipartite event graph for retrieval.
   The tradeoff explicitly stated: E²RAG achieves better temporal-causal consistency in RAG but at the cost of cross-story identity queries. You cannot easily ask "who is this character across all books" without a separate canonical layer. This is precisely the gap that LoreVault's ResolvedEntity fills — E²RAG shows what you lose if you skip it, and what you gain if you keep the mention layer.
   Analogy to LoreVault: E²RAG's mention nodes ≈ LoreVault's CharacterMention. E²RAG has no canonical layer — which is the gap LoreVault's ResolvedEntity is designed to fill. E²RAG's event nodes ≈ LoreVault's SceneEvent or ChapterEvent.
---
6. Stardog EntityMatch — Non-Destructive Canonical Grouping
   Source: docs.stardog.com/entity-resolution (https://docs.stardog.com/entity-resolution/)
   The Pattern
   Stardog's entity resolution deliberately avoids merging source nodes. Instead it creates a canonical grouping node:
   :Sebestian_Vincent  ──entityMatch──>  :MatchGroup_001
   :Sebestn_Vincent    ──entityMatch──>  :MatchGroup_001
   :S_Vincent          ──entityMatch──>  :MatchGroup_001
   :MatchGroup_001  a  stardog:EntityMatch
   ├── hasEntityMatchInfo → score nodes (pairwise similarity)
   └── metadata (timestamp, query, user, config)
   Key design decision: "Entity resolution does not modify the existing data and writes the results to the provided target graph." The source nodes are untouched. The canonical grouping is a separate overlay graph.
   Query entrypoint: You query the EntityMatch node to find all equivalent entities, then traverse to whichever source node you need. The match group is the canonical identity node; the source nodes are the evidence/provenance layer.
   Relinking cost: Because the canonical grouping is in a separate named graph, it can be rebuilt without touching source data. Re-running entity resolution produces a new target graph. This is the cleanest separation of canonical identity from source provenance.
   Tradeoff: The EntityMatch node is a thin grouping node — it carries no synthesized attributes. If you want a canonical description or merged properties, you must compute them separately. Stardog leaves that to the user (via SPARQL UPDATE). LoreVault's ResolvedEntity is richer — it should carry synthesized canonical attributes, not just a grouping pointer.
---
Cross-Cutting Principles and Tradeoffs
Principle 1: Canonical Node = Query Entrypoint, Not Storage Node
Every strong analogue (Wikidata Q-item, Linked Art Person, GOLEM Stoff, TRACE-KG ResolvedEntity) uses the canonical node as the entry point for queries while keeping evidence/provenance in lower layers. The canonical node is lightweight — it holds stable identity and synthesized attributes, not raw evidence.
LoreVault implication: ResolvedEntity should be the entrypoint for cross-book/cross-series queries. CharacterMention and BookAggregate are the evidence/provenance layer. This is the correct direction.
Principle 2: Provenance Must Be a First-Class Graph Citizen
Wikidata uses statement reification. Linked Art uses event nodes. TRACE-KG uses chunk provenance on every node. GOLEM uses instance-to-Stoff links. In every case, provenance is not a property — it is a traversable graph structure.
LoreVault implication: The [:MENTIONED_IN {chapter, page, context}] edge pattern is correct. Do not flatten provenance into node properties.
Principle 3: Incremental Extension, Not Full Rebuild
GOLEM adds new G1_Character instances without rebuilding G0_Character-Stoff. Wikidata adds new statements without changing Q-items. Stardog writes resolution results to a separate graph without touching source data.
LoreVault implication: When a new book is ingested, new CharacterMention nodes and a new BookAggregate are created. The ResolvedEntity is extended (new MENTIONED_IN edges added), not rebuilt. Full rebuild of ResolvedEntity is only needed when the resolution decision itself changes (e.g., two previously separate entities are merged).
Principle 4: The Non-Merge Argument (E²RAG) Is Real
E²RAG shows that aggressive merging loses temporal/causal narrative context. The correct answer is not to skip the canonical layer, but to keep both layers. The mention layer preserves temporal context; the canonical layer enables cross-story identity queries.
LoreVault implication: CharacterMention nodes should carry their narrative context (chapter position, emotional state, relationships at that point). ResolvedEntity should carry only stable cross-story identity. This is the correct separation.
Principle 5: Rebuild Cost Is Proportional to Merge Decision Scope
System	Rebuild trigger	Rebuild scope
Wikidata	Q-item merge/split	Redirect + statement migration
TRACE-KG	Resolution decision changes	Re-run EntRes from clustering stage
Stardog	Re-run ER	New target graph (source untouched)
GOLEM	New story added	New G1 instances only
Linked Art	Provenance event added	New event node + reference
The pattern: canonical node changes are expensive; evidence layer additions are cheap. Design the system so that new ingestion (new book, new chapter) only adds to the evidence layer. Canonical node changes (merges, splits, reclassifications) are rare and deliberate.
---
Summary Table for LoreVault Comparison
Analogue	Mention Layer	Scoped Aggregate	Canonical Node	Query Entrypoint	Provenance Strategy	Rebuild Cost
TRACE-KG	EntityMention (JSONL)	Chunk/document	ResolvedEntity	ResolvedEntity	Chunk IDs on every node	Re-run EntRes stage
Wikidata	Statement (reified)	Sitelink (per-wiki)	Q-item	Q-item	Statement reification + references	Redirect + migration
Linked Art	ProvenanceActivity	Object lifecycle	Person/Object URI	Canonical URI	Event chain (first-class nodes)	New event node
GOLEM	G1_Character (story-scoped)	Story/fandom	G0_Character-Stoff	Stoff node	Instance-to-Stoff links	New G1 instances
E²RAG	EntityMention (kept distinct)	Event chunk	(none — gap)	Mention node	Bipartite event edges	N/A
Stardog	Source nodes (untouched)	(none)	EntityMatch group	Match group	Separate named graph	Re-run ER
LoreVault (proposed)	CharacterMention	BookAggregate	ResolvedEntity	ResolvedEntity	MENTIONED_IN edges	Extend on ingest
---
Key Takeaways for LoreVault's ResolvedEntity Decision
1. The ladder is well-established. TRACE-KG, GOLEM, and Linked Art all independently converge on the same three-tier structure. LoreVault is not inventing a novel pattern — it is implementing a well-validated one in a fiction domain.
2. ResolvedEntity as query entrypoint is correct. Every production system uses the canonical node as the query entrypoint. The evidence layer is traversed only when provenance is explicitly needed.
3. The scoped aggregate (BookAggregate) is the missing middle in most systems. TRACE-KG uses chunks implicitly; Wikidata uses sitelinks. LoreVault's explicit Book/Series/Universe hierarchy is actually stronger than most analogues for hierarchical scope queries ("who appears in Book 2 but not Book 3?").
4. Keep mentions alive. E²RAG proves that discarding mentions loses temporal/causal narrative context. TRACE-KG keeps them as JSONL artifacts. LoreVault should keep CharacterMention nodes as live graph nodes, not just ingestion artifacts.
5. Rebuild cost is manageable if the canonical layer is thin. The expensive case is when ResolvedEntity carries synthesized attributes that must be recomputed on merge/split. Keep the canonical node's synthesized content minimal and derivable from the evidence layer on demand.