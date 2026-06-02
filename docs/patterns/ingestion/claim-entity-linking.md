# Claim-Entity Linking

**Status:** Established

## Design Philosophy

Claim-entity linking connects `RelationClaim` nodes to the entity graph at the evidence layer, enabling traversal from any entity to its relationships. The mechanism rests on three design decisions:

1. **Prompt structuring over string parsing.** The LLM prompt produces structured XML (`<subject><entityType>Individual</entityType><alias>Frodo</alias></subject>`) rather than free-form `"Kind: Name"` strings. Entity aliases are reused verbatim from the same extraction output, making claim-entity matching a structural guarantee.

2. **In-memory matching within handler scope.** All entity and claim persistence happens inside `SceneDetectionHandler.execute()`. Persistence services return `Map<String, UUID>` (normalized alias → mention ID), and the claim service does O(1) map lookups — zero Neo4j roundtrips.

3. **EntityNode polymorphic label.** All 15 entity node types carry `EntityNode` as a secondary label, unifying the query surface so `(a:IndividualNode)-[:RELATES_SUBJECT]-(rc)-[:RELATES_OBJECT]->(b:EntityNode)` returns cross-kind relationships without per-kind query branching.

## Graph Shape

```
RelationClaim
    |-- RELATES_SUBJECT --> EntityMention (IndividualMention, etc.)
    |-- RELATES_OBJECT  --> EntityMention (IndividualMention, etc.)

EntityMention
    |-- REFERS_TO --> ChapterIndividual / ChapterLocation / etc.

ChapterIndividual (and other kinds)
    |-- REFERS_TO --> BookIndividual / BookLocation / etc.
```

Edges are write-once at claim persistence (MERGE-based). Entity consolidation (chapter, book) never touches claim edges. The `REFERS_TO` ladder already connects mentions to chapter and book entities; queries walk the ladder through `IndividualNode`/`CollectiveNode`/etc. labels that span all resolution levels.

## Component Map

| Component | File | Role |
|---|---|---|
| `SceneDetectionHandler` | `Scene.java` | Collects mention-ID maps, passes to claim service |
| `NameNormalizer` | `NameNormalizer.java` | Shared `trim()+lower()+strip-punctuation` normalization |
| `TriadRelationClaimExtraction` | `SceneRelationshipAnalysisService.java` | Structured record (`TriadEntityRef` subject/object) |
| `IndividualPersistenceService` (×5) | `*PersistenceService.java` | Returns `Map<String, UUID>` with all alias variants |
| `RelationClaimPersistenceService` | `RelationClaimPersistenceService.java` | Accepts bookId + 5 mention-ID maps, creates Layer 1 edges |
| `RelationClaimGraphRepository` | `RelationClaimGraphRepository.java` | `linkSubjectMention()` / `linkObjectMention()` — MERGE-based |

## Key Query Pattern

```cypher
-- Find all relationships of Kevin Jenkins with any entity kind
MATCH (a:IndividualNode {normalizedName: "jenkins"})
      <-[:RELATES_SUBJECT|RELATES_OBJECT]-(rc:RelationClaim)
      -[:RELATES_SUBJECT|RELATES_OBJECT]->(b:EntityNode)
WHERE a <> b
RETURN DISTINCT rc.relationName, b.normalizedName, labels(b)[0] AS bKind
```

## Prompt Format

The `scene-analysis.txt` prompt produces structured relation references:

```xml
<relation>
  <subject>
    <entityType>Individual</entityType>
    <alias>Frodo</alias>           <!-- verbatim from entity <aliases> -->
  </subject>
  <relationType>
    <name>trusts</name>
    <description>trust-based companionship...</description>
  </relationType>
  <object>
    <entityType>Individual</entityType>
    <alias>Samwise</alias>
  </object>
</relation>
```

## Lifecycle

1. **Extraction:** LLM produces structured `TriadEntityRef` with `entityType` + `alias`
2. **Persistence:** Entity persistence services save IndividualMention etc., return `Map<String, UUID>` with all alias variants
3. **Linking:** Claim service normalizes `subjectName`/`objectName`, looks up in appropriate kind map, calls `linkSubjectMention()`/`linkObjectMention()`
4. **Query:** `IndividualNode` label spans Mention → ChapterIndividual → BookIndividual; `EntityNode` spans all kinds
5. **Idempotency:** Both claim dedup (`countBySceneIdAndContentIdentity`) and edge creation (MERGE) are restart-safe

## Related

- Planning doc: `docs/planning/2026-05-31T1509_claim-entity-linking.md`
- Prompt: `lorevault-core/src/main/resources/prompts/scene-analysis.txt`
- Normalizer: `lorevault-core/src/main/java/com/lorevault/api/common/NameNormalizer.java`
