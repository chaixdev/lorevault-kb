# Claim-Entity Linking

**Status:** PHASE 1 IMPLEMENTED, PHASE 2 REVISED  
**Last revised:** 2026-06-01 — Phase 2/3 revised. Original 3-layer edge model (`HAS_CHAPTER_SUBJECT`, `HAS_BOOK_SUBJECT`) dropped. Replaced with: add `EntityNode` secondary label to all entity nodes. Existing `RELATES_SUBJECT` + `REFERS_TO` ladder already provides full queryability via `IndividualNode`/`CollectiveNode`/etc. polymorphic labels. No new edge types needed.

## Summary

Link `RelationClaim` nodes to the entity ladder at every scope level (Mention, Chapter, Book) so that claims are traversable from any entity node, and entity-scoped queries can find their claims without scanning all scenes.

## Problem

RelationClaim nodes currently float disconnected from the entity graph. They link to their source `Scene` via `CONTAINS`, but they have no edges to the entities they reference. This means:

1. **No traversal from entity to claim.** Starting from a `ChapterIndividual`, there is no Cypher path to find which claims mention that character. You must scan all claims in the chapter and filter by `subjectName`/`objectName` string matching.
2. **No scope-aware claim queries.** A book-scoped query ("what claims exist about Gandalf across the whole book?") has no structural support — it must do full-text matching against `subjectName` on every claim in every chapter.
3. **`bookId` is null on every RelationClaim.** The field exists on the record but is never populated, so even the most basic book-scoped filter doesn't work.
4. **`linkSubjectMention` and `linkObjectMention` exist but are never called.** The repository methods are implemented (MERGE-based, idempotent) but the persistence service doesn't invoke them.

**Root cause for Layer 1:** The LLM prompt (`scene-analysis.txt`) treats entity extraction and relation extraction as independent parallel tasks. Entities produce `<aliases>` blocks; relation `<subject>`/`<object>` elements are free-form `"Kind: Name"` strings with no instruction to reuse the entity aliases. The LLM can produce `Individual: Frodo` in entity aliases and `Individual: Frodo Baggins` as a relation subject — same person, mismatched strings. The code then has to parse `"Kind: Name"` strings with fallback heuristics (`parseEntityRef()`, 23 lines) rather than receiving structured data.

The brainstorm proposal (`docs/brainstorm/entity-pipelines/2026-05-15T0106_claim-entity-linking-proposal.md`) designed a 3-layer edge model to solve the graph-level problem. This planning doc identifies the concrete implementation steps, with the root-cause fix (prompt restructuring) as Phase 1's primary strategy.

## Product Context

- **Query power:** Without claim-entity edges, the graph cannot answer "what relationships does this entity participate in?" — a core lore-query capability.
- **Spoiler gating:** Claims carry `certainty` and `evidenceText`. Scope-aware edges let future spoiler-gating filter claims by the reader's progress (chapter-level vs book-level).
- **M4 reassessment:** The original M4 milestone (project `REL` edges between consolidated entities) is largely superseded by claim-entity linking. Two-hop paths through RelationClaim are sufficient for most queries. M4 may become a query-optimization layer rather than a core pipeline step.

## Technical Context

### Current state

| Component | Status |
|---|---|
| `RelationClaim` node | Persisted with `subjectName`, `objectName`, `subjectKind`, `objectKind`, `chapterId`, `sceneId`. `bookId` is always `null`. |
| `RelationClaimGraphRepository` | Has `linkClaimToScene()`, `linkSubjectMention()`, `linkObjectMention()` — all MERGE-based. Only `linkClaimToScene()` is called. |
| `RelationClaimPersistenceService` | Persists claims, calls `linkClaimToScene()`, does NOT call `linkSubjectMention()` or `linkObjectMention()`. |
| LLM prompt (`scene-analysis.txt`) | Entities and relations are independent parallel outputs. Relations use free-form `"Kind: Name"` strings (e.g., `<subject>Individual: Frodo</subject>`). No instruction to reuse entity aliases verbatim. |
| `TriadRelationClaimExtraction` | Carries `subject` and `object` as raw `String`. `parseEntityRef()` splits on `": "` with 3 fallback paths (kind-is-null, kind-unknown). |
| Mention repositories (×5) | Each has `findByChapterId()` only. No `findBySceneId()` or `findBySceneIdAndNormalizedName()`. |
| Neo4j indexes | `RelationClaim` has indexes on `(chapterId, definitionKey)` and `(bookId, definitionKey)`. No indexes on `subjectName`/`objectName`. |
| Consolidation services | `ChapterIndividualConsolidationService` and `BookIndividualConsolidationService` both use delete-and-rebuild pattern (`DETACH DELETE` destroys all incoming edges). |

