# Entity Pipeline Terminology Alignment

**Status:** IMPLEMENTED — consolidation is the canonical term across all code and docs
**Last Updated:** May 20, 2026

## Summary

The ingestion pipeline currently uses three different terms — **resolution**, **reduction**, and **aggregate** — to describe conceptually similar operations across the `Mention → ChapterEntity → BookEntity` ladder. This document explores which term best captures what the pipeline actually does and proposes alignment on a single canonical term.

## Problem

The codebase exhibits inconsistent terminology:

| Term | Where used | Example |
|------|-----------|---------|
| **Resolution** | Chapter-level handlers, services, results, operations | `ChapterIndividualResolutionHandler`, `ChapterIndividualResolutionResult`, `*ResolutionOperation` |
| **Reduction** | Book-level handlers, services, claims, operations | `BookIndividualReductionHandler`, `BookReductionClaimService`, `*ReductionOperation` |
| **Reduction + Resolution mixed** | Book-level result type | `BookIndividualResolutionResult` — uses "Resolution" in a class named with "Reduction" |
| **Aggregate** | Schema, labels, docs | `AGGREGATE_LABEL_BACKFILLS`, "aggregate nodes" |

A developer reading the code must infer that `ChapterIndividualResolutionHandler` and `BookIndividualReductionHandler` perform the same class of operation at different aggregation levels. The terminology should make this obvious.

## Product Context

- The entity pipeline is the core value of LoreVault: extracting narrative entities and projecting them into a queryable knowledge graph.
- The pipeline has 5 regular entity lanes (Individual, Location, Object, Collective, Event) with a 6th (Concept) deferred. All lanes follow the same ladder pattern: `Mention → ChapterEntity → BookEntity`.
- Consistent terminology makes the pipeline teachable, the code greppable, and future lanes mechanically reproducible.
- The terminology will be used in handler names, service names, result types, operation interfaces, event names, API endpoints, and documentation.

## Technical Context

### What the pipeline actually does

At both the chapter level and the book level, the pipeline performs the same class of operation:

1. **Group** lower-level entities by `normalizedName`
2. **Select** the best `displayName` from the group
3. **Create** a higher-level entity node (or find an existing one)
4. **Establish** `REFERS_TO` relationships: `Mention -[:REFERS_TO]-> ChapterEntity`, `ChapterEntity -[:REFERS_TO]-> BookEntity`
5. **Persist** the new nodes and edges in Neo4j

The chapter-level step groups `IndividualMention` nodes and produces `ChapterIndividual` nodes. The book-level step groups `ChapterIndividual` nodes and produces `BookIndividual` nodes. The shape of the operation is identical; only the input and output types differ.

### Current naming survey

```
ingestion/resolution/
  individual/
    ChapterIndividualResolutionHandler    (chapter level)
    ChapterIndividualResolutionService
    ChapterIndividualResolutionResult
    ChapterIndividualResolutionOperation
    BookIndividualReductionHandler        (book level)
    BookIndividualReductionService
    BookIndividualResolutionResult        ← mismatch: "Resolution" result in "Reduction" service
    BookIndividualReductionOperation
    BookReductionClaimService             (shared reduction claim)
  collective/
    ChapterCollectiveResolutionHandler
    BookCollectiveReductionHandler
    BookCollectiveResolutionResult        ← same mismatch
  location/
    ChapterLocationResolutionHandler
    BookLocationReductionHandler
    BookLocationResolutionResult          ← same mismatch
  object/
    ChapterObjectResolutionHandler
    BookObjectReductionHandler
    BookObjectResolutionResult            ← same mismatch
```

Event names follow the same pattern: `ChapterIndividualsResolvedEvent`, `BookIndividualsReducedEvent`.

### The ResolutionResult mismatch

The `BookIndividualResolutionResult` class is used by `BookIndividualReductionService` — "Resolution" result from a "Reduction" service. This is the most visible inconsistency. All five entity lanes have the same mismatch.

## Terminology Options

### Option A: Resolution (status quo chapter-level term)

**Meaning in NLP/CS:** Entity resolution is the task of determining whether two records refer to the same real-world entity. This is exactly what our pipeline does — determining that "Gandalf the Grey" in scene 1 and "Mithrandir" in scene 3 refer to the same character.

**Pros:**
- Precisely matches the NLP/academic term for what we're doing
- Already used throughout the chapter-level code
- Clear meaning: "resolving mentions to entities"

**Cons:**
- In plain English, "resolution" implies a lookup or a return, not the creation of new nodes and relationships
- Does not naturally capture the book-level step (resolving chapter entities to book entities is less intuitive than resolving mentions to chapter entities)
- "Resolution" can also mean "solution to a problem" — ambiguous in error contexts

