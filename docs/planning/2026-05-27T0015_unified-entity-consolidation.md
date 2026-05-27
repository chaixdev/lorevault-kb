# Unified Entity Consolidation Algorithm

**Status:** NOT STARTED — oracle-reviewed May 27, 2026. Direction confirmed correct; 11 adjustments incorporated below.

## Summary

Extract the duplicated entity-clustering logic from 8 resolution/reduction services (4 entity types × 2 lifecycle levels) into a single shared `ConsolidationEngine`. Replaces 4 divergent algorithm implementations (Cypher pushdown, O(n²) in-memory scan, connected-components) with one reusable connected-components algorithm. Also consolidates 4 copies of `pickFirstNonBlank`, 4 copies of `chapterExists`, eliminates 6 `isResolvable` methods via engine-level empty-key filtering, normalizes inconsistent zero-mention guard behavior, and drops unused parameters. Net reduction: **~530 lines**, 8 services simplified, algorithm correctness upgraded for 3 of 4 entity types.

## Problem

The resolution pipeline for non-event entities (Individual, Location, Object, Collective) performs the same fundamental operation at 8 points: group source entities that refer to the same real-world thing, then merge them into one target entity. Despite this, the codebase has four different implementations of this clustering step:

| Type | Chapter level algorithm | Book level algorithm |
|---|---|---|
| **Individual** | Cypher `GROUP BY normalizedName` | Cypher `GROUP BY normalizedName` |
| **Location** | In-memory connected components (alias-aware) | Same |
| **Object** | In-memory O(n²) `normalizedName` scan | Same |
| **Collective** | In-memory O(n²) `normalizedName` scan | Same |

This divergence is not a conscious design choice — it's a regression born of independent, sequential implementation. Each entity lane was built in isolation, re-solving the same clustering problem from scratch:

- **Individual** was built first. The implementation took the path of least resistance: push grouping to the database via Cypher `GROUP BY`. Aliases on `IndividualMention` existed but were never threaded through to resolution — the Cypher `GROUP BY normalizedName` could not express alias-aware merge.
- **Location** was built next and got the most sophisticated algorithm: in-memory connected-components with full alias awareness. This implementation is the reference for correctness.
- **Object** and **Collective** were built last. They re-invented clustering from scratch, this time as a naive O(n²) `normalizedName`-only scan — the worst of the three approaches. Neither learned from Location's connected-components work, and neither addressed aliases.

The result: four implementations ranked by algorithm quality in inverse order of implementation sequence. The codebase has known-good code (Location) sitting unused by the lanes that need it most.

Additionally, Individual's chapter and book entities lack an `aliases` field — despite `IndividualMention` having one — so "Gandalf" and "Mithrandir" never merge, while "Mordor" and "Land of Shadow" already do (Location).

## Product Context

Entity resolution quality directly affects downstream features: entity search, cross-book entity discovery, relationship extraction, and the entity catalog. If the consolidation algorithm is incorrect or inconsistent across types, the knowledge graph contains duplicate entities that degrade these features.

The divergence also makes the system harder to evolve. If a better consolidation algorithm is discovered (e.g., triad-based structural identity, embedding similarity), it must be implemented and tested 4 separate times.

## Technical Context

**Affected files:** 8 services in `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/`:

```
individual/
  ChapterIndividualResolutionService.java     (114 lines)
  BookIndividualReductionService.java         (150 lines)
location/
  ChapterLocationResolutionService.java       (231 lines)
  BookLocationReductionService.java           (236 lines)
object/
  ChapterObjectResolutionService.java         (193 lines)
  BookObjectReductionService.java             (195 lines)
collective/
  ChapterCollectiveResolutionService.java     (193 lines)
  BookCollectiveReductionService.java         (201 lines)
```

**Also affected:**
- `ChapterIndividualCandidate.java` + `ChapterIndividualCandidateView.java` — DTOs rendered unnecessary by the unified approach
- `ChapterIndividualGraphRepository` — `findResolutionCandidates()` Cypher query and `linkMentionsToChapterIndividual()` signature
- `BookIndividualGraphRepository` — `countChapterIndividualsForBookAndName()`, `linkChapterIndividualsForBookAndNameToBookIndividual()` queries
- `ChapterIndividual` and `BookIndividual` entity records — need `aliases` field added
- `IndividualMentionGraphRepository` — new mention repository needed (Individual currently lacks one)

