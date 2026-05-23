# Provenance and Projection Generation Model Brainstorm — April 2026

**Status:** Exploratory proposal  
**Related canonical guidance:** `../../patterns/ingestion/handler-retry-safety.md`, `../../rules/handler-design-contract.md`

## Summary

LoreVault needs stronger provenance around ingestion handlers so retries, manual reruns, and upstream invalidation can be handled deliberately. The goal is not an immutable append-only data layer. Aggregate cards and projections may still mutate. The goal is to know which handler owns which projection, which upstream outputs it consumed, which downstream projections become stale when it changes, and when a success event is allowed to unlock dependent work.

The recommended direction is a **generation-backed mutable projection model**: keep current domain nodes mutable/rebuildable, but introduce a small provenance/control layer around every handler-owned projection. Each owned projection gets a `ProjectionGeneration` record with an owner key, status, input fingerprint/watermark, and explicit dependency edges to upstream generations.

## Problem

The current ingestion pipeline has a causal shape:

1. scene/entity evidence persistence creates mention/evidence nodes
2. chapter reducers create chapter-scoped aggregate projections
3. book reducers create book-scoped aggregate projections
4. completion fan-in waits for branch success events

This shape is retryable in principle, but the current system does not have enough provenance to answer basic operational questions:

- Which active book projection was derived from which chapter projections?
- If mention extraction is rerun for a chapter, which chapter and book projections are now stale?
- Did a handler skip because nothing changed, or because it could not acquire a claim?
- Is a completion event tied to a coherent projection or merely to handler execution?
- Can a manual rerun safely emit downstream events, and what does it invalidate?

Without an explicit provenance structure, retry/replay safety is enforced by convention rather than by the domain model.

## Design Principles

- **Not event sourcing.** The domain graph does not need to become immutable. Mutable aggregate cards remain acceptable.
- **Projection ownership is explicit.** Every handler owns a projection scope, not arbitrary graph data.
- **Success means current coherent projection.** Completion-barrier events should carry enough identity to prove what became current.
- **Invalidation propagates downstream.** Earlier invalidation is more expensive because more dependent projections must be recomputed.
- **LLM work happens outside long DB transactions.** Analysis can be slow and non-deterministic; activation should be the small coherent write step.
- **Determinism is not required.** LLM-backed reducers may produce a different latest card on retry, but the resulting projection must still be coherent and traceable.

## Recommended Model: Generation-Backed Mutable Projections

Introduce a control-plane node for handler-owned output generations:

```text
(:ProjectionGeneration {
  id,
  ownerKey,
  stage,
  lane,
  scopeType,
  scopeId,
  status,
  inputFingerprint,
  jobId,
  correlationId,
  startedAt,
  completedAt,
  handlerVersion,
  modelId,
  promptVersion,
  estimatedCost
})

(:ProjectionGeneration)-[:DEPENDS_ON]->(:ProjectionGeneration)
(:ProjectionGeneration)-[:PRODUCED]->(:ChapterIndividual|:BookIndividual|:ChapterEvent|...)
(:Chapter|:Book)-[:CURRENT_PROJECTION {ownerKey}]->(:ProjectionGeneration)
```

Domain nodes can still be updated, replaced, or rebuilt. The generation node records the control-plane truth: what projection was produced, what it depended on, what is current, and whether downstream work needs to run.

## Core Concepts

### Owner key

An owner key identifies one handler-owned projection scope.

Examples:

- `entity-mentions:chapter:{chapterId}:lane:object`
- `chapter-objects:chapter:{chapterId}`
- `book-objects:book:{bookId}`
- `chapter-events:chapter:{chapterId}`
- `book-event-candidates:book:{bookId}`

The exact string format can evolve, but it must be stable enough for retries and manual reruns to target the same projection scope.

### Projection generation

A projection generation is a version of handler-owned output, not a user-facing lore entity.

Possible statuses:

- `BUILDING` — work has started, not safe for dependents
- `ACTIVE` — current coherent output for the owner key
- `STALE` — upstream dependency changed; do not treat as current
- `SUPERSEDED` — replaced by a newer active generation
- `FAILED` — attempt failed and requires retry or attention
- `DEFERRED` — work is blocked or queued, such as claim contention

### Current pointer

Use an explicit current relationship from the scope anchor to the active generation:

```text
(:Chapter {id})-[:CURRENT_PROJECTION {ownerKey: "chapter-objects:chapter:{id}"}]->(:ProjectionGeneration)
(:Book {id})-[:CURRENT_PROJECTION {ownerKey: "book-objects:book:{id}"}]->(:ProjectionGeneration)
```

This is easier to reason about than scattering `current=true` across many produced nodes. Read paths can gradually become projection-aware by following current generation anchors.

### Dependency edges

Projection generations explicitly depend on upstream generations.

Examples:

- chapter object generation depends on object mention generation for that chapter
- book object generation depends on all current chapter object generations for that book
- event candidate generation depends on chapter event generation and relevant existing book event/index generations

These edges let invalidation become graph traversal instead of tribal knowledge.

### Watermark / input fingerprint

Each generation records an `inputFingerprint`, derived from upstream generation IDs plus handler version/config/model/prompt versions.

This enables a handler to answer:

- are my current inputs unchanged?
- can I safely short-circuit?
- did upstream change while I was working?
- should this result activate or mark itself stale/deferred?

Creative framing called this a **watermark**: a compact proof of what upstream tide level the projection was built from.

## Event Semantics

