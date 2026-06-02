# Entity Interfaces — Field Standardization + Consolidation Simplification

**Status:** PLANNING

## Motivation

Every entity type (Individual, Location, Object, Collective, Concept, Event) has structurally identical fields replicated across 18 record classes (6 Mentions + 6 ChapterXxx + 6 BookXxx). Two clusters of common fields emerge:

1. **Name identity:** `displayName`, `normalizedName`, `aliases` — used by consolidation clustering but duplicated per type
2. **Provenance:** `id`, `stageId`, `createdAt`, `updatedAt` — present on every single graph node

Extracting these into interfaces lets consolidation and pipeline code operate on the interface instead of concrete types, collapsing call sites and reducing the blast radius of future changes.

## Evidence vs Interpretation

LoreVault's domain model has two layers. See `docs/rules/entity-node-contract.md`.

| | Evidence Layer | Interpretation Layer |
|---|---|---|
| **Nodes** | `*Mention`, `RelationClaim` | `ChapterXxx`, `BookXxx` |
| **Role** | Raw LLM extractions — facts | Identity grouping — connecting tissue |
| **Carries coordinates?** | Yes — `PublicationCoordinates` resolved at persistence | No — visible because backing evidence is visible |
| **Shared interfaces** | `HasProvenance`, `EntityIdentity` | `HasProvenance`, `EntityIdentity` |

Spoilergating filters evidence by coordinates, then traverses to interpretation.
Interpretation nodes don't hold knowledge — they're identity clustering results.

## Interface Design

```java
// Present on every entity node in the graph
public interface HasProvenance {
    UUID id();
    UUID stageId();
    LocalDateTime createdAt();
    LocalDateTime updatedAt();
}

// Name identity fields used by consolidation clustering
public interface EntityIdentity {
    String displayName();          // raw LLM extraction, for display/audit
    String primaryName();          // normalized, for consolidation clustering
    List<String> aliases();        // normalized, for consolidation clustering
    List<String> displayAliases(); // raw LLM extraction, audit trail
}
```

```java
public static Set<String> from(EntityIdentity entity) {
    return from(entity.primaryName(), entity.aliases());
}
```

All `ConsolidationEngine.cluster()` sites collapse from:
```java
e -> NameKeys.from(e.normalizedName(), e.aliases())
```
to:
```java
NameKeys::from
```

**Pipeline cleanup** (`deleteDataByStageId`, `rerunStage`) can reference `HasProvenance.stageId()` on any node instead of type-specific Cypher.

## Files Affected

| Layer | Files | Change |
|-------|-------|--------|
| **Interfaces** | 2 new files | `HasProvenance.java`, `EntityIdentity.java` in `orchestration/` |
| **Entity records** | 18 files | Rename 2 fields, add 1 field, `implements HasProvenance, EntityIdentity` |
| **Persistence services** | 12 files | Populate new `aliases`, rename `normalizedName` → `primaryName` |
| **Consolidation services** | 12 files | Simplify lambda to `NameKeys::from`, rename field references |
| **GraphRepositories** | ~12 files | Update Cypher `@Query` property names |
| **P1 pipeline** | `IngestionPipelineCoordinator.java` | Generic `deleteDataByStageId` via `HasProvenance` |
| **Tests** | ~15 files | Field renames, constructor args, assertions |
| **Total** | ~72 files | |

## DB Impact

Field renames change Neo4j property names. Requires **dev DB wipe + re-ingest**. Schema constraints referencing `normalizedName` need update in `Neo4jSchemaInitializer`.

## Pre-requisites

- This doc approved
- Dev DB reset: `./scripts/reset-dev-db.sh` + `./scripts/prepare-dev-environment.sh`
- No in-flight ingestion jobs

## Out of Scope

- Normalization of `displayAliases` at read time (they're raw audit fields)
- Fuzzy matching or stemming in `NameKeys`
- Event entity type — Event has a different structure (no aliases field on `EventExtraction`), documented in `LOW-5` of P2 review. Event integration with these interfaces is Phase 4.
- Mention-level interfaces for scope (`sceneId`/`chapterId`/`bookId`) and pipeline state (`resolutionStatus`) — can be extracted later
- `PublicationCoordinates` on evidence nodes — separate pass, see `docs/rules/entity-node-contract.md` for the evidence/interpretation distinction
