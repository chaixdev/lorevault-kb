# Minimal Relation Type Catalog

**Status:** NOT STARTED  
**Last Updated:** April 29, 2026

## Summary

Design and implement a minimal, curated catalog of named relation types that the extraction pipeline uses when writing typed semantic edges between entity nodes. This is the prerequisite for inter-entity edges (e.g., `participated_in`, `member_of`, `located_in`) that make the knowledge graph semantically traversable for Q&A.

## Problem

Today the graph has rich entity nodes (`BookIndividual`, `BookLocation`, `BookObject`, `BookCollective`, `BookEvent`) but no typed edges between them. The connections that exist are:

- `Scene -[:MENTIONS]-> EntityMention` (provenance only)
- `EntityMention -[:REFERS_TO]-> ChapterEntity -[:REFERS_TO]-> BookEntity` (resolution ladder)

There are no edges expressing that "Kirk participated in the Ceres incident," "the UNSC is located at Earth," or "Kevin Jenkins is a member of the crew." Without these, the graph cannot answer relational or structural questions, and the entity nodes are isolated knowledge islands.

Adding typed edges without a controlled vocabulary produces a graph where edge labels are inconsistent, unqueryable, and impossible to use for cross-entity reasoning. A minimal catalog of named relation types is the necessary foundation.

A second, deeper problem is that `MENTIONS` is too coarse. It currently conflates two semantically different things:

- **Direct occurrence** — the entity or event is actually present and happening in this scene
- **Indirect reference** — the scene refers to the entity or event (recalls it, discusses it, reports on it, is caused by it) but the entity or event is not itself occurring

This conflation causes the event resolution pipeline to treat a recalled or reported event as a new distinct event, because structurally it looks identical to an event that is actually happening. That is technically correct (a separate mention was extracted) but semantically harmful — it produces sparse resolution where the same real occurrence is fragmented into multiple BookEvent nodes: the occurrence itself and one or more "discussion of the occurrence" events that should not exist as standalone entities.

The catalog work must therefore also address how `MENTIONS` is split or replaced. At minimum, the brainstorm should decide:

1. Whether the distinction can be captured in a single indirect-reference bucket (e.g., `REFERS_TO_OFFSCENE`) or requires a curated vocabulary of referral types (recalled, reported, dreamed, caused by)
2. Whether the typed mention relations are complementary to the current `MENTIONS` edge (additive, with `MENTIONS` retained as a catch-all provenance relation) or replace it at the evidence layer

## Product Context

- Relational and structural questions ("how is X related to Y?", "which factions participated in event Z?") require typed inter-entity edges to answer with graph grounding
- The Q&A validation work (`qa-retrieval-quality-validation.md`) will identify exactly which relation types matter most — this catalog should be informed by those findings
- A controlled vocabulary makes the graph semantically stable: the same real-world relationship always uses the same edge label, regardless of how the LLM described it in text
- The catalog is also the foundation for the broader entity-claim model in `docs/concepts/` — the relation type table is the same structure whether claims are simple edges today or endorsed claim nodes in the future

## Technical Context

The conceptual model for the catalog is documented in:

- `docs/concepts/Entity-Event-Claim-model.md` — reltype table schema: `(id, label, description, subjectTypes, objectTypes, inverseId?, synonyms[], status, embedding)`
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — catalog microservice design, retrieval flow, provisional handling

The extraction pipeline touchpoints that will consume the catalog:

| Component | Role |
|---|---|
| `scene-analysis.txt` / `scene-analysis-usertemplate.st` | Scene-level extraction prompt — must reference catalog relation types |
| Triad analysis pipeline | Normalizes LLM output into structured entity/relation results |
| Entity persistence services | Write edges using relation type IDs from the catalog |
| `lorevault-web/src/main/resources/application.yml` | Configuration anchor for catalog seeding |

The catalog itself has no implementation today. The minimum viable form is a curated seed dataset (YAML or in-code) that covers the relation types needed for the first slice of typed edges.

**Initial candidate relation types** (to be refined during brainstorm):

| ID | Label | Subject types | Object types | Notes |
|---|---|---|---|---|
| `R:participated_in` | participated in | Individual, Collective | Event | Core event-entity link |
| `R:located_in` | located in | Individual, Collective, Event | Location | Scene/event spatial anchor |
| `R:member_of` | member of | Individual | Collective | Affiliation |
| `R:affiliated_with` | affiliated with | Individual, Collective | Collective | Looser than member_of |
| `R:instance_of` | instance of | Individual | Concept | Species membership, type assignment |
| `R:set_in` | set in | Event | Location | Event location (alternative framing) |

This list is illustrative, not final. The brainstorm and Q&A validation findings should drive the actual v1 set.

## Scope

1. **Catalog schema** — define the reltype table shape: `id`, `label`, `description`, `subjectTypes`, `objectTypes`, `inverseId?`, `synonyms[]`, `status`