**The shared algorithm** is the connected-components clustering from `ChapterLocationResolutionService.clusterMentions()` (lines 113-161), which already handles the general case: entities with 1+N identity keys and transitive merging.

## Design

### Shared Algorithm: `ConsolidationEngine<S>`

A generic connected-components engine (~50 lines) that clusters source entities based on overlapping identity key sets. Type-agnostic — works for all 4 entity types at both Mention→Chapter and Chapter→Book lifecycle levels.

```java
// lorevault-core/.../ingestion/resolution/consolidation/ConsolidationEngine.java
public class ConsolidationEngine<S> {
    public List<List<S>> cluster(List<S> sources, Function<S, Set<String>> keyExtractor) { ... }
}
```

- For each source, extracts a `Set<String>` of identity keys
- Sources sharing any key are transitively merged into one component
- Returns a list of clusters in deterministic order

### Type-Specific Merge: `EntityMerger<S, T>`

```java
// lorevault-core/.../ingestion/resolution/consolidation/EntityMerger.java
@FunctionalInterface
public interface EntityMerger<S, T> {
    T merge(List<S> sources, UUID ownerId);
}
```

Each entity type provides its own ~10-15 line merger for field collapsing (shared `pickFirstNonBlank` utility, alias union, count derivation). 8 implementations total (4 types × 2 levels). The `ownerId` is the chapterId or bookId — used to set the owner field on the target entity.

### Key Extraction

All 4 types use identical key extraction: `{normalizedName} ∪ {normalized(alias) for each alias}`. A shared `NameKeys` helper (~10 lines) provides `normalizeName()` and `from(normalizedName, aliases)`. The `addKey` guard is inlined into `from()` — no separate public method needed.

### Engine-Owned Empty-Key Filtering

`ConsolidationEngine.cluster()` silently skips sources whose `keyExtractor` returns an empty set. This eliminates the need for 6 per-service `isResolvable` methods — services no longer pre-filter sources before clustering. The only filtering concern left in services is the top-level "zero resolvable sources → return no-op" guard, now expressed as `if (clusters.isEmpty()) return noOp`.

### Per-Service Skeleton

Every resolution/reduction service follows the same template after consolidation:

```
1. Guard: fetch sources, return no-op if zero (delete NOTHING — normalized across all types)
2. Delete prior entities for this owner (chapterId/bookId)
3. Fetch sources (mentions or chapter entities), sort for determinism
4. engine.cluster(sources, NameKeys::from)   ← one line; engine skips empty-key sources
5. For each cluster: merger.merge(cluster, ownerId) → target entity
6. Persist entities + link edges
```

Steps 1-2 and 5-6 are already identical across services. Step 4 is the only new shared call. Step 2 is currently inconsistent: Object and Collective delete prior entities on zero mentions (destructive no-op), while Individual and Location do not. This is normalized to "do not delete on zero mentions" across all 4 types in Phase B.

### Line-Count Reduction

| Service | Before | After | Δ |
|---|---|---|---|
| `ChapterLocationResolutionService` | 231 | 131 | **-100** |
| `ChapterObjectResolutionService` | 193 | 136 | -57 |
| `ChapterCollectiveResolutionService` | 193 | 140 | -53 |
| `ChapterIndividualResolutionService` | 114 | 131 | +17 |
| `BookLocationReductionService` | 236 | 132 | **-104** |
| `BookObjectReductionService` | 195 | 134 | -61 |
| `BookCollectiveReductionService` | 201 | 143 | -58 |
| `BookIndividualReductionService` | 150 | 118 | -32 |
| **Services subtotal** | **1,513** | **1,065** | **-448** |

| Category | Lines |
|---|---|---|
| New shared code (`ConsolidationEngine` + `EntityMerger` + `NameKeys` + shared `pickFirstNonBlank` + shared `chapterExists` utility) | +95 |
| Deleted DTOs + removed repo queries | -82 |
| Eliminated 6 `isResolvable` methods (engine owns filtering) | -36 |
| Eliminated 4 `pickFirstNonBlank` copies (shared utility) | -36 |
| Eliminated 4 `chapterExists` copies (shared utility) | -12 |
| Dropped unused `clusterId` parameter + `addKey` method | -5 |
| **Grand total** | **~-530** |

