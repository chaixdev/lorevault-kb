# Domain Node Tagging — stageId Provenance

**Date:** May 29, 2026
**Status:** ✅ Shipped — all phases A–C complete, 463 tests passing
**Category:** Architecture / Observability / Ingestion Pipeline
**Parent:** [Stage Execution Context & Domain Provenance](2026-05-29T0000_stage-execution-context-and-provenance.md)

## Problem

Phases 1–2 of the StageExecutionContext work shipped (commit `bb9f196`): `DispatchContext` → `StageExecutionContext` with `stageId`, `StageOutput` deleted, provenance unstubbed, `deleteDataByStageId` implemented with generic Cypher. But domain nodes and their structural edges are **not yet tagged** with `stageId`. The infrastructure flows `ctx.stageId()` to handlers, but the property doesn't reach domain data.

**Impact:** `deleteDataByStageId` is implemented but won't find anything to delete until domain nodes carry `stageId`. Rerun cleanup is a no-op.

## Scope

### Entity classes needing `stageId` property (18 `@Node` classes)

| # | Entity | Repository | Creation mechanism | Package |
|---|--------|------------|-------------------|---------|
| 1 | `Scene` | `SceneGraphRepository` | SDN `saveAll()` | `content.scene` |
| 2 | `Chunk` | `ChunkGraphRepository` | SDN `saveAll()` | `content.chunk` |
| 3 | `IndividualMention` | `IndividualMentionGraphRepository` | SDN `save()` | `content.mention` |
| 4 | `LocationMention` | `LocationMentionGraphRepository` | SDN `save()` | `content.mention` |
| 5 | `EventMention` | `EventMentionGraphRepository` | SDN `save()` | `content.mention` |
| 6 | `ObjectMention` | `ObjectMentionGraphRepository` | SDN `save()` | `content.mention` |
| 7 | `CollectiveMention` | `CollectiveMentionGraphRepository` | SDN `save()` | `content.mention` |
| 8 | `ChapterIndividual` | `ChapterIndividualGraphRepository` | SDN `saveAll()` | `content.association` |
| 9 | `BookIndividual` | `BookIndividualGraphRepository` | SDN `saveAll()` | `content.association` |
| 10 | `ChapterLocation` | `ChapterLocationGraphRepository` | SDN `saveAll()` | `content.association` |
| 11 | `BookLocation` | `BookLocationGraphRepository` | SDN `saveAll()` | `content.association` |
| 12 | `ChapterEvent` | `ChapterEventGraphRepository` | SDN `saveAll()` | `content.association` |
| 13 | `BookEvent` | `BookEventGraphRepository` | SDN `saveAll()` | `content.association` |
| 14 | `ChapterCollective` | `ChapterCollectiveGraphRepository` | SDN `saveAll()` | `content.association` |
| 15 | `BookCollective` | `BookCollectiveGraphRepository` | SDN `saveAll()` | `content.association` |
| 16 | `ChapterObject` | `ChapterObjectGraphRepository` | SDN `saveAll()` | `content.association` |
| 17 | `BookObject` | `BookObjectGraphRepository` | SDN `saveAll()` | `content.association` |
| 18 | `RelationClaim` | `RelationClaimGraphRepository` | SDN `save()` | `content.relation` |

### Explicit Cypher queries needing `stageId` parameter (1)

| # | Repository | Query | Current Cypher |
|---|------------|-------|---------------|
| 1 | `BookConsolidationClaimRepository` | `tryAcquireClaim` | `MERGE ... ON CREATE SET` — needs `stageId` added |

**Already done:** `TemporalEdgeWriteRepository` — `stageId` added in Phase 2 (replaced `statusRecordId`).

### Domain services needing `StageExecutionContext` parameter (~23)

None currently accept `StageExecutionContext`.

**Mention persistence services (5):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `IndividualPersistenceService` | `IndividualMentionGraphRepository` | `ingestion.infrastructure` |
| `LocationPersistenceService` | `LocationMentionGraphRepository` | `ingestion.infrastructure` |
| `EventPersistenceService` | `EventMentionGraphRepository` | `ingestion.infrastructure` |
| `ObjectPersistenceService` | `ObjectMentionGraphRepository` | `ingestion.infrastructure` |
| `CollectivePersistenceService` | `CollectiveMentionGraphRepository` | `ingestion.infrastructure` |

**Chapter consolidation services (5):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `ChapterIndividualConsolidationService` | `ChapterIndividualGraphRepository` | `resolution.individual` |
| `ChapterLocationConsolidationService` | `ChapterLocationGraphRepository` | `resolution.location` |
| `ChapterEventConsolidationService` | `ChapterEventGraphRepository` | `resolution.event` |
| `ChapterCollectiveConsolidationService` | `ChapterCollectiveGraphRepository` | `resolution.collective` |
| `ChapterObjectConsolidationService` | `ChapterObjectGraphRepository` | `resolution.object` |

