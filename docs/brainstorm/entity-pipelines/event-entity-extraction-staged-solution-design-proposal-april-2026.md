# Event Entity Extraction Staged Solution Design Proposal — April 2026

**Date:** April 2026  
**Status:** Proposed direction  
**Purpose:** Capture the current best implementation-oriented proposal for Event entity resolution in LoreVault using a semantic-first hybrid reduction pipeline: rolling-triad entity likeness analysis, local aggregate cards, ANN candidate generation, and bounded semantic verification.

---

## Relationship to Earlier Material

This document replaces the earlier narrower staged proposal with the current stronger design shape.

It sharpens and stages the converged direction from:

- `event-entity-extraction-proposal-april-2026.md`
- `event-entity-extraction-external-research-verbatim-april-2026.md`
- current Individual / Location ladder implementations
- current triad-analysis implementation and later architecture review

Those documents remain useful as option-space history and design record.

This document is the cleaner proposal for the next Event-focused implementation path.

---

## Problem

LoreVault currently has:

- persisted `Scene` nodes
- a scene-first temporal backbone
- implemented mention-resolution ladders for Individuals and Locations
- vector retrieval over chunk embeddings
- a growing query model that already benefits from thin scoped aggregate nodes as traversal anchors

What it still lacks is a robust Event resolution lane that can handle all of the following at once:

- scene-local evidence that some broader event-like happening was mentioned
- scene-relative temporal semantics of that mention
- local semantic grouping when the same event is referred to across nearby scenes
- long-range recurrence without chapter-wide or book-wide full cross-product matching
- stable scoped aggregate nodes for query-time traversal

The key design pressure is now clearer than before:

> Event identity is harder than Individual or Location identity, so LoreVault needs a hybrid reduction strategy rather than only local-window grouping or only higher-scope ANN matching.

---

## Executive Summary

LoreVault should implement Event entity resolution in **stages**, but the internal reduction logic should now be treated as a **hybrid pipeline**.

The recommended event-first implementation shape is:

1. persist `EventMention` evidence
2. keep scene-relative temporal semantics on `EventMention`
3. use rolling-triad semantic likeness analysis as the primary grouping mechanism
4. append mentions to local aggregate nodes as rolling triads progress
5. build deterministic aggregate cards for those local aggregates
6. embed those cards and run ANN candidate generation **book-wide first**
7. run type-tuned semantic merge verification only on bounded ANN candidates
8. let chapter be the first spoiler-safe rich aggregate resolution boundary
9. keep higher-scope cross-chapter aggregates thin

This proposal explicitly rejects three tempting but unstable shortcuts:

- no alias-first preliminary grouping as the primary reduction strategy
- no chapter/book full cross-product semantic comparison
- no separate always-on LLM summary-synthesis stage before embeddings

And it also rejects one premature end-state:

- no direct universe-wide `ResolvedEvent` as the primary write target in the first implementation path

---

## Core Decisions

### 1. The first slice remains an evidence lane, not a new temporal backbone

The current persisted temporal backbone remains scene-first.

That means:

- `Scene` remains the current persisted `TEMPORAL` participant
- the Event lane adds evidence and aggregate nodes
- Event resolution does not yet change scene-to-scene temporal write rules

### 2. `EventMention` is still the must-build foundational node

`EventMention` remains the safest and most valuable first unit because it captures the thing LoreVault does not currently preserve well enough:

- that a scene referred to some broader event-like happening
- how the scene relates to that event

This evidence is the substrate for every later grouping, retrieval, and broader temporal reasoning step.

### 3. The scene-relative temporal classifier belongs on `EventMention`

This remains stable.

Each `EventMention` should carry a scene-relative classifier such as:

- `before`
- `after`
- `during`
- `contains`
- `starts`
- `finishes`
- `equals`
- optionally a weaker fallback when temporal positioning is too uncertain

This remains mention-level truth because the same underlying event may be:

- remembered in one scene
- onstage in another
- anticipated in a third

### 4. The canonical Scene/EventMention write path remains evidence-only

V1 should still keep the Scene/EventMention relationship simple and provenance-oriented:

- `(:Scene)-[:MENTIONS]->(:EventMention)`

Do **not** add:

- `TEMPORAL` edges to `EventMention`
- direct `Scene -> ChapterEvent`
- direct `Scene -> BookEvent`

