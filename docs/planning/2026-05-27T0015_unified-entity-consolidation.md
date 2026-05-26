# Unified Entity Consolidation Algorithm

**Status:** NOT STARTED

## Summary

Extract the duplicated entity-clustering logic from 8 resolution/reduction services (4 entity types × 2 lifecycle levels) into a single shared `ConsolidationEngine`. Replaces 4 divergent algorithm implementations (Cypher pushdown, O(n²) in-memory scan, connected-components) with one reusable connected-components algorithm. Net reduction: **-457 lines**, 8 services simplified, algorithm correctness upgraded for 3 of 4 entity types.

## Problem

The resolution pipeline for non-event entities (Individual, Location, Object, Collective) performs the same fundamental operation at 8 points: group source entities that refer to the same real-world thing, then merge them into one target entity. Despite this, the codebase has four different implementations of this clustering step:

| Type | Chapter level algorithm | Book level algorithm |
|---|---|---|
| **Individual** | Cypher `GROUP BY normalizedName` | Cypher `GROUP BY normalizedName` |
| **Location** | In-memory connected components (alias-aware) | Same |
| **Object** | In-memory O(n²) `normalizedName` scan | Same |
| **Collective** | In-memory O(n²) `normalizedName` scan | Same |

Object and Collective's O(n²) scan is a degenerate, less-correct variant of Location's connected-components algorithm. Individual's Cypher pushdown is a degenerate variant that also ignores aliases entirely.

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

### Type-Specific Merge: `ClusterMerger<S, T>`

```java
// lorevault-core/.../ingestion/resolution/consolidation/ClusterMerger.java
@FunctionalInterface
public interface ClusterMerger<S, T> {
    T merge(List<S> sources, UUID ownerId, UUID clusterId);
}
```

Each entity type provides its own ~10-15 line merger for field collapsing (`pickFirstNonBlank`, alias union, count derivation). 8 implementations total (4 types × 2 levels).

### Key Extraction

All 4 types use identical key extraction: `{normalizedName} ∪ {normalized(alias) for each alias}`. A shared `NameKeys` helper (~15 lines) provides `normalizeName()`, `addKey()`, and `from(normalizedName, aliases)`.

Individual already has `aliases` on `IndividualMention`. Chapter/Body Individual need the field added to align with the other 3 types.

### Per-Service Skeleton

Every resolution/reduction service follows the same template after consolidation:

```
1. Guard: count sources, return no-op if zero
2. Delete prior entities for this owner (chapterId/bookId)
3. Fetch sources (mentions or chapter entities), sort for determinism
4. engine.cluster(sources, NameKeys::from)   ← one line
5. For each cluster: merger.merge(cluster, ownerId, clusterId) → target entity
6. Persist entities + link edges
```

Steps 1-2 and 5-6 are already identical across services. Step 4 is the only new shared call.

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
|---|---|
| New shared code (`ConsolidationEngine` + `ClusterMerger` + `NameKeys`) | +73 |
| Deleted DTOs + removed repo queries | -82 |
| **Grand total** | **-457** |

Individual is the only gainer (+17) because it currently delegates clustering to Cypher with no in-memory logic. It joins the unified approach for correctness (aliases currently ignored) and architectural coherence.

### Correctness Upgrades

1. **Object and Collective** move from degenerate 1-key O(n²) scan to full connected-components with alias-aware merging. "Silver Sword" (aliases: ["Moonblade"]) and "Ceremonial Dagger" (aliases: ["Moonblade"]) will merge via the shared alias — they don't today.

2. **Individual** gains alias-aware merging. "Gandalf" (aliases: ["Mithrandir", "Grey Pilgrim"]) and "Mithrandir" (aliases: ["Gandalf"]) will merge — they don't today.

3. **Individual mention linking** switches from `MATCH ... WHERE normalizedName = $name` to explicit `UNWIND $mentionIds` matching — the pattern Location/Object/Collective already use. This fixes a latent bug where alias-grouped mentions with different `normalizedName` values would fail to link after resolution.

## Scope

- Extract `ConsolidationEngine`, `ClusterMerger`, and `NameKeys` into a new `consolidation/` subpackage
- Refactor 8 services to use the shared engine (7 net-shrink, 1 small net-grow)
- Add `List<String> aliases` to `ChapterIndividual` and `BookIndividual` records
- Add `IndividualMentionGraphRepository` (Individual currently lacks its own mention repo)
- Change Individual mention linking from normalizedName-based to mentionId-based
- Delete `ChapterIndividualCandidate`, `ChapterIndividualCandidateView`, and dead repo queries
- Update tests to reflect behavioral changes (Individual alias merging, Object/Collective alias merging)