### Option B: Reduction (status quo book-level term)

**Meaning in map/reduce:** The reduce step combines intermediate key-value pairs produced by the map step into final output. Our pipeline maps mentions (by normalized name), then reduces them to chapter entities, then reduces chapter entities to book entities.

**Pros:**
- Invokes the map/reduce pattern, which is conceptually accurate (group → combine → output)
- "Reduction" carries the sense of transformation — multiple inputs becoming fewer, more refined outputs
- Already used throughout the book-level code
- Works naturally for both levels: mention reduction, chapter reduction

**Cons:**
- Map/reduce has specific connotations (distributed computation, key-value pairs) that don't fully apply
- Less familiar to developers without map/reduce experience
- "Reduction" in plain English means "making smaller/less" — doesn't capture the enrichment aspect

### Option C: Aggregation

**Meaning in data processing:** Aggregation is gathering and summarizing data — multiple rows into one with SUM/COUNT/AVG. Multiple mentions → one entity with counts is an aggregation pattern.

**Pros:**
- Simple, widely understood term
- Accurately describes grouping by name and selecting the best name
- Used in existing docs and schema ("aggregate nodes")

**Cons:**
- Aggregation implies stateless summarization, not persistent entity creation
- Multiple-aggregation: "ChapterIndividualAggregation" → "BookIndividualAggregation" creates "aggregation of aggregation" — awkward
- Does not capture the entity linking/relationship creation aspect
- "Aggregate" as a noun (aggregate node) and verb (aggregate mentions) are distinct uses, but the noun form is already entrenched in Neo4j labels

### Option D: Consolidation

**Meaning:** Combining multiple things into a single, more effective or coherent whole.

**Pros:**
- Plain English, immediately understood
- Captures both the combining and the improvement aspects
- Works at both levels: "mention consolidation", "chapter consolidation"
- No prior art in codebase — clean break

**Cons:**
- No prior art in codebase — requires renaming everything
- Less precise than technical alternatives
- Not a standard NLP or data processing term

### Option E: Canonicalization

**Meaning:** Establishing a canonical (authoritative, standard) representation from multiple variants.

**Pros:**
- Precise: we're establishing canonical entity representations
- "ChapterCanonicalEntity" reads naturally
- Works well with the catalog module's concept of canonical definitions

**Cons:**
- Verbose: "ChapterIndividualCanonicalizationHandler" is very long
- Focuses on naming/identity, underweights the linking/relationship aspect
- Not widely used in entity resolution literature

## Decision

**Use "consolidation" as the canonical term for both pipeline levels.**

The term was selected through a DDD lens: what would a lore curator call the operation of bringing scattered evidence together into a coherent entity profile? The shortlist ranked:

| Rank | Term | Verdict |
|------|------|---------|
| 1 | **Consolidation** | Plain English, zero jargon, domain-native: a wiki editor consolidates scattered references into a character page. Selected. |
| 2 | Reconciliation | Two-way agreement between evidence. Precise about identity confirmation but carries dispute-resolution connotations. Honorable mention. |
| 3 | Promotion | Cleanest ladder symmetry (promote mentions → chapter entities → book entities). Overlaps with catalog module's "promote" (provisional → canonical). |
| 4 | Reduction | Technically accurate (map/reduce shape), smallest rename surface. Fails the DDD test — a curator doesn't "reduce" evidence into profiles. |
| 5 | Unification | Strong domain register, less precise about evidence-confirmation than reconciliation. |

### Why consolidation

The pipeline transforms fragmented evidence into a unified whole. Multiple `IndividualMention` nodes scattered across scenes become one `ChapterIndividual`. Multiple `ChapterIndividual` nodes across chapters become one `BookIndividual`. A lore curator consolidates scattered evidence; a researcher consolidates field notes; an intelligence analyst consolidates reports. The word carries exactly the right weight — bringing together, strengthening, reducing fragmentation — without borrowing from computer science, data processing, or crime scene investigation.

### What changes

| Current name | → New name |
|---|---|
| `ChapterIndividualResolutionHandler` | `ChapterIndividualConsolidationHandler` |
| `ChapterIndividualResolutionService` | `ChapterIndividualConsolidationService` |
| `ChapterIndividualResolutionResult` | `ChapterIndividualConsolidationResult` |
| `ChapterIndividualResolutionOperation` | `ChapterIndividualConsolidationOperation` |
| `ChapterIndividualsResolvedEvent` | `ChapterIndividualsConsolidatedEvent` |
| `BookIndividualResolutionResult` | `BookIndividualConsolidationResult` |
| `BookIndividualReductionHandler` | `BookIndividualConsolidationHandler` |
| `BookIndividualReductionService` | `BookIndividualConsolidationService` |
| `BookIndividualReductionOperation` | `BookIndividualConsolidationOperation` |
| `BookIndividualsReducedEvent` | `BookIndividualsConsolidatedEvent` |
| `BookReductionClaimService` | `BookConsolidationClaimService` |
| `BookReductionClaimUnavailableException` | `BookConsolidationClaimUnavailableException` |
| (same pattern for Location, Object, Collective, Event, Concept) | |