### 5. Semantic likeness analysis is the primary grouping mechanism

This proposal now adopts a stronger decision than the earlier draft.

LoreVault will not use alias-first preliminary grouping as the primary reduction strategy for this Event pipeline.

Reason:

- semantic likeness analysis is required anyway
- alias-first grouping is weaker than the semantic reducer we already need
- keeping alias-first as the main reducer would bias the architecture around a less accurate early decision

That means the new baseline is:

1. rolling-triad semantic likeness analysis forms local aggregates
2. aggregate cards are built from those local aggregates
3. ANN retrieves broader candidate merges
4. semantic likeness analysis verifies broader merges

Alias/exact matching may still remain useful as a supporting signal or later optimization, but it is no longer the conceptual backbone of this design.

### 6. Local semantic grouping should happen before ANN candidate retrieval

Pure rolling triads are not enough to catch long-range recurrence.

But they are still the right local semantic reducer because they:

- give bounded narrative context
- reuse the existing triad-local reasoning shape
- avoid immediate chapter-wide semantic cross-product comparison

So triads should produce the local aggregate layer that the rest of the pipeline operates on.

### 7. No separate always-on LLM summary synthesis stage

This is the most important refinement from the later architecture review.

Do **not** add a separate default LLM call whose only job is to summarize each local aggregate before embedding.

Instead:

- have the local grouping/verdict step emit enough structured information to support later compaction
- then build a deterministic aggregate card from that structure

Reason:

- lower cost
- less drift
- less lossy than paraphrased summaries
- better preservation of discriminative identity signals

### 8. ANN is a candidate-generation layer, not a merge decision layer

ANN should be used to avoid exhaustive pairwise comparison.

It should **not** be trusted as final identity truth.

So the rule is:

- ANN proposes
- semantic verifier decides

That means:

- no auto-merge from vector neighborhood alone
- no cluster-size-only merge rule
- every proposed merge still needs direct verification

### 9. Book-wide first; series-wide later; universe-wide offline only

The first ANN pass should be **book-wide**.

Reason:

- book is already an implemented scope boundary
- query-side already benefits from book-scoped entry nodes
- the current event-driven ladder and completion semantics are book-aware
- book-wide search catches long-range recurrence without full-universe noise

If that works well:

- later add series-wide reduction using already book-resolved entities

For now:

- universe-wide resolution should be offline reconciliation / suggestion generation only
- not the primary hot-path write target

### 10. `BookEvent` remains thin and conservative

`BookEvent` should remain structurally useful but semantically conservative.

It should begin as:

- a continuity/aggregation structure
- a query traversal anchor
- a rebuildable interpretation-layer node

It should not yet become:

- a giant accumulated fact bag
- a direct carrier of flattened scene-relative temporal truth
- a guaranteed final `ResolvedEvent`

---

## Proposed Hybrid Reduction Pipeline

## Stage 1 — Persist `EventMention`

### Goal

Add Event extraction as a persisted evidence lane after scene persistence.

### Required output

- scene analysis structured output includes extracted events
- `EventMention` nodes are persisted
- `Scene -[:MENTIONS]-> EventMention` edges are persisted
- mention nodes retain enough provenance to audit later grouping decisions

### Recommended minimum fields

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

---

## Stage 2 — Rolling-triad semantic likeness analysis

### Goal

Use bounded narrative context to form the first local Event aggregates directly.

### Rule

Rolling-triad semantic likeness analysis is the primary grouping mechanism.

### Output

- mention-to-local-aggregate links as rolling triads progress
- local aggregate nodes that accumulate the triad-local grouping result
- unresolved mentions remain allowed when likeness analysis is uncertain

---

## Stage 3 — Deterministic local aggregate-card construction

### Goal

Convert each provisional local aggregate into a deterministic embedding card.

### Why this exists

The ANN stage needs a compact representation, but freeform summaries are too lossy and too expensive if produced by a separate LLM pass.

### Recommended card contents

Build the card deterministically from structured fields such as:

- canonical/local representative name
- aliases
- event type / kind
- important descriptors
- participant hints (when available)
- location hints (when available)
- scene-relative relation distribution
- provenance counts
- representative evidence snippets
- scope coordinates (chapter/book/universe as relevant)

### Important rule