2. **Seed dataset** — curate the minimal set of relation types needed to express the first slice of inter-entity edges; start from Q&A validation findings where available

3. **Storage and access** — decide the storage mechanism (YAML seed projected to Neo4j, in-code enum, or lightweight catalog nodes) and implement the access path used by the extraction pipeline

4. **Extraction integration** — update the scene-analysis prompt and triad normalization to emit typed relation IDs from the catalog when extracting entity relations

5. **Provisional handling** — define how the extraction handles relation descriptions that don't match a catalog entry; at minimum, log them as provisional rather than dropping or free-texting them

6. **Embedding (deferred or minimal)** — embedding-based retrieval of relation types is a future concern; v1 may use exact-match or simple keyword lookup

## Out of Scope

- Full hybrid BM25 + vector retrieval against the catalog (deferred; v1 uses simpler lookup)
- Property catalog (attributes of entities like `P:body.composition`) — separate concern
- Action catalog (`A:pull`, `A:push`) — not needed for this slice
- Confidence aggregation or claim-backed edge persistence — the catalog is compatible with both simple edges and future claim nodes; this item does not require claims
- Curation UI for reviewing and promoting provisional relation types
- Cross-universe or cross-book catalog federation

## Known Constraints / Prior Findings

- The conceptual model distinguishes relation types from properties (attributes) and actions — this catalog covers relation types only
- The entity taxonomy has six kinds (Individual, Collective, Object, Location, Concept, Event); relation type constraints (`subjectTypes`, `objectTypes`) must reference this taxonomy
- The Collective entity lane now exists. The Concept lane (`concept-resolution-lane.md`) must exist before relation types involving Concept targets can be used.
- The Q&A validation work should inform which relation types have the highest product value; it is reasonable to run validation first and use findings to seed this catalog
- The concepts catalog design calls for provisional handling (unknown relations get `provisional_*` IDs and queue for review) — this prevents free-text pollution of the edge label space
- Oracle's advisory on the relation vocabulary (session context, April 2026) recommends starting with the minimum set needed from reviewed failures and expanding from evidence, not speculatively
- The `MENTIONS` conflation problem is a known root cause of event resolution fragmentation: recalled/reported events are structurally indistinguishable from occurring events today, causing the ANN+LLM pipeline to treat them as distinct entities rather than references to the same occurrence
- Two broad referral buckets have been identified as the minimum split: (1) direct occurrence — entity/event is present and happening in this scene; (2) indirect reference — scene recalls, reports, or is caused by the entity/event. Whether the indirect bucket is a single type or a curated vocabulary is an open question for the brainstorm

## Open Questions

- Should the catalog live in Neo4j as `CatalogRelType` nodes (consistent with the graph-first stack), in a YAML seed file (version-controlled, easier to curate), or both?
- How should the extraction prompt communicate the catalog to the LLM? Feed top-K relevant entries per scene, or include the full small catalog?
- What is the right provisional handling behavior in v1 — log and skip, log and use the description as a fallback edge label, or queue for async review?
- Should `inverseId` be populated in v1 (e.g., `member_of` ↔ `has_member`) or deferred?
- How do we handle relation types that are directional vs. symmetric?
- **`MENTIONS` split**: Can indirect scene references (recalled, reported, dreamed, effect-of) be captured in a single bucket, or do they require a curated vocabulary? If a curated vocabulary, does it complement `MENTIONS` (additive) or replace it at the evidence layer?
- **`MENTIONS` migration**: If `MENTIONS` is split or replaced, what is the rollout strategy? Can the split be additive first (new typed relations alongside `MENTIONS`) before `MENTIONS` is retired, or is a clean re-ingestion the right move?

## Success Criteria

- A curated seed of at least 5–8 relation types is defined, covering the most important inter-entity relationships identified by Q&A validation
- The catalog has a clear storage and access mechanism that the extraction pipeline can use
- Scene analysis produces typed relation edges using catalog IDs for at least one relation type end to end
- Unknown relation descriptions are handled as provisionals rather than free-text edge labels
- The catalog schema is compatible with future embedding-based retrieval and the broader entity-claim model without requiring a migration

## Links

- `docs/concepts/Entity-Event-Claim-model.md` — reltype table schema and catalog design
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — catalog microservice, retrieval flow, provisional handling
- `docs/planning/qa-retrieval-quality-validation.md` — should inform which relation types matter most
- `docs/planning/concept-resolution-lane.md` — remaining Concept entity type that will be a relation target
- `docs/patterns/ingestion/entity-resolution-ladder.md` — entity resolution pattern
- `docs/patterns/ingestion/triad-analysis.md` — triad normalization pipeline that will consume the catalog
- `docs/brainstorm/entity-modeling/oracle_raw.md` — relation vocabulary advisory
