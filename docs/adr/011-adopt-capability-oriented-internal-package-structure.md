# ADR 011: Adopt Capability-Oriented Internal Package Structure

**Status:** Accepted  
**Date:** April 2026

## Decision

Within each top-level feature package in `lorevault-core`, LoreVault now defaults to
capability-oriented internal packages instead of the old `application/domain/infrastructure`
split.

Examples of the current shape:

- `graph/{collective,event,individual,location,mention,object,relation,timeline}`
- `orchestration/{consolidation,job,pipeline,signals,submission,triad}`
- `library/{book,chapter,chunk,series,service,universe}`
- `search/{extraction,model,rag,semantic}`
- `ai/{embedding,infrastructure,llm,telemetry}`
- `common/{error}`

Layer-oriented subpackages remain acceptable only when they are a deliberate local fit for
one dense sub-area, not the default shape for an entire bounded context.

## Why

- The old internal `application/domain/infrastructure` layering hid product meaning inside
  large flat buckets.
- Capability-oriented packages make the package tree itself explain what the system does.
- The shipped codebase already proved this shape is mechanically workable across `ingestion`,
  `content`, `ai`, `library`, and `search` while keeping architecture tests green.
- This direction aligns with LoreVault's broader preference for semantic ownership over
  technical taxonomy and for removing accidental architecture ceremony.

## Alternatives Considered

**Keep `application/domain/infrastructure` as the default inside every bounded context** —
Rejected. This kept growing large mixed buckets that were easy to add to and hard to
navigate.

**Flatten each top-level feature completely with no internal subpackages** — Rejected.
Several areas (`ingestion`, `content`, `search`) are too semantically dense for a single
flat package to remain legible.

**Force the same internal template in every feature** — Rejected. LoreVault's features have
different density and coupling patterns. The right internal shape should reflect semantic
ownership, not template symmetry.

## Implications

- Contributor guidance should treat capability-first packaging as the default rule.
- Topology docs should describe the current representative package map, not the retired
  layered one.
- Empty legacy directories left behind by semantic moves should be removed so the source tree
  does not advertise a stale model.
- Shared types that are used by multiple capability packages inside one feature should live in
  a clearly owned local seam such as `search.model`, not in one capability package that
  creates back-edges.
