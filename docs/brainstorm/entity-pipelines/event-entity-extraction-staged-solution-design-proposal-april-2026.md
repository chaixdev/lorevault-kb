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

1. a post-persistence rolling-triad LLM co-reference pass builds local co-reference chains from persisted `EventMention` nodes using scene-windowed input
2. aggregate cards are built deterministically from those chains
3. ANN retrieves broader candidate merges over those cards
4. semantic likeness analysis verifies broader merges

Alias/exact matching may still remain useful as a supporting signal or later optimization, but it is no longer the conceptual backbone of this design.

### 6. Local semantic grouping should happen before ANN candidate retrieval

Individual `EventMention` nodes are semantically too thin to cluster reliably with ANN alone.

A single extracted mention carries a name, event type, a scene-relative classifier, and a short evidence phrase.

That is not enough semantic surface for an embedding model to produce stable, trustworthy clusters — especially when the same event may be named differently across scenes, referred to by consequence rather than name, or described at very different levels of specificity.

This is why a dedicated rolling-triad LLM judgment pass is required before embedding:

- it operates on already-persisted `EventMention` nodes, not mid-ingestion
- it uses rolling overlapping windows of three consecutive scenes so ongoing events mentioned across many consecutive scenes can be knitted into one chapter-local chain
- it presents all `EventMention` nodes from those scenes together, grouped by scene rather than flattened into one undifferentiated list
- it asks whether mentions across that bounded scene window refer to the same event
- it outputs same-event grouping links, building a co-reference chain per chapter
- later slices may apply the same judgment style to carefully bounded cross-chapter handoff windows before ANN, but that is not required for the first implementation

The implementation target here is important: the triad unit is the persisted scene window, not a sliding list of individual mentions. Scene boundaries are part of the evidence, because the model needs to know whether two mentions were extracted from the same scene, an adjacent scene, or the edge of the local narrative window.

The chain that results from this pass is what becomes the aggregate card.

That card, built from accumulated co-referent mentions, has enough combined evidence to embed meaningfully.

Without this step, ANN has nothing rich enough to operate on.

So the judgment pass is the semantic bridge between individual thin mentions and embeddable aggregate cards — not an optional optimisation, but the load-bearing step that makes ANN viable at all.

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

## Stage 2 — Post-persistence rolling-triad co-reference pass

### Goal

Use bounded narrative context over already-persisted `EventMention` nodes to form the first chapter-local event co-reference chains.

### Rule

Rolling-triad co-reference judgment is the primary local grouping mechanism.

### Input

Persisted scenes for the current chapter are examined through rolling, overlapping triad windows.

For each window, the pass gathers all persisted `EventMention` nodes attached to the three scene ids in that window and sends them to the model grouped by scene.

This is a post-persistence pass, not the original scene-temporal triad stage.

### Shared building blocks with scene analysis

This stage should intentionally reuse the same triad-scene processing shape already used by scene analysis, but with a different payload and prompt:

- scene analysis uses a rolling three-scene window over chapter-local scene order to reason about temporal relations and extract entities
- event co-reference should use that same rolling three-scene window as its local context boundary
- the reusable unit is therefore the ordered scene window, not a flat list of `EventMention` ids

What is shared:

- ordered scene ids from the persisted chapter scene sequence
- rolling window construction over those scene ids
- per-scene grouping as part of the LLM input contract
- bounded async fanout from `ScenesDetectedEvent`

What remains type-specific:

- the co-reference prompt and response schema
- mention-field rendering
- pair judgment validation rules
- confidence thresholds and later merge verification rules

### Output

- same-event links between `EventMention` nodes as rolling scene triads progress
- chapter-local co-reference chains built from those links
- unresolved mentions remain allowed when likeness analysis is uncertain

### Implementation notes for the next slice

The immediate implementation target should keep the building blocks clean before the new Stage 2 lands:

- keep `EventMention` persistence intact as the Stage 1 evidence lane
- keep Stage 3 chapter aggregation intact as the consumer of `SAME_EVENT` chains
- delete the misleading mention-windowed Stage 2 implementation rather than incrementally mutating it
- reintroduce Stage 2 around the correct contract: `ScenesDetectedEvent` provides ordered scene ids, Stage 2 windows those ids in groups of three, then loads mentions for those scenes

This matters because the event branch does not need to reconstruct chapter triads from scratch once scenes are already persisted. By the time `ScenesDetectedEvent` fires, the scene ordering needed for Stage 2 is already available in the event payload.

### Accepted v1 limitation

V1 may produce multiple chapter-local co-reference units that later appear to refer to the same underlying event.

That fragmentation is acceptable in the first implementation slice as long as:

- the links are rebuildable rather than treated as final identity truth
- later aggregate-card + ANN + semantic verification stages remain free to reunify them
- a dedicated cleanup pass is deferred until real data shows it is needed

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

### Rendering rule

