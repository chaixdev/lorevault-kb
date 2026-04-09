# Content Persistence Pattern

**Status:** Established

LoreVault persists core graph content through SDN-annotated domain entities and repository-driven access.

## Current Shape

- domain entities annotated directly for persistence
- repositories return domain types directly
- no large mapper layer between domain and persistence nodes
- no God Port for content persistence

## Why This Pattern Exists

The old persistence shape introduced avoidable duplication and abstraction cost.

The current pattern keeps the graph-facing domain model closer to the actual persistence model while reserving abstraction for real external boundaries.

## Practical Consequences

- core content entities can be read and written directly through repositories
- persistence code is easier to trace through the feature packages
- tests can verify real persistence behavior without simulating fake internal boundaries
- refactors should not reintroduce duplicate `*Node` models or broad content persistence ports

Primary references:
- `../development/current/data-model/neo4j-content-data-model.md`
- `../development/current/refactor-roadmap.md`
- `../adr/003-prefer-direct-services-over-ports-and-mappers.md`