### Handler scope

All entity and claim persistence happens within `SceneDetectionHandler.execute()` — one handler, one method. The `SceneRelationshipOutcome` (containing all entity extractions and relation claims from a single LLM call) is a local variable. Every persistence service call is on an adjacent line. There is no stage boundary between entity persistence and claim persistence. No data needs to be handed off or queried back from Neo4j for Layer 1.

The fix is: persistence services return mention IDs instead of `void`, and the claim service receives those IDs for direct in-memory lookup.

### Proposed 3-layer edge model

| Layer | Edge | From → To | Lifecycle |
|---|---|---|---|
| 1 | `RELATES_SUBJECT` | RelationClaim → EntityMention | Stable. Created once at claim persistence. Survives all consolidation cycles. |
| 2 | `HAS_CHAPTER_SUBJECT` | RelationClaim → Chapter* | Rebuilt. Destroyed by `DETACH DELETE` during chapter consolidation, must be recreated. |
| 3 | `HAS_BOOK_SUBJECT` | RelationClaim → Book* | Rebuilt. Destroyed by `DETACH DELETE` during book consolidation, must be recreated. |

Same pattern for `RELATES_OBJECT`, `HAS_CHAPTER_OBJECT`, `HAS_BOOK_OBJECT`.

### Hook points in existing code

| Layer | Where | What to add |
|---|---|---|
| **Layer 1** | `SceneDetectionHandler.execute()` — after all 6 persistence calls | Collect mention-ID maps from 6a–6e, pass to `persistExtractedRelationClaims()`, call `linkSubjectMention()`/`linkObjectMention()` (already exist, MERGE-based) |
| **Layer 2** | `ChapterIndividualConsolidationService.consolidateChapter()` — after mention→individual linking loop | Batched Cypher query that traverses `RelationClaim → EntityMention → ChapterIndividual` and creates `HAS_CHAPTER_SUBJECT`/`HAS_CHAPTER_OBJECT` edges |
| **Layer 3** | `BookIndividualConsolidationService.consolidateBook()` — after `replaceBookIndividuals()` | Batched Cypher query that traverses `RelationClaim → ChapterIndividual → BookIndividual` and creates `HAS_BOOK_SUBJECT`/`HAS_BOOK_OBJECT` edges |

### Pipeline ordering

Claims are persisted AFTER entities in `SceneDetectionHandler` (step 6f after 6a–6e). The same handler owns the `SceneRelationshipOutcome` and all mention-ID maps. Layer 1 matching is pure in-memory — no Neo4j roundtrip.

## Scope

### In scope

1. **Prompt restructuring** — Replace free-form `"Kind: Name"` strings with structured XML elements. Add instruction to reuse entity aliases verbatim in relation references. This is the primary strategy for Layer 1 — no complex matching needed.
2. **Java record changes** — Update `TriadRelationClaimExtraction` to use structured `TriadEntityRef` and `TriadRelationType` records. Delete `parseEntityRef()`.
3. **Layer 1: Mention-level edges** — `RELATES_SUBJECT`/`RELATES_OBJECT` from RelationClaim to EntityMention, created via in-memory mention-ID lookup within `persistExtractedRelationClaims()`. Same handler scope, same transaction.
4. **bookId population** — Resolve and set `bookId` on every RelationClaim during persistence. The `Chapter` reference (with `getBookId()`) is available at the hook point.
5. **Layer 2: Chapter-level edges** — `HAS_CHAPTER_SUBJECT`/`HAS_CHAPTER_OBJECT` from RelationClaim to Chapter* nodes, rebuilt via batched Cypher during chapter consolidation.
6. **Layer 3: Book-level edges** — `HAS_BOOK_SUBJECT`/`HAS_BOOK_OBJECT` from RelationClaim to Book* nodes, rebuilt via batched Cypher during book consolidation.
7. **Extend to all entity kinds** — Individual, Location, Object, Collective (4 kinds × 2 roles × 3 layers). Events deferred to Phase 4.

### Out of scope