This card is for embedding and candidate generation.

It is not canonical truth.

---

## Stage 4 — Book-wide ANN candidate generation

### Goal

Catch long-range recurrence without chapter-wide or book-wide full cross-product comparison.

### Scope

Book-wide first.

### Rule

ANN should retrieve bounded candidate pools only.

It should not decide merges.

### Candidate policy

Use ANN as the main broad-scope candidate generation mechanism for local aggregates.

That means candidate pools are derived from:

- embedded local aggregate cards
- ANN top-K retrieval within the target scope

### Practical safeguards

- similarity band threshold
- top-K cap
- cluster-size cap
- type-specific veto rules

---

## Stage 5 — Semantic merge verification

### Goal

Run a type-tuned semantic verifier only on bounded candidate sets.

### Role

This is the final merge gate.

ANN proposes. Verifier decides.

### Output

For each proposed candidate pair or small candidate set:

- merge
- keep separate
- unresolved / insufficient evidence

### Important caution

Do not rely on transitive closure of prior merge decisions.

If A≈B and B≈C, that does **not** make A≈C automatically true.

Each merge pair still needs direct verification.

---

## Stage 6 — Write scoped aggregates

### Chapter layer

Persist:

- local aggregate nodes formed by rolling-triad likeness analysis
- chapter-local rich aggregate state once chapter-level semantic grouping has stabilized enough to support spoiler-safe resolution
- optionally `Chapter -[:HAS_EVENT]-> ChapterEvent`

### Book layer

Persist:

- `ChapterEvent -[:REFERS_TO]-> BookEvent`
- optionally `Book -[:HAS_EVENT]-> BookEvent`

### `ChapterEvent` responsibilities

- chapter-local continuity anchor
- first spoiler-safe rich aggregate resolution boundary
- richer local aggregate card than higher-scope nodes
- rebuildable interpretation node

### `BookEvent` responsibilities

- thin cross-chapter continuity anchor
- book-scoped query traversal root
- conservative aggregate, not final universe identity

---

## Stage 7 — Retrieval integration

### Goal

Make the new Event lane usable in LoreVault retrieval and answer generation.

### Retrieval posture

The likely medium-term retrieval shape remains:

1. embed query
2. retrieve chunks and/or entity nodes
3. traverse upward toward scoped aggregate roots when useful
4. traverse downward again toward mentions, scenes, and chunks for grounded evidence

### What this proposal enables

- event mentions and event aggregates can become traversal anchors
- `BookEvent` can become the current book-scoped entry node for event-aware retrieval
- the model still preserves provenance by allowing traversal back down to mention/scene evidence

---

## Stage 8 — Later scope expansion

### Cross-chapter ANN within universe later

If chapter-local Event resolution + book-scoped ANN works well, add a broader cross-chapter ANN pass within universe.

That higher-scope pass should still keep its aggregate layer thin for spoiler gating.

That later pass should work over:

- already aggregated lower-scope Event nodes

not over:

- raw `EventMention`
- raw scene windows directly

### Universe-wide later

Universe-wide resolution should begin as:

- offline reconciliation
- suggestion generation
- analysis tooling

not as the primary hot-path write target.

### Future root node

This proposal still leaves room for a later scope-independent root such as:

- `ResolvedEvent`
- `CanonicalEvent`
- `EventIdentity`

But it does **not** require that layer before useful Event resolution ships.

---

## Generic Pipeline Template (Later Generalization)

The Event lane should be the first implementation target.

Only after it behaves well should LoreVault generalize the pipeline shape.

The general reusable template becomes:

1. evidence persistence (`<Type>Mention`)
2. local semantic grouping
3. deterministic aggregate card construction
4. ANN candidate generation at the next stable scope
5. type-tuned semantic verification
6. thin scoped aggregate write (`Chapter<Type>`, `Book<Type>`, later maybe higher scopes)

### What stays generic

- the staged skeleton
- candidate-generation philosophy
- ANN as blocking, not truth
- thin aggregate philosophy
- provenance-first layering

### What stays type-specific

- prompts
- thresholds
- veto rules
- admissibility rules
- aggregate-card field selection
- what counts as a stable identity vs a grouping bucket

### Expected type differences

