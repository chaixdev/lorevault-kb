# ADR 008: Define Ingestion Completion Across Parallel Branches

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault defines chapter ingestion as complete only after both post-scene branches finish:

- the content branch (`chunking -> embeddings`)
- the identity branch (`chapter resolution -> book reduction`)

`IngestionCompletedEvent` is therefore emitted only after both branches report completion for the same `(jobId, chapterId)`.

## Why

- Ingestion completion should mean all reactive work caused by chapter upload has finished, not just one branch of it
- Book-level identity reduction is part of the ingestion result, not optional post-processing after the fact
- A coordinator preserves the event-driven pipeline while giving completion semantics one explicit place to live
- This avoids overloading `IngestionCompletedEvent` as both a terminal signal and a trigger for additional work

## Alternatives Considered

**Emit `IngestionCompletedEvent` after embeddings only** — treat identity reduction as later optional work. Rejected: it weakens the meaning of completion and leaves the job in a partially processed state.

**Use `IngestionCompletedEvent` to trigger book reduction** — keep completion early and run identity reduction afterward. Rejected: a terminal event should not also mean “more required work still needs to happen.”

**Inline identity work into one earlier handler** — avoid a coordinator by collapsing the branch structure. Rejected: it would blur stage boundaries and make the event-driven ingestion pipeline less clear.

## Implications

- Completion semantics are now explicitly branch-aware rather than stage-local
- Future ingestion branches that become required work must be considered by the completion contract
- The event-driven ingestion pipeline remains intact, but terminal completion is now coordinated rather than assumed by one handler
