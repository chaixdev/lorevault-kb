# Mention-to-Individual Linking Brainstorm — April 2026

**Date:** April 2026  
**Status:** Exploratory  
**Purpose:** Consolidate the current design exploration for linking `IndividualMention` evidence nodes to canonical `Individual` nodes without losing provenance

---

## Why This Exists

LoreVault now persists scene-local `IndividualMention` nodes as extracted evidence.

That gives us a better model boundary than the previous provisional `Individual` approach:

- `Scene -[:CONTAINS]-> IndividualMention`
- later: `IndividualMention -[:REFERS_TO]-> Individual`

This solves the semantic confusion between:

- **mention evidence** — what the extraction pipeline observed in a scene
- **canonical identity** — who that mention is believed to actually be

The next design problem is therefore not “how do we deduplicate nodes?” but:

> How should LoreVault run a separate identity-linking process that ties `IndividualMention` nodes to canonical `Individual` nodes safely, reversibly, and with strong provenance?

This document consolidates the current exploration so we do not lose the useful thinking.

---

## Current State

### What exists now

LoreVault currently persists:

- `IndividualMention` nodes with scene-local evidence
- `Scene -[:CONTAINS]-> IndividualMention` links

Each mention currently carries mention-ready metadata such as:

- `displayName`
- `normalizedName`
- `aliases`
- `description`
- `age`
- `physicalProperties`
- `sceneId`
- `chapterId`
- `resolutionStatus`
- `extractionIndex`

The current write path is intentionally mention-only.

### What does not exist yet

- canonical `Individual` node creation
- `IndividualMention -[:REFERS_TO]-> Individual`
- mention grouping/reconciliation
- review queue for ambiguous identity proposals
- persisted proposal/candidate model for entity linking

### Important design constraint

The current mention layer is **evidence**, not truth.

So any future linking process must:

- preserve mention nodes intact
- avoid destructive merges
- allow wrong links to be undone without losing extraction provenance

---

## Core Modeling Principle

The cardinal entity type should remain the canonical type:

- `Individual`

The provisional extraction layer should stay a mention layer:

- `IndividualMention`

That gives LoreVault a clean distinction between:

1. **what was extracted in a scene**
2. **what canonical person that extraction refers to**

In other words:

- `IndividualMention` is evidence-bearing and scene-local
- `Individual` is canonical and cross-scene/book-local identity-bearing

This is the central conceptual decision behind all of the options below.

---

## Problem Restatement

LoreVault now has many duplicated `IndividualMention` nodes representing the same real person across scenes.

The challenge is to decide:

- **when** resolution runs
- **how** candidate links are generated
- **how** ambiguous cases are handled
- **when** canonical `Individual` nodes are materialized

Those are separate choices and should not be conflated.

---

## The Two Decision Axes

### Axis 1 — When resolution runs

This is the orchestration question.

Possible answers:

- event-driven immediately after mention extraction
- event-driven after chunking
- event-driven after full ingestion completion
- explicit post-processing job
- scheduled/batch reconciliation
- query-time virtual grouping only

### Axis 2 — How resolution decides

This is the candidate-evaluation question.

Possible answers:

- deterministic scoring
- human-in-the-loop review
- LLM-assisted resolution
- graph clustering
- hybrid approaches

In practice, the most promising architecture will likely combine multiple answers across these two axes.

---

## Best Hook Points in the Current Codebase

Current ingestion/event flow already offers several good integration points.

### 1. `SceneDetectionHandler` immediately after mention persistence

Mention extraction currently finishes here:

- `SceneDetectionHandler.detectAndPersistScenes(...)`
- `individualPersistenceService.persistExtractedIndividuals(...)`

This is the earliest point where persisted `IndividualMention` evidence exists.

**Use when:**

- we want immediate post-extraction candidate generation
- we only need scenes + mention metadata

**Tradeoff:**

- too easy to overload the scene-detection stage with identity logic

### 2. `ScenesDetectedEvent`

This is the cleanest existing asynchronous stage boundary right after scene + mention persistence.