Rename package on concept-lane starts only (not existing codebase — defer to implementation).

### What does NOT change

- `AGGREGATE_LABEL_BACKFILLS` — refers to Neo4j aggregate labels (a different concept). The `Mention` label is an aggregate label in the Neo4j sense; it's not related to the pipeline operation terminology.
- "Aggregate nodes" in docs — these are Neo4j nodes that carry an aggregate label. The pipeline **produces** aggregate nodes; the pipeline operation is **consolidation**.

## Scope

1. Decide on the canonical term (this document — **decided: consolidation**)
2. Rename chapter-level handlers, services, results, operations, events, and event records from `*Resolution*` to `*Consolidation*`
3. Rename book-level handlers, services, results, operations, and events from `*Reduction*` to `*Consolidation*`
4. Fix the `Book*ResolutionResult` → `Book*ConsolidationResult` mismatches in existing book-level code
5. Update event names (`ChapterIndividualsResolvedEvent` → `ChapterIndividualsConsolidatedEvent`, `BookIndividualsReducedEvent` → `BookIndividualsConsolidatedEvent`, etc.)
6. Rename `BookReductionClaimService` → `BookConsolidationClaimService` and related classes
7. Update test class names and references
8. Update docs/planning/, docs/patterns/, and PROJECT-STATUS.md
9. Update the Concept lane planning doc (`2026-04-30T1237_concept-resolution-lane.md`) to use the new terminology before implementation

## Out of Scope

- Renaming the package `ingestion/resolution/` — that's a structural concern for a separate refactoring pass. Package rename can follow once the class renames settle.
- Renaming `AGGREGATE_LABEL_BACKFILLS` or other Neo4j schema terms — these refer to Neo4j's aggregate label concept, not the pipeline operation.
- Renaming the entity resolution ladder pattern doc — the *pattern* is still entity resolution in the NLP sense; the *pipeline step name* changes to consolidation.
- Generic lane framework extraction.

## Known Constraints / Prior Findings

- 224+ usages of "Resolution"/"Reduction"/"Aggregat" in the ingestion package alone.
- The rename is mechanical but large — 5 existing entity lanes × ~8 classes each = ~40 classes to rename, plus book-level handlers that already use "Reduction."
- IDE refactoring tools (`ide_refactor_rename`) can handle this with reference updating.
- The Concept lane has NOT been implemented yet — it can be built with the new terminology from the start.
- The Relation Claim pipeline uses different terminology (claim, evidence, proposition) and is not affected.

## Open Questions

- **Applied consistently to Event lane too?** The Event lane currently uses `ChapterEventResolutionHandler` and `ChapterEventEmbeddingHandler`. The pipeline has an extra stage (embedding + ANN candidates) that the other lanes don't. Should both stages become `*Consolidation*` or should the embedding stage keep a distinct name? Recommendation: `ChapterEventConsolidationHandler` (chapter resolution) + `ChapterEventEmbeddingHandler` (distinct embedding stage) — consolidation covers the entity-linking step, embedding covers the ANN candidate generation.
- **Should we rename the package?** `ingestion/resolution/` → `ingestion/consolidation/`? Defer to a follow-up task; class renames are sufficient for immediate clarity.
- **Catalog module's "promote" conflict:** The Decision section flagged this as a minor tension for "promotion" but consolidation avoids it entirely. No action needed.

## Success Criteria

- Chapter-level handlers, services, results, and operations use `*Consolidation*` consistently
- Book-level handlers, services, results, and operations use `*Consolidation*` consistently
- `BookReductionClaimService` renamed to `BookConsolidationClaimService`
- Event names use `*ConsolidatedEvent` consistently at both levels
- All references in tests, docs, and planning files are updated
- The Concept lane planning doc uses the new terminology
- `mvn test` passes with zero compilation errors from the rename

## Links

- `docs/patterns/ingestion/entity-resolution-ladder.md` — the entity resolution ladder pattern
- `docs/planning/2026-04-30T1237_concept-resolution-lane.md` — Concept lane planning (uses old terminology)
- `docs/planning/2026-05-07T1917_relation-evidence-harvesting.md` — relation evidence harvesting (uses different terminology)
- `docs/rules/handler-design-contract.md` — handler design contract
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/` — current package with mixed naming