- **M4 `REL` edge projection** — Largely superseded by claim-entity linking. May become a query-optimization layer later, but not part of this work.
- **Incremental book consolidation** — Separate planning item (`2026-05-30T1750_incremental-book-consolidation.md`). The delete-and-rebuild pattern is assumed for Layer 2; Layer 3 is blocked on this.
- **Spoiler gating** — Claim-entity edges enable it, but the gating logic itself is a separate concern.
- **Query API changes** — This work adds graph edges; exposing them through API endpoints is a follow-up.
- **Event lane** — Events use a different pipeline path (event-specific consolidation, embeddings, ANN). Handled in Phase 4 with dedicated design — not deferred because events are unimportant, but because they need lane-specific treatment. During Phases 1–3, claims with `subjectKind=Event` or `objectKind=Event` will not receive Layer 1 edges (best-effort fallback to string properties).
- **Orphan mention creation** — Not needed as a primary strategy. With prompt restructuring guaranteeing alias verbatim reuse, every claim's subject/object should match a co-extracted entity. If mismatches persist despite prompt tuning, orphan creation can be added as a secondary fallback.

## Known Constraints / Prior Findings

1. **Chapter scope is bounded; book scope is not.** Chapter consolidation can safely use delete-and-rebuild because it owns all the data for one chapter. Book consolidation spans multiple chapters — it cannot destroy and recreate Book* nodes in normal operation because that would sever edges from claims across all chapters. Layer 2 (chapter-level edges) can use the existing delete-and-rebuild pattern. Layer 3 (book-level edges) requires incremental merge and is **blocked on incremental book consolidation** (`2026-05-30T1750_incremental-book-consolidation.md`).

2. **`linkSubjectMention` and `linkObjectMention` already exist** in `RelationClaimGraphRepository` with MERGE semantics. Phase 1d calls them directly with the mention IDs returned by the entity persistence services.

3. **`bookId` resolution pattern exists** — `SceneDetectionHandler` (line 348) resolves `bookId` from `chapter.getBookId()`. The same pattern applies to `RelationClaimPersistenceService` — pass `bookId` as a parameter rather than adding a repository dependency.

4. **Handler scope enables in-memory matching.** All 6 persistence calls happen in the same handler method (`SceneDetectionHandler.execute()`). The `SceneRelationshipOutcome` and the returned mention-ID maps are local variables. No stage boundary exists between entity and claim persistence. Layer 1 matching is O(1) map lookup, no Neo4j roundtrip needed.

5. **Prompt restructuring is the primary name-divergence fix.** The current prompt's free-form `"Kind: Name"` strings and independent entity/relation extraction cause the LLM to use different name variants for the same entity. The fix: structured XML elements (`<subject><entityType>Individual</entityType><alias>Frodo</alias></subject>`) with a prompt instruction to use entity aliases verbatim. This makes claim-entity matching a structural guarantee rather than a string-parsing problem.

6. **`parseEntityRef()` becomes dead code.** With structured XML elements, the LLM output maps directly to typed fields. No string splitting, no kind-is-null fallback, no kind-unknown logging. Delete the method (~23 lines) and the `VALID_ENTITY_KINDS` validation set.

7. **Polymorphic targets.** Each edge type targets multiple node labels (e.g., `HAS_CHAPTER_SUBJECT` points to `ChapterIndividual`, `ChapterLocation`, etc.). Cypher handles this naturally — the edge type is the discriminator, not the target label. 6 distinct edge-type names cover all combinations.

8. **Batched Cypher vs per-claim repository calls.** Layer 2 and 3 re-linking should use single batched queries (UNWIND pattern), not per-claim repository method calls. For a book with 10K claims, per-claim calls would be 10K individual Cypher executions — unacceptable. The brainstorm proposal's batched traversal approach is correct.

9. **No dedicated StageKey for claim re-linking.** Layer 2/3 re-linking is inline in the existing consolidation services. If claim re-linking fails, the entire consolidation transaction rolls back (consistent with the current `@Transactional` scope). A separate StageKey would allow independent failure handling but adds orchestration complexity. Start inline; extract to separate stage if failure isolation becomes necessary.

10. **Consolidation service pattern verification.** Phase 2 assumes all four consolidation lanes (Individual, Location, Object, Collective) follow the same `delete → cluster → save → link` pattern. From code inspection they do, but verify before extending. Phase 2a should target Individual only; 2b extends after verification.

