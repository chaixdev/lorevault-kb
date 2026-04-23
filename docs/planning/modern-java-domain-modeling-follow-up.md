# Modern Java domain-modeling follow-up

## Status

In progress

## Summary

After the semantic package reorganization, the codebase still contains several families of POJOs and records that repeat overlapping fields and shapes.

Current progress on this follow-up is intentionally narrow:

- added a feature-owned `Mention` capability contract for `IndividualMention`, `LocationMention`, and `EventMention`
- kept persisted mention fields flat to avoid introducing Spring Data Neo4j composition that would require a coordinated schema/data migration
- deferred value-object extraction for naming/scope/lifecycle clusters until a later pass has a demonstrated consumer and an explicit migration plan

This follow-up tracks a bounded review of `lorevault-core` content-modeling choices, especially where newer Java features or small semantic value objects might improve clarity without reintroducing inheritance-heavy design, speculative abstraction, or new shared model layers.

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

The codebase should also avoid treating every repeated field cluster as evidence that a new interface, sealed family, or shared model type is needed. Some repetition may still be the clearer choice when the shared shape is persistence-driven rather than semantically stable.

## Product Context

LoreVault is building a graph-shaped knowledge model from ingestion workflows and then iterating heavily on retrieval and graph-aware question answering.

That means the content model will continue to grow in at least three directions:

- more content entities
- richer extracted mention families and resolved aggregates
- more search-side logic that may benefit from clear, narrow contracts where there is real reuse, rather than defaulting to new shared abstractions over concrete model classes

## Technical Context

Recent restructuring established a stronger semantic package shape:

- `content` owns the canonical persisted knowledge model
- `ingestion` owns workflows that produce or enrich `content`
- `search` owns retrieval and answer-generation workflows
- `ai` is narrowed to generic AI-facing concerns

Within that new shape, the codebase currently mixes:

- mutable Spring Data Neo4j node classes
- immutable or record-based result/model types
- narrow interfaces such as `content.timeline.domain.Event`

The planning question is no longer whether these patterns exist; they already do. The question is whether any specific duplication hotspot inside `content` or closely adjacent content-model code deserves a clearer semantic contract or small composed value object without violating the newer guardrails around over-abstraction, semantic ownership, and shared-model sprawl.

The strongest duplication pressure currently appears in families like:

- `IndividualMention`, `LocationMention`, `EventMention`
- `ChapterIndividual`, `ChapterLocation`, `BookIndividual`, `BookLocation`

The current `Event` contract is a useful data point, but not a blanket template for the rest of the model. Any future contract extraction still needs to justify itself against current rules that reject single-implementation interfaces and speculative abstraction.

## Scope

This future work should evaluate a small set of content-model cleanup opportunities such as:

- narrowly justified capability contracts only where there are multiple real consumers or implementations and the contract remains feature-owned and intentionally small
- small composed value objects for repeated field clusters such as names or scope context, but only where the resulting model is semantically clearer and compatible with current Spring Data Neo4j mapping constraints
- selective cleanup of duplicated field groups where the resulting model is clearer than the current flat repetition and does not create a new shared abstraction layer across features
- whether any type family is actually closed enough to justify sealed types, rather than assuming that mention-like concepts are ready for that treatment

The goal is not generic deduplication. The goal is a clearer and more intention-revealing model vocabulary.

## Out Of Scope

- broad inheritance trees across all persisted node types
- introducing generic types such as `Entity<T>` without a strong demonstrated use case
- forcing `Scene` or other content entities into a generic taxonomy that weakens current semantic ownership
- introducing interface-first capability contracts as a default modeling style
- creating new shared model types that span `content`, `library`, `search`, or `ingestion` just to reduce repeated fields
- treating mention-like families as sealed by default before the family is clearly closed
- package restructuring work itself
- repository or persistence abstraction work unrelated to model clarity

## Known Constraints / Prior Findings

- Interfaces are likely a better default than abstract base classes for shared contracts.
- Records fit interface-based modeling better than base-class hierarchies when a real shared contract already exists.
- A single class hierarchy is a poor fit for the current model because several type families vary along multiple axes at once:
  - semantic kind
  - scope
  - lifecycle stage
  - timeline/event modality
- `Scene` participating in timeline logic does not necessarily mean it should be forced into a generic entity hierarchy.
- `Event` currently exists as a narrow semantic contract, but it is not by itself enough evidence that interface-first modeling should spread broadly across persisted model families.
- If state reuse is desired, composition via small value objects may be a better fit than inheritance.
- Current coding standards reject single-implementation interfaces and speculative abstraction without a demonstrated reuse case.
- LoreVault's module conventions forbid creating new shared domain models across feature boundaries just to reduce duplication.
- Spring Data Neo4j supports constructor-based and immutable-style modeling, but nested value-object persistence is not automatic and any composition idea must be checked against actual SDN mapping constraints.

## Open Questions

- Which duplicated field groups are truly semantic and stable enough to deserve named contracts?
- Which repeated field groups are better left duplicated because they are persistence-shaped rather than domain-shaped?
- Which families are genuinely closed enough to benefit from sealed interfaces?
- How well do small composed value objects fit the current Spring Data Neo4j mapping model?
- Which candidate contracts would have more than one meaningful consumer or implementation, rather than becoming single-implementation interfaces?
- Should contract exploration stay inside `content` and closely related timeline code only, rather than treating the entire model layer as one design surface?
- Is there a minimal naming vocabulary for capability interfaces that reads clearly in this codebase, such as `BookScoped` rather than `BookScope`?

## Success Criteria

- The model gains clearer reusable contracts without growing an inheritance forest.
- Shared capability interfaces, if introduced, are few, clearly justified, and smaller than the concrete model families they describe.
- Repeated field clusters are extracted only when the result improves semantic clarity.
- Mutable SDN classes and record-based types continue to fit naturally in the design.
- Any new contracts or value objects remain feature-owned and do not create a new shared model layer across package boundaries.
- Search and ingestion logic depend on narrower contracts only where that narrowing reflects real reuse and ownership, not abstraction for its own sake.

## Links

- [Code organization guidance](../rules/code-organization-guidance.md)
- [Service design principles](../rules/service-design-principles.md)
- [Systematically transform package structure toward the target shape](package-shape-transformation-plan.md)
