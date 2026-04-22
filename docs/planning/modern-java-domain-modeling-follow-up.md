# Modern Java domain-modeling follow-up

## Status

Parked

## Summary

After the semantic package reorganization, the codebase still contains several families of POJOs and records that repeat overlapping fields and shapes.

This follow-up tracks future work to revisit the domain-modeling style using newer Java language features, especially interface-first contracts, sealed families, and small composed value objects where they improve clarity without reintroducing inheritance-heavy design.

## Problem

Several model families currently repeat the same structural fields:

- naming fields such as `displayName`, `normalizedName`, and sometimes `aliases`
- scope fields such as `bookId`, `chapterId`, and `sceneId`
- lifecycle/status fields such as `resolutionStatus` and `extractionIndex`
- audit fields such as `createdAt` and `updatedAt`

This duplication is not automatically wrong, but it creates pressure to answer two separate questions more explicitly:

- which types share a meaningful **contract**
- which types share enough stable **state shape** to justify extracting a reusable value object or other composition mechanism

The codebase should avoid solving this with a broad abstract-class hierarchy unless the hierarchy reflects a genuinely stable semantic taxonomy.

## Product Context

LoreVault is building a graph-shaped knowledge model from ingestion workflows and then iterating heavily on retrieval and graph-aware question answering.

That means the model layer will continue to grow in at least three directions:

- more content entities
- richer extracted mention families and resolved aggregates
- more search-side logic that benefits from depending on clear, narrow contracts rather than on concrete implementation classes

## Technical Context

Recent restructuring established a stronger semantic package shape:

- `content` owns the canonical persisted knowledge model
- `ingestion` owns workflows that produce or enrich `content`
- `search` owns retrieval and answer-generation workflows
- `ai` is narrowed to generic AI-facing concerns

Within that new shape, the codebase currently mixes:

- mutable Spring Data Neo4j node classes
- immutable record-based node types
- narrow interfaces such as `content.timeline.domain.Event`

The strongest duplication pressure currently appears in families like:

- `IndividualMention`, `LocationMention`, `EventMention`
- `ChapterIndividual`, `ChapterLocation`, `BookIndividual`, `BookLocation`

The current `Event` contract is a useful example of the kind of future direction worth evaluating: a small semantic interface used by timeline logic, rather than a shared base entity class.

## Scope

This future work should evaluate whether the model should adopt more explicit modern-Java modeling patterns such as:

- interface-first capability contracts like `BookScoped`, `ChapterScoped`, or `SceneScoped`
- sealed interfaces for closed type families such as mention-like concepts
- small composed value objects for repeated field clusters such as names or scope context
- selective cleanup of duplicated field groups where the resulting model is clearer than the current flat repetition

The goal is not generic deduplication. The goal is a clearer and more intention-revealing model vocabulary.

## Out Of Scope

- broad inheritance trees across all persisted node types
- introducing generic types such as `Entity<T>` without a strong demonstrated use case
- forcing `Scene` or other content entities into a generic taxonomy that weakens current semantic ownership
- package restructuring work itself
- repository or persistence abstraction work unrelated to model clarity

## Known Constraints / Prior Findings

- Interfaces are likely a better default than abstract base classes for shared contracts.
- Records fit interface-based modeling better than base-class hierarchies.
- A single class hierarchy is a poor fit for the current model because several type families vary along multiple axes at once:
  - semantic kind
  - scope
  - lifecycle stage
  - timeline/event modality
- `Scene` participating in timeline logic does not necessarily mean it should be forced into a generic entity hierarchy.
- `Event` currently works well as a narrow semantic contract rather than as a shared stored-state abstraction.
- If state reuse is desired, composition via small value objects may be a better fit than inheritance.

## Open Questions

- Which duplicated field groups are truly semantic and stable enough to deserve named contracts?
- Which repeated field groups are better left duplicated because they are persistence-shaped rather than domain-shaped?
- Which families are genuinely closed enough to benefit from sealed interfaces?
- How well do small composed value objects fit the current Spring Data Neo4j mapping model?
- Should contract interfaces initially target only record families, rather than the entire model layer?
- Is there a minimal naming vocabulary for capability interfaces that reads clearly in this codebase, such as `BookScoped` rather than `BookScope`?

## Success Criteria

- The model gains clearer reusable contracts without growing an inheritance forest.
- Shared capability interfaces, if introduced, make consumers easier to write and understand.
- Repeated field clusters are extracted only when the result improves semantic clarity.
- Mutable SDN classes and record-based node types continue to fit naturally in the design.
- Search and ingestion logic can depend on narrower contracts where helpful, instead of on overly concrete model classes.

## Links

- [Code organization guidance](../rules/code-organization-guidance.md)
- [Service design principles](../rules/service-design-principles.md)
- [Systematically transform package structure toward the target shape](package-shape-transformation-plan.md)
