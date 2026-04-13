# Location Extraction Proposal — April 2026

**Date:** April 2026  
**Status:** Proposed  
**Purpose:** Add a location entity extraction slice that is lighter than the individual work in design effort, but still strong enough to support a genuinely entity-based Q&A direction.

---

## Problem

LoreVault's query-side next phase is likely to become more entity-aware.

If the next Q&A improvements are built only on top of individuals, the result risks becoming too narrowly shaped around character questions and needing avoidable refactoring once other entity types arrive.

The best way to prevent that is to add one more entity lane first.

`Location` is the strongest candidate because it is:

- already requested by the pass 2 scene-analysis prompt
- high-value for lore retrieval and reader questions
- structurally simpler than many other entity types
- enough to push the next product slice toward a generic entity-aware solution instead of a character-only solution

---

## Current State

### Prompt behavior

The current pass 2 prompt already requests Locations.

Relevant file:

- `lorevault-api/src/main/resources/prompts/scene-detection-pass2.txt`

So LoreVault is already paying for Location extraction attempts in the existing triad call.

### Runtime behavior

Today the code captures and persists only individual extraction results.

The current individual flow is:

1. pass 2 returns structured scene-local entity output
2. `TriadOrchestrationService` normalizes and buffers individual extractions by `sceneIndex`
3. scenes are persisted
4. extracted individuals are persisted after final scene persistence
5. later entity-resolution layers consolidate those mentions into chapter-level and book-level identity nodes

Locations are not currently captured in the structured Java DTOs or persisted anywhere.

### Important implementation reality

The individual implementation has already proven a durable pattern for:

- structured pass 2 capture
- post-scene persistence timing
- evidence-node persistence
- scoped chapter/book consolidation
- event-driven follow-up processing

This proposal should reuse that pattern where it is helpful, but should not blindly copy individual-specific fields or assumptions.

---

## Decision Summary

Implement a first Location extraction slice that includes:

- `LocationMention`
- `ChapterLocation`
- `BookLocation`

This differs from the earlier instinct to stop at a mention-only MVP.

Current recommendation:

- Locations should run in parallel to individuals once pass 2 results are available
- Locations should use the same post-scene persistence timing as individuals
- Locations should get a minimal scoped ladder in v1
- matching should stay intentionally light: primary name + aliases only
- no geospatial, address, or ontology ambitions should be introduced

This gives the next Q&A/product work a genuinely more generic entity base.

---

## Why Not Stop At Mention-Only?

If Locations stop at `LocationMention` while individuals already have chapter/book layers, the next query/product work will still be asymmetrical.

That would likely encourage:

- individual-specific query routing
- individual-specific aggregation logic
- individual-specific UI language

Adding thin chapter/book Location layers now avoids that asymmetry at relatively low cost, as long as the matching rules remain conservative and exact.

---

## Scope

### In scope

- capturing Location data from pass 2 output
- extending structured pass 2 DTOs so current-scene Locations are available to application code
- carrying extracted Locations forward until real scene persistence completes
- persisting `LocationMention` evidence nodes
- linking `Scene -> LocationMention`
- deterministic chapter-level grouping into `ChapterLocation`
- deterministic book-level grouping into `BookLocation`
- using only exact/lightweight matching based on primary name and aliases

### Out of scope

- lat/lng or geocoding
- canonical external IDs
- containment graph (`city -> region -> country`, etc.)
- address decomposition
- confidence scoring heuristics
- fuzzy Location resolution
- ontology work or place taxonomy design
- stronger semantic scene-Location claims like `LOCATED_IN` unless later evidence justifies them
- cross-book or cross-series Location resolution
- generalized multi-entity framework abstraction before we have at least two concrete types in use

---

## Proposed Data Source

Continue using the existing pass 2 triad prompt.

Do **not** add a separate Location-extraction prompt for this slice.

Reasoning:

- lower incremental cost
- matches the existing individual extraction path
- keeps the first Location slice focused on capture + persistence + lightweight reduction
- gives us signal about whether current pass 2 output is good enough before redesigning prompt architecture

---

## Structured Output Shape

The Location block should be intentionally small and usable.

Recommended extracted fields:

- `primaryName`
- `aliases`
- `kind`
- `region`
- `description`

### Why these fields

- `primaryName` avoids overloading aliases as the only naming source
- `aliases` supports exact-match reconciliation at chapter/book scope
- `kind` gives user-facing and query-facing utility (`city`, `inn`, `fortress`, `forest`, `planet`, etc.)
- `region` provides lightweight locality/context without requiring full containment modeling
- `description` supports evidence display and later Q&A usefulness

### Explicitly excluded fields

The first Location slice should not ask for or persist:

- latitude / longitude
- address fields
- external canonical IDs
- containment hierarchy fields
- confidence values

These do not currently appear useful enough for LoreVault's immediate product direction.

---

## Persistence Timing And Parallelism

Location processing should follow the same persistence timing as individuals, with one additional explicit rule.