**Book persistence services (5):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `BookIndividualPersistenceService` | `BookIndividualGraphRepository` | `resolution.individual` |
| `BookLocationPersistenceService` | `BookLocationGraphRepository` | `resolution.location` |
| `BookEventPersistenceService` | `BookEventGraphRepository` | `resolution.event` |
| `BookCollectivePersistenceService` | `BookCollectiveGraphRepository` | `resolution.collective` |
| `BookObjectPersistenceService` | `BookObjectGraphRepository` | `resolution.object` |

**Book consolidation services (5):**

| Service | Delegates to | Package |
|---------|-------------|---------|
| `BookIndividualConsolidationService` | `BookIndividualPersistenceService` | `resolution.individual` |
| `BookLocationConsolidationService` | `BookLocationPersistenceService` + `ChapterLocationGraphRepository` | `resolution.location` |
| `BookEventConsolidationService` | `BookEventPersistenceService` + `ChapterEventGraphRepository` | `resolution.event` |
| `BookCollectiveConsolidationService` | `BookCollectivePersistenceService` + `ChapterCollectiveGraphRepository` | `resolution.collective` |
| `BookObjectConsolidationService` | `BookObjectPersistenceService` + `ChapterObjectGraphRepository` | `resolution.object` |

**Scene/Chunk services (1):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `SceneProcessingService` | `SceneGraphRepository`, `ChapterGraphRepository` | `ingestion.scene` |

> **Excluded:** `TriadBuilderService` is read-only (no `save()` calls) — does not need tagging.

**Other services (2):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `RelationClaimPersistenceService` | `RelationClaimGraphRepository` | `ingestion.infrastructure` |
| `SceneTemporalRelationshipPersistenceService` | `TemporalEdgeWriteRepository` | `resolution.event` |

**Needs ctx threading (1):**

| Service | Repository called | Package |
|---------|-------------------|---------|
| `BookConsolidationClaimService` | `BookConsolidationClaimRepository` | `resolution.location` |

> **Correction:** `BookConsolidationClaimService` does **not** already accept `StageExecutionContext`. Its `tryAcquireClaim` method takes `(UUID bookId, String lane)` — it needs ctx threaded like all other services.

## Design

### SDN entity tagging

For the 18 `@Node` entity classes, add a `stageId` property:

```java
@Property("stageId")
private UUID stageId;
```

Set it in the service layer before calling `repository.save()` / `repository.saveAll()`:

```java
entity.setStageId(ctx.stageId());
repository.save(entity);
```

For entities constructed in bulk (e.g., `List<ChapterIndividual>` in consolidation services):

```java
entities.forEach(e -> e.setStageId(ctx.stageId()));
repository.saveAll(entities);
```

### Explicit Cypher tagging

For `BookConsolidationClaimRepository.tryAcquireClaim`, add `stageId` parameter:

```cypher
MERGE (claim:BookConsolidationClaim {bookId: $bookId, claimType: $claimType})
ON CREATE SET claim.stageId = $stageId, claim.createdAt = datetime()
```

### Structural edges (no change needed)

MERGE edge queries (`linkSceneToChapter`, `linkChapterToIndividual`, `linkBookToEvent`, etc.) do **not** need `stageId`. These edges connect a tagged node to an untagged structural node (Chapter, Book). When `deleteDataByStageId` runs `DETACH DELETE` on the tagged node, these edges are cascade-deleted automatically.

Only edges between **two untagged nodes** need `stageId` — and that's already handled for temporal edges (Phase 2).

## Sequencing

### Phase A: Entity model (prerequisite for all lanes)

Add `stageId` property to all 18 `@Node` entity classes.

**Construction pattern split:**
- **16 Java records** (`ChapterIndividual`, `BookIndividual`, etc.): `stageId` must be added as a record component, which breaks **every construction site** (both production and test). Estimated ~30–40 call sites total.
- **2 Lombok `@Data` classes** (`Scene`, `Chunk`): `stageId` can be added as a field + setter. For `Scene`, prefer the setter approach (`scene.setStageId(ctx.stageId())`) because its `@PersistenceCreator` already has 15 parameters — adding a 16th is error-prone.

**Estimated files:** 18 entity classes + ~30–40 construction site changes (production + test).

### Phase B: Service plumbing (can be done lane by lane)

Thread `StageExecutionContext` through services and set `stageId` on entities before save. Each lane is independently testable.

**B1 — Mention persistence + RelationClaim (6 services, 6 entities):**
- `IndividualPersistenceService`, `LocationPersistenceService`, `EventPersistenceService`, `ObjectPersistenceService`, `CollectivePersistenceService`
- `RelationClaimPersistenceService`
- Each: add `StageExecutionContext ctx` parameter, set `entity.setStageId(ctx.stageId())` before save
- Merged because these are all called from the same handler (`SceneDetectionHandler`) in the same code block

**B2 — Chapter consolidation (5 services, 5 entities):**
- `ChapterIndividualConsolidationService`, `ChapterLocationConsolidationService`, `ChapterEventConsolidationService`, `ChapterCollectiveConsolidationService`, `ChapterObjectConsolidationService`
- Each: add `StageExecutionContext ctx` parameter, set `stageId` on entities before `saveAll()`

