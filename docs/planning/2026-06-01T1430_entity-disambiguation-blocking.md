# Entity disambiguation — Blocking + Fuzzy matching

**Status:** PLANNING  
**Last Updated:** June 1, 2026

## Problem

The consolidation engine clusters entity mentions using alias overlap as the sole merge signal. Ambiguous aliases ("Mr. Baggins" used for both Frodo and Bilbo in the same scene) create false bridges between distinct entity clusters, producing catastrophic fusions that cannot be undone without data loss. Conversely, strict exact-match aliases cause fragmentation — "Krk'aktznk" and "Krkaktznk" are the same entity but produce different normalized keys, creating duplicate BookIndividual nodes.

## Summary

Three layers, two directions:

- **Layers 1+2 (blocking):** Prevent false merges. Co-occurrence is deterministic and in-memory. Relation blocking queries the graph and degrades gracefully. Default to *don't merge* when blocking evidence is unavailable — fusion is irreversible corruption; keeping entities separate is a correctable split.
- **Layer 3 (fuzzy):** Enable true merges across spelling drift. Made safe by Layers 1+2 — blocking eliminates the false-positive risk class, so fuzzy matching can be more lenient without creating bad merges.

---

## Layer 1: Co-occurrence blocking (in-memory, always available)

### Rule

If two distinct `EntityMention` nodes in the same scene share an alias, that alias is *tainted* for that scene. Tainted aliases are excluded from name keys during clustering, so they cannot create adjacency edges between different entity clusters.

### Why it works

The LLM already groups aliases within a single extraction. "Frodo" (aliases: ["Frodo", "Mr. Baggins"]) and "Bilbo" (aliases: ["Bilbo", "Mr. Baggins"]) are two *separate* entities in the same scene. The shared "Mr. Baggins" is LLM output ambiguity, not merge evidence. Excluding it from name keys breaks the false bridge while allowing correct cross-scene matching via "Frodo" and "Bilbo".

### Algorithm

```
Input:  List<EntityMention> for chapter, grouped by sceneId
Output: Set<String> taintedAliases (per scene)

For each scene:
  Map alias → Set<mentionId>
  For each (alias, mentionIds) where mentionIds.size() >= 2:
    taintedAliases.add(alias)

NameKeys.from(mention) now accepts a taintedAliases set.
Any alias in the tainted set is excluded from the returned keys.
```

### Implementation surface

- **`ConsolidationEngine.cluster()`** — new overload accepting `BlockingConfig` parameter
- **`NameKeys.from()`** — new overload accepting `Set<String> taintedAliases`
- **`ChapterIndividualConsolidationService`** — builds `taintedAliases` from in-memory mention grouping before calling engine
- Same for `ChapterCollectiveConsolidationService`, `ChapterObjectConsolidationService`, `ChapterLocationConsolidationService`
- **`ChapterConceptConsolidationService`** — wire when Concept lane lands (same shape as Collective)
- **`ChapterEventConsolidationService`** — **excluded** for now. Events have different extraction semantics (single `name` not `aliases[]`, frequent same-scene paraphrases like "the battle" / "The Battle of the Hornburg"). Needs separate investigation. Pass `BlockingConfig.EMPTY`.
- **Book-level consolidation** — not needed. By book level, co-occurrence is resolved at chapter level. Book consolidation merges `ChapterEntity` nodes across chapters using `normalizedName`, which never creates cross-scene alias conflicts.

---

## Layer 2: Relation blocking (graph query, best-effort)

### Rule

If a `RelationClaim` connects entity A and entity B via `RELATES_SUBJECT` or `RELATES_OBJECT`, those two mentions can never merge into the same cluster.

### Why it works

The LLM's extraction of a relation between two entities is an assertion of their distinctness. No false positive risk. The query reads directly from the graph — no ordering assumption, no stale parameter.

### Algorithm

```
Input:  chapterId
Output: Set<Pair<UUID, UUID>> blockedMentionPairs

Cypher:
  MATCH (a:EntityMention)-[:RELATES_SUBJECT|RELATES_OBJECT]-
        (rc:RelationClaim)-[:RELATES_SUBJECT|RELATES_OBJECT]-
        (b:EntityMention)
  WHERE a.chapterId = $chapterId
    AND b.chapterId = $chapterId
    AND id(a) < id(b)
  RETURN id(a) AS aId, id(b) AS bId

During clustering: skip adjacency edge if (a,b) or (b,a) ∈ blockedPairs
```

### Failure behavior

The relation query runs in a `try/catch` inside the consolidation service. If it fails (Neo4j timeout, connection drop, missing index), the blocking set is empty — consolidation proceeds with co-occurrence blocking only. Fewer merges than ideal, zero false fusions.

```java
Set<Pair<UUID, UUID>> blockedPairs;
try {
    blockedPairs = relationClaimRepository.findRelatedMentionPairs(chapterId);
} catch (Exception e) {
    log.warn("Relation blocking query failed for chapter={}, proceeding without relation blocking", chapterId, e);
    blockedPairs = Set.of();
}
```

### Implementation surface