Individual is the only gainer (+17) because it currently delegates clustering to Cypher with no in-memory logic. It joins the unified approach for correctness (aliases currently ignored) and architectural coherence.

### Correctness Upgrades

1. **Object and Collective** move from degenerate 1-key O(n²) scan to full connected-components with alias-aware merging. "Silver Sword" (aliases: ["Moonblade"]) and "Ceremonial Dagger" (aliases: ["Moonblade"]) will merge via the shared alias — they don't today.

2. **Individual** gains alias-aware merging. "Gandalf" (aliases: ["Mithrandir", "Grey Pilgrim"]) and "Mithrandir" (aliases: ["Gandalf"]) will merge — they don't today.

3. **Individual mention linking** switches from `MATCH ... WHERE normalizedName = $name` to explicit `UNWIND $mentionIds` matching — the pattern Location/Object/Collective already use. This fixes a latent bug where alias-grouped mentions with different `normalizedName` values would fail to link after resolution. The same ID-based linking must also be applied at the book level: `BookIndividualPersistenceService` and `BookIndividualGraphRepository.linkChapterIndividualsForBookAndNameToBookIndividual()` currently use name-based linking and will break when chapter-level Individual entities carry aliases (Phase C).

4. **Zero-mention guard normalized** across all 4 types: Object and Collective currently delete prior entities on zero new mentions (destructive no-op), while Individual and Location do not. All types adopt the safer "do not delete on zero" behavior (Phase B).

## Scope

- Extract `ConsolidationEngine`, `EntityMerger`, and `NameKeys` into a new `consolidation/` subpackage
- Extract shared `PickFirstNonBlank` utility — replaces 4 identical copies in Object/Collective chapter+book services
- Extract shared `ChapterEntityGuardService` or static utility — replaces 4 identical `chapterExists(UUID)` copies
- Engine silently skips empty-key sources — eliminates 6 `isResolvable` methods
- Drop unused `clusterId` parameter from merger; drop `addKey` from `NameKeys` public API
- Normalize zero-mention guard behavior: all 4 types adopt "do not delete on zero" semantics
- Refactor 8 services to use the shared engine (7 net-shrink, 1 small net-grow)
- Add `List<String> aliases` to `ChapterIndividual` and `BookIndividual` records
- Add `IndividualMentionGraphRepository` (Individual currently lacks its own mention repo)
- Change Individual mention linking from normalizedName-based to mentionId-based
- Change `BookIndividualPersistenceService` and `BookIndividualGraphRepository.linkChapterIndividualsForBookAndNameToBookIndividual()` from name-based to ID-based linking
- Delete `ChapterIndividualCandidate`, `ChapterIndividualCandidateView`, and dead repo queries
- Update tests to reflect behavioral changes — including inverting `doesNotMergeObjectsThroughSharedAliases` (currently asserts 2 clusters, must become 1)
- Add new scenario tests for Object, Collective, and Individual alias merging (currently untested)

## Out of Scope

- **Event resolution.** Events use co-reference graph traversal (`SAME_EVENT` edges from triad analysis) — a genuinely different domain. Not included in this consolidation.
- **Triad-based identity.** The `ConsolidationEngine` accepts a `Function<S, Set<String>>` for key extraction, making it compatible with future alternative key derivation strategies (e.g., structural/scene-graph-based identity). But designing or implementing a triad-based strategy is out of scope.
- **Book\*PersistenceService consolidation.** These 4 services share structural duplication (deleteByBookId → saveAll → linkAll) but are kept separate — Spring Data Neo4j's typed repo constraint makes consolidation here low-ROI.
- **Mention repository consolidation.** The 5 thin Mention repos stay separate — SDN mandates one `Neo4jRepository` interface per node type.

## Known Constraints / Prior Findings

- **Spring Data Neo4j typed repos are SDN-mandated.** The 15 association+mention repos are justified by Neo4j's label-per-type model. Consolidating them would require Neo4jClient-based operations with significant type-safety loss. The typed repos stay — only the service-layer algorithm consolidates.
- **Chapter-scoped data is small** (<500 mentions typically), so in-memory Java clustering is appropriate. The existing Individual Cypher pushdown is replaced for architectural coherence, not performance reasons.
- **Location's algorithm is the reference implementation.** `ChapterLocationResolutionService.clusterMentions()` (lines 113-161) is the most complete and correct clustering code in the codebase. The unified engine is extracted from it.
- **`Mention` interface is insufficient for engine input.** At the mention layer, `Mention` does not carry `aliases()` — that field is on the concrete types (`LocationMention`, `IndividualMention`, etc.). At the chapter-entity layer, `ChapterIndividual`, `ChapterLocation`, etc. are `@Node` records in `content.association` and do not implement `Mention`. No single interface spans both lifecycle levels AND carries both `normalizedName()` AND `aliases()`. The generic `Function<S, Set<String>>` parameter is the correct bridge — it's less invasive than creating a new interface that all entity types must implement.