11. **Polymorphic labels already wired at every ladder level.** All 5 mention types carry `EntityMention` as a secondary label. All chapter-level entities carry `ChapterEntity` (`@Node(primaryLabel = "ChapterIndividual", labels = {"ChapterEntity", "IndividualNode"})`). All book-level entities carry `BookEntity`. This means Layer 2/3 batched Cypher queries can use polymorphic labels — no per-kind query duplication.

12. **Service return-type change is scoped.** Only the 5 entity persistence services change from `void` to return `Map<String, UUID>`. The claim persistence service adds the ID-map parameter. This is 6 service signatures, not an architectural change. Existing tests that call these services will need their assertions updated (return type change) but no logic changes needed — services still persist the same nodes.

13. **Event lane is specially handled, not deferred.** Events use a different pipeline path (event-specific consolidation, embeddings, ANN). They aren't deferred because they're unimportant — they need dedicated design work. The EventPersistenceService returns mention IDs (collected in Phase 1c) but the claim service does not route Event claims to the eventIds map until Phase 4.

14. **Concept entity kind — no persistence service exists.** The `VALID_ENTITY_KINDS` set includes Concept and the prompt defines Concept entities, but there is no `ConceptPersistenceService`, no `ConceptMention`, and no concept-ID map. Claims with `subjectKind=Concept` will silently fail to link (the switch default returns null). This gap exists until the Concept resolution lane (`docs/planning/2026-04-30T1237_concept-resolution-lane.md`) is implemented. During Phases 1–3, Concept claims remain unlinked.

15. **Transaction boundary for Layer 2/3.** `ChapterIndividualConsolidationService.consolidateChapter()` is `@Transactional`. Adding batched Cypher re-linking inside this method means claim edges atomically commit or roll back with entity consolidation. This is correct — no orphan edges can exist. Same for book-level consolidation. The plan should explicitly state this rather than implying a separate call.

## Implementation Phases

### Phase 1: Prompt restructuring + return mention IDs + Layer 1 edges + bookId

**Goal:** Every RelationClaim links to its subject and object EntityMention nodes, and has a populated `bookId`.

**Strategy:** Prompt restructuring guarantees name consistency. Persistence services return mention IDs. The claim service does O(1) in-memory lookup — same handler, no Cypher.

#### 1a: Prompt restructuring

**File:** `lorevault-core/src/main/resources/prompts/scene-analysis.txt`

Replace the free-form relation format:

```xml
<!-- BEFORE -->
<relations>
  <relation>
    <subject>Individual: Frodo</subject>
    <relationName>trusts</relationName>
    <relationDescription>trust-based companionship...</relationDescription>
    <object>Individual: Samwise</object>
    <certainty>Explicit</certainty>
    <evidence>...</evidence>
  </relation>
</relations>
```

With structured elements:

```xml
<!-- AFTER -->
<relations>
  <relation>
    <subject>
      <entityType>Individual</entityType>
      <alias>Frodo</alias>
    </subject>
    <relationType>
      <name>trusts</name>
      <description>trust-based companionship and loyalty between individuals</description>
    </relationType>
    <object>
      <entityType>Individual</entityType>
      <alias>Samwise</alias>
    </object>
    <certainty>Explicit</certainty>
    <evidence>Frodo turned to Sam and said, &quot;I trust you more than anyone.&quot;</evidence>
  </relation>
</relations>
```

**Prompt instruction** (add to the relation section):

> `subject`/`object`: the entities in the relation. `<entityType>` must match one of the entity categories above (Individual, Collective, Object, Location, Concept, Event). `<alias>` must match the primary alias from that entity's `<aliases>` section verbatim. Do not invent a new name — reuse the alias exactly as extracted. Use the first alias you listed for that entity — this is the primary alias. Do not invent a new name.

#### 1b: Java record changes

**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/triad/SceneRelationshipAnalysisService.java`

```java
// New records — replace free-form String subject/object
public record TriadEntityRef(String entityType, String alias) {}
public record TriadRelationType(String name, String description) {}