The card should be rendered as deterministic, readable prose rather than a sparse key-value dump.

Preferred shape:

- stable heading / canonical label
- normalized aliases or alternate phrasings
- event type / kind
- important descriptors
- participant and location hints when available
- scene-relative relation distribution
- provenance counts / scope coordinates
- 2-4 representative evidence snippets as plain text bullets

Reason:

- embedding models generally perform better on readable text than on flat schema fragments
- prose-like deterministic rendering preserves more discriminative identity texture
- it reduces the risk that generic labels such as `battle`, `journey`, or `meeting` dominate the embedding

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

### Threshold policy

The first implementation should make the threshold strategy explicit:

- low similarity scores are rejected directly
- a middle similarity band is labeled for review / semantic verification
- very high similarity scores are still verified, but can be prioritized

Do **not** assume one global cosine threshold will work for all event types in narrative fiction.

Thresholds should be type-tuned and empirical.

HITL review tooling is out of scope for the first implementation, but the pipeline may still label borderline candidates for later inspection.

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

### Fanout / fanin fit

This verification-and-reduction lane should be treated as part of the same ingestion fanout/fanin architecture already used for embeddings, individual reduction, and location reduction.

The implementation technique differs, but the architectural role is the same:

- `ScenesDetectedEvent` fans out into multiple bounded follow-on branches
- each branch performs its own scoped reduce operation
- chapter/book-level completion can fan back in through the existing coordinator pattern

For the event branch specifically, this means `ScenesDetectedEvent` is also the right trigger for the local co-reference pass because it already carries the ordered scene ids that define the Stage 2 rolling scene windows.

This means Event reduction should be modeled as another reducer branch, not as an ad hoc exception to the current pipeline shape.

---

## Stage 6 — Write scoped aggregates

### Chapter layer

Persist:

- local same-event chains formed by the post-persistence co-reference judgment pass
- chapter-local rich aggregate state once chapter-level semantic grouping has stabilized enough to support spoiler-safe resolution
- optionally `Chapter -[:HAS_EVENT]-> ChapterEvent`

### Local chain identity

The cheapest first implementation is to keep Stage 2 chains primarily as relationship-level structure between `EventMention` nodes, with deterministic rebuild semantics.

That means the first write can be as simple as same-event links between mentions, plus deterministic derivation of a chapter-local aggregate card from the connected chain.

If needed later, LoreVault can promote those chains into explicit provisional local aggregate nodes without invalidating the later chapter/book design.

So both representations are valid, but the cheapest first option is:

- relationship-level chain first
- chapter-scoped aggregate node as the first stable rich resolution boundary

This keeps Stage 2 light while preserving freedom for later promotion.

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

It also now fits more concretely than the earlier draft suggested because the repo already has:

- a `ScenesDetectedEvent` fanout point carrying ordered scene ids for a chapter
- scene analysis that already treats a three-scene window as a bounded reasoning unit
- event persistence that already writes `EventMention` evidence after scene analysis and before downstream reducer branches

So this Event design is not a greenfield reinvention.

It is a stronger hybridization of patterns the repo already uses, with Stage 2 now explicitly aligned to the same scene-windowed processing shape rather than a flat mention-window approximation.

---

## Recommended Delivery Order

### Slice 1

- `EventMention` persistence
- mention-level scene-relative temporal classifier
- clean removal of the misleading mention-windowed Stage 2 implementation
- scene-windowed rolling-triad entity likeness analysis
- local same-event links as rebuildable chain structure

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
- local Event grouping service(s) aligned to ordered three-scene windows
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
- use scene-windowed rolling-triad semantic likeness analysis as the primary grouping mechanism
- treat ordered three-scene windows as the reusable local processing unit shared with scene analysis
- append mentions to local same-event chains as scene triads progress
- build deterministic aggregate cards instead of separate summary calls
- use ANN book-wide first for long-range candidate generation
- use semantic verification as the final merge gate
- let chapter be the first spoiler-safe rich aggregate resolution boundary
- keep higher-scope cross-chapter aggregates thin
- generalize this pipeline later only after the Event lane works on real data

This path gives LoreVault a strong, practical next implementation shape without forcing premature commitment to a final universe-wide `ResolvedEvent` architecture.

---

## Implementation Notes Since This Proposal

This section is partly stale relative to current code. Stage 1 `EventMention` persistence is shipped, Stage 2 is now implemented around ordered scene ids and rolling scene windows, and Stage 3 reduction now persists a richer `ChapterEvent` support surface, though broader book-level event resolution is still intentionally unimplemented.

The notes below are retained as design context, but the Stage 2 "not yet implemented" statements are superseded by the current `EventCoreferenceService` shape.

What stayed aligned with the proposal:

- `EventMention` remains the Stage 1 evidence unit
- scene-relative temporal semantics remain mention-level truth
- `SAME_EVENT` links remain the light-weight rebuildable local chain representation
- `ChapterEvent` remains the first stable rich aggregate boundary

