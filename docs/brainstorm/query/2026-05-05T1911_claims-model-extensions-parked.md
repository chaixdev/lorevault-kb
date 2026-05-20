# Parked: Claims Model Extensions Deferred to Future Slices

**Status:** Parked — not yet scoped for implementation

These topics were discussed and refined during the claims model working session. They are sound design decisions but are second-order concerns for Q&A utility — they earn their value after the core relation evidence harvesting loop (Slice 1) and catalog promotion path are working.

---

## 1. Event subspecies: NarrativeEvent vs ClaimedEvent

`Event` has two subspecies that must be distinguished at extraction time because their graph behavior differs.

### NarrativeEvent

Things that happened in the story world. Lives in the Event DAG. Participates in temporal relations (`NEXT_IN_READING_ORDER`, `before`, `after`, `contains`, `overlaps`, `meets`, `equals`). Has canonical narrative coordinates (`pubCoords`). Can be the subject or object of claims (`supported_by`, `disputed_by`, `preceded_by`, etc.).

### ClaimedEvent

Things alleged, rumored, or disputed to have happened. Does not enter the Event DAG. Its identity is the aggregate of assertions made about it. Lives in the claims layer. Has aggregate `status ∈ {alleged | supported | contested | confirmed}`. Can be the subject or object of meta‑claims (`asserted_by`, `heard_about`, `disputed_by`, `retracted`). Its provenance chain tracks the hearsay path.

### Why the distinction matters

"Ralph allegedly cheated on Wendy" extracted as a `NarrativeEvent` implies it happened. It may not have — it was claimed. A `ClaimedEvent` can be elevated to a `NarrativeEvent` when sufficient supporting evidence accumulates or an explicit narrative passage confirms it; this link is itself a claim (`R:substantiated_as`).

When a `ClaimedEvent` is confirmed, the system adds a `R:substantiated_as` claim from the `ClaimedEvent` to the corresponding `NarrativeEvent`. This is a claim like any other, not a state transition — the provenance chain on the `ClaimedEvent` is preserved.

Both subspecies use `E:event/<slug>` with a different `label`/`kind` value. The constraint system on relation types uses `subjectTypes`/`objectTypes` to specify which subspecies is valid at each endpoint.

### Entity kind taxonomy summary

| Kind | Subspecies | DAG? | Temporal relations? | Claim behavior |
|---|---|---|---|---|
| Event | NarrativeEvent | Yes | Yes | Normal |
| Event | ClaimedEvent | No | No — status-gated | Meta‑claims only until substantiated |

---

## 2. Claims as nodes and the hearsay chain

Claims are first-class graph nodes, not just provenance metadata on edges. A claim can be the subject or object of other claims. This is required to model hearsay chains faithfully:

```
E:individual/narrator -[:R:told]-> C:narrator-friend-call
C:narrator-friend-call -[:R:heard_about]-> C:sister-claimed-cheating
C:sister-claimed-cheating -[:R:asserted_by]-> E:individual/sister
C:sister-claimed-cheating -[:R:subject]-> E:individual/ralph
C:sister-claimed-cheating -[:R:object]-> E:individual/wendy
C:sister-claimed-cheating -[:R:rel_type]-> R:cheated_on
```

Each step in the chain is a claim node. The terminal claim ("Ralph cheated on Wendy") is an allegation — its aggregate status is what determines whether it's substantiated. `R:told` and `R:heard_about` are canonical relation types.

### Implications

- The graph is a **hypergraph**: edges can be nodes. A `Claim` node participates in multiple relations — to asserters, to subject entities, to object entities, to relation types.
- The `reltypes` catalog must accommodate relation types where either subject or object is a claim node.
- The aggregate confidence roll-up applies to the claim-as-node, not only to projected edges.
- Claims about claims handle retractions, disputes, and confirmed corrections.

### Constraint enforcement for meta-layer relations

Meta-layer relations (`R:heard_about`, `R:asserted_by`, etc.) bypass `subjectTypes`/`objectTypes` constraint checks. The validation layer handles them as special-cased relation types with their own endpoint type signature:

- `Claim → Entity` for relations like `ASSERTED_BY`
- `Claim → Claim` for relations like `HEARD` (claim chain continuation)

---

## 3. Relation type taxonomy (three bins)

This taxonomy is a future categorization aid, not the starting extraction vocabulary. The first slice should let the LLM preserve open-ended semantic relation phrases, store them as provisional observations, and use accumulated evidence to decide which clusters deserve canonical relation IDs. These bins become useful during review/promotion, not as a rigid prompt menu.

Relations fall into three bins based on the semantic axis they capture:

### Bin 1 — SPATIAL (where something is in relation to something else)