**Use when:**

- we want a separate asynchronous mention-resolution stage
- we want to stay close to current pipeline patterns

**Tradeoff:**

- chunks and embeddings are not yet available

### 3. `ChunksCreatedEvent`

At this point, scenes, mentions, and chunks all exist.

**Use when:**

- chunk-local lexical evidence matters
- we do not need embeddings yet

**Tradeoff:**

- still not the richest possible evidence set

### 4. `IngestionCompletedEvent`

This is the strongest event boundary if resolution needs the full post-ingestion graph.

By this point, LoreVault has:

- scenes
- mentions
- chunks
- embeddings

**Use when:**

- semantic similarity might matter
- resolution is clearly a post-processing concern
- we want the cleanest separation from ingestion

**Tradeoff:**

- identity freshness is delayed until the end of the ingestion pipeline

### 5. Explicit post-processing job

This is not currently a first-class pipeline stage, but it is still a valid architecture option.

Examples:

- resolve one book on demand
- resolve all unresolved mentions for a book after ingestion
- rerun reconciliation after heuristic changes

**Use when:**

- replayability matters
- we want clean experimentation
- we do not want resolution tightly coupled to ingestion events yet

**Tradeoff:**

- requires explicit orchestration/job ownership

---

## Signals Already Available in the Graph

LoreVault already has a surprisingly strong signal set for a first resolution engine.

### Mention-level signals

Available now on `IndividualMention`:

- `displayName`
- `normalizedName`
- `aliases`
- `description`
- `age`
- `physicalProperties`
- `sceneId`
- `chapterId`
- `resolutionStatus`
- `extractionIndex`

These give immediate deterministic and fuzzy-match inputs.

### Scene-level structural signals

Available now on `Scene`:

- `sceneIndex`
- `chapterId`
- `startCharacterOffset`
- `endCharacterOffset`
- `contextSummary`
- `text`

These support scene-local proximity reasoning and context-based similarity.

### Chapter/publication ordering signals

Available now on `Chapter` and related read paths:

- book / chapter positioning
- publication coordinates
- chapter ordering

This supports book-local and chapter-local windowing.

### Chunk-level signals

Available now on `Chunk`:

- `text`
- `chunkNumberInChapter`
- `startCharInChapter`
- `endCharInChapter`
- `embedding`
- `contentHash`

This supports:

- lexical evidence comparison
- semantic similarity via embeddings
- chunk-local co-occurrence reasoning later

### Temporal / adjacency signals

Available now through temporal logic and graph writes:

- default `MEETS` edges between consecutive scenes
- cross-chapter continuity edges for last scene → first scene
- richer triad-upgraded temporal relations later

These are especially valuable as **soft proximity features**.

Adjacent scenes are not proof of identity, but they are a plausible positive signal when combined with alias/name overlap.

### Co-occurrence signals

Not stored as dedicated relationships yet, but computable from:

- `Scene -[:CONTAINS]-> IndividualMention`
- scene/chunk neighborhood traversal

So co-occurrence is available as a **derived graph signal**.

---

## Candidate Architecture Options

## Option A — Event-driven handler right after mention extraction

### Shape

- mention extraction completes
- new handler runs immediately after mentions are persisted
- handler generates candidate links or even writes `REFERS_TO`

### Attractive when

- we want identity freshness as soon as possible
- we want per-chapter processing close to ingestion

### Pros

- fits current event-driven pipeline style
- simple mental model
- no separate operator workflow required

### Cons

- easy to overload ingestion with immature identity logic
- difficult to evolve if resolution becomes expensive or review-heavy
- encourages early over-coupling

### Verdict

Good for **candidate generation**, but risky as the first full resolution engine.

---

## Option B — Explicit post-processing job

### Shape

- mention extraction remains purely literal
- separate job resolves unresolved mentions for a chapter/book

### Attractive when

- replayability matters
- heuristics will change over time
- we want to keep resolution clearly separate from extraction

### Pros

- highest reversibility
- easiest to rerun after heuristic changes
- best fit for experimentation
- easiest to debug and observe