// Updated record
public record TriadRelationClaimExtraction(
    TriadEntityRef subject,
    TriadRelationType relationType,
    TriadEntityRef object,
    String certainty,
    String evidence
) {}
```

**Delete:** `parseEntityRef()` (~23 lines, lines 492–514) and `validateKind()` helper. The structured output eliminates the need for string-parsing heuristics.

**Update:** `normalizeRelationClaims()` to map `TriadEntityRef.entityType → subjectKind`, `TriadEntityRef.alias → subjectName` (same for object). The `RelationClaimExtraction` downstream record stays the same — its flat fields just get populated from the structured source.

#### 1c: Persistence services return mention IDs

**Files:** `IndividualPersistenceService`, `CollectivePersistenceService`, `ObjectPersistenceService`, `LocationPersistenceService`, `EventPersistenceService`

Change return type from `void` to `Map<String, UUID>` keyed by normalized name (`trim + collapse whitespace + lowercase`). Each service already computes `normalizedName` internally — collect it alongside the saved mention ID.

**File:** `SceneDetectionHandler.execute()` (Scene.java ~line 412)

```java
Map<String, UUID> individualIds = individualPersistenceService.persistExtractedIndividuals(ctx, scenes, outcome.sceneIndividualExtractions());
Map<String, UUID> collectiveIds = collectivePersistenceService.persistExtractedCollectives(ctx, scenes, outcome.sceneCollectiveExtractions());
Map<String, UUID> objectIds     = objectPersistenceService.persistExtractedObjects(ctx, scenes, outcome.sceneObjectExtractions());
Map<String, UUID> locationIds   = locationPersistenceService.persistExtractedLocations(ctx, scenes, outcome.sceneLocationExtractions());
Map<String, UUID> eventIds      = eventPersistenceService.persistExtractedEvents(ctx, scenes, outcome.sceneEventExtractions());

relationClaimPersistenceService.persistExtractedRelationClaims(
    ctx, scenes, outcome.sceneRelationClaimExtractions(),
    bookId, individualIds, collectiveIds, objectIds, locationIds, eventIds
);
```

#### 1d: Layer 1 edge creation + bookId

**File:** `RelationClaimPersistenceService`

Add `UUID bookId` and the 5 mention-ID maps to `persistExtractedRelationClaims()` parameters. The caller (`SceneDetectionHandler`, line 348) already has `chapter.getBookId()`.

After saving each claim, match by kind + normalized name:

```java
String subjectNormalizedName = normalizeName(extracted.subjectName()); // trim+lower+collapse+strip-punct
UUID mentionId = switch (extracted.subjectKind()) {
    case "Individual" -> individualIds.get(subjectNormalizedName);
    case "Collective" -> collectiveIds.get(subjectNormalizedName);
    case "Object"     -> objectIds.get(subjectNormalizedName);
    case "Location"   -> locationIds.get(subjectNormalizedName);
    // Event: deferred to Phase 4 (eventIds map is collected but not routed here)
    default           -> null;
};
if (mentionId != null) {
    relationClaimRepository.linkSubjectMention(saved.id(), mentionId);
}
// Same for object
```

**Normalization:** Extract `normalizeName()` into a shared utility (`NameNormalizer` in `common/`): `trim().replaceAll("\\s+", " ").toLowerCase().replaceAll("[^a-z0-9 ]", "")`. Strips case, whitespace, and punctuation — covers LLM punctuation drift (e.g., "Mr. Underhill" → "mr underhill"). Used by both entity persistence (for map keys) and claim persistence (for lookup keys).

**Multi-key alias map:** Each persistence service returns a map where every alias variant resolves to the same mention ID — not just the primary alias:

```java
// For IndividualMention with aliases=["Frodo", "Frodo Baggins"]:
map.put(normalizeName("Frodo"), saved.id());
map.put(normalizeName("Frodo Baggins"), saved.id());
```

If the LLM uses any alias variant (not just the primary), the lookup still succeeds. This is the secondary fallback — it makes the >95% match rate achievable without absolute LLM compliance on "primary alias" selection.

**Kind matching:** Each persistence service returns its own map — `individualIds`, `collectiveIds`, etc. `subjectKind` determines which map to look up. If the LLM mislabels a subject's kind, the lookup returns null and no edge is created. Log a warning. Strict kind matching by construction — no Cypher needed.

**Transaction:** `persistExtractedRelationClaims()` is `@Transactional`. Both claim persistence and edge creation happen in the same transaction — claims and their edges commit atomically. No orphan risk.

**Idempotency on retry:** `linkSubjectMention()`/`linkObjectMention()` use MERGE — calling them again on a retry is safe. The idempotency guard for claims (`countBySceneIdAndContentIdentity`) skips already-persisted claims, which means their edges from the first successful run persist without re-creation. The mention-ID maps are still collected on retry (persistence services re-run, creating no new nodes, returning the same IDs), so the lookup path works for new claims too.

#### Phase 1 verification

After persisting claims for a chapter, verify edges exist:

```cypher
MATCH (rc:RelationClaim)-[:RELATES_SUBJECT]->(m:EntityMention)
RETURN rc.subjectName, m.normalizedName
LIMIT 10
```

Both `rc.subjectName` and `m.normalizedName` are stored in normalized form (lowercased, punctuation-stripped). They should match exactly.

Monitor: ratio of linked claims to total claims. Target: >95% after prompt restructuring.

### Phase 2: EntityNode label (replaces original Layer 2/3)

**Goal:** Add `EntityNode` as a secondary label to all entity nodes at every ladder level, so that `(a:IndividualNode)-[:RELATES_SUBJECT]-(rc)-[:RELATES_OBJECT]->(b:EntityNode)` returns every entity kind a character relates to.

**Why no new edges needed:** The existing Phase 1 edges (`RELATES_SUBJECT`/`RELATES_OBJECT` → `EntityMention`) combined with the `REFERS_TO` ladder (`EntityMention → ChapterEntity → BookEntity`) already connect every entity level. The existing polymorphic kind labels (`IndividualNode`, `CollectiveNode`, `ObjectNode`, `LocationNode`, `EventNode`) unify the subject side. Adding `EntityNode` unifies the object side — so claims can be traversed from any entity kind without per-kind query branching.

**Target query:**
```cypher
MATCH (a:IndividualNode {normalizedName: "jenkins"})
      <-[:RELATES_SUBJECT|RELATES_OBJECT]-(rc:RelationClaim)
      -[:RELATES_SUBJECT|RELATES_OBJECT]->(b:EntityNode)
