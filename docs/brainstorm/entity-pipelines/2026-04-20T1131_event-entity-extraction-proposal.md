# Event Entity Extraction Proposal — April 2026

**Date:** April 2026  
**Status:** Proposed direction  
**Purpose:** Capture LoreVault's current understanding of how extracted Event entities should complement the existing Scene timeline backbone, preserve evidence-vs-interpretation separation, and evolve toward a broader queryable Event DAG over time.

---

## Relationship to Existing Work

This document builds on the current understanding established by:

- the scene temporal-linking work and ADR 010 practical Allen relation usage
- the Event DAG concept direction
- the evidence-vs-interpretation layering discussion
- the already implemented regular entity mention-resolution ladders (currently Individual, Location, Object, and Collective)

This is not yet canonical truth.

It is the current best working proposal for how LoreVault should introduce extracted Event entities if implementation proceeds.

---

## Problem

LoreVault already persists `Scene` as the current backbone of its narrative timeline.

That gives us:

- durable scene boundaries
- reading-order adjacency
- scene-to-scene temporal constraints
- a sparse DAG backbone for local chronology

What it does **not** yet give us is a reusable anchor for broader temporal context such as:

- a battle referred to across many scenes
- a coronation remembered in one chapter and anticipated in another
- an investigation, voyage, war, or disaster that multiple scenes orient themselves around
- a way to position scenes relative to larger happenings without relying only on adjacent-scene inference

So the missing capability is not another scene type.

It is an **extracted event lane** that lets scenes point toward broader happenings while preserving the currently implemented scene-first temporal slice and leaving room for a larger interpreted Event DAG.

---

## Core Diagnosis

### 1. Scene and extracted Event are not the same job

`Scene` is already an event subtype in the broad conceptual sense, but in implementation it plays a very specific role:

- narrative slice
- persistence backbone
- chapter-local ordered unit
- source of scene-to-scene temporal edges

An extracted Event would serve a different role:

- reusable happening inferred or named from one or more scenes
- potential anchor for broader temporal reasoning
- something that may be past-relative in one scene, ongoing in another, and future-relative in a third

So extracted Event entities should **complement** scenes, not compete with them.

### 2. Events differ from regular entity ladders in one important way

The regular entity ladders are mostly identity ladders.

For Events, identity is not enough.

An event mention also carries an important **scene-relative classifier**:

- the event happened before this scene
- the event is happening during this scene
- the event will happen after this scene
- the scene contains or equals the event in some stronger sense

That classifier is not a stable property of the event itself.

It is a stable property of the **scene's relationship to that event**.

This is the key architectural difference.

---

## Proposed Solution in One View

LoreVault should introduce an explicit event evidence-and-resolution ladder:

- `Scene -[:CONTAINS]-> EventMention`
- `EventMention -[:REFERS_TO]-> ChapterEvent`
- `ChapterEvent -[:REFERS_TO]-> BookEvent`

This is the currently preferred first shape because it mirrors the proven mention ladder pattern while preserving the special semantics Events need.

The important additional rule is:

> `EventMention` must carry the scene-relative temporal classifier.

That means:

- the evidence layer says how a scene relates to an event
- the chapter/book layers provide scoped identity anchors
- the current persisted implementation remains scene-first while the broader conceptual target may grow beyond that

---

## Core Decisions

### 1. Keep the evidence/interpretation boundary explicit

The lower layer should preserve extraction-time evidence.

The upper layers should remain rebuildable interpretation.

So the model should distinguish:

- `EventMention` = evidence that a scene referred to some event-like happening in a specific way
- `ChapterEvent` = chapter-scoped grouping of compatible mentions
- `BookEvent` = book-scoped grouping of chapter-level event clusters

This follows the same conceptual discipline already used for Individuals and Locations.

### 2. Do not persist both `CONTAINS` and `TEMPORAL` between `Scene` and `EventMention`

It is tempting to put two separate edges between the same nodes:

- one provenance edge (`CONTAINS`)
- one temporal edge (`TEMPORAL`)

Current recommendation: **do not do this**.

Reason:

- `CONTAINS` expresses evidence/provenance
- `TEMPORAL` is already the language of LoreVault's timeline backbone
- using both on the same Scene/EventMention pair risks making evidence nodes look like first-class timeline nodes

Instead, keep:

- `Scene -[:CONTAINS]-> EventMention`

and put the temporal classifier on the mention itself.

### 3. Keep the scene-relative classifier on `EventMention`

This is the most important current conclusion.

`EventMention` should carry a field such as:

- `sceneRelativeRelation`

Candidate values could include:

- `before`
- `after`
- `during`
- `contains`
- `starts`
- `finishes`
- `equals`
- possibly `refers_to` or another weaker fallback when temporal positioning is too weak to classify

This belongs on `EventMention` because the same resolved event can be:

- `after` relative to one scene
- `during` relative to another
- `before` relative to a third

That is not a contradiction.

It is exactly the behavior the model must preserve.

### 4. Keep `ChapterEvent` and `BookEvent` thin by default

These nodes should begin as identity/anchor structures, not second-order scene containers.

They should not become the place where LoreVault stores:

- scene offsets
- timeline ordering logic
- scene-relative temporal truth
- an oversized accumulation of all event facts

Good default responsibilities for these nodes are instead:

- stable scoped IDs
- normalized naming
- mention counts / chapter counts
- representative pointers
- lightweight reduction metadata

This does **not** mean they are permanently forbidden from participating in a larger Event DAG.

Current working distinction:

- a **thin grouping node** is just a continuity/aggregation structure
- a **time-bearing node** denotes a narratively meaningful temporal abstraction

The same node may be both a query root and a DAG participant **only if** temporal predicates such as `before`, `after`, `during`, or `overlaps` are meaningful about the node itself.

### 5. Preserve a single sparse Event DAG concept while keeping the current implementation honest

The earlier wording in this proposal treated the Scene DAG as the only timeline backbone.

Current discussion has refined that point.

The stronger long-term idea is:

> LoreVault should aim for a **single sparse, auditable Event DAG** as its temporal backbone.

In that broader model:

- `Scene` is one event subtype inside the DAG
- extracted or resolved event anchors may also participate in the DAG
- `EventMention` remains in the evidence layer and is **not** itself a backbone DAG node

At the same time, the repository's current accepted implementation is still scene-first:

- persisted `TEMPORAL` edges are currently scene-to-scene
- structural adjacency remains separate from inferred temporal semantics
- aggregate event nodes are not yet implemented as temporal participants

So the current working rule is:

- **conceptually** allow the Event DAG to grow beyond scenes
- **practically** keep the current repo truthful about the fact that only scenes participate in persisted temporal edges today

This means:

- no event-driven replacement for scene↔scene edges in the first implementation slice
- no eager dense scene↔scene materialization through shared events
- no automatic assumption that every aggregate node belongs in the temporal DAG

---

## Proposed Graph Shape

### Evidence layer

Persist:

- `(:EventMention)`
- `(:Scene)-[:CONTAINS]->(:EventMention)`

The mention node carries scene-specific event semantics.

### Scoped aggregation layers

Persist thin scoped nodes:

- `(:ChapterEvent)`
- `(:BookEvent)`

And links:

- `(:EventMention)-[:REFERS_TO]->(:ChapterEvent)`
- `(:ChapterEvent)-[:REFERS_TO]->(:BookEvent)`

Potential ownership links, analogous to the other ladders:

- `(:Chapter)-[:HAS_EVENT]->(:ChapterEvent)`
- `(:Book)-[:HAS_EVENT]->(:BookEvent)`

These scoped nodes may later sit below additional layers such as:

- `(:SeriesEvent)`
- `(:UniverseEvent)`
- or a future scope-independent `(:ResolvedEvent)` / `(:EventIdentity)` layer

This proposal does not commit to the exact final root shape yet, but it now explicitly records that the ladder is expected to scale upward rather than stop at book scope.

---

## Proposed Node Responsibilities

### `EventMention`

Purpose:

- preserve scene-local evidence that some event-like happening was referred to
- preserve how the scene is positioned relative to that event
- preserve extraction metadata and provenance

This node answers:

> What event-like anchor did this scene refer to, and how did it relate to it?

Suggested fields:

- `displayName`
- `normalizedName`
- `aliases`
- `eventType` or `kind`
- `sceneRelativeRelation`
- `certainty`
- `evidence`
- `source`
- `sceneId`
- `chapterId`
- `bookId`
- `extractionIndex`
- `resolutionStatus`
- timestamps

### `ChapterEvent`

Purpose:

- chapter-scoped clustering of event mentions that appear to refer to the same broader happening

This node answers:

> Within this chapter, which event mentions appear to converge on the same event anchor?

It does **not** answer:

- the definitive global identity of the event
- the scene-relative temporal semantics of each mention

### `BookEvent`

Purpose:

- thin book-scoped anchor joining chapter-local event clusters

This node answers:

> Across this book, which chapter-level event clusters appear to refer to the same event anchor?

It should remain thin and conservative.

---

## Why Not Put Scene-Relative Temporal Truth On `BookEvent`?

Because that truth is not book-global truth.

It is viewpoint-relative.

Example:

- Scene A remembers the Winter War as already over
- Scene B depicts the Winter War onstage
- Scene C foreshadows the Winter War as still ahead

All three may resolve to the same `BookEvent`.

So the event identity may be shared, but the scene-relative relation is not.

That is why the classifier belongs at mention level.

---

## Direct Scene → Resolved Event Anchors

This remains the main open architectural fork.

### Current recommendation for v1

Do **not** make direct Scene → `ChapterEvent` or Scene → `BookEvent` links part of the canonical model in the first implementation slice.

Canonical anchoring should remain:

- `Scene -> EventMention -> ChapterEvent -> BookEvent`

### Why this is the current preference

- it preserves evidence/interpretation separation
- it avoids duplicating one claim across multiple layers
- it avoids drift between mention truth and aggregate truth
- it prevents `BookEvent` from becoming an accidental pseudo-canonical event identity layer

### Refined follow-up conclusion

The core issue is **not** that a root node cannot be both:

- a query entrypoint
- and a DAG participant

External research and current retrieval direction both support that dual purpose.

The real issue is narrower:

> a node should participate in the temporal DAG only if it denotes a stable, time-bearing narrative abstraction rather than merely a grouping bucket.

So the design fork is no longer:

- "query root vs DAG participant"

It is:

- "thin grouping aggregate vs time-bearing event anchor"

### Why `Scene -> BookEvent later` is not a satisfying answer

If LoreVault later expands to:

- `SeriesEvent`
- `UniverseEvent`

then `BookEvent` is revealed to be a scope-bound layer, not a final identity anchor.

That means a direct Scene → `BookEvent` link would move the goalpost rather than solving the modeling problem cleanly.

### What this implies

If LoreVault ever truly needs a direct scene-to-resolved-event anchor, the cleaner destination is likely **not** `BookEvent`.

It is more likely a future scope-independent node such as:

- `ResolvedEvent`
- `CanonicalEvent`
- `EventIdentity`

This proposal does **not** introduce that node yet.

But it records the pressure clearly:

> direct Scene → resolved-event anchoring should not hard-code a scope-bound layer as though it were the final event identity.

At the same time, current discussion now leans toward a future model where:

- a final scope-independent node such as `ResolvedEvent` may become the canonical query entrypoint
- traversal may climb from matched leaves to that root node
- traversal may then move outward across the broader graph and back down to evidence leaves/chunks for answer generation

That future direction is now considered plausible and well-supported, but still intentionally unimplemented.

---

## Scope

### In scope for the proposed first slice

- extending scene analysis structured output to include event-like extraction results
- persisting `EventMention` evidence nodes after real scene persistence
- linking `Scene -> EventMention` with `CONTAINS`
- storing scene-relative temporal classification on the mention
- deterministic or conservative chapter-level grouping into `ChapterEvent`
- deterministic or conservative book-level grouping into `BookEvent`
- reusing the existing post-scene persistence and event-driven ladder pattern where practical

### Out of scope for the proposed first slice

- replacing the Scene DAG with an event-driven backbone
- creating dense scene↔scene temporal edges through shared resolved events
- introducing direct Scene → `BookEvent` canonical links
- introducing a global `ResolvedEvent` / `CanonicalEvent` identity layer immediately
- cross-book, series-wide, or universe-wide event resolution
- franchise-scale event DAG reasoning
- automatic transitive temporal materialization

---

## Matching And Resolution Caution

Event identity is harder than Individual or Location identity.

Naive name matching will likely:

- over-merge generic recurring phrases like `the battle`, `the trial`, `the attack`
- under-merge unnamed references to the same event

So Event resolution should begin conservatively.

Likely useful signals:

- normalized name match
- alias overlap
- chapter/book proximity
- shared participants or locations later, once available
- temporal compatibility
- repeated evidence phrases

But the first implementation should avoid pretending event identity is solved more strongly than it is.

---

## Query Implications

This proposal suggests the following retrieval philosophy.

### Canonical query path in the current proposal

For faithful reasoning:

- start from `Scene`
- follow `CONTAINS` to `EventMention`
- follow `REFERS_TO` upward to chapter/book-level anchors

This preserves provenance and scene-relative temporal semantics.

### Emerging root-traversal retrieval model

The current discussion has made a broader retrieval shape clearer.

A likely long-term query flow is:

1. embed the user query
2. run bi-encoder retrieval over chunks and/or entities
3. if the matched node is not already a root aggregate, traverse upward until reaching the root aggregate / canonical identity node
4. use that root as the stable query entrypoint for broader graph traversal
5. traverse back downward toward leaf evidence such as mentions, scenes, and chunks
6. use those leaves for grounded ranking and later answer generation