- `R:located_in` — entity ↔ location containment (X is at Y)
- `R:part_of` — entity ↔ container membership (X is part of Y)
- `R:occurs_at` — event ↔ location (X happened at Y)

### Bin 2 — PARTICIPANT (who is involved with what)

- `R:participated_in` — individual ↔ event (X was at Y)
- `R:member_of` — individual ↔ collective (X is a member of Y)
- `R:affiliated_with` — collective ↔ collective / collective ↔ location (X operates in Y)

### Bin 3 — INFLUENCE (how one entity affects or enables another)

- `R:assisted` — individual contributed to event/collective
- `R:enabled` — entity made another entity possible
- `R:created_by` — entity was authored/produced by entity
- `R:opposed` — entity actively resisted another entity

### Dead letter handling

Unknown relation types are emitted as `R:provisional.<normalized_phrase>` with the raw surface text, usage hint, endpoint kinds, and evidence examples. The review operator clusters and promotes useful observations into canonical relation IDs; the three bins are one possible categorization lens, not a required target for every relation.

### Relation directionality

Each relation type has exactly one canonical direction, stored on the edge. `R:parent_of` is canonical from parent → child; `R:child_of` is derived on render via `inverseId`. Ordered pairs are not needed — the edge type encodes directionality.

### Entity pair validity

Not all entity type combinations are valid for promoted relation types. `subjectTypes[]` and `objectTypes[]` constraints should be applied during catalog matching and promotion, not used to suppress the initial raw observation. If the LLM extracts a strange endpoint pair, preserve it as evidence and let review decide whether it reflects a bad extraction, a missing entity kind, or a legitimate relation.

---

## 4. Relation types for the claims meta-layer

These relation types are seeded separately from the three bins:

- `R:heard_about` — source entity heard a claim
- `R:asserted_by` — claim was made by source entity
- `R:disputed_by` — source entity disputed a claim
- `R:supported_by` — source entity supported a claim
- `R:retracted` — source entity retracted a prior claim
- `R:substantiated_as` — ClaimedEvent confirmed as a NarrativeEvent

---

## 5. Catalog module expansion

The catalog is resurfacing as a relation-vocabulary intelligence module. It should likely begin as an in-process module / bounded context in the modulith, not a separately deployed microservice. It owns relation names, definitions, aliases, examples, embeddings or matching logic, provisional observations, cluster metadata, and promotion/merge status.

It should not own scene analysis, claim storage, entity resolution, edge projection, Q&A retrieval routing, or Allen-style temporal relations.

Example request:

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

The full catalog design includes:

### Two distinct retrieval paths

**Path A — Relation evidence harvesting (during ingestion)**: The scene analysis LLM emits open-ended relation claims with a short name, usage hint, endpoints, evidence, certainty, and `pubCoords`. The catalog module then attempts candidate matching. If a strong match exists, the claim can carry that candidate `relTypeId`; otherwise it remains a provisional observation such as `R:provisional.turned_against`.

**Path B — Graph-aware query routing (at query time)**: Once relation clusters are promoted to canonical IDs, retrieval can traverse stable `REL` edges and choose which relation types to expand along. Before promotion, provisional relations are best treated as diagnostic or experimental signals rather than durable query semantics. See `2026-04-12_graph-aware-qa-design-april-2026.md`.

### Entity pair constraints

`subjectTypes[]` and `objectTypes[]` on promoted reltype entries filter which entity kinds are valid at each endpoint. During evidence harvesting, endpoint kinds are stored as matching signals rather than hard rejection rules. The constraint system also distinguishes `NarrativeEvent` from `ClaimedEvent` — only `NarrativeEvent` nodes enter the Event DAG and participate in temporal relations.

### Dead letter queue

Relation observations that do not confidently match a promoted catalog entry are emitted as `R:provisional.<normalized_phrase>` and queued for clustering/post-processing. The review operator promotes, merges, deprecates, or leaves them provisional.

---

## Open questions (not yet decided)

- **Substantiation trigger**: What count or confidence threshold promotes a `ClaimedEvent` to `NarrativeEvent`? Automatic (aggregate confidence ≥ threshold), manual (operator action), or hybrid? Policy not yet defined.
- **Claim deduplication key**: What makes two claims the same assertion for deduplication purposes — `(subject, relTypeId, objectId, pubOrdinal)`? Needed to prevent re-ingestion from doubling the claim history.
- **Migration path**: How do existing `Mention`-based edges coexist with the new claim model during transition?
- **`R:occupation` and property-native relations**: `R:occupation` does not fit cleanly in the three bins — it may be more naturally an Ascription (`P:occupation`) with claim history. This is a catalog entry design question, not a retrieval blocker.
- **Catalog matching policy**: What correlation threshold is enough to attach a canonical `relTypeId` automatically, and when should the system store only candidates plus a provisional observation?
