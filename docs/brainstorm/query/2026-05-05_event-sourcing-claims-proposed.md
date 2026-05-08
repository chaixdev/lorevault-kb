# Claims as Event-Sourcing: Spoiler-Gated Relation State Reconstruction

**Status:** Proposed — Slice 3 of the claims model

**Relationship to core doc:** This is a child of `core-domain-model-and-graph-process-restructured.md`. It depends on the relation evidence harvesting slice (claims exist with raw relation descriptions, catalog candidates, and `pubCoords`) and later graph-aware retrieval expansion.

---

## Core principle

Inter-entity relations are not projection-time static facts. They are derived at query time from an append-only claim history, filtered by the reader's progress position. The canonical relation state is replayed, not stored.

This is the event-sourcing analogy: the claim log is the event stream; the relation state is the materialized view.

---

## Why this matters

A character's occupation, affiliation, or role can change across a narrative.

```
Chapter 1:  JohnDoe is a police officer
Chapter 2:  JohnDoe is a private detective
Chapter 3:  JohnDoe is an INTERPOL agent
```

All three states are real. The graph must not collapse them to a single projected edge. Instead, all three are claims in an append-only log, and the reader's `readThroughChapterNumber` determines which claims are visible.

---

## Pattern

```
E:individual/john_doe -[:CLAIM {relationName:'served as', provisionalRelTypeId:'R:provisional.served_as', pubOrdinal:1001}]-> E:concept/police_officer
E:individual/john_doe -[:CLAIM {relationName:'became', provisionalRelTypeId:'R:provisional.became', pubOrdinal:1005}]-> E:concept/private_detective
E:individual/john_doe -[:CLAIM {relationName:'joined', candidateRelTypeId:'R:affiliated_with', candidateCorrelation:0.82, pubOrdinal:1010}]-> E:collective/INTERPOL
```

At query time, the retrieval path reconstructs the relation state by:

1. Collecting all claims on `(subject, relation meaning)` up to `pubOrdinal ≤ readerBoundaryOrdinal`; before catalog promotion this may use provisional clusters, after promotion it uses canonical `relTypeId`
2. The most recent claim (by `pubOrdinal`) is the canonical state at that boundary
3. All prior claims remain as provenance history

---

## Retrieval path impact

Graph-aware expansion is extended by per-boundary claim replay:

- A question answered at chapter 2 looks at a different visible claim set than the same question answered at chapter 5
- The aggregation deduplicates across bins at query time
- The claim history itself is preserved per boundary — no claim is deleted when a newer one overrides it

---

## Contrast with the Event DAG

Temporal relations on `NarrativeEvent` nodes work differently. A `NarrativeEvent` either exists at a given point in the DAG or it doesn't — there is no "in the DAG at chapter 1, out at chapter 3." Once in, it's always there.

Claims on `ClaimedEvent` nodes, by contrast, accumulate. The `ClaimedEvent`'s aggregate status (`alleged | supported | contested | confirmed`) is what updates as new claims come in. Once substantiated and linked via `R:substantiated_as`, the resulting `NarrativeEvent` enters the DAG like any other.

---

## Projection layer: materialized but invalidatable

Projected edges (`REL`, `HAS_PROPERTY`, `COMP`) are materialized but invalidatable cached views — not the canonical store. They are recomputed from the claim history when:

- A scene is reprocessed (upstream claim changes)
- A processing stage is replayed
- An operator triggers a recompute

The existing `chapterId`/provenance chain on scene and chunk nodes provides the invalidation trigger. When a scene is reprocessed, affected claim aggregates are marked stale and downstream materialized edges are recomputed. This ties into the existing stage-run DAG invalidation mechanism.

The canonical store is the append-only claim log. Edges are derived views.

---

## What this defers

Implementing full per-boundary claim-history replay is a meaningful scope addition. For MVP:

- The system stores the claims correctly
- Materializes aggregates as edges
- Recomputes them on reprocessing

Per-boundary replay for reader-facing spoiler scoping is a later optimization once the claim accumulation rate and query volume are understood.

---

## Relationship to other slices

- **Relation evidence harvesting**: Provides the raw relation claim records, provisional relation observations, catalog candidates, and `pubCoords` that this slice replay operates on
- **Graph-aware retrieval**: Uses the expanded entity graph after enough relation clusters are promoted; per-boundary replay determines which relation states are visible at the reader's boundary
- **Slice 4** (`ClaimedEvent` / hearsay chain): The `ClaimedEvent` aggregate status update is itself a form of claim accumulation; per-boundary replay for contested/alleged events is a special case of this pattern