### Cons

- results are not immediate unless automatically triggered
- requires explicit orchestration/job ownership

### Verdict

This is one of the strongest current options.

---

## Option C — Deterministic scoring engine

### Shape

Use a scoring function over candidate mention pairs/groups using available graph signals.

Possible features include:

- exact normalized-name match
- alias overlap
- same chapter
- adjacent scenes via `MEETS`
- same/nearby context summaries
- similar descriptions
- compatible age/physical properties
- co-mention neighborhood overlap
- chunk semantic similarity later

### Attractive when

- we want explainability and safe first automation

### Pros

- cheap
- auditable
- easy to threshold
- easy to debug

### Cons

- weak on subtle aliases/titles/pronouns
- likely to false-split more than an LLM

### Verdict

This should likely be the **first decision engine**, regardless of orchestration choice.

---

## Option D — Human-in-the-loop review queue

### Shape

- system generates candidate links or candidate groups
- human approves/rejects/defer decisions

### Attractive when

- false merges are highly expensive
- trust and auditability matter more than raw automation

### Pros

- strongest provenance and safety
- excellent long-term training/feedback source

### Cons

- review burden can explode
- requires review tooling and workflow

### Verdict

Very strong for ambiguous cases, but likely too heavy as the universal first path.

---

## Option E — LLM-assisted reconciliation

### Shape

- deterministic prefilter narrows candidates
- LLM judges likely identity matches among shortlisted candidates

### Attractive when

- descriptions, epithets, and literary aliases matter
- deterministic rules leave too many unresolved cases

### Pros

- stronger semantic flexibility
- can reason over messy extracted descriptions

### Cons

- expensive
- harder to audit if used directly for writes
- risk of overconfident wrong links

### Verdict

Best used as a **bounded second opinion**, not first-line truth creation.

---

## Option F — Query-time virtual grouping

### Shape

- do not persist canonical links yet
- group likely same-person mentions at query time only

### Attractive when

- we want to evaluate heuristics before committing to durable writes

### Pros

- lowest data risk
- easiest experimental path
- zero destructive graph mutation

### Cons

- no durable canonical identity graph
- inconsistent read behavior across consumers
- poor long-term foundation if it lingers

### Verdict

Excellent short-term exploration tool; weak final architecture.

---

## Option G — Hierarchical map-reduce resolution

### Shape

Two-step resolution:

1. chapter-local grouping first
2. later book-global reduction into canonical `Individual`

### Attractive when

- cast size is large
- chapter boundaries are meaningful narrative chunks
- we want bounded context during first grouping pass

### Pros

- scalable
- easier to reason about than full-book pairwise matching at first
- supports incremental refinement

### Cons

- chapter-boundary identities stay unresolved until reduction runs
- adds another conceptual layer

### Verdict

Potentially a very good medium-term evolution if a single-step book resolver becomes too noisy.

---

## Option H — Sliding scene-window propagation

### Shape

Use scene adjacency and a limited window of neighboring mentions as a strong linking prior.

### Attractive when

- local continuity is high
- references are mostly short-range and narrative flow is contiguous

### Pros

- mimics narrative continuity
- useful for epithets and short-range re-mentions

### Cons

- one wrong early decision can poison later decisions if used as truth rather than as a feature

### Verdict

Useful as a **feature**, dangerous as the architecture itself.

---

## Strongest Combined Designs

The best ideas are not individual options in isolation; they are combinations.

### Combined Design 1 — Explicit book-scoped job + deterministic scoring

This is the current strongest default.

Flow:

1. ingestion completes
2. a book-scoped resolver gathers unresolved `IndividualMention`
3. deterministic scoring generates candidates/groups
4. high-confidence cases may auto-link
5. ambiguous cases remain unresolved or become proposals

Why it is strong:

- additive
- replayable
- explainable
- low blast radius

### Combined Design 2 — Event-triggered work item + explicit resolver

Flow:

1. `IngestionCompletedEvent` fires
2. handler records that a book/chapter needs mention resolution
3. dedicated resolver picks up the work item and runs separately

