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

- already requested by the scene analysis prompt
- high-value for lore retrieval and reader questions
- structurally simpler than many other entity types
- enough to push the next product slice toward a generic entity-aware solution instead of a character-only solution

---

## Current State

### Prompt behavior

The current scene analysis prompt already requests Locations.

Relevant file:

- `lorevault-api/src/main/resources/prompts/scene-analysis.txt`

So LoreVault is already paying for Location extraction attempts in the existing triad call.

### Runtime behavior

Today the code captures and persists only individual extraction results.

The current individual flow is:

1. scene analysis returns structured scene-local entity output
2. `TriadOrchestrationService` normalizes and buffers individual extractions by `sceneIndex`
3. scenes are persisted
4. extracted individuals are persisted after final scene persistence
5. later entity-resolution layers consolidate those mentions into chapter-level and book-level identity nodes

Locations are not currently captured in the structured Java DTOs or persisted anywhere.

### Important implementation reality

The individual implementation has already proven a durable pattern for:

- structured scene analysis capture
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

- Locations should run in parallel to individuals once scene analysis results are available
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

- capturing Location data from scene analysis output
- extending structured scene analysis DTOs so current-scene Locations are available to application code
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

Continue using the existing scene analysis triad prompt.

Do **not** add a separate Location-extraction prompt for this slice.

Reasoning:

- lower incremental cost
- matches the existing individual extraction path
- keeps the first Location slice focused on capture + persistence + lightweight reduction
- gives us signal about whether current scene analysis output is good enough before redesigning prompt architecture

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

1. scene analysis returns current-scene entity results
2. extracted Locations are buffered by chapter-local `sceneIndex`
3. scenes are persisted
4. Location persistence resolves `sceneIndex -> persisted Scene.id`
5. `LocationMention` nodes and scene links are written using real scene IDs

### Parallelism

Once scene analysis results are available and scenes are persisted, the Location pipeline can and should execute in parallel to the individual pipeline.

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

1. extend scene analysis structured DTOs to carry current-scene Locations
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

---

## Implementation Notes Since This Proposal

**Status update:** This proposal's first slice is now shipped on `main` as the next ingestion slice after the shipped `Individual` ladder.

### What the implementation kept from the proposal

The current implementation remains aligned with the core proposal in these ways:

- `Location` is modeled as a sibling Entity lane rather than as an Individual-specific extension
- `LocationMention` remains the evidence-bearing layer
- `ChapterLocation` and `BookLocation` are both included already in the first slice
- matching stays exact and lightweight: normalized primary name plus normalized aliases only
- `Scene -[:MENTIONS]-> LocationMention` remains the first semantic edge instead of introducing a stronger claim like `LOCATED_IN`

### Important implementation details learned while building it

#### 1. The actual scoped node payloads are thinner than the proposal's mention payload

The mention layer preserves the Location-specific descriptive fields:

- `displayName`
- `normalizedName`
- `aliases`
- `kind`
- `region`
- `description`

But the scoped reduction layers are intentionally thinner:

- `ChapterLocation` stores `chapterId`, `displayName`, `normalizedName`, `aliases`, and `mentionCount`
- `BookLocation` stores `bookId`, `displayName`, `normalizedName`, `aliases`, `chapterLocationCount`, `representativeChapterLocationId`, and `firstSeenChapterId`

So the current implementation keeps richer descriptive evidence on `LocationMention`, while the chapter/book layers stay focused on grouping and query navigation.

#### 2. Lightweight matching already includes transitive alias bridging

The proposal called for exact normalized primary-name and alias matching.

The implemented clustering logic keeps that rule, but one useful detail is now explicit: clusters can merge transitively when aliases bridge previously separate exact-match groups.

That means the implementation is still exact-only, but it is slightly stronger than a naive one-pass grouping by a single key.

This is worth preserving because it should remain an intentional property if future Entity lanes reuse the same reduction pattern.

#### 3. The parallel branch semantics are now concrete in the ingestion flow

The proposal said the `Location` branch should run in parallel with the `Individual` branch once scene analysis output exists and scenes are persisted.

That is now reflected directly in the implementation shape:

1. scene analysis returns scene-level Individuals and Locations
2. scenes are persisted first
3. `IndividualMention` and `LocationMention` persistence both use the real persisted Scene IDs; this was later extended to Object and Collective mention persistence as the regular entity ladder grew
4. chapter/book regular entity follow-up processing runs as sibling post-scene branches; current implemented lanes are Individual, Location, Object, and Collective
5. `IngestionCompleted` waits for all required scene-level follow-up work rather than firing per Entity type

This has since been promoted into the canonical entity-resolution and ingestion-pipeline patterns: new regular entity lanes attach as sibling branches, not as nested sub-steps of earlier entity types.

#### 4. Book-level Location reduction is fed by a direct repository query

One implementation deviation worth noting is that book-level `Location` reduction currently reads `ChapterLocation` nodes through a direct repository query:

- `ChapterLocationGraphRepository.findByBookId(...)`

instead of traversing `Chapter` objects first and then mapping chapter IDs in memory.

This was a practical simplification and also avoided fragile getter/accessor behavior in the current environment. It is a good example of preferring direct graph reads for reduction inputs when the scope boundary is already known.

#### 5. Completion coordination now explicitly includes the Location branch

`IngestionCompleted` already had to wait for embeddings and book-level `Individual` reduction.

With the `Location` branch added, the completion coordinator now also waits for book-level `Location` reduction before publishing the terminal ingestion event.

That preserves the meaning that ingestion completion covers the whole scene-derived reactive workload, not just the original `Individual` path.

### What remains intentionally unimplemented

The current implementation still does **not** add:

- lat/lng
- canonical IDs
- containment reasoning
- fuzzy matching
- geo heuristics
- richer ontology/taxonomy design
- generic multi-Entity framework extraction

So the present state is best described as a concrete second Entity lane built with the same structural pattern as Individuals, while deliberately keeping the resolution rules and graph semantics conservative.