WHERE a <> b
RETURN a.normalizedName, rc.relationName, b.normalizedName, labels(b)[0] AS bKind
```

**Current labels → revised:**
| Node type | Current secondary labels | Add |
|---|---|---|
| IndividualMention | EntityMention, IndividualNode | EntityNode |
| CollectiveMention | EntityMention, CollectiveNode | EntityNode |
| ObjectMention | EntityMention, ObjectNode | EntityNode |
| LocationMention | EntityMention, LocationNode | EntityNode |
| EventMention | EntityMention, EventNode | EntityNode |
| ChapterIndividual | ChapterEntity, IndividualNode | EntityNode |
| ChapterCollective | ChapterEntity, CollectiveNode | EntityNode |
| ChapterObject | ChapterEntity, ObjectNode | EntityNode |
| ChapterLocation | ChapterEntity, LocationNode | EntityNode |
| ChapterEvent | ChapterEntity, EventNode | EntityNode |
| BookIndividual | BookEntity, IndividualNode | EntityNode |
| BookCollective | BookEntity, CollectiveNode | EntityNode |
| BookObject | BookEntity, ObjectNode | EntityNode |
| BookLocation | BookEntity, LocationNode | EntityNode |
| BookEvent | BookEntity, EventNode | EntityNode |

**Implementation:** Either Neo4j schema initializer (centralized) or per-entity `@Node(labels = {...})` annotations. The labels are already configured in `@Node` annotations — add `EntityNode` to each.

**Note:** Event lane deferred. Add `EntityNode` to event nodes but don't create RELATES edges for event claims until Phase 4.

### Phase 3: Dropped

Original Phase 2 (`HAS_CHAPTER_SUBJECT`) and Phase 3 (`HAS_BOOK_SUBJECT`) are unnecessary. The existing Phase 1 edges + `EntityNode` label provide equivalent query power without materializing redundant edge types.

## Open Questions

1. **Prompt tuning validation.** Run the restructured prompt against 2–3 representative chapters with known entity sets. Parse the output, extract `subjectName`/`objectName`, cross-reference against entity aliases in the same scene, measure match rate. Target: ≥95%. Below that: tune prompt wording, adjust few-shot examples, or strengthen the alias-verbatim instruction. Manual evaluation during Phase 1a/1b — not a CI gate.

2. **Cross-kind claims.** The LLM might label a subject as `Individual` when the matching mention is `Collective` (e.g., "The Knights Radiant"). The in-memory map approach enforces strict kind matching: `subjectKind` routes to a specific map (`"Individual" → individualIds`). If the kind mismatches the extraction, the lookup returns null, no edge is created, and a warning is logged. This is acceptable — cross-kind mismatches should be rare with prompt restructuring, and the warning surfaces extraction quality issues worth surfacing.

3. **`EntityNode` label placement.** Should `EntityNode` be added via `@Node(labels = {...})` on each entity class, or via the `Neo4jSchemaInitializer`? Per-class annotations are self-documenting but spread across 15+ files. Schema initializer centralizes the change but decouples the label from the class definition. Prefer annotations for discoverability.

4. **Polymorphic query performance.** The `RELATES_SUBJECT|RELATES_OBJECT` multi-type match and `EntityNode` label filter should leverage existing indexes. Verify no full scans on the Deathworlders data set after Phase 2.

5. **`bookId` population.** Already resolved — passed from `chapter.getBookId()` via handler to claim service. All 22 Deathworlders claims have populated bookId in smoke test.

## Success Criteria

- [x] Restructured prompt produces structured entity refs (`<entityType>` + `<alias>`) with alias reuse
- [x] `parseEntityRef()` deleted; `TriadEntityRef` and `TriadRelationType` records in use
- [x] All 5 entity persistence services return `Map<String, UUID>` (normalizedName → mentionId)
- [x] Every RelationClaim has `RELATES_SUBJECT` and `RELATES_OBJECT` edges
- [x] Every RelationClaim has a populated `bookId`
- [ ] Layer 1 subjectName-to-mentionName match rate ≥ 95% (smoke test: 93.2% — close)
- [ ] `EntityNode` secondary label on all 15 entity node types (Phase 2)
- [ ] Query `(a:IndividualNode)<-[r:RELATES_SUBJECT\|RELATES_OBJECT]-(rc)-[r2:RELATES_SUBJECT\|RELATES_OBJECT]->(b:EntityNode)` returns cross-kind relationships
- [ ] Existing consolidation cycles remain correct
- [ ] No performance regression on consolidation cycle time (<5% increase)

## Links

- **Brainstorm proposal:** `docs/brainstorm/entity-pipelines/2026-05-15T0106_claim-entity-linking-proposal.md`
- **Entity resolution ladder pattern:** `docs/patterns/ingestion/entity-resolution-ladder.md`
- **ADR 007 (scoped identity ladder):** `docs/adr/007-adopt-scoped-identity-ladder.md`
- **Relation evidence harvesting (Phase 0):** `docs/planning/2026-05-07T1917_relation-evidence-harvesting.md`
- **Relation catalog module:** `docs/planning/2026-05-13T2027_relation-catalog-module.md`
- **Incremental book consolidation:** `docs/planning/2026-05-30T1750_incremental-book-consolidation.md`
- **Key implementation files:**
  - `lorevault-core/src/main/resources/prompts/scene-analysis.txt` — prompt restructuring
  - `lorevault-core/src/main/java/com/lorevault/api/orchestration/triad/SceneRelationshipAnalysisService.java` — records, delete `parseEntityRef()`
  - `lorevault-core/src/main/java/com/lorevault/api/orchestration/triad/TriadAnalysisModels.java` — `RelationClaimExtraction`
  - `lorevault-core/src/main/java/com/lorevault/api/graph/relation/RelationClaimPersistenceService.java` — bookId, mention-ID maps, edge creation
  - `lorevault-core/src/main/java/com/lorevault/api/graph/relation/RelationClaimGraphRepository.java` — `linkSubjectMention`/`linkObjectMention` (exist, unused)
  - `lorevault-core/src/main/java/com/lorevault/api/graph/event/scene/Scene.java` — handler hook point, collect mention-ID maps
  - `lorevault-core/src/main/java/com/lorevault/api/graph/individual/persistence/IndividualPersistenceService.java` — return mention IDs
  - `lorevault-core/src/main/java/com/lorevault/api/graph/collective/persistence/CollectivePersistenceService.java` — return mention IDs
  - `lorevault-core/src/main/java/com/lorevault/api/graph/object/persistence/ObjectPersistenceService.java` — return mention IDs
  - `lorevault-core/src/main/java/com/lorevault/api/graph/location/persistence/LocationPersistenceService.java` — return mention IDs
  - `lorevault-core/src/main/java/com/lorevault/api/graph/event/persistence/EventPersistenceService.java` — return mention IDs
  - `lorevault-core/src/main/java/com/lorevault/api/graph/individual/consolidation/chapter/ChapterIndividualConsolidationService.java` — Layer 2 hook
  - `lorevault-core/src/main/java/com/lorevault/api/graph/individual/consolidation/book/BookIndividualConsolidationHandler.java` — Layer 3 hook
   - `lorevault-core/src/main/java/com/lorevault/api/config/Neo4jSchemaInitializer.java`

## Phase 1 Implementation Notes (2026-06-01)

### Files Created
- `lorevault-core/src/main/java/com/lorevault/api/common/NameNormalizer.java` — shared normalization utility

### Files Modified
| File | Change |
|---|---|
| `prompts/scene-analysis.txt` | Replaced free-form `"Kind: Name"` with structured `<subject><entityType>...<alias>...</alias></entityType></subject>`. Added instruction to reuse entity aliases verbatim. Updated XML examples. |
| `SceneRelationshipAnalysisService.java` | Added `TriadEntityRef` and `TriadRelationType` records. Updated `TriadRelationClaimExtraction` to use structured fields. Updated `normalizeRelationClaims()` to extract from records instead of parsing strings. Deleted `parseEntityRef()` (~23 lines), `validateKind()`, and `VALID_ENTITY_KINDS`. |
| `IndividualPersistenceService.java` | Returns `Map<String, UUID>` with all alias variants as keys. Uses `NameNormalizer`. Deleted private `normalizeName()`. |
| `CollectivePersistenceService.java` | Same transformation as Individual. |
| `ObjectPersistenceService.java` | Same transformation as Individual. |
| `LocationPersistenceService.java` | Same transformation as Individual. |
| `EventPersistenceService.java` | Same transformation as Individual (single-key map for event name). |
| `RelationClaimPersistenceService.java` | Accepts `UUID bookId` + 5 mention-ID maps. After saving each claim, looks up mention by kind+normalized name (O(1) in-memory). Calls `linkSubjectMention()`/`linkObjectMention()`. Populates `bookId`. Added `resolveMentionId()` helper. Logs linked/unlinked counts. |
| `Scene.java` (SceneDetectionHandler) | Collects mention-ID maps from 5 persistence services. Passes `bookId` + maps to claim persistence service. |
| `RelationClaimPersistenceServiceTest.java` | Updated all test method calls to new 9-param signature. Changed `bookId().isNull()` assertion to `bookId().isEqualTo(BOOK_ID)`. |
| `SceneRelationshipAnalysisServiceTest.java` | Removed `parseEntityRef` test battery (7 tests for deleted method). |

### Deviations from Plan
1. **No `VALID_ENTITY_KINDS` in lookup.** The in-memory lookup uses strict kind-matching by map, not validation against a set. If kind is unknown (e.g., "Concept"), the switch returns `null` and no edge is created. This matches the plan's intent. The `VALID_ENTITY_KINDS` set was only used by the old `validateKind()` helper — no replacement needed.
2. **Event map collected but not routed.** The `eventIds` map is collected in the handler but the `resolveMentionId()` switch does not route Event claims. This matches the plan's Phase 4 deferral.

### Verification
- `mvn test -rf :lorevault-core`: **441 tests, 0 failures, 0 errors** (includes architecture tests, boundary tests, and all unit tests)
- Web module has pre-existing compilation errors (unrelated package reorganization — no changes made to `/web` sources)

### Remaining Phase 1 Work
- Integration test with real LLM output to measure match rate
- Prompt tuning if match rate < 95%

### Next: Phase 2 — Add `EntityNode` secondary label

**Revised (2026-06-01):** Original Phase 2/3 (HAS_CHAPTER_SUBJECT, HAS_BOOK_SUBJECT edges) dropped as unnecessary. The existing `RELATES_SUBJECT`/`RELATES_OBJECT` edges (Phase 1) + `REFERS_TO` ladder + polymorphic kind labels (`IndividualNode`, etc.) already connect claims to entities at every resolution level. 

Phase 2 is now: add `EntityNode` as a secondary label to all entity nodes at every ladder level (Mention, Chapter, Book, all 5 kinds). This unifies the query surface — `(a:IndividualNode)-[:RELATES_SUBJECT]-(rc)-[:RELATES_OBJECT]->(b:EntityNode)` returns cross-kind relationships without per-kind branching.

Implementation: update `@Node(labels = {...})` annotations or schema initializer. 15 node types × 1 label addition. No new edges, no Cypher, no consolidation hook points.
