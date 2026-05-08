# Relation Catalog Discovery Module

**Status:** NOT STARTED  
**Last Updated:** May 05, 2026

## Summary

Design and implement the first slice of a relation catalog module that can receive open-ended LLM relation descriptions, return candidate relation IDs with semantic correlation scores, and preserve unmatched observations for later clustering and curation. The goal is not to force scene analysis into a small predefined relation list; the goal is to retain semantic detail while creating a path toward stable, queryable relation IDs.

## Problem

Today the graph has rich entity nodes (`BookIndividual`, `BookLocation`, `BookObject`, `BookCollective`, `BookEvent`) but no typed edges between them. The connections that exist are:

- `Scene -[:MENTIONS]-> EntityMention` (provenance only)
- `EntityMention -[:REFERS_TO]-> ChapterEntity -[:REFERS_TO]-> BookEntity` (resolution ladder)

There are no edges expressing that "Kirk participated in the Ceres incident," "the UNSC is located at Earth," or "Kevin Jenkins is a member of the crew." Without these, the graph cannot answer relational or structural questions, and the entity nodes are isolated knowledge islands.

Adding typed edges without vocabulary management produces a graph where edge labels are inconsistent, unqueryable, and hard to use for cross-entity reasoning. But forcing the LLM into a tiny predefined set also loses the semantic richness that prose carries. Relation extraction should therefore start with open-ended relation claims, while a catalog module records candidate matches and accumulates provisional observations until useful clusters emerge.

A second, deeper problem is that `MENTIONS` is too coarse. It currently conflates two semantically different things:

- **Direct occurrence** — the entity or event is actually present and happening in this scene
- **Indirect reference** — the scene refers to the entity or event (recalls it, discusses it, reports on it, is caused by it) but the entity or event is not itself occurring

This conflation causes the event resolution pipeline to treat a recalled or reported event as a new distinct event, because structurally it looks identical to an event that is actually happening. That is technically correct (a separate mention was extracted) but semantically harmful — it produces sparse resolution where the same real occurrence is fragmented into multiple BookEvent nodes: the occurrence itself and one or more "discussion of the occurrence" events that should not exist as standalone entities.

The catalog work must therefore also address how `MENTIONS` is split or replaced. At minimum, the brainstorm should decide:

1. Whether the distinction can be captured in a single indirect-reference bucket (e.g., `REFERS_TO_OFFSCENE`) or requires a curated vocabulary of referral types (recalled, reported, dreamed, caused by)
2. Whether the typed mention relations are complementary to the current `MENTIONS` edge (additive, with `MENTIONS` retained as a catch-all provenance relation) or replace it at the evidence layer

## Product Context

- Relational and structural questions ("how is X related to Y?", "which factions aligned with Z?") require more than co-mention evidence; they need preserved inter-entity relation claims.
- Q&A validation should identify which relation questions hurt most, but the catalog should also learn from actual extraction output rather than only from a hand-authored taxonomy.
- Vocabulary stability is still necessary for query-time traversal, but stability should emerge through candidate matching, clustering, review, and promotion.
- The catalog is the bridge between raw claims and future stable typed edges: raw relation phrases remain evidence, while promoted catalog IDs become traversal semantics.

## Technical Context

The conceptual model for the catalog is documented in:

- `docs/concepts/Entity-Event-Claim-model.md` — reltype table schema: `(id, label, description, subjectTypes, objectTypes, inverseId?, synonyms[], status, embedding)`
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — catalog module design, retrieval flow, provisional handling

The extraction pipeline touchpoints that will produce or consume relation catalog data:

| Component | Role |
|---|---|
| `scene-analysis.txt` / `scene-analysis-usertemplate.st` | Scene-level extraction prompt — emits open-ended relation claims and usage hints |
| Triad analysis pipeline | Normalizes LLM output into structured relation-claim observations |
| Relation catalog module | Maps `{name, usageHint, subjectKind, objectKind}` to candidate relation IDs and correlations |
| Claim persistence | Stores raw phrase, candidates, provisional key, evidence, certainty, and `pubCoords` append-only |
| Entity projection services | Later replay promoted relation claims into stable typed edges |
| `lorevault-web/src/main/resources/application.yml` | Configuration anchor for catalog seeding |

The catalog itself has no implementation today. The minimum viable form is an in-process module inside the existing modulith, not a separately deployed microservice. It can start with no or very few canonical entries; its primary v1 value is preserving observations and candidate-match results so real extraction data can drive the first promoted vocabulary.

Example query shape:

```json
{
  "name": "betrayed",
  "usageHint": "the general turned against the king and deposed him",
  "subjectKind": "Individual",
  "objectKind": "Individual"
}
```

Example response:

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

If no candidate clears the confidence threshold, the relation claim receives a provisional key such as `R:provisional.turned_against` while preserving the raw phrase and evidence. Provisional keys are observations, not accepted taxonomy.

## Scope

1. **Catalog module boundary** — define an in-process relation catalog capability that owns relation definitions, candidate matching, provisional observations, cluster review metadata, and promotion/merge status.

2. **Open-ended extraction contract** — update scene analysis to emit relation claims with `subject`, `relationName`, `usageHint` / `relationDescription`, `object`, evidence text, certainty, source/chunk/scene provenance, and `pubCoords`.

