# Handler Design Contract

**Status:** Active

LoreVault ingestion handlers must be designed around retry safety, explicit ownership, and dependency-aware invalidation.

Do not describe a handler as "idempotent" unless it is strictly idempotent: running it more than once has the same effect as running it once. Most pipeline work should instead be held to a **retry-safe handler contract**.

## Required Rules

### 1. Declare ownership

Every ingestion handler must have a clear ownership scope:

- owned nodes
- owned relationships
- owned projection scope
- upstream dependencies
- downstream dependents

A handler may replace or invalidate only its owned output. It must not casually delete upstream evidence or downstream projections owned by another stage.

### 2. Success events require coherent output

A success-shaped event must mean downstream handlers can safely start from the emitted state.

For example, a `Book*ReducedEvent` must mean the relevant book-level projection is coherent for that lane. It must not mean merely that the handler woke up, observed contention, skipped work, or exhausted a retry budget.

### 3. Failure is not alternate success

Retryable or deferred conditions must not be represented by downstream success events.

Examples:

- claim contention
- transient LLM/provider failure
- partial write failure
- dependency not yet current
- retry budget exhaustion that should be requeued or escalated

Use retry/deferred/failure handling instead of publishing completion-barrier events for these cases.

### 4. Invalidation must propagate

When a handler invalidates or replaces its owned output, dependent stages must be invalidated, rebuilt, or prevented from being treated as current.

The dependency cost increases upstream:

- replacing mention/evidence output can invalidate chapter and book projections
- replacing chapter projections can invalidate book projections
- replacing book projections should stay local to that book-level lane projection

### 5. Projection replacement must be coherent

Do not delete an active projection in an earlier committed transaction and rebuild it later as a routine pipeline side effect.

Use one of these patterns instead:

- atomic scoped replacement where delete/save/link commit together
- staged replacement plus activation/supersession
- explicit invalidation state that prevents stale dependents from being treated as current

Long-running analysis, including LLM calls, must not be wrapped in database transactions merely to satisfy this rule. Do analysis first, then perform a small coherent write/activation step.

### 6. Manual reruns are pipeline operations

Manual command endpoints must follow the same ownership, invalidation, and event semantics as event-driven retries.

Before adding a rerun endpoint, document:

- what projection is recomputed
- what downstream projections become stale
- whether downstream events are emitted
- how concurrent reruns for the same scope are handled
- how retryable/deferred/terminal outcomes are surfaced

## Required Handler Contract

Every new ingestion handler, and every substantial handler behavior change, must provide a contract using this structure:

```md
## Handler Contract: <HandlerName>

- Trigger event:
- Owned nodes:
- Owned relationships:
- Owned projection scope:
- Upstream dependencies:
- Downstream dependents:
- Retry/replay behavior:
- Failure behavior:
- Invalidation behavior:
- Completion event meaning:
- Safe manual rerun conditions:
```

The contract can live in a focused pattern/spec doc, an ADR when the shape is architectural, or the implementation proposal while a design is still being worked through. Accepted behavior must be promoted into canonical docs.

## Review Standard

A handler change is not merge-ready when reviewers cannot answer:

- what does this handler own?
- what does its success event allow downstream handlers to assume?
- what stale outputs are created if this handler is retried or manually rerun?
- what happens on duplicate event delivery?
- what happens after a partial failure?
- what distinguishes completed-empty, deferred, retryable failure, and terminal failure?

Related pattern: [Handler Retry-Safety Pattern](../patterns/ingestion/handler-retry-safety.md).

### 7. `execute()` must not publish domain events

When a handler exposes an `execute()` method for direct REST invocation (the `*Operation` interface pattern), that method must not publish domain events. Event emission is the caller's responsibility — either the `@EventListener` adapter or the REST controller via `StepEventMapper`.

This ensures that direct `execute()` calls from step endpoints don't trigger downstream cascades unless explicitly requested via `fireEvents=true`.

The `@EventListener` method delegates to `execute()` and handles event publication. The REST controller calls `execute()` directly and publishes events conditionally based on the `fireEvents` parameter.