Success events should carry the active `projectionGenerationId` they produced or consumed.

Example direction:

```text
ChapterObjectsResolvedEvent(
  jobId,
  correlationId,
  chapterId,
  bookId,
  projectionGenerationId,
  processed,
  ...observability counts
)
```

The event means:

> The handler-owned projection for this owner key is coherent and active for generation `projectionGenerationId`.

It must not mean:

- the handler woke up
- the claim was contended
- work was skipped because dependencies were stale
- a retry budget was exhausted

Fan-in should count only success events tied to coherent active generations. If a handler discovers that upstream changed during processing, it should mark its attempt `STALE`/`DEFERRED` or emit failure/deferred state, but it should not publish a completion-barrier success event.

## Invalidation Flow

When an upstream projection changes:

1. Create or activate the new upstream generation.
2. Traverse `DEPENDS_ON` in reverse to find dependent active generations.
3. Mark dependents `STALE` or enqueue rebuilds.
4. Prevent stale dependent projections from being treated as current for fan-in or read paths.
5. Rebuild dependents in dependency order.
6. Activate new downstream generations and mark old generations `SUPERSEDED`.

The earlier the invalidation, the larger the wave:

- mention/evidence rerun invalidates chapter and book projections for affected lanes
- chapter projection rerun invalidates book projection for that lane
- book projection rerun is local to that book-level lane projection

Creative framing called this a **high tide**: upstream changes raise the waterline, and downstream projections with old watermarks are locked until they are rebuilt or confirmed still valid.

## Options Considered

### Option 1 — Property-only provenance

Add provenance fields directly to every produced node:

- `projectionGenerationId`
- `ownerKey`
- `sourceGenerationIds`
- `inputFingerprint`
- `status`
- `current`

**Pros:** fastest to add, minimal schema expansion.  
**Cons:** weak dependency traversal, hard to query “what depends on this,” repeated fields across many node types, read paths need type-specific scans.

### Option 2 — Generation-backed mutable projections

Add explicit `ProjectionGeneration` nodes, dependency edges, produced-output edges, and current pointers.

**Pros:** graph-native provenance, supports invalidation traversal, keeps domain graph mutable, works for LLM and deterministic reducers, enables manual rerun reasoning.  
**Cons:** requires new schema and read/write discipline; existing queries need gradual projection-awareness.

**Recommendation:** use this as the baseline.

### Option 3 — Full append-only/event-sourced graph

Store every semantic data change as immutable facts and derive current state from event history.

**Pros:** maximum auditability and time travel.  
**Cons:** over-solves this problem, conflicts with the desire to mutate aggregate cards, and would require a broad architecture rewrite.

**Recommendation:** do not pursue now.

### Option 4 — Tidal watermarks plus lineage edges

Use watermarks/input fingerprints on projections and explicit `DERIVED_FROM`/`DEPENDS_ON` lineage edges. Upstream changes trigger an invalidation wave that locks or marks stale downstream outputs.

**Pros:** pragmatic, explainable in Neo4j, good short-circuit behavior.  
**Cons:** needs careful status semantics to avoid stale projections leaking into read paths.

**Recommendation:** fold this into Option 2 as vocabulary and implementation detail.

## Minimal Viable Slice

Start with the event lane or the next reducer lane that needs LLM-assisted behavior.

Why event lane first:

- event reduction already has LLM-capable behavior
- embeddings and ANN candidates create non-trivial downstream dependencies
- provenance has immediate debugging value
- it pressure-tests generation IDs, model/prompt versions, and active projection semantics

Suggested MVP:

1. Add `ProjectionGeneration` node and repository/service.
2. Define owner keys for event mention extraction, chapter event resolution, and event candidate/book event work.
3. Stamp produced nodes with `projectionGenerationId`.
4. Add `DEPENDS_ON` and `PRODUCED` relationships.
5. Add current projection pointers from `Chapter`/`Book` anchors.
6. Add generation IDs to success events.
7. Update fan-in to count only active current generations.
8. Add manual rerun behavior that invalidates dependents before enqueueing downstream work.

## Open Questions

- Should `ProjectionGeneration` live in core domain packages or an ingestion/provenance package?
- Should current pointers live on `Chapter`/`Book`, or on dedicated scope nodes such as `ProjectionScope`?
- How granular should mention/evidence generations be: all entity mentions for a chapter, or one generation per lane?
- What is the right status model for deferred work versus retryable failure?
- How should stale projections appear in read APIs: hidden, marked stale, or shown with warnings?
- What retention policy should eventually clean up `SUPERSEDED` generations and produced nodes?
- Which model/prompt/config fields are mandatory for LLM-backed generations?

## Proposed Vocabulary

- **Projection** — handler-owned derived view of part of the graph.
- **Projection generation** — one version/attempt of a projection.
- **Owner key** — stable key identifying who owns a projection scope.
- **Current projection** — the active coherent generation for an owner key.
- **Watermark** — fingerprint of upstream generations and handler configuration.
- **Invalidation wave** — propagation of stale state from upstream replacement to downstream dependents.
- **Activation** — small coherent write step that makes a generation current.
- **Supersession** — marking prior current generation replaced but not necessarily deleting it.

## Next Steps

1. Decide whether to prototype provenance on the event lane first.
2. Draft an ADR if generation-backed mutable projections become the chosen architecture.
3. Update handler contracts for existing lanes as they are touched.
4. Fix immediate book reducer safety issues separately; provenance is the strategic model, not a prerequisite for stopping unsafe completion events.
