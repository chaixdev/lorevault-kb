# Event Entity Extraction Staged Solution Design Proposal — April 2026

**Date:** April 2026  
**Status:** Proposed direction  
**Purpose:** Replace the broader Event entity extraction brainstorm with a staged implementation plan that LoreVault can execute now without waiting for the full long-term `ResolvedEvent` architecture to settle.

---

## Relationship to Earlier Material

This document sharpens and stages the converged direction from:

- `event-entity-extraction-proposal-april-2026.md`
- `event-entity-extraction-external-research-verbatim-april-2026.md`

Those documents remain the design record and option-space history.

This document is the cleaner proposal for the parts LoreVault should actually build next.

---

## Problem

LoreVault currently has:

- persisted `Scene` nodes
- a scene-first temporal backbone
- implemented mention-resolution ladders for Individuals and Locations
- a growing query model that already uses scoped aggregate nodes as traversal anchors

What it does **not** yet have is an Event entity lane that lets scenes preserve explicit references to broader happenings such as:

- named battles
- remembered coronations
- anticipated journeys
- investigations, betrayals, discoveries, disasters, and wars

The main architectural question is no longer whether Event entities are useful.

They are.

The practical question is how much of the Event entity model can be implemented now without pretending that the final event identity layer and the future Event DAG are already solved.

---

## Proposed Solution in One View

LoreVault should implement Event entity extraction in **stages**.

The first shippable stages should build:

1. `EventMention` evidence persistence
2. scene-relative temporal semantics on `EventMention`
3. conservative `ChapterEvent` grouping
4. thin `BookEvent` reduction once chapter-level outputs look sane

The stages should **not** yet build:

- a final `ResolvedEvent` identity layer
- direct `Scene -> ChapterEvent` or `Scene -> BookEvent` canonical anchoring
- aggregate/root-event participation in persisted temporal ordering
- event-driven replacement for the current scene-first temporal backbone

So the implementation target is:

- useful Event evidence now
- scoped aggregate continuity next
- broader event identity and DAG participation later

---

## Core Decisions

### 1. The first slice is an evidence lane, not a new temporal backbone

The initial implementation adds a new sibling ingestion branch alongside Individual and Location processing.

It does **not** change the truth that the current persisted temporal backbone is still scene-first.

That means:

- `Scene` remains the current persisted `TEMPORAL` participant
- Event extraction adds evidence and aggregate nodes
- Event extraction does not yet alter scene-to-scene temporal write rules

### 2. `EventMention` is the first must-build node

`EventMention` is the safest and most valuable first unit because it captures the thing LoreVault does not currently preserve:

- that a scene referred to some broader event-like happening
- how the scene relates to that event

This is the key payload that later grouping, retrieval, and broader temporal reasoning will depend on.

### 3. The scene-relative temporal classifier belongs on `EventMention`

This is already the most stable design conclusion.

Each `EventMention` should carry a scene-relative classifier such as:

- `before`
- `after`
- `during`
- `contains`
- `starts`
- `finishes`
- `equals`
- optionally a weaker fallback when temporal positioning is too uncertain

This should remain on the mention layer because the same underlying event can be remembered, depicted, and anticipated by different scenes.

### 4. The canonical write path is `Scene -[:MENTIONS]-> EventMention`

V1 should keep the Scene/EventMention relationship simple and evidence-oriented:

- `(:Scene)-[:MENTIONS]->(:EventMention)`

Do **not** add:

- `TEMPORAL` edges to `EventMention`
- direct `Scene -> ChapterEvent`
- direct `Scene -> BookEvent`

Those are later decisions.

### 5. `ChapterEvent` and `BookEvent` should begin as thin aggregates

The first aggregate layers should behave like the current book-level Individual and Location continuity structures:

- conservative
- rebuildable
- explicit about scope
- thin in node responsibilities

Good early responsibilities include:

- normalized name/key
- mention count
- chapter count / book count
- representative mention or representative chapter-level pointer
- first-seen location in the book/chapter sequence

### 6. Event grouping quality is the main practical risk

The primary blocker is **not** the missing final `ResolvedEvent` layer.

It is grouping quality.

Event grouping is much harder than person/place grouping because of:

- generic names (`the battle`, `the attack`, `the trial`)
- granularity drift (`war` vs `battle` vs `skirmish`)
- factuality/modality differences (planned, remembered, hypothetical, onstage)

So the first reduction stages must stay conservative and prefer unresolved mentions over aggressive bad merges.

### 7. Future `ResolvedEvent` remains intentionally deferred

The current discussion strongly suggests that a future top-level `ResolvedEvent` is likely the clean long-term query identity.

But it is not required to start useful implementation.

V1 and V2 should not block on it.

---

## Staged Implementation Plan

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

### Why this stage is safe now

- it reuses the current Individual/Location evidence pattern
- it does not depend on final event identity
- it does not require temporal backbone changes
- it gives immediate inspectable graph value

---

## Stage 2 — Conservative `ChapterEvent` grouping

### Goal

Group obviously-compatible event mentions inside one chapter without forcing premature cross-chapter identity.

### Required output

- `EventMention -[:REFERS_TO]-> ChapterEvent`
- optional `Chapter -[:HAS_EVENT]-> ChapterEvent`

### Grouping guidance

Prefer conservative matching such as:

- strong normalized-name agreement
- obvious alias agreement
- compatible event type / kind
- compatible factuality/modality
- compatible scene-relative framing

If grouping is uncertain, leave the mention unresolved.

### Why this stage is safe now