- **`RelationClaimGraphRepository`** — new method `findRelatedMentionPairs(UUID chapterId)`
- **Consolidation services** — query before clustering, wrap in try/catch, pass to engine
- **`ConsolidationEngine`** — accepts `Set<Pair<UUID, UUID>>` blocked pairs, skips those edges

---

## Layer 3: Fuzzy alias matching (exact + approximate, made safe by Layers 1+2)

### Motivation

Blocking eliminates the false-positive risk class (same-scene ambiguous aliases, related entities). With that safety net, matching can be more lenient for true entities that suffer LLM-induced spelling drift. The canonical case: "Krk'aktznk" → "Krkaktznk" → "Krk'aktzink" across three scenes, each producing a different normalized key → three separate `BookIndividual` nodes instead of one.

### Rule

An adjacency edge exists between two mentions if any *non-tainted* alias pair passes a fuzzy similarity threshold. Fuzzy edges are additional to exact-key edges — they do not replace them.

### Algorithm

```
For each pair of mentions (a, b):
  1. If blocked by co-occurrence or relation → skip
  2. If any exact name key matches → edge
  3. For each (alias_a ∈ nonTaintedAliases(a), alias_b ∈ nonTaintedAliases(b)):
     If jaroWinkler(alias_a, alias_b) >= 0.90 → edge
```

**Threshold selection:** Jaro-Winkler ≥ 0.90. This is deliberately high — it catches spelling variations of the *same* name while avoiding cross-name collisions. At 0.90:
- "Krk'aktznk" vs "Krkaktznk" → 0.95 ✓
- "Krk'aktznk" vs "Krk'aktzink" → 0.93 ✓
- "Adrian" vs "Adrien" → 0.89 ✗ (just below threshold — rare LLM variant)
- "Adrian" vs "Adrian Saunders" → ~0.73 ✗
- "Frodo" vs "Bilbo" → 0.33 ✗ (but also co-occurrence blocked)

**Alternative:** If Jaro-Winkler proves too aggressive at 0.90 (e.g., false merges on short names), fall back to trigram similarity ≥ 0.75, which handles substring variations better but requires tuning.

### Implementation

Fuzzy comparison runs on *non-tainted* aliases only. Tainted aliases (Layer 1) are excluded from fuzzy comparison entirely — they cannot contribute to candidate edge generation. This prevents fuzzy matches on shared ambiguous aliases.

The fuzzy check is more expensive than exact key lookup (O(n²) alias comparisons per mention pair). It runs only for pairs that survive the blocking filter AND have no exact key match. In practice, this is a small fraction of all pairs.

**Where:** `ConsolidationEngine.cluster()`, during adjacency graph construction. The engine receives a `SimilarityThreshold` configuration with `enabled` and `threshold` fields. Default: enabled, Jaro-Winkler 0.90.

### Trade-off

| | Without fuzzy | With fuzzy |
|---|---|---|
| "Krk'aktznk" drift | 3 separate BookIndividual nodes | 1 merged BookIndividual |
| "Jon" vs "Joe" (different scenes) | Separate (correct) | Could merge if J-W ≥ 0.90 → 0.78, safe |
| Performance | O(n) key lookups | O(n) + O(k) fuzzy comparisons |

---

## BlockingConfig

The engine receives blocking metadata in a single parameter object:

```java
public record BlockingConfig(
    Set<String> taintedAliases,           // Layer 1: per-scene tainted aliases
    Set<Pair<UUID, UUID>> blockedPairs,   // Layer 2: relation-blocked mention pairs
    SimilarityConfig similarity           // Layer 3: fuzzy matching config
) {
    public static final BlockingConfig EMPTY = new BlockingConfig(Set.of(), Set.of(), SimilarityConfig.DISABLED);
}

public record SimilarityConfig(
    boolean enabled,
    double threshold                      // Jaro-Winkler, default 0.90
) {
    public static final SimilarityConfig DISABLED = new SimilarityConfig(false, 0.0);
    public static final SimilarityConfig DEFAULT = new SimilarityConfig(true, 0.90);
}
```

`ConsolidationEngine.cluster()` gains overloads:
- Existing: `cluster(List<S>, Function<S, Set<String>>)` — backward compatible
- New: `cluster(List<S>, Function<S, Set<String>>, BlockingConfig)` — blocking-aware with fuzzy

---

## Consolidation service flow

```
consolidateChapter(chapterId):
  1. mentions = mentionRepo.findByChapterId(chapterId)
  
  2. taintedAliases = buildFromCoOccurrence(mentions)
     // in-memory, grouped by mention.sceneId()
  
  3. blockedPairs = tryFetchRelationBlockedPairs(chapterId)
     // graph query, try/catch → empty on failure
  
  4. clusters = engine.cluster(mentions, 
        mention -> NameKeys.from(mention, taintedAliases),
        new BlockingConfig(taintedAliases, blockedPairs, SimilarityConfig.DEFAULT))
        // Connects mentions via exact keys + Jaro-Winkler ≥ 0.90 on non-tainted aliases
        // Blocked pairs and co-occurrence filtering prevent false bridges
  
  5. persist ChapterEntities from clusters, with tainted aliases stripped:
     for each cluster:
       mergedAliases = all non-tainted aliases from constituent mentions
       // taintedAliases excluded → stops alias propagation to book level
       // prevents cross-chapter fusion (see Known Limitations)
```

