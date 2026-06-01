# Cross-Kind Subgraph Relations

**Status:** BRAINSTORM  
**Date:** June 1, 2026

## Idea

When the same real-world entity appears across multiple entity kinds (e.g., "Hephaestus" as an Object/spaceship AND a Location/setting), the typed subgraphs capture different facets independently. A post-processing pass could identify these cross-kind clusters and materialize typed relations between them.

Example:
```
(ObjectMention{name:"Hephaestus", type:"Spaceship"})
  -[:RELATES_SUBJECT]-> (RelationClaim{name:"is meeting place of"})
  -[:RELATES_OBJECT]-> (LocationMention{name:"Hephaestus", kind:"Spaceship", coordinates:...})
```

## Why not scene-local

Scene-level evidence is unlikely to produce these links — the LLM sees the entity in one context per scene (inside the ship → Location, piloting it → Object). A separate post-processing pass that compares cross-kind `normalizedName` clusters could detect when facets exist and suggest relation types.

## Prerequisites

- At least 2 entity lanes producing data for the same universe (Individual + Location, Location + Object, etc.)
- A clustering mechanism by `normalizedName` across kinds
- A catalog of cross-kind relation types (or reuse the existing relation catalog)

## Out of scope for v1

Concept entity lane ships without this. The infrastructure (`RelationClaimPersistenceService.resolveMentionId()` routing by kind) already supports it — no code changes needed for the graph layer. The bottleneck is evidence quality, not code.

## See also

- `docs/planning/2026-06-01T1400_concept-entity-lane.md` — D3 cross-kind multiplicity discussion
- `docs/patterns/ingestion/claim-entity-linking.md` — relation claim persistence infrastructure