What changed in the implementation understanding:

- the Stage 2 rolling unit is now explicitly the ordered three-scene window, not a flat list of mentions
- the right trigger for the event branch is still `ScenesDetectedEvent`, but now for a stronger reason: it already carries the ordered scene ids needed to form Stage 2 windows
- the reusable building block shared between scene analysis and event resolution is the persisted scene-window shape, while prompts and judgment logic stay type-specific
- Stage 3 aggregate reduction is present and now carries support metadata on `ChapterEvent`: `supportedAliases`, `supportedEventTypes`, and `evidenceSnippets`

Implementation review notes worth preserving:

- The new `ChapterEvent` support fields are aggregate metadata, not new extraction behavior. They preserve the evidence variation that contributed to the chapter aggregate without pretending that one representative label or event type is canonical truth.
- `ChapterEventResolutionService` populates `supportedAliases` from the representative names plus all mention aliases, `supportedEventTypes` from nonblank mention event types, and `evidenceSnippets` from distinct mention evidence capped to a small inspection-friendly set.
- These fields are currently persisted for graph inspection and future reducers. They are not yet retrieval inputs, except that `supportedEventTypes` and `evidenceSnippets` contribute to the deterministic aggregate card.
- Runtime validation against the 18-chapter Deathworlders sample showed all produced `ChapterEvent` nodes populated the support fields, while book-wide ANN, semantic verification, and `BookEvent` remain outside this slice.

What remains intentionally unimplemented:

- book-wide ANN candidate generation
- semantic merge verification
- `BookEvent`
- event embeddings
- event-aware retrieval integration
- cross-chapter `SAME_EVENT` handoff and book-wide event identity

This proposal should therefore now be read as the target architecture for the next implementation slice, with the Stage 2 notes above superseded where they describe the mechanism as future work.

---

## Open Design Question: Chapter Scope vs Book Scope for SAME_EVENT Windowing

### The tension

The current Stage 2 implementation is chapter-scoped in two places:

1. **The windowing input** — `runCorefPass` receives `orderedSceneIds` from a single `ScenesDetectedEvent`, which is chapter-bounded by the ingestion trigger unit.
2. **The write/invalidation step** — `deleteCoreferenceLinks(chapterId)` uses `chapterId` as the deletion unit before rewriting `SAME_EVENT` links.

This means an event that begins in chapter N and is referenced in chapter N+1 will never be co-referenced across that boundary by Stage 2 — the windows are closed at chapter edges.

### The precedent from scene analysis

Scene analysis already resolves this for scene-level temporal reference: when processing the first scenes of a new chapter, the scene analysis triad includes the previous chapter's last scene as a boundary anchor. This is precisely what gives LoreVault cross-chapter temporal grounding at the scene level.

The same logic applies to event mentions. A battle that began in chapter 3 and is referenced again in chapter 4 is the *same battle*. The windowing mechanism, if left chapter-bounded, will never connect those two mentions even if they are adjacent scenes with the same event description.

### The spoiler-gating constraint

Dissolving chapter scope entirely is not safe. The `ChapterEvent` aggregate node is the boundary unit for spoiler gating: "what events has this reader seen?" is a chapter-boundary question. That means:

- **`SAME_EVENT` links can be book-scoped** — they are a lightweight rebuildable chain, not the visibility boundary.
- **`ChapterEvent` must remain chapter-scoped** — Stage 3 reads `SAME_EVENT` components and builds one `ChapterEvent` per component per chapter, which is the correct spoiler-gating anchor.

### Proposed resolution direction

| Concern | Scope |
|---|---|
| Rolling triad window | Book-scoped — extend first window of a chapter backward to include tail scenes from prior chapter (same pattern as scene analysis) |
| `SAME_EVENT` links | Book-scoped — keyed on mention-pair only, not chapter |
| Invalidation unit | Job-scoped or mention-scoped — not chapter-scoped deletion |
| `ChapterEvent` aggregate | Chapter-scoped — Stage 3 unchanged, spoiler gating preserved |
| `ChapterEventsResolvedEvent` | Chapter-scoped — fan-in and completion coordinator unchanged |

### What must change to implement this

1. `runCorefPass` would need access to the ordered tail scenes of the previous chapter (or a configured lookback count) to build its first window.
2. `EventMentionGraphRepository.deleteCoreferenceLinks(chapterId)` would be replaced with a job-scoped or mention-id-scoped invalidation strategy.
3. `SAME_EVENT` relationship schema would drop `chapterId` as a property (or demote it to a soft annotation).
4. Stage 3 (`ChapterEventResolutionService`) remains unchanged in contract — it still queries `SAME_EVENT` components scoped by chapter for aggregation.

### Current status

Not yet implemented. The current Stage 2 is chapter-scoped throughout. This question should be resolved before the Stage 2 design is considered stable, but it does not block the current implementation slice — it is a known scope limitation rather than a defect.