**B3 — Book consolidation + persistence (10 services, 5 entities):**
- `BookIndividualConsolidationService` → `BookIndividualPersistenceService`
- `BookLocationConsolidationService` → `BookLocationPersistenceService`
- `BookEventConsolidationService` → `BookEventPersistenceService`
- `BookCollectiveConsolidationService` → `BookCollectivePersistenceService`
- `BookObjectConsolidationService` → `BookObjectPersistenceService`
- Thread ctx through both consolidation and persistence layers

**B4 — Scene/Chunk (1 service, 2 entities):**
- `SceneProcessingService`
- Set `stageId` on Scene/Chunk entities before save
- `TriadBuilderService` excluded — it's read-only (no save calls)

**B5 — BookConsolidationClaim (1 service, 1 explicit Cypher query):**
- `BookConsolidationClaimService` — needs ctx threaded (does NOT already have it)
- `BookConsolidationClaimRepository.tryAcquireClaim` — add `stageId` parameter to MERGE query

### Phase C: Test updates

Update all affected test files to:
- Construct `StageExecutionContext` with `stageId` in test setups
- Verify `stageId` is set on saved entities
- Update mock setups for services that now accept `StageExecutionContext`

**Critical:** Existing tests will compile and pass with `stageId = null` — they don't assert `stageId`. Add at least one integration test per lane that creates an entity through the full path and asserts `entity.getStageId() != null`. This catches the most common failure mode (field declared but never set).

### Phase D: Verification

```bash
mvn clean install -DskipTests  # Build all modules
mvn test                        # Run full test suite
```

Then smoke test: run ingestion end-to-end and verify:
1. Domain nodes have `stageId` property in Neo4j
2. `deleteDataByStageId` correctly removes tagged nodes and their edges
3. Temporal edges retain `stageId` provenance (already working from Phase 2)

## Out of Scope

- **Index on `stageId`:** Not needed for `deleteDataByStageId` (uses label-agnostic property scan). Add when query use cases emerge (e.g., operator dashboard).
- **Structural edge tagging:** MERGE edges between tagged and untagged nodes are cascade-deleted with `DETACH DELETE`. No `stageId` needed.
- **`TemporalEdgeProvenance` simplification:** Already addressed in Phase 2 — `statusRecordId` → `stageId`.
- **`DefaultTemporalEdgeService`:** Already receives `StageExecutionContext` through `SceneTemporalRelationshipPersistenceService` path. Verify during implementation.

## Open Questions

- **Constructor vs setter for `stageId`:** Entity classes use various construction patterns (builders, constructors, field assignment). Decide per-class during implementation — prefer the pattern already used in each class. For `Scene` (Lombok `@Data` with 15-param `@PersistenceCreator`), use the setter approach. For records, add `stageId` as the last record component.
- **Nullable `stageId`:** Should `stageId` be nullable on entity classes to support existing data that predates this change? Yes — `UUID stageId` can be null for nodes created before this feature ships. `deleteDataByStageId` only targets nodes where `stageId` matches.
- **Sequencing note:** B3 (book consolidation) logically depends on B2 (chapter consolidation) for end-to-end verification. B4 and B5 can be done in parallel. B1 should come first.

## Oracle Review (May 29, 2026)

| # | Finding | Severity | Resolution |
|---|---------|----------|------------|
| 1 | `TriadBuilderService` is read-only — doesn't need tagging | Critical | Removed from B4 scope |
| 2 | `BookConsolidationClaimService` does NOT already have ctx | Important | Reclassified as "needs ctx threading" |
| 3 | Record construction blast radius ~30–40 call sites | Critical | Updated Phase A estimate |
| 4 | `Scene` `@PersistenceCreator` has 15 params | Important | Use setter approach for Scene |
| 5 | No automated check that `stageId` is set | Important | Added integration test requirement in Phase C |
| 6 | B1 and B5 merged (same handler, same shape) | Minor | Merged into B1 |
| 7 | B3 depends on B2 for verification | Minor | Noted in sequencing |
| 8 | `DETACH DELETE` cascade confirmed correct | Confirmed | No change |
| 9 | Temporal edges already handled | Confirmed | No change |
| 10 | `stageId` naming consistent | Confirmed | No change |

## Success Criteria

1. All 18 domain entity classes have `stageId` property
2. All ~23 domain services accept `StageExecutionContext` and set `stageId` on entities before save (including `BookConsolidationClaimService`)
3. `BookConsolidationClaimRepository.tryAcquireClaim` includes `stageId` in MERGE
4. `deleteDataByStageId` correctly removes tagged nodes and their edges on rerun
5. Integration test per lane asserts `entity.getStageId() != null`
6. 463+ tests green
7. Smoke test: ingestion creates nodes with `stageId`, rerun cleans them up

## Links

- Parent: [Stage Execution Context & Domain Provenance](2026-05-29T0000_stage-execution-context-and-provenance.md)
- Related: [Pipeline Issues from Smoke Test](2026-05-27T0230_pipeline-issues-from-smoke-test.md)
- Related: [Submission Flow Cleanup](2026-05-23T1530_submission-flow-cleanup.md)