- **Individuals** may later keep stronger lexical fast paths even if they adopt the same broad staged skeleton.
- **Locations** may still rely on stronger alias and geography/containment guards.
- **Events** need the richest local semantic grouping and the most cautious merge verification.

So the architecture generalizes, but rollout should stay type-specific.

---

## Why This Is Better Than The Earlier Alternatives

### Better than alias-first or exact-only reduction

Because long-range recurrence and vague naming will be missed, and because the semantic reducer is required anyway.

### Better than pure local-window reduction

Because recurrence more than a few scenes apart will remain disconnected.

### Better than full pairwise semantic comparison

Because cost explodes too quickly.

### Better than ANN-only clustering

Because vector similarity is not identity truth.

### Better than always-on LLM summaries

Because summary drift and cost are too high relative to deterministic aggregate cards.

---

## Invariants

The pipeline should keep these invariants explicit.

1. Mention evidence is never destroyed.
2. Scene-relative temporal semantics remain on `EventMention`.
3. ANN never decides merges by itself.
4. Every merge candidate must still be directly verified.
5. `BookEvent` remains thin and scope-bound.
6. Scope expansion does not imply immediate collapse into one universe-wide root.

---

## Main Risks

### 1. Over-merging Events

Mitigation:

- keep local semantic grouping type-tuned and conservative
- cap ANN candidate counts
- require direct semantic verification
- prefer unresolved over wrong merge

### 2. Summary drift

Mitigation:

- no separate default LLM summary stage
- deterministic aggregate-card construction from structured fields

### 3. Non-transitive merge chains

Mitigation:

- do not infer transitive merge truth automatically
- verify each proposed merge pair directly

### 4. Premature scope collapse

Mitigation:

- book-wide first
- series-wide only later over already resolved book nodes
- universe-wide offline only for now

### 5. Completion semantics regression

Mitigation:

- integrate ANN/verification inside scoped reducer paths or as clearly bounded sibling branches
- do not break existing completion-coordinator assumptions without explicit redesign

---

## Implementation Fit With Current Repo Shape

This design fits the repo because LoreVault already has:

- evidence-first mention persistence for Individuals and Locations
- scoped chapter/book reduction services
- triad-local bounded analysis infrastructure
- vector infrastructure that can be extended beyond chunks
- query traversal that already benefits from scoped aggregate roots

So this Event design is not a greenfield reinvention.

It is a stronger hybridization of patterns the repo already uses.

---

## Recommended Delivery Order

### Slice 1

- `EventMention` persistence
- mention-level scene-relative temporal classifier
- rolling-triad entity likeness analysis
- local aggregate links + local aggregate nodes

### Slice 2

- chapter-local rich aggregate card construction
- chapter-local stable aggregate resolution

### Slice 3

- embedding of local aggregate cards
- book-wide ANN candidate generation
- bounded semantic merge verification

### Slice 4

- thin cross-chapter aggregate write
- retrieval integration
- evaluation on Event query flows

### Slice 5

- later cross-chapter ANN within universe
- later generalization to other entity types if Event-first implementation proves out

---

## Recommended File-Level Work Areas

The exact file list should be refined during implementation planning, but the expected work areas are now clearer than before:

- scene analysis output contract / DTOs
- scene analysis prompt(s)
- event persistence service and repository layer
- event mention domain nodes / graph repositories
- local Event grouping service(s)
- chapter-level event aggregation service + handler
- higher-scope event aggregation service + handler
- aggregate-card serialization / embedding code
- ANN candidate generation inside scoped reducer path
- retrieval templates / semantic search / RAG context assembly
- docs and debug queries for inspection

---

## Current Bottom Line

LoreVault should now move forward with an **event-first hybrid entity-resolution design**:

- preserve `EventMention` as evidence
- keep scene-relative temporal semantics on the mention
- use rolling-triad semantic likeness analysis as the primary grouping mechanism
- append mentions to local aggregates as triads progress
- build deterministic aggregate cards instead of separate summary calls
- use ANN book-wide first for long-range candidate generation
- use semantic verification as the final merge gate
- let chapter be the first spoiler-safe rich aggregate resolution boundary
- keep higher-scope cross-chapter aggregates thin
- generalize this pipeline later only after the Event lane works on real data

This path gives LoreVault a strong, practical next implementation shape without forcing premature commitment to a final universe-wide `ResolvedEvent` architecture.
