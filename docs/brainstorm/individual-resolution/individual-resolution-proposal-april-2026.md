# Individual Resolution Proposal — April 2026

**Date:** April 2026  
**Status:** Proposed direction  
**Purpose:** Replace the wide option space in the earlier brainstorm with a single coherent proposal for LoreVault's first identity-resolution design

---

## Relationship to Earlier Material

This document consolidates the converged decisions from:

- `mention-to-individual-linking-brainstorm-april-2026.md`

That earlier document remains useful as option-space history. This document is the cleaner proposal for the shape we would actually build if we proceed.

---

## Conceptual Foundation

LoreVault should treat identity resolution as a **layering problem**, not a deduplication problem.

The key distinction is:

- a **mention** is evidence that some person was referred to in a specific scene
- an **identity aggregate** is a later interpretation that multiple mentions refer to the same person at a broader scope

That means the system should not try to turn mention evidence directly into canonical truth.

Instead, it should preserve the lower layer and build higher identity layers on top of it.

This proposal therefore adopts a scoped identity ladder:

- scene-local reference evidence
- chapter-local identity consolidation
- book-level identity consolidation

This is the core conceptual move.

It keeps provenance intact, makes the model easier to reason about, and aligns identity aggregation with LoreVault's existing chapter/book structure.

---

## Problem

LoreVault already persists scene-local `IndividualMention` nodes as extracted evidence.

That gives us the correct raw layer, but it does not yet answer questions like:

- which mentions in a chapter refer to the same person?
- which chapter-local identities represent the same book-level person?
- how do we preserve provenance while still creating a usable identity graph?

The proposal below answers those questions without collapsing mention evidence into canonical truth.

---

## Proposed Solution in One View

LoreVault should use an explicit scoped identity ladder:

- `Scene -[:MENTIONS]-> IndividualMention`
- `IndividualMention -[:REFERS_TO]-> ChapterIndividual`
- `ChapterIndividual -[:REFERS_TO]-> BookIndividual`

This creates three distinct layers:

1. **mention evidence** — what the extraction pipeline observed in one scene
2. **chapter-local identity** — the chapter-safe consolidation of same-person mentions
3. **book-level identity** — the thin book-scoped identity scaffold that links chapter identities together

This is the proposed first real shape of the system.

---

## Core Decisions

### 1. Keep the hierarchy explicit and scope-aware

Do **not** flatten everything into a single canonical `Individual` node yet.

Preferred names:

- `IndividualMention`
- `ChapterIndividual`
- `BookIndividual`

This keeps the model readable and leaves room for later extensions such as:

- `SeriesIndividual`
- `UniverseIndividual`

without renaming earlier layers.

### 2. Keep mentions immutable as evidence

`IndividualMention` remains the provenance-bearing layer.

It should continue to store:

- extracted naming surface
- aliases
- descriptive text
- source scene/chapter linkage
- extraction metadata
- resolution metadata

Mentions are not merged away. Linking adds structure on top of them.

### 3. Make chapter the first real consolidation boundary

Chapter is the first useful aggregation scope because:

- it naturally reduces local duplication
- ambiguity is lower than cross-book resolution
- it aligns with current spoiler granularity

So the first meaningful resolver step is:

- `IndividualMention -> ChapterIndividual`

This is not just a convenient implementation order.

It is the first scope where the system can create real identity value without taking on full book-level ambiguity.

### 4. Keep book-level identity thin

`BookIndividual` should exist primarily to connect chapter-local identities across the book.

It should stay structurally useful but semantically conservative.

Good candidates for `BookIndividual` state:

- stable id
- resolution metadata
- first-seen chapter coordinates
- chapter aggregate count
- representative chapter-individual pointer

It should **not** become a bag of all revealable facts across the book.

### 5. No HITL in v1

The first pass should not depend on a human review queue.

v1 direction:

- immediate linking
- deterministic or hybrid scoring
- unresolved remains allowed
- no review tooling required

This is acceptable because:

- lower-level evidence remains preserved
- data is disposable and reingestable today
- wrong links can be corrected by rerunning the pipeline rather than preserving user edits

### 6. Match with weighted context-first scoring

The decision engine should not be exact-name-only.

The preferred scoring philosophy is:

- use graph and context signals heavily
- give extra weight to strong lexical anchors

Examples of high-value signals:

- exact normalized-name match
- alias overlap
- same chapter
- nearby scenes
- similar descriptive text
- compatible age / physical properties
- co-mention neighborhood overlap

This should behave as a weighted scoring model where:

- some signals are near-deterministic anchors
- others are supporting evidence

### 7. Use embeddings for candidate generation, not truth creation

Embeddings are promising for surfacing likely candidates when string matching is weak.

Preferred pattern:

1. generate candidate matches with vector retrieval
2. make the final decision with weighted lexical + graph scoring

Embeddings should help find candidates. They should **not** be the sole basis for durable linking.

---

## Why This Is the Right Shape

This proposal is preferable to a flat canonical model because it keeps the semantics honest.

- `IndividualMention` remains evidence
- `ChapterIndividual` becomes the first useful identity abstraction
- `BookIndividual` provides continuity without overexposing cross-chapter facts

That gives LoreVault a map/reduce identity model:

