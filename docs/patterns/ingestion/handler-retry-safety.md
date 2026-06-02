# Handler Retry-Safety Pattern

**Status:** Established guidance

LoreVault's ingestion pipeline is event-driven and stage-oriented. Handlers do not participate in one global transaction. Each handler receives a durable event, performs bounded work for a specific scope, and emits the next event only after its owned output is coherent enough for dependents to consume.

This pattern avoids using the word "idempotent" as a blanket promise. Strict idempotency means running the same operation multiple times has the same effect as running it once. Some LoreVault stages use or may later use LLM-assisted reducers, where retries can legitimately produce a different latest aggregate card. The required property is therefore **retry safety**: replaying or retrying a handler must leave the system coherent, must not make partial state look complete, and must propagate invalidation to dependent projections.

## Core Contract

Every ingestion handler must have an explicit ownership scope.

A handler is retry-safe when all of the following are true:

1. **Owned output is explicit.** The handler owns a defined set of nodes, relationships, and projection state for a scope such as `(chapterId, lane)` or `(bookId, lane)`.
2. **Inputs and dependents are explicit.** The handler declares which upstream outputs it depends on and which downstream stages consume its output.
3. **Success means coherent output.** A success event means the handler's owned output is in a valid terminal state for that run attempt.
4. **Failure is not alternate success.** Claim contention, transient provider errors, partial writes, and retryable infrastructure failures must not emit success-shaped downstream events.
5. **Invalidation propagates.** If a handler invalidates or replaces owned output, all dependent downstream projections must be marked stale, rebuilt, or otherwise prevented from being treated as current.
6. **Retry cost is understood.** The earlier a stage sits in the dependency chain, the more expensive its retry is because more downstream projections are invalidated.

## Ownership and Dependency Shape

The current entity pipeline has this ownership model:

| Stage | Owned output | Downstream invalidated by replacement |
|---|---|---|
| Scene/entity evidence persistence | Scene-local mention/evidence nodes for a chapter/run | Chapter reducers for affected lanes, then book reducers for those lanes |
| Chapter entity reducers | Chapter-scoped aggregate nodes and mention-to-chapter `REFERS_TO` links | Book reducer for the same lane and parent book |
| Book entity reducers | Book-scoped aggregate nodes and chapter-to-book `REFERS_TO` links | Current book-level projection for that lane |
| Completion coordinator | Orchestration state for `(jobId, correlationId, chapterId)` | No semantic graph ownership |

Handlers own their projections, not arbitrary upstream or downstream data. For example, a chapter reducer may replace its own `Chapter*` aggregate nodes and mention-to-chapter aggregate relationships. It does not own mention extraction output or book-level aggregate output.

## Event Semantics

Pipeline events are control-plane contracts. They should describe what downstream handlers are allowed to assume, not merely that code ran.

- `Chapter*ConsolidatedEvent` means the chapter-level projection for that lane reached a coherent terminal state.
- `Book*ConsolidatedEvent` means the book-level projection for that lane reached a coherent terminal state.
- `IngestionFailedEvent` means a stage failed and the affected pipeline key should not be completed by late success-branch events.

Observability flags such as `processed=false` must not be consequential control-plane signals. If a reduced/resolved event is emitted, fan-in may count that branch as complete. Therefore, conditions such as claim contention, retry budget exhaustion, or deferred work must not publish reduced/resolved events. They should be retried, requeued, or represented as failure/deferred state before completion can advance.

## Replacement and Deletion

Deletion is not forbidden, but replacement must respect ownership and dependency boundaries.

Acceptable replacement:

- scoped to the handler's owned nodes/relationships
- performed as one coherent projection update, or guarded by an active-version/supersession model
- followed by invalidation or recomputation of dependent projections
- observable as complete only after the replacement reaches a valid terminal state

Unsafe replacement:

- deleting an active projection in one committed transaction and rebuilding it later in a separate transaction
- deleting upstream source/evidence nodes without invalidating downstream projections derived from them
- emitting completion-barrier events for skipped or contended work
- relying on event arrival alone when the event can mean "did not run"

For derived aggregate projections, delete-and-rebuild can be the right implementation when it is scoped and coherent. If reducers become long-running or LLM-assisted, prefer a staged or versioned projection model where analysis happens outside destructive writes and activation is the small coherent step.

## Retry and Manual Rerun Semantics

Manual rerun endpoints follow the same contract as event-triggered retries.

Before adding or modifying a rerun path, define:

- what owned projection is being recomputed
- which upstream outputs are read
- which downstream projections become stale
- whether the rerun should emit downstream events automatically
- what happens when another run holds the same scope
- how operators can distinguish completed, empty, deferred, retryable, and failed outcomes

Rerunning an earlier stage is more expensive because it invalidates more work. Rerunning mention extraction can invalidate chapter and book projections. Rerunning a chapter reducer invalidates the book reducer for that lane. Rerunning a book reducer should affect only that book-level projection.

## Handler Contract Template

Every new ingestion handler, and every substantial modification to an existing handler, should document this contract either in a nearby pattern/spec doc or in the implementation proposal that promotes into canonical docs:

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

## Minimum Review Checklist

Reviewers should reject a handler change when any of these are unclear:

- What exact projection does this handler own?
- What downstream work becomes stale if this handler replaces its output?
- Can duplicate event delivery produce duplicate active projections?
- Can a failure leave no coherent active projection while still advancing fan-in?
- Does the success event mean durable coherent output, or only that a handler attempted work?
- Are retryable/deferred conditions distinct from terminal failure and success?

## Related Guidance

- [Ingestion Pipeline Pattern](ingestion-pipeline.md)
- [Handler Design Contract](../../rules/handler-design-contract.md)