## Out of Scope

- **Event resolution.** Events use co-reference graph traversal (`SAME_EVENT` edges from triad analysis) — a genuinely different domain. Not included in this consolidation.
- **Triad-based identity.** The `ConsolidationEngine` accepts a `Function<S, Set<String>>` for key extraction, making it compatible with future alternative key derivation strategies (e.g., structural/scene-graph-based identity). But designing or implementing a triad-based strategy is out of scope.
- **Book\*PersistenceService consolidation.** These 4 services share structural duplication (deleteByBookId → saveAll → linkAll) but are kept separate — Spring Data Neo4j's typed repo constraint makes consolidation here low-ROI.
- **Mention repository consolidation.** The 5 thin Mention repos stay separate — SDN mandates one `Neo4jRepository` interface per node type.

## Known Constraints / Prior Findings

- **Spring Data Neo4j typed repos are SDN-mandated.** The 15 association+mention repos are justified by Neo4j's label-per-type model. Consolidating them would require Neo4jClient-based operations with significant type-safety loss. The typed repos stay — only the service-layer algorithm consolidates.
- **Chapter-scoped data is small** (<500 mentions typically), so in-memory Java clustering is appropriate. The existing Individual Cypher pushdown is replaced for architectural coherence, not performance reasons.
- **Location's algorithm is the reference implementation.** `ChapterLocationResolutionService.clusterMentions()` (lines 113-161) is the most complete and correct clustering code in the codebase. The unified engine is extracted from it.
- **All 4 types implement the `Mention` interface** at the mention layer. This interface provides the common fields (`id()`, `displayName()`, `normalizedName()`, etc.) needed by the unified engine, but the engine doesn't depend on it — it works with any source type via the `Function<S, Set<String>>` parameter.

## Open Questions

- Should Individual aliases at the Mention level be backfilled from LLM extraction, or only forward-looking? (Current `IndividualMention` already has `aliases`; this is about whether existing data needs migration.)
- Should the Cypher-based approach be retained as a configurable alternative for types with very large mention counts? (Decision: not needed — chapter-scoped data is small. Revisit if chapter mention counts regularly exceed 1,000.)

## Success Criteria

- All 8 resolution/reduction services use the same `ConsolidationEngine` for clustering
- `ChapterIndividual` and `BookIndividual` carry `aliases` (aligned with other 3 types)
- Individual, Object, and Collective correctly merge through shared aliases (currently only Location does)
- Individual mention linking uses explicit mention IDs (not normalizedName matching)
- All existing resolution tests pass (updated for behavioral changes)
- Unit tests for `ConsolidationEngine` cover: single-key clusters, multi-key clusters, transitive merges, empty input, blank-key filtering, deterministic ordering

## Migration Path

| Phase | What | Behavior change? | Lines affected |
|---|---|---|---|
| **A** | Extract `ConsolidationEngine` + `ClusterMerger` + `NameKeys`. Unit-test the engine. | Zero | +73 |
| **B** | Refactor Location/Object/Collective services (6 services) to use engine. Extract existing clustering code without changing behavior. | Zero | ~570 removed, ~120 added per 6 services |
| **C** | Add `aliases` to `ChapterIndividual` + `BookIndividual`. Create `IndividualMentionGraphRepository`. Switch Individual resolution from Cypher to engine. Switch mention linking from normalizedName to mentionIds. Delete `ChapterIndividualCandidate` + dead repo queries. | Individual: aliases now merge. Object/Collective: aliases now merge. | ~86 removed from services, ~90 removed from repos/DTOs, ~70 added |

## Links

- [Repository audit and analysis](../brainstorm/2026-05-27T0015_entity-consolidation-brainstorm.md) (placeholder — the oracle analysis that informed this design)
- Entity model packages: `lorevault-core/src/main/java/com/lorevault/api/content/mention/`, `association/`
- Resolution services: `lorevault-core/src/main/java/com/lorevault/api/ingestion/resolution/{individual,location,object,collective}/`
- [Code organization guidance](../rules/code-organization-guidance.md) — "Backward compatibility is never the goal" rule