### Timing

1. pass 2 returns current-scene entity results
2. extracted Locations are buffered by chapter-local `sceneIndex`
3. scenes are persisted
4. Location persistence resolves `sceneIndex -> persisted Scene.id`
5. `LocationMention` nodes and scene links are written using real scene IDs

### Parallelism

Once pass 2 results are available and scenes are persisted, the Location pipeline can and should execute in parallel to the individual pipeline.

That means:

- individual persistence/resolution remains one branch
- Location persistence/resolution becomes a sibling branch
- neither should be modeled as a sub-step of the other
- IngestionCompleted event remains the terminal event for the whole scene-level work, not a separate one per entity type

This matches the current event-driven ingestion direction more cleanly and keeps the next entity additions composable.

---

## Graph Shape

### Evidence layer

Persist:

- `(:LocationMention)`
- `(:Scene)-[:MENTIONS]->(:LocationMention)`

Use `MENTIONS` for the first slice rather than a stronger semantic edge.

Reason:

- evidence-first semantics
- consistency with the current mention-layer pattern
- avoids overclaiming that a scene definitively occurs in a place just because it references it

### Scoped aggregation layers

Add thin scoped nodes:

- `(:ChapterLocation)`
- `(:BookLocation)`

And links:

- `(:LocationMention)-[:REFERS_TO]->(:ChapterLocation)`
- `(:ChapterLocation)-[:REFERS_TO]->(:BookLocation)`

This mirrors the scoped entity-resolution ladder already proven with individuals.

---

## Proposed Node Shapes

### `LocationMention`

- `id: UUID`
- `source: String`
- `displayName: String`
- `normalizedName: String`
- `aliases: List<String>`
- `kind: String | null`
- `region: String | null`
- `description: String | null`
- `sceneId: UUID`
- `chapterId: UUID`
- `bookId: UUID | null`
- `resolutionStatus: String`
- `extractionIndex: Integer`
- `createdAt`
- `updatedAt`

### `ChapterLocation`

- `chapterId: UUID`
- `displayName: String`
- `normalizedName: String`
- `mentionCount: Integer`

### `BookLocation`

- `bookId: UUID`
- `displayName: String`
- `normalizedName: String`
- `chapterLocationCount: Integer`
- `representativeChapterLocationId: UUID`
- `firstSeenChapterId: UUID`

These chapter/book layers should remain intentionally thin in v1.

---

## Matching Rules

Location reduction should stay deliberately simple.

Recommended v1 rules:

- normalize `primaryName` / `displayName` by trimming, collapsing whitespace, and lowercasing
- treat exact normalized name match as the base grouping rule
- allow exact normalized alias matches to join the same group
- do not add fuzzy matching
- do not add containment reasoning
- do not add geographic heuristics

This preserves the same core bias as the individual work:

> a bad merge is worse than a missed entity

---

## Why Location Needs Slightly Different Modeling Than Individuals

The individual extraction solution should be treated as a structural template, not a field template.

Individual-specific fields like:

- `age`
- `physicalProperties`
- `activity`

should not be copied.

Instead, Locations need fields that better support:

- user-facing explanation
- exact-match reconciliation
- later query filtering

That is why `primaryName`, `kind`, `region`, and `description` are the recommended minimal fields.

---

## Query/Product Payoff

This proposal is intentionally shaped to support the next planned product slice: entity-aware Q&A.

With both individuals and Locations represented as scoped entities, LoreVault can begin designing Q&A around:

- entity kind
- entity lookup
- scene evidence
- chapter/book aggregation
- citations and follow-up navigation

instead of building a character-specific product surface first and refactoring later.

Example question classes this could support:

- "Where does this chapter take place?"
- "What Locations are associated with this book?"
- "Which scenes mention Rivendell?"
- "What places are connected to this character in this chapter?"

---

## Implementation Shape

Recommended order:

1. extend pass 2 structured DTOs to carry current-scene Locations
2. normalize and buffer Location results by `sceneIndex`
3. add `LocationMention` persistence after final scene save
4. add `ChapterLocation` deterministic grouping
5. add `BookLocation` deterministic grouping
6. wire the Location branch as a sibling to the individual branch in the post-scene flow

This should stay explicitly narrower than a general multi-entity framework.

Do the concrete work first.
Generalize only after at least individuals + Locations both exist.

---

## Recommended Follow-On Decision After This Slice

After Location extraction is shipped, the next decision should be:

> Is the entity-aware Q&A slice now ready to be built generically enough around individuals + Locations?

That is the main strategic reason to do this work now.

---

## Bottom Line

LoreVault should implement a minimal Location extraction ladder now:

- `LocationMention`
- `ChapterLocation`
- `BookLocation`

using:

- the same post-scene persistence timing as individuals
- a sibling parallel branch to the individual pipeline
- exact/lightweight name + alias matching only
- a Location-specific field set instead of copying individual fields blindly

This is the lightest useful path that keeps the next query/product work entity-based rather than individual-specific.