3. **Candidate matching contract** — define the request/response shape for resolving `{name, usageHint, subjectKind, objectKind}` to candidate catalog IDs with correlations and descriptions.

4. **Relation claim persistence** — store raw relation phrase, normalized provisional key, candidate matches, selected match if any, evidence, certainty, and `pubCoords` append-only.

5. **Provisional harvest** — aggregate unmatched or low-confidence relation observations by normalized phrase, endpoint kinds, frequency, evidence examples, and candidate cluster.

6. **Projection policy** — distinguish stable `REL {relTypeId}` edges from exploratory `PROVISIONAL_REL` views. Stable query traversal should depend on promoted catalog IDs; provisional views are diagnostic or experimental.

## Out of Scope

- Full hybrid BM25 + vector retrieval against the catalog (deferred; v1 may use simple fuzzy matching or embeddings only if cheap)
- Property catalog (attributes of entities like `P:body.composition`) — separate concern
- Action catalog (`A:pull`, `A:push`) — not needed for this slice
- Full confidence aggregation over competing relation claims
- Complete curation UI for reviewing and promoting provisional relation types; v1 only needs review-ready data output
- Stable graph-aware routing over relation IDs; routing should wait until enough relation IDs are promoted
- Cross-universe or cross-book catalog federation

## Known Constraints / Prior Findings

- The conceptual model distinguishes relation types from properties (attributes) and actions — this catalog covers relation types only
- The entity taxonomy has six kinds (Individual, Collective, Object, Location, Concept, Event); relation type constraints (`subjectTypes`, `objectTypes`) must reference this taxonomy
- The Collective entity lane now exists. The Concept lane (`concept-resolution-lane.md`) must exist before relation types involving Concept targets can be used.
- The Q&A validation work should inform which relation questions have the highest product value, but actual relation IDs should be learned from observed extraction output and curated after enough evidence accumulates.
- Provisional handling prevents free-text pollution of the stable edge label space while still preserving the LLM's semantic detail.
- Oracle's advisory on the relation vocabulary (session context, April/May 2026) recommends expanding from evidence, not speculatively; the catalog module should make that evidence loop explicit.
- The `MENTIONS` conflation problem is a known root cause of event resolution fragmentation: recalled/reported events are structurally indistinguishable from occurring events today, causing the ANN+LLM pipeline to treat them as distinct entities rather than references to the same occurrence
- Two broad referral buckets have been identified as the minimum split: (1) direct occurrence — entity/event is present and happening in this scene; (2) indirect reference — scene recalls, reports, or is caused by the entity/event. Whether the indirect bucket is a single type or a curated vocabulary is an open question for the brainstorm

## Open Questions

- Should canonical catalog entries live in Neo4j as `CatalogRelType` nodes, in YAML/JSON seed files, or both after the first promotion pass?
- Should the first mapping implementation use lexical/fuzzy matching, embeddings, an LLM judging step, or a staged combination?
- What correlation threshold is high enough to attach a canonical `relTypeId` automatically versus storing only candidates?
- Should provisional observations be materialized as `PROVISIONAL_REL` edges for diagnostics, or kept only in the claim/catalog observation store until promotion?
- Should `inverseId` be populated in v1 (e.g., `member_of` ↔ `has_member`) or deferred?
- How do we handle relation types that are directional vs. symmetric?
- **`MENTIONS` split**: Can indirect scene references (recalled, reported, dreamed, effect-of) be captured in a single bucket, or do they require a curated vocabulary? If a curated vocabulary, does it complement `MENTIONS` (additive) or replace it at the evidence layer?
- **`MENTIONS` migration**: If `MENTIONS` is split or replaced, what is the rollout strategy? Can the split be additive first (new typed relations alongside `MENTIONS`) before `MENTIONS` is retired, or is a clean re-ingestion the right move?

## Success Criteria

- Scene analysis emits open-ended relation claims with enough context for later semantic mapping.
- Relation claims are persisted append-only with raw phrase, usage hint, evidence, provenance, `pubCoords`, and candidate catalog matches.
- The catalog module exposes a clear candidate-matching contract and stores provisional observations without treating them as accepted taxonomy.
- Unknown or low-confidence relation descriptions receive provisional keys rather than becoming stable free-text edge labels.
- A review-ready relation harvest exists: counts, endpoint kinds, examples, and candidate clusters that can drive the first promoted catalog entries.
- The design remains compatible with future stable `REL` projection, embedding-based matching, and the broader entity-claim model without requiring evidence migration.

## Links

- `docs/concepts/Entity-Event-Claim-model.md` — reltype table schema and catalog design
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — catalog module, retrieval flow, provisional handling
- `docs/planning/qa-retrieval-quality-validation.md` — should inform which relation types matter most
- `docs/planning/concept-resolution-lane.md` — remaining Concept entity type that will be a relation target
- `docs/patterns/ingestion/entity-resolution-ladder.md` — entity resolution pattern
- `docs/patterns/ingestion/triad-analysis.md` — triad normalization pipeline that will consume the catalog
- `docs/brainstorm/entity-modeling/oracle_raw.md` — relation vocabulary advisory
