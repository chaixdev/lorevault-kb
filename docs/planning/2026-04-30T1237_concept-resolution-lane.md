# Concept Entity Resolution Lane

**Status:** NOT STARTED  
**Last Updated:** April 30, 2026

## Summary

Add the remaining regular entity resolution lane for Concept using the established `Mention → ChapterEntity → BookEntity` ladder. Concept should cover species, technologies, artifact classes, doctrines, roles, and other narrative-significant categories that are not acting groups, locations, objects, individuals, or events.

Object and Collective are now implemented regular lanes. Concept is intentionally deferred until its subtype boundaries are clear enough to avoid mixing abstract categories with concrete objects or collective actors.

## Problem

The graph now has resolved anchors for Individuals, Locations, Objects, and Collectives, but not for conceptual categories such as species, technologies, ideologies, titles, biological categories, or artifact classes.

Without Concept anchors:

- species and technology questions cannot be grounded in the graph
- relation types such as `instance_of` have no canonical Concept target
- taxonomy-like knowledge remains trapped in scene text or misclassified into other entity lanes

## Product Context

- Users ask category questions such as “what species is X?”, “what technology enables Y?”, or “which groups use this doctrine?”
- Typed semantic edges need Concept targets for relationships such as species membership, technology use, artifact classification, and doctrine affiliation.
- Concept completion will round out the six cardinal entity kinds used by the broader LoreVault model: Individual, Location, Object, Collective, Concept, and Event.

## Technical Context

The reference pattern is documented in `docs/patterns/ingestion/entity-resolution-ladder.md`.

Concept should follow the regular-lane structure now implemented by Individual, Location, Object, and Collective:

```text
Scene -[:CONTAINS]-> ConceptMention -[:REFERS_TO]-> ChapterConcept -[:REFERS_TO]-> BookConcept
```

Expected implementation surfaces:

- scene-analysis prompt and structured output model
- `ConceptMention`, `ChapterConcept`, `BookConcept`
- graph repositories and schema/index support
- concept evidence persistence after scene persistence
- chapter resolution handler/service/event
- book reduction handler/service/event
- manual rerun command endpoints
- ingestion completion fan-in update
- focused service, handler, controller, schema, and completion tests

## Scope

1. Define Concept extraction boundaries and subtype vocabulary.
2. Persist scene-local `ConceptMention` evidence.
3. Add `ChapterConcept` and `BookConcept` aggregate nodes.
4. Implement chapter resolution and book reduction using the retry-safe handler contract.
5. Add Concept completion-barrier events to ingestion fan-in only after the full lane exists.
6. Add manual rerun endpoints for chapter and book Concept reduction.
7. Add schema/index/backfill support and focused tests.

## Out of Scope

- Typed semantic edges involving Concept targets; that belongs to the relation catalog slice.
- LLM-backed concept merge adjudication in v1.
- A full ontology or taxonomy service.
- Cross-book or cross-universe concept identity.
- Refactoring the existing four regular lanes into a generic lane framework.

## Known Constraints / Prior Findings

- Species are treated as Concepts, not Collectives; biological taxonomy is conceptual rather than an acting group.
- Concept is more prone to over-extraction than Object or Collective because ordinary nouns, roles, and abstract ideas can be concept-like without being graph-worthy.
- Concept extraction should not degrade the existing Individual, Location, Object, Collective, and Event evidence quality.
- The handler must follow `docs/rules/handler-design-contract.md`: success events require coherent owned output, and retry/deferred conditions must not publish success-shaped downstream events.

## Open Questions

- What is the minimal v1 `conceptType` vocabulary?
- Which concepts are graph-worthy enough to extract, and which should remain descriptive text?
- Should roles/titles such as “captain” or “ambassador” be Concepts, attributes, or future relation targets?
- How should species, technologies, doctrines, and artifact classes be distinguished in prompts without overfitting?
- Should Concept v1 group strictly by `normalizedName`, like Object and Collective, or require a stronger subtype-aware key?

## Success Criteria

- `ConceptMention`, `ChapterConcept`, and `BookConcept` form the full ladder for ingested chapters with concept evidence.
- Concept chapter resolution and book reduction run automatically from `ScenesDetectedEvent` through completion fan-in.
- Empty Concept lanes publish valid terminal zero-count events.
- Manual rerun endpoints exist for chapter and book Concept reduction.
- Tests cover service behavior, handler event publication/failure behavior, schema/index initialization, completion fan-in, and command endpoints.
- Retrieval can use `BookConcept` as a graph anchor for concept/category questions.

## Links

- `docs/patterns/ingestion/entity-resolution-ladder.md`
- `docs/patterns/ingestion/handler-retry-safety.md`
- `docs/rules/handler-design-contract.md`
- `docs/planning/2026-05-13T2027_relation-catalog-module.md`
- `docs/planning/2026-04-30T1237_qa-retrieval-quality-validation.md`