## Open Questions

- Should Individual aliases at the Mention level be backfilled from LLM extraction, or only forward-looking? (Current `IndividualMention` already has `aliases`; this is about whether existing data needs migration.)
- Should the Cypher-based approach be retained as a configurable alternative for types with very large mention counts? (Decision: not needed — chapter-scoped data is small. Revisit if chapter mention counts regularly exceed 1,000.)

## Success Criteria

- All 8 resolution/reduction services use the same `ConsolidationEngine` for clustering
- `ChapterIndividual` and `BookIndividual` carry `aliases` (aligned with other 3 types)
- Individual, Object, and Collective correctly merge through shared aliases (currently only Location does)
- Individual mention linking uses explicit mention IDs (not normalizedName matching); book-level Individual linking also uses ID-based matching
- All 4 types use identical zero-mention guard behavior (do not delete on zero)
- 6 `isResolvable` methods eliminated — engine handles empty-key filtering
- 4 `pickFirstNonBlank` copies consolidated into shared utility
- 4 `chapterExists` copies consolidated into shared utility
- All existing resolution tests pass (updated for behavioral changes)
- New scenario tests for Object, Collective, and Individual alias merging
- `ChapterObjectResolutionServiceTest.doesNotMergeObjectsThroughSharedAliases()` inverted: asserts 1 cluster (merged), not 2
- `BookIndividualPersistenceService` and `BookIndividualGraphRepository` use ID-based linking
- Unit tests for `ConsolidationEngine` cover: single-key clusters, multi-key clusters, transitive merges, empty input, blank-key filtering, deterministic ordering

## Migration Path

| Phase | What | Behavior change? | Lines affected |
|---|---|---|---|
| **A** | Extract `ConsolidationEngine` + `EntityMerger` + `NameKeys` (+ shared `PickFirstNonBlank` utility + shared `ChapterEntityGuardService`). Engine owns empty-key filtering. Drop unused `clusterId` parameter + `addKey` method. Unit-test the engine. | Zero | +95 new, -5 cleanup |
| **B** | Refactor Location/Object/Collective services (6 services) to use engine. Extract existing clustering code without changing behavior. Consolidate `isResolvable` → engine filtering. Consolidate `pickFirstNonBlank` → shared utility. Consolidate `chapterExists` → shared utility. Normalize zero-mention guard behavior (Object/Collective stop deleting on zero). | Guard normalization: Object/Collective no longer delete on zero mentions | ~700 removed from 6 services, ~100 added |
| **C** | Add `aliases` to `ChapterIndividual` + `BookIndividual`. Create `IndividualMentionGraphRepository`. Switch Individual resolution from Cypher to engine. Switch chapter-level mention linking from normalizedName to mentionIds. Switch book-level linking (`BookIndividualPersistenceService` line 36, `BookIndividualGraphRepository.linkChapterIndividualsForBookAndNameToBookIndividual()`) from name-based to ID-based. Adopt sort-before-cluster determinism (same pattern as Location/Object/Collective). Delete `ChapterIndividualCandidate` + `ChapterIndividualCandidateView` + dead repo queries. Update tests: invert `doesNotMergeObjectsThroughSharedAliases`, add new scenario tests for Object/Collective/Individual alias merging. | Individual: aliases now merge. Object/Collective: aliases now merge. Book-level Individual linking changes. | ~150 removed from services/DTOs/repos, ~90 added (services + tests) |

## Links

- [Repository audit and analysis](../brainstorm/2026-05-27T0015_entity-consolidation-brainstorm.md) (placeholder — the oracle analysis that informed this design)
- Entity model packages: `lorevault-core/src/main/java/com/lorevault/api/content/mention/`, `association/`
- Resolution services: `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/{individual,location,object,collective}/`
- [Code organization guidance](../rules/code-organization-guidance.md) — "Backward compatibility is never the goal" rule