1. extract local evidence
2. consolidate within a chapter
3. reduce upward into a thin book-level identity layer

This also aligns naturally with spoiler-aware design:

- chapter scope is a safe place for richer aggregation
- book scope is useful for continuity, but should remain thin

So the hierarchy is not just an implementation detail. It is the conceptual shape that best matches the product's information boundaries.

---

## Proposed Node Responsibilities

### `IndividualMention`

Purpose:

- raw identity-reference evidence
- scene-local provenance
- extraction-layer details

This node answers:

> Who is being referred to here?

It does **not** answer:

- what facts are true about that entity overall
- what broader canonical truth should be exposed to the reader

### `ChapterIndividual`

Purpose:

- chapter-safe identity consolidation
- richer, chapter-local aggregate identity facts

Likely fields:

- display name
- chapter-local aliases
- merged chapter-local description
- mention count
- representative mention
- first scene / last scene in chapter

This is the first layer that becomes directly useful for cleaner chapter-level graph traversal.

### `BookIndividual`

Purpose:

- connect chapter-local identities across a book
- provide a thin structural backbone for book-level continuity

Likely fields:

- stable id
- resolution metadata
- chapter aggregate count
- first-seen chapter coordinates
- representative chapter-individual pointer

This node should stay intentionally thin.

---

## Orchestration Proposal

Resolution should run incrementally by chapter.

Preferred flow:

1. ingestion persists `IndividualMention` evidence
2. chapter-level reconciliation groups mentions into `ChapterIndividual`
3. chapter-level completion triggers or updates book-level reduction into `BookIndividual`

This fits the current event-driven architecture well because LoreVault already processes content in chapter-oriented stages and can safely rerun ingestion from stable inputs.

---

## Incremental Delivery Plan

The proposal should be implemented in three slices.

### Slice 1 — Chapter-local exact linking

Deliver:

- `IndividualMention -[:REFERS_TO]-> ChapterIndividual`
- one `ChapterIndividual` per `(chapter, normalizedName)` group
- deterministic linking only
- validation via Neo4j inspection and integration tests

Why this slice comes first:

- it creates immediate value
- it makes extraction quality problems easier to see
- it validates the graph shape without taking on book-level ambiguity

Non-goals:

- no `BookIndividual` yet
- no fuzzy matching
- no embeddings
- no dedicated UI or API surface required

### Slice 2 — Book-level consolidation

Deliver:

- `ChapterIndividual -[:REFERS_TO]-> BookIndividual`
- thin `BookIndividual` nodes
- deterministic upward reduction first

Why this slice comes second:

- chapter-level identity must be proven before cross-chapter consolidation
- it adds book-wide continuity only after the lower layer is behaving well

Non-goals:

- no rich book-level fact bag
- no cross-book identity
- no LLM-based reconciliation

### Slice 3 — Better candidate generation and scoring

Deliver:

- embedding-assisted candidate generation
- weighted lexical + graph scoring
- better handling of aliases and weak string matches

Why this slice comes last:

- it improves matching quality after the structural ladder already exists
- it avoids mixing model design with early heuristic complexity

Non-goals:

- embeddings as the truth engine
- generic all-entity resolution infrastructure
- premature automation beyond demonstrated value

---

## Why This Shape Fits LoreVault Now

This proposal fits the current project situation because:

- LoreVault already has the mention-evidence layer
- the graph can be rebuilt from stable chapter content
- there is no user-authored data forcing backward-compatible migration behavior
- spoiler safety is better served by scope-explicit aggregation than by one flat canonical layer

The design therefore optimizes for:

- clarity
- reversibility
- reingestability
- incremental value

not for long-term data immutability guarantees yet.

---

## Boundaries and Non-Goals

### Not in scope for v1

- a generic entity-resolution framework for all entity types
- human review workflows
- cross-book identity resolution
- LLM-only identity decisions
- destructive mention merging
- claim extraction or claim truth storage inside mention nodes

### Important semantic boundary

Keep this rule explicit:

> If the extracted object says **who is being referred to**, it belongs in the mention/identity layer.  
> If it says **what is asserted about them**, it belongs in the claim layer.

That keeps mention resolution from drifting into proposition storage.

---

## Generalization Rule

The pattern should generalize by type, not by collapsing names into generic nodes.

Reusable template:

- `<Type>Mention`
- `Chapter<Type>`
- `Book<Type>`

Examples:

- `IndividualMention -> ChapterIndividual -> BookIndividual`
- `LocationMention -> ChapterLocation -> BookLocation`
- `CollectiveMention -> ChapterCollective -> BookCollective`

The framework may later be shared, but rollout, scoring, and aggregation richness should stay type-specific.

---

## Final Recommendation

The proposed direction is:

- keep `IndividualMention` as immutable evidence
- consolidate first into `ChapterIndividual`
- reduce upward into a thin `BookIndividual`
- match with weighted lexical + graph scoring
- use embeddings only for candidate generation
- keep v1 automatic, conservative, and reingestable

In delivery terms, that means:

1. ship chapter-local exact linking first
2. add thin book-level consolidation second
3. improve candidate generation and scoring third

This is the cleanest current proposal for LoreVault's identity-resolution shape and rollout order.
