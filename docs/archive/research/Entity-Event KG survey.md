External Event-Graph Modeling Patterns: A Pressure-Test Report for LoreVault's ResolvedEvent Architecture
---
Executive Summary
Across NLP event coreference research, temporal knowledge graph engineering, and narrative timeline systems, a consistent three-tier separation emerges: raw evidence units → interpreted/resolved events → canonical identity nodes. LoreVault's ladder ending in ResolvedEvent is structurally well-grounded. However, the field reveals several pressure points: the canonical top node is only stable as a query entrypoint if it is decoupled from the evidence provenance chain, and the interpreted DAG below it must tolerate quasi-identity (partial coreference) rather than demanding strict identity. The evidence-vs-interpretation split is not just a design preference — it is a hard requirement in every serious system reviewed.
---
Pattern 1: The Mention → Cluster → Canonical Event Ladder (ECR Literature)
Source: Cross-Document Event Coreference Resolution (ECB+ benchmark, X-AMR, ACCI framework)
- Papers: arxiv.org/abs/2404.08656 (https://arxiv.org/abs/2404.08656) (X-AMR, LREC-COLING 2024), nature.com/articles/s41598-025-32765-6 (https://www.nature.com/articles/s41598-025-32765-6) (ACCI, 2025)
  What the pattern is
  Every serious ECR system separates three layers:
  TextSpan (mention)
  ↓  extracted from document
  EventMention  [scoped to document/scene, carries local temporal args]
  ↓  clustered by coreference resolution
  EventCluster  [canonical real-world event identity]
  Mentions are never promoted into the canonical layer. They remain as evidence. The cluster node is the stable identity that participates in cross-document temporal reasoning.
  In X-AMR specifically, each mention gets an Event Identifier (EID) computed from (roleset, ARG-0, ARG-1) — optionally extended with ARG-Loc and ARG-Time. Two mentions are coreferent if their EIDs match. The canonical event is the cluster, not any individual mention. Crucially:
> "We generate EID using the roleset, ARG-0, and ARG-1. To evaluate the influence of location and time, we produce EIDlt by incorporating ARG-Loc and ARG-Time."
This is structurally identical to LoreVault's EventMention carrying scene-relative temporal semantics, with ResolvedEvent as the cluster identity.
Relevance to LoreVault
✅ Confirms: Evidence mentions (scene-scoped) must stay outside the canonical DAG. The canonical node (ResolvedEvent) is the right query entrypoint.
⚠️ Pressure point: EID computation in X-AMR is deterministic from arguments. LoreVault's resolution step must be similarly principled — if ResolvedEvent identity is fuzzy or LLM-generated without a stable key, the entrypoint becomes unreliable when lower layers are rebuilt.
---
Pattern 2: Quasi-Identity and Event Hoppers (CMU Dissertation)
Source: Zheng et al., CMU LTI PhD Dissertation 2024 — "Graph Based Event Coreference and Sequencing"
- lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf (https://www.lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf)
  What the pattern is
  The dissertation introduces the Quasi-Identity problem: some event mentions are related but not fully identical — they share spatiotemporal continuity without being the same event instance. The solution is Event Hoppers: a relaxed coreference cluster that allows partial identity.
  EventHopper  [relaxed canonical node — tolerates partial identity]
  ↑ AFTER relation between hoppers (temporal ordering)
  ↑ member mentions may not be strictly identical
  The key insight: strict identity is too brittle for real narrative events. The Haitian cholera example in the dissertation shows an event whose identity "continuously evolves over space and time." A canonical node that demands strict identity will fail to aggregate mentions of the same evolving event.
  Relevance to LoreVault
  ✅ Confirms: ResolvedEvent as a top-level canonical node is correct. But it should be modeled as a hopper (tolerating quasi-identity), not a strict identity cluster.
  ⚠️ Pressure point: If LoreVault's ResolvedEvent requires all contributing EventMentions to be strictly identical, it will either over-split (creating too many resolved events for the same narrative event) or under-merge (collapsing distinct events). The hopper model suggests ResolvedEvent should carry a confidence/identity-type attribute (strict vs. quasi).
---
Pattern 3: The Three-Tier Episodic/Semantic/Community Graph (Zep/Graphiti)
Source: Zep: A Temporal Knowledge Graph Architecture for Agent Memory (arXiv 2501.13956, Jan 2025)
- arxiv.org/html/2501.13956v1 (https://arxiv.org/html/2501.13956v1)
  What the pattern is
  Graphiti implements a three-tier hierarchical graph that is the closest production analog to LoreVault's ladder:
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
  The bi-temporal model is critical:
- T (narrative timeline): when facts held true in the story world
- T' (transactional timeline): when facts were ingested into the system
> "Zep implements a bi-temporal model, where timeline T represents the chronological ordering of events, and timeline T' represents the transactional order of Zep's data ingestion."
Episodes are never modified — they are the evidence layer. Semantic entities are resolved and can be invalidated (edge invalidation when contradictions arise). Communities are the stable top-level query nodes.
The episodic edges maintain bidirectional indices: semantic artifacts can be traced back to source episodes, and episodes can retrieve their derived entities. This is the provenance chain.
Relevance to LoreVault
✅ Directly validates the architecture: raw scenes (episodes) → interpreted events (semantic entities) → resolved canonical events (communities/resolved nodes). The episode layer must be non-lossy and immutable.
✅ Validates ResolvedEvent as query entrypoint: Graphiti's community nodes are the stable search targets, not the raw episodes.
⚠️ Pressure point: Graphiti's edge invalidation model means the semantic layer can change when new evidence arrives. LoreVault's Event DAG (interpreted layer) must similarly support invalidation/reinterpretation without touching the ResolvedEvent identity. If ResolvedEvent is rebuilt from scratch when lower layers change, it loses its value as a stable entrypoint.
⚠️ Pressure point: The bi-temporal model is non-negotiable for correctness. LoreVault needs to distinguish when an event happened in the story from when LoreVault learned about it. Without this, temporal queries across scenes will conflate narrative time with ingestion order.
---
Pattern 4: Narrative Containers and Epoch Abstractions (NarrativeTime / TimeML)
Source: NarrativeTime: Dense Temporal Annotation on a Timeline (LREC-COLING 2024)
- aclanthology.org/2024.lrec-main.1054 (https://aclanthology.org/2024.lrec-main.1054/)
  Source: Pustejovsky & Stubbs (2011) — Narrative Containers (referenced in medical timeline dissertation)
  What the pattern is
  NarrativeTime replaces pairwise temporal link (TLINK) annotation with a holistic timeline — a structure from which all pairwise relations can be unambiguously inferred. The key insight:
> "A timeline contains all the information needed for ordering all event pairs."
The Narrative Container concept (Pustejovsky & Stubbs 2011, widely cited) is a timex (time expression) that acts as a default interval containing events. It is an epoch abstraction:
NarrativeContainer (epoch/frame)
↓ contains
EventMention_1, EventMention_2, ...  [all within this temporal scope]
Containers solve the underspecification problem: when you can't determine the exact order of two events, you can still assert they both fall within the same container. This is a sparse temporal graph strategy — you don't need all pairwise relations, just containment.
NarrativeTime also introduces timeline branches for counterfactual/hypothetical events — events that "maybe didn't/won't happen" get placed on separate branches, not the main timeline.
Relevance to LoreVault
✅ Validates the scene-as-container model: a scene (chapter/passage) is a natural narrative container. EventMentions extracted from a scene inherit the scene's temporal scope. This is exactly the "scene-relative temporal semantics" LoreVault describes.
✅ Validates sparse temporal graph: you don't need to resolve all pairwise temporal relations between ResolvedEvent nodes. Containment within scenes/epochs is sufficient for most queries.
⚠️ Pressure point: Hypothetical/counterfactual events need to be on separate branches. If LoreVault's EventMention extraction doesn't distinguish factual from hypothetical events, the temporal DAG will be polluted with non-actual events. NarrativeTime handles this via event type definitions with vagueness built in.
---
Pattern 5: Event-Centric TKG Construction Pipeline (MDPI Survey)
Source: Event-Centric Temporal Knowledge Graph Construction: A Survey (Mathematics 2023, 11(23), 4852)
- mdpi.com/2227-7390/11/23/4852 (https://www.mdpi.com/2227-7390/11/23/4852)
  What the pattern is
  The survey identifies the canonical three-step pipeline for event-centric TKG construction:
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
  The survey notes that only two of the surveyed systems implement the full pipeline end-to-end. Most systems handle only one or two steps. This is a known gap — and it means most real-world event graphs are assembled from modular components with explicit handoff points between layers.
  The OWL Time Ontology is the standard for temporal attributes: time:Instant, time:Interval, time:TemporalEntity, time:before/time:after relations.
  The survey also notes the granularity problem: an event-centric KG must accommodate different levels of event granularity. A "war" and a "battle" are both events but at different scales. This requires hierarchical event typing.
  Relevance to LoreVault
  ✅ Confirms the modular pipeline: LoreVault's ladder (scene → mention → event → resolved event) maps cleanly onto the three-step pipeline. Each layer is a legitimate handoff point.
  ⚠️ Pressure point: The granularity problem is real. LoreVault's Event nodes need a type hierarchy or granularity attribute. A ResolvedEvent for "The Battle of Helm's Deep" and one for "Aragorn drew his sword" are at incompatible granularities — the DAG will be incoherent without explicit granularity handling.
---
Pattern 6: LOME — Modular Pipeline with Explicit Mention/Cluster Separation
Source: LOME: Multilingual Information Extraction (arXiv 2101.12175)
- arxiv.org/pdf/2101.12175 (https://arxiv.org/pdf/2101.12175)
  What the pattern is
  LOME is a production NLP pipeline that explicitly separates:
1. FrameNet parser → detects event triggers and arguments (mention-level)
2. Coreference resolution → clusters coreferent mentions (cluster-level)
3. Temporal relation extraction → orders events (DAG-level)
4. Entity typing → assigns types to canonical entities
   The architecture uses CONCRETE as the inter-module communication schema — a standardized data format that carries both raw spans and derived annotations. This is the key design insight: the schema must support both the evidence layer (raw spans) and the interpretation layer (clusters, types, relations) simultaneously.
> "Our system is designed to be modular: each component is trained independently and tuned on task-specific data. To communicate between modules, we use CONCRETE."
The coreference module operates on predetermined mention spans from the FrameNet parser — it does not re-detect mentions. This means the mention layer is fixed before coreference runs, and coreference only assigns cluster membership.
Relevance to LoreVault
✅ Confirms the separation: mention detection (scene-level) must be complete before coreference/resolution runs. EventMention extraction is a prerequisite for ResolvedEvent construction, not concurrent with it.
✅ Validates the modular handoff: each layer can be rebuilt independently as long as the schema contract is maintained. This directly supports the hypothesis that ResolvedEvent can remain stable while lower layers are rebuilt.
⚠️ Pressure point: LOME's CONCRETE schema carries provenance — every cluster node knows which mention spans it was derived from. If LoreVault's ResolvedEvent loses its back-links to EventMentions when lower layers are rebuilt, it becomes an orphaned canonical node with no evidence trail. The provenance chain must survive rebuilds.
---
Pattern 7: Temporal Anchor Memory for Sparse Graphs (MATA, ICLR 2026)
Source: MATA: Memory-Augmented Temporal Anchors for Sparse-Time Dynamic Knowledge Graph Embedding (ICLR 2026 under review)
- openreview.net/pdf?id=9CwDDoag8I (https://openreview.net/pdf?id=9CwDDoag8I)
  What the pattern is
  MATA addresses the sparse temporal supervision problem in TKGs: real-world temporal graphs have irregular, missing timestamps. The solution is learnable temporal anchors — a differentiable memory of K anchor vectors, each corresponding to a learned time point. Given a query timestamp, the system interpolates over anchors:
  Temporal Anchor Memory M = {(t_i, a_i)}^K
  ↓ attention-weighted interpolation
  Time-aware entity embedding at arbitrary t_q
  This is the "epoch abstraction" pattern at the embedding level: instead of requiring dense temporal coverage, you maintain a sparse set of anchor points and interpolate between them.
  The notation: G_t = (E_t, R_t, T) — a TKG snapshot at time t, where each fact is (h, r, o, t).
  Relevance to LoreVault
  ✅ Validates sparse temporal graph design: LoreVault's temporal DAG does not need dense pairwise temporal relations. A sparse set of anchor events (major plot points, chapter boundaries) can serve as temporal anchors from which other events are positioned.
  ⚠️ Pressure point: If LoreVault's ResolvedEvent nodes don't carry explicit temporal coordinates (even approximate ones), temporal queries will degrade to graph traversal without time-awareness. The anchor pattern suggests maintaining a small set of well-dated ResolvedEvents as temporal scaffolding.
---
Synthesis: Pressure-Testing the ResolvedEvent Entrypoint
What the field agrees on
Claim	Evidence
Evidence mentions must stay outside the canonical DAG	ECR literature (X-AMR, ACCI), LOME, Graphiti — unanimous
A canonical/resolved event node is the right query entrypoint	Graphiti communities, ECR clusters, LOME coreference clusters — unanimous
The canonical layer must survive lower-layer rebuilds	Graphiti edge invalidation, LOME modular design — explicit
Bi-temporal modeling is required	Graphiti (T vs T'), NarrativeTime (narrative vs annotation time)
Sparse temporal graphs are acceptable	NarrativeTime containers, MATA anchors, TKG survey
Granularity hierarchy is required	TKG survey, ChronoGrapher sub-event model
Where the field pushes back on strict ResolvedEvent identity
Risk	Source	Mitigation
Strict identity is too brittle	CMU quasi-identity / event hoppers	ResolvedEvent should carry an identity-type attribute (strict vs. quasi/hopper)
Canonical node becomes orphaned if provenance is lost	LOME CONCRETE schema, Graphiti episodic back-links	ResolvedEvent must maintain back-links to EventMentions even after lower-layer rebuilds
Factual/hypothetical conflation	NarrativeTime timeline branches	EventMention must carry a factuality/modality flag before being promoted
Granularity incoherence	TKG survey, ChronoGrapher	ResolvedEvent needs a granularity/scale attribute
Temporal anchor sparsity	MATA	Maintain a small set of well-dated ResolvedEvents as temporal scaffolding
The core verdict
ResolvedEvent as the clean query entrypoint is well-supported by the field — with one non-negotiable condition: it must be an identity node with stable provenance back-links, not a derived summary that gets recomputed from scratch when lower layers change. The Graphiti model is the clearest production analog: community nodes (≈ ResolvedEvent) are stable query targets, but they maintain edges back to semantic entities (≈ Event) which maintain edges back to episodes (≈ Scene/EventMention). When a semantic entity is invalidated, the community node is updated — not replaced.
The field also consistently shows that the interpreted Event DAG (the layer between EventMention and ResolvedEvent) is the most volatile layer — it is where temporal relations, causal links, and sub-event hierarchies live, and it is the layer most likely to be rebuilt as new evidence arrives. This is exactly why ResolvedEvent must be decoupled from it: the canonical identity should survive DAG restructuring below it.
---
Key Sources for Further Reading
Source	URL	Why relevant
X-AMR (LREC-COLING 2024)	https://aclanthology.org/2024.lrec-main.920 (https://aclanthology.org/2024.lrec-main.920)	EID-based canonical event identity from mention arguments
Zep/Graphiti (arXiv 2501.13956)	https://arxiv.org/abs/2501.13956 (https://arxiv.org/abs/2501.13956)	Production three-tier episodic/semantic/community graph with bi-temporal model
CMU Event Coreference Dissertation (2024)	https://www.lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf (https://www.lti.cs.cmu.edu/research/dissertations/zhengzhl_phd_lti_2024.pdf)	Quasi-identity, event hoppers, graph-based coreference
NarrativeTime (LREC-COLING 2024)	https://aclanthology.org/2024.lrec-main.1054 (https://aclanthology.org/2024.lrec-main.1054)	Timeline-based annotation, narrative containers, factuality branches
Event-Centric TKG Survey (Mathematics 2023)	https://www.mdpi.com/2227-7390/11/23/4852 (https://www.mdpi.com/2227-7390/11/23/4852)	Full pipeline survey, granularity problem, OWL Time Ontology
LOME (arXiv 2101.12175)	https://arxiv.org/abs/2101.12175 (https://arxiv.org/abs/2101.12175)	Modular pipeline, CONCRETE schema, mention/cluster separation
MATA (ICLR 2026)	https://openreview.net/forum?id=9CwDDoag8I (https://openreview.net/forum?id=9CwDDoag8I)	Sparse temporal anchor memory for irregular TKGs