In that model:

- scoped aggregate or resolved root nodes are not just bookkeeping
- they are part of the retrieval/navigation structure
- and some of them may also be part of the interpreted Event DAG if they satisfy the time-bearing rule above

### Broader temporal reasoning later

If LoreVault wants to place scenes in a broader temporal DAG through shared events, it should likely do so by:

- gathering all event mentions that resolve to the same higher-level event anchor
- composing those scene-relative constraints in the interpretation layer
- deriving broader placements without treating the aggregate node as if it carried one flattened temporal truth

This is additive reasoning on top of the current scene-first implementation, and may later become part of a richer single Event DAG.

---

## Suggested First Structured Output Shape

The first event extraction block should stay compact and useful.

Candidate extracted fields:

- `primaryName`
- `aliases`
- `eventType`
- `sceneRelativeRelation`
- `certainty`
- `evidence`
- optional short `description`

This is intentionally narrower than a full event ontology.

---

## Open Questions

### 1. Is `ChapterEvent` actually valuable, or just a staging boundary?

Current proposal keeps it because it matches existing ladders and gives a chapter-safe first grouping scope.

But if event identity mostly stabilizes only at book scope, LoreVault may later simplify to:

- `EventMention -> BookEvent`

### 2. Do we need a weaker non-temporal fallback for mentions?

Some scenes may refer to an event without enough evidence to classify it strongly as `before`, `during`, `after`, etc.

That suggests either:

- a fallback classifier value
- or a weaker event-reference mode distinct from stronger temporal anchoring

### 3. When should a scope-independent `ResolvedEvent` exist?

This proposal originally recorded only the architectural pressure.

Current discussion now strengthens that pressure considerably.

The strongest emerging case for `ResolvedEvent` is:

- a stable root query entrypoint above chapter/book/series/universe scopes
- a node that survives scope expansion without moving the goalpost from `BookEvent` to `SeriesEvent` to `UniverseEvent`
- a node from which LoreVault can traverse downward through the aggregation tree to leaf evidence and sideways through related graph structure

So while `ResolvedEvent` is still not yet accepted as an implemented layer, it is now the leading candidate for the long-term top-level query identity.

### 4. How much event extraction should be filtered?

Not every scene-local action deserves its own extracted event anchor.

The implementation will need practical rules for preferring:

- named events
- reusable narrative landmarks
- broader happenings that help relate distant scenes

over one-off microscopic actions that the Scene already represents adequately.

---

## Current Bottom Line

LoreVault should treat extracted events as **scene-independent anchors inferred from scenes**, not as replacements for scenes.

Current best proposal:

- `Scene -[:CONTAINS]-> EventMention`
- `EventMention.sceneRelativeRelation` carries the scene-relative temporal classifier
- `EventMention -[:REFERS_TO]-> ChapterEvent -[:REFERS_TO]-> BookEvent`
- `ChapterEvent` and `BookEvent` begin as thin scoped anchors, but some future aggregate/root event nodes may also become genuine DAG participants when they denote stable time-bearing abstractions
- the repository's current persisted temporal implementation remains scene-first, even if the broader conceptual target grows into a single sparse Event DAG

And the most important caution is:

> do not solve future scope expansion by pretending `BookEvent` is a final canonical event identity.

If LoreVault later needs direct stable scene-to-resolved-event anchoring beyond the evidence layer, that likely implies a future scope-independent event identity node rather than a stronger commitment to `Scene -> BookEvent`.

---

## Working Conclusions Since This Proposal

The following points have become much clearer through the later design discussion and external research review:

1. **Dual purpose is valid.** There is no inherent architectural conflict between a root aggregate serving as a query entrypoint and also participating in graph relationships.
2. **The real distinction is different.** The meaningful tension is not `query root vs DAG node`, but `thin grouping aggregate vs time-bearing narrative abstraction`.
3. **A simple promotion rule is emerging.** If `before`, `after`, `during`, or `overlaps` is meaningful about the node itself, that node is a candidate DAG participant. If it only means "all these mentions are grouped here," it is a grouping node only.
4. **Current LoreVault implementation is still scene-first.** The repository today persists temporal edges between Scene nodes. Any broader aggregate/event participation in persisted temporal edges remains future work.
5. **`ResolvedEvent` is now the leading long-term root candidate.** It appears to be the cleanest future top-level query identity above chapter/book/series/universe aggregation scopes, as long as it stays thin and preserves provenance back-links.
6. **External research strongly supports the ladder.** Recovered external analogues support a pattern of: evidence mentions remain alive, canonical/root nodes serve as query entrypoints, and provenance is preserved below them rather than by making the root passive.