---

## Files to create (~3)

```
lorevault-core/src/main/java/com/lorevault/api/orchestration/consolidation/
├── BlockingConfig.java                              — parameter object (+ SimilarityConfig)
└── AliasSimilarity.java                             — Jaro-Winkler utility
```

## Files to modify (~13)

```
lorevault-core/src/main/java/com/lorevault/api/orchestration/consolidation/
├── ConsolidationEngine.java                          — cluster() overload with BlockingConfig
└── NameKeys.java                                     — from() overload with taintedAliases

lorevault-core/src/main/java/com/lorevault/api/graph/
├── relation/RelationClaimGraphRepository.java        — findRelatedMentionPairs()
├── individual/consolidation/chapter/
│   └── ChapterIndividualConsolidationService.java    — wire blocking
├── collective/consolidation/chapter/
│   └── ChapterCollectiveConsolidationService.java    — wire blocking
├── object/consolidation/chapter/
│   └── ChapterObjectConsolidationService.java        — wire blocking
├── location/consolidation/chapter/
│   └── ChapterLocationConsolidationService.java      — wire blocking
└── event/consolidation/chapter/
    └── ChapterEventConsolidationService.java         — pass BlockingConfig.EMPTY (excluded)
```

## Tests (~4 files)

```
lorevault-core/src/test/java/com/lorevault/api/orchestration/consolidation/
├── ConsolidationEngineTest.java
│   ├── coOccurrenceBlocking_preventAliasMergeAcrossEntities()
│   ├── relationBlocking_preventMergeForRelatedMentions()
│   ├── blockingConfig_empty_preservesBackwardCompat()
│   ├── taintedAlias_excludedFromNameKeys()
│   ├── fuzzyMatching_mergesVariantSpellings()
│   └── fuzzyMatching_excludesTaintedAliases()
└── AliasSimilarityTest.java
    ├── jaroWinkler_identicalStrings()
    ├── jaroWinkler_spellingVariation()
    └── jaroWinkler_differentStrings()

lorevault-core/src/test/java/com/lorevault/api/graph/individual/consolidation/chapter/
└── ChapterIndividualConsolidationServiceTest.java
    ├── lotrFrodoBilbo_notMerged()                    — integration-level smoke test
    ├── relationQueryFailure_gracefulDegradation()
    └── krkaktznk_fuzzyMatch_singleEntity()            — spelling drift across scenes
```

## Interaction with Concept lane

The same blocking infrastructure applies to Concept consolidation with zero changes:
- Co-occurrence: concept mentions in the same scene with shared alias → tainted
- Relation: `RELATES_SUBJECT`/`RELATES_OBJECT` edges involving concept mentions → blocked
- `BlockingConfig` is entity-kind-agnostic

---

---

## Known limitations

### Cross-chapter alias reuse — mitigated by alias propagation stop

**Scenario:** "Mr. Baggins" is tainted in Chapter 1 (used for both Frodo and Bilbo in same scene). Co-occurrence blocks within-scene merge. But if "Mr. Baggins" appears in Chapter 2 (referring to Frodo alone, no Bilbo), and both `ChapterIndividual` nodes carry "Mr. Baggins" in their merged alias sets, book-level consolidation would fuse them.

**Mitigation:** Tainted aliases are **excluded from the ChapterEntity merged alias set** during consolidation. If an alias was tainted in ANY scene of the chapter, it never propagates to the `ChapterIndividual`/`ChapterCollective`/etc. node. At book level, Frodo's `ChapterIndividual` carries `["Frodo"]` and Bilbo's carries `["Bilbo"]` — zero shared aliases, no fusion.

**Rule:** "Once tainted in any scene, never propagate to any entity at any level."

Implementation: one-line filter in each consolidation service's alias-merging loop after building `taintedAliases`.

### Event blocking — deferred

Events use a separate consolidation path (`EventMentionComponentLookup.findSameEventComponents()`, Cypher-based connected components) rather than `ConsolidationEngine.cluster()`. Architecturally, `BlockingConfig` doesn't apply to events. Relation blocking for events requires extending `EventMentionComponentLookup`, not `ConsolidationEngine`. Deferred to a separate analysis task.

### Short-name fuzzy false positives

Jaro-Winkler at 0.90 is calibrated for names ≥ 5 characters. For 2-3 character names ("Ed"/"Ted" = 0.89, "Bo"/"Bob" = 0.85), discriminability drops. A minimum alias length of 3 characters for fuzzy matching (falling back to exact-match-only) would eliminate this risk class. Not yet implemented — low-likelihood in practice with lore character names.

---

## Out of scope

- Deterministic blocking keys (structured aliases: first name, last name, epithet) — deferred
- Book-level relation blocking (cross-chapter relation claims that should block book consolidation)
- LLM prompt changes for structured name output
- Event relation blocking — deferred to separate analysis (different consolidation path)