Why it is strong:

- preserves automatic freshness
- keeps heavy logic out of ingestion handler
- fits existing event style without overloading it

### Combined Design 3 — Deterministic scoring + review queue

Flow:

1. score candidates conservatively
2. exact obvious cases auto-link
3. medium-confidence cases become review items
4. low-confidence cases remain unresolved

Why it is strong:

- balances safety and progress
- avoids “all or nothing” automation

### Combined Design 4 — Query-time overlay before durable writes

Flow:

1. test grouping heuristics at read time
2. measure ambiguity/error patterns
3. only later promote stable logic into persisted `REFERS_TO`

Why it is strong:

- very safe way to learn

Why it is limited:

- should remain an exploratory step, not the long-term model

---

## Oracle’s Main Recommendation

Oracle’s strongest recommendation was:

> Treat reconciliation as a separate post-processing concern.

More specifically:

- keep `IndividualMention` immutable as evidence
- prefer a **non-destructive explicit post-processing job**
- use **deterministic graph scoring** as the first decision engine
- optionally use **query-time grouping** for exploration before durable writes
- route ambiguous cases to **review** or bounded **LLM assistance** later
- do **not** start with ingestion-time matching
- do **not** create unversioned truth from heuristics or LLMs

This matches the current LoreVault maturity level well.

---

## Things to Avoid

The exploration converged strongly on several anti-patterns.

Do **not**:

- destructively merge or mutate mention evidence nodes into canonicals
- make ingestion-time matching the first identity step
- let an LLM autonomously write canonical truth from open candidate sets
- create broad pairwise same-as webs with weak provenance
- treat adjacent scenes as proof rather than one signal among many
- jump immediately to cross-book identity resolution

---

## Best Current Recommendation

If LoreVault wants the safest and most maintainable next step, the most credible design is:

### Recommended orchestration

- a **separate book-scoped post-processing resolver**
- triggered either:
  - explicitly, or
  - by `IngestionCompletedEvent` via a lightweight work-item handoff

### Recommended decision engine

- **deterministic scoring first**

### Recommended ambiguity handling

- unresolved by default
- review queue or bounded LLM assistance later for ambiguous cases

### Recommended scope

- **book-local first**

That keeps the first real identity-linking system:

- additive
- conservative
- explainable
- reversible
- well-aligned with LoreVault’s current rope-bridge maturity

---

## Promising Follow-On Questions

The next round of design work should probably answer:

1. What is the smallest useful candidate model?
   - pairwise links?
   - mention groups?
   - proposal nodes?

2. What exact deterministic features should go into a first score?

3. What confidence bands should map to:
   - auto-link
   - review
   - unresolved

4. Should the first durable write path create:
   - direct `REFERS_TO` links, or
   - intermediate proposal artifacts first?

5. Should the first orchestration hook be:
   - explicit resolve-book command/job, or
   - `IngestionCompletedEvent` work-item trigger?

---

## Short Summary

The current exploration suggests:

- `IndividualMention` is the right evidence layer
- canonical `Individual` should be added later, not conflated with mentions
- the linking process should be separate from extraction
- deterministic scoring should be the first core decision mechanism
- event-driven orchestration can help trigger work, but heavy matching should not be stuffed into the ingestion write path
- ambiguous cases should remain unresolved until the system has better review or tie-break support

This is the best current conceptual footing for the next phase of LoreVault identity work.

---

## Interview Discoveries and Narrowed Decisions (April 2026)

The earlier sections in this document intentionally cast a wide net. During follow-up design discussion, several options were explicitly ruled out and the model was narrowed considerably.

### 1. The hierarchy should be explicit

The strongest current direction is no longer a flat:

- `IndividualMention -> Individual`

Instead, the hierarchy should be explicit and scalable:

- `IndividualMention`
- `ChapterIndividual`
- `BookIndividual`

This gives LoreVault a clear map/reduce ladder:

- scene-local evidence
- chapter-local consolidation
- book-local consolidation

and leaves room for later extension to:

- `SeriesIndividual`
- `UniverseIndividual`

without forcing renames of earlier layers.

### 2. The cardinal entity should remain scope-explicit for now

There was a temptation to let `Individual` be the book-level canonical layer, but the scoped hierarchy is easier to reason about and easier to evolve.

So the current preferred naming is:

- `IndividualMention`
- `ChapterIndividual`
- `BookIndividual`

This also generalizes naturally to other entity types later:

- `LocationMention -> ChapterLocation -> BookLocation`
- `CollectiveMention -> ChapterCollective -> BookCollective`

### 3. Spoiler gating helps define the aggregation boundary

Spoiler gating granularity is chapter-level today.

That gives a useful architectural rule:

- `ChapterIndividual` can safely store richer aggregated facts because its scope does not exceed the chapter spoiler boundary
- `BookIndividual` should stay thin because richer cross-chapter identity facts are much more likely to leak narrative revelations

This means spoiler gating does **not** block internal linking itself.

The real spoiler-sensitive concern is:

- what properties are surfaced from aggregate nodes at query time

So the emerging principle is:

- internal reconciliation may know more than the reader should see
- query-time gating must still decide what names/aliases/descriptions are safe to expose

### 4. Aggregate nodes should be thin or rich by scope

The current direction is:

#### `IndividualMention`
- raw extracted evidence
- scene-local provenance
- extraction-level details

#### `ChapterIndividual`
- richer, chapter-safe aggregated identity facts
- likely safe to hold:
  - display name
  - chapter-local aliases
  - merged chapter-local description
  - mention count
  - representative mention
  - first/last scene in chapter

#### `BookIndividual`
- thin structural identity scaffold
- likely safe to hold:
  - stable id
  - resolution metadata
  - chapter aggregate count
  - first-seen chapter coordinates
  - representative chapter-individual pointer

But `BookIndividual` should avoid becoming a naive bag of all revealable facts across the book.

### 5. Human review is ruled out for the first pass

At this stage, HITL was explicitly considered too much overhead.

So the current first-pass direction is:

- immediate linking
- deterministic or hybrid scoring
- no review queue in v1

This increases the importance of:

- preserving all lower-level evidence nodes
- keeping `BookIndividual` thin
- making upward links reversible later if needed

### 6. Execution should be incremental by chapter

The most natural execution model now is:

1. reconcile mentions into `ChapterIndividual` per chapter
2. emit a chapter-level completion event
3. trigger/update book-level reduction into `BookIndividual`

This means:

- processing unit = chapter
- book-level accumulated state = reconciliation context for the upward reduction

This matches the existing event-driven architecture well and mirrors the way the content hierarchy already grows.

### 7. Matching should be context-first with weighted features

The current favored matching philosophy is **not** exact-name-only.

Instead:

- use graph/context signals heavily
- but assign higher weights to stronger lexical signals like:
  - exact normalized name
  - alias overlap

This gives a more graph-native reconciliation process while still respecting the importance of names and aliases.

So the likely shape is a weighted scoring model where:

- some signals are near-deterministic anchors
- others are supporting or disambiguating evidence

### 8. Embeddings may be an excellent candidate-generation layer

One of the most promising newly surfaced ideas is:

- synthesize a text representation of a mention or aggregate node
- embed that text
- use semantic search to surface likely similar nodes for consideration

This is especially attractive because LoreVault already has:

- embedding generation infrastructure
- Neo4j vector search patterns
- graph-based signals for re-ranking after candidate retrieval

The important caution is:

- embeddings should surface candidates
- embeddings should **not** become the sole truth engine for final linking

The strongest emerging hybrid is:

1. vector retrieval surfaces likely candidates
2. weighted graph + lexical scoring makes the actual linking decision

This feels especially promising for:

- `IndividualMention -> ChapterIndividual`
- `ChapterIndividual -> BookIndividual`

where aliases, descriptive text, and narrative role clues may be semantically similar even when string matching is weak.

### 9. Open question introduced by the embedding idea

If embeddings are used for candidate generation, we need to decide later:

- what exact synthetic text should be embedded
- whether to start with mention embeddings or chapter-aggregate embeddings first
- which structural metadata should remain out of the embedding text and instead stay in the graph scorer

The current instinct is:

- embed semantic content
- score structural/provenance signals separately

### 10. Narrowed current direction

The current best-understood path now looks like:

- `Scene -[:CONTAINS]-> IndividualMention`
- `IndividualMention -[:REFERS_TO]-> ChapterIndividual`
- `ChapterIndividual -[:REFERS_TO]-> BookIndividual`

with these properties:

- chapter-level reconciliation runs incrementally
- chapter completion can trigger book-level reduction
- no HITL in v1
- chapter aggregates may store richer facts
- book aggregates stay thin
- spoiler gating remains mainly a query-time visibility concern
- matching is context-first with weighted signals
- vector similarity is promising as a candidate-generation layer ahead of graph/lexical scoring

### 11. Mention is not the same thing as a claim

An important conceptual check surfaced during the discussion:

- `IndividualMention` and the older claim model are related
- but they are **not** the same artifact

The cleanest current distinction is:

- `IndividualMention` answers: **who is being referred to here?**
- a claim answers: **what is being asserted about that entity here?**

Example:

> "The assassin slipped through the window."

Possible mention-layer output:

- `IndividualMention(displayName="the assassin")`

Possible claim-layer outputs later:

- someone appeared in this scene
- someone entered through a window
- someone had a role/descriptor like assassin

So a mention is fundamentally about **reference**, while a claim is fundamentally about **proposition**.

### 12. Where mention and claim overlap

The overlap is not mainly semantic — it is infrastructural.

Both systems may eventually want:

- chunk or scene linkage
- offsets/evidence spans
- publication coordinates
- provenance
- confidence or evidence-quality markers

This means LoreVault should be careful not to accidentally build two completely separate evidence infrastructures:

1. one for identity mentions
2. one for claims

That would create duplicated concepts for:

- offsets
- source text evidence
- publication scoping
- provenance metadata
- confidence metadata

### 13. Current design is still on the safe side

The current `IndividualMention` direction is still meaningfully distinct from the entity-claim model because it is being used for:

- identity-resolution evidence
- not general proposition storage

That means the current mention system is not yet a shadow claim system.

The danger only appears if mention nodes start absorbing:

- interpreted factual assertions
- role truth instead of role reference
- generalized semantic facts better represented as claims

### 14. Best boundary rule going forward

The cleanest current test is:

> If the extracted object says **who is being referred to**, it belongs in the mention/identity layer.  
> If it says **what is asserted about them**, it belongs in the claim layer.

This is the most useful boundary discovered so far for preventing the two models from drifting into each other.

### 15. Likely long-term convergence point

The likely future convergence is **not** that mention nodes and claim nodes become the same thing.

The likely convergence point is:

- a shared provenance/evidence substrate

In other words, both mention resolution and claim extraction may eventually want to reuse the same conventions for:

- evidence spans
- source chunk references
- publication coordinates
- evidence-quality metadata

But they should still remain different semantic layers:

- mention = identity reference evidence
- claim = assertion evidence

That distinction remains important even if the underlying evidence scaffolding becomes shared later.

### 16. Generalization path beyond Individuals

Although this document has focused on `Individual` reconciliation, the intended pattern should generalize across entity kinds.

The current preferred rule is:

- keep node names **typed and scope-explicit**
- generalize the **pattern**, not the node names themselves

So the likely reusable naming template is:

- `<Type>Mention`
- `Chapter<Type>`
- `Book<Type>`

Examples:

- `IndividualMention -> ChapterIndividual -> BookIndividual`
- `LocationMention -> ChapterLocation -> BookLocation`
- `CollectiveMention -> ChapterCollective -> BookCollective`

This keeps the model easy to read and query while still making the hierarchy reusable. The reconciliation framework may later be shared across types, but the scoring signals, aggregation richness, and rollout order should remain type-specific rather than forced into one generic entity pipeline too early.