- it mirrors the implemented ChapterIndividual / ChapterLocation pattern
- it gives a useful chapter-local event continuity layer
- it creates a clean staging boundary for later book reduction

---

## Stage 3 — Thin `BookEvent` reduction

### Goal

Create a cross-chapter book-local Event continuity layer once chapter-level outputs are credible on real data.

### Required output

- `ChapterEvent -[:REFERS_TO]-> BookEvent`
- optional `Book -[:HAS_EVENT]-> BookEvent`

### Node shape

Keep `BookEvent` thin:

- normalized key / representative name
- mention / chapter counts
- representative chapter event pointer
- first-seen chapter metadata
- maybe aliases if confidently reducible

### Important rule

`BookEvent` is a book-scoped aggregate.

It is not yet the final event identity.

---

## Stage 4 — Retrieval integration

### Goal

Make the new event lane usable in LoreVault's retrieval and answer-generation flow.

### Retrieval posture

The likely medium-term retrieval shape is:

1. embed query
2. retrieve chunks and/or entity nodes
3. move upward toward aggregate/root nodes when useful
4. traverse downward again toward grounded evidence leaves
5. rank candidate chunks / mentions / scenes for answer generation

### What Stage 4 can safely do

- allow event mentions and/or scoped event aggregates to appear in retrieval context
- support graph traversal through `EventMention`, `ChapterEvent`, and `BookEvent`
- expose event evidence in debugging, QA, and narrative inspection flows

### What Stage 4 should still avoid

- assuming a final `ResolvedEvent` exists
- treating aggregate nodes as persisted temporal anchors by default

---

## Stage 5 — Future decision point

This stage is intentionally deferred until earlier stages are stable on real data.

Questions deferred to this stage:

- should LoreVault introduce a scope-independent `ResolvedEvent`?
- which aggregate/root event nodes are true time-bearing abstractions rather than grouping nodes?
- which of those nodes, if any, should participate directly in the persisted Event DAG?
- how should event identity handle quasi-identity / hopper-like cases?

This is a separate design decision, not a prerequisite for Stage 1–3.

---

## Target Outcomes By Stage

### After Stage 1

LoreVault can point from scenes to explicit event evidence.

### After Stage 2

LoreVault can inspect chapter-local event continuity.

### After Stage 3

LoreVault can inspect book-local event continuity.

### After Stage 4

LoreVault can use the event lane during retrieval and context assembly.

### After Stage 5

LoreVault may be ready to promote some event roots into a richer Event DAG and/or introduce `ResolvedEvent`.

---

## Graph Shape For The First Three Stages

### Stage 1

```text
(scene:Scene)-[:MENTIONS]->(mention:EventMention)
```

### Stage 2

```text
(scene:Scene)-[:MENTIONS]->(mention:EventMention)-[:REFERS_TO]->(chapterEvent:ChapterEvent)
```

### Stage 3

```text
(scene:Scene)-[:MENTIONS]->(mention:EventMention)-[:REFERS_TO]->(chapterEvent:ChapterEvent)-[:REFERS_TO]->(bookEvent:BookEvent)
```

Important rule for all three stages:

- no `TEMPORAL` edge to `EventMention`
- no direct canonical Scene→aggregate event edge
- no new persisted temporal ordering edges outside the existing scene-first model

---

## Implementation Fit With Current Repo Shape

This staged design fits the repo because LoreVault already has:

- post-scene-persistence ingestion stages
- Individual / Location mention services
- chapter/book reduction services and handlers
- rebuildable interpretation-layer aggregate patterns
- retrieval code that already mixes root-first and chunk-first traversal shapes

So the first event slices are not a greenfield architectural bet.

They are a sibling extension of patterns the repo already uses successfully.

---

## Main Risks

### 1. Over-merging events

This is the biggest practical risk.

Mitigation:

- keep grouping conservative
- prefer unresolved over wrong merge
- validate on real chapter/book data before moving to book-level reduction

### 2. Factuality/modality collapse

Remembered, anticipated, hypothetical, and onstage events may all look similar by name.

Mitigation:

- include a minimal admissibility contract before reduction
- do not merge across incompatible modalities when the evidence is weak

### 3. Premature temporal-graph ambition

There will be pressure to use the new event lane to improve timeline ordering immediately.

That part is not ready yet.

Mitigation:

- keep this proposal explicit that stages 1–3 are evidence and scoped continuity slices, not a new temporal backbone

---

## Recommended File-Level Work Areas

The exact file list should be refined during implementation planning, but the expected work areas are already clear:

- scene analysis output contract / DTOs
- scene analysis prompt(s)
- event persistence service and repository layer
- event mention domain nodes / graph repositories
- chapter-level event reduction service + handler
- book-level event reduction service + handler
- retrieval templates / semantic search / RAG context assembly
- docs and debug queries for inspection

This is expected to mirror the existing Individual and Location sibling branches more than the temporal-linking services.

---

## Current Bottom Line

LoreVault should **start now** with the Event evidence lane and thin scoped event aggregates.

The implementation is **not blocked** by the absence of a final `ResolvedEvent` design.

The repo can safely build:

- `EventMention`
- scene-relative event semantics on the mention
- conservative `ChapterEvent`
- thin `BookEvent`

and defer:

- `ResolvedEvent`
- aggregate/root participation in persisted temporal ordering
- event-driven replacement of the scene-first temporal backbone

That staged path gives LoreVault real progress now while preserving freedom for the later, harder event-identity and Event-DAG decisions.
