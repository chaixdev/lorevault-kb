# Entity Node Contract

LoreVault's domain model has two layers. Every node belongs to one.

## Evidence Layer

`*Mention` nodes and `RelationClaim` nodes. Raw LLM extractions from scenes.
These are the facts. They carry provenance anchors and publication coordinates.

### Publication Coordinates

Evidence nodes carry resolved `PublicationCoordinates` as a snapshot — coordinates
are trivially available at persistence (scene → chapter → book are all in scope),
and the publication hierarchy is immutable once ingested. There is no staleness
risk, no invalidation concern.

Spoilergating is a first-class concern. A query must be able to filter evidence
by reading progress without joining through the library hierarchy. Coordinates on
evidence nodes make this possible on any query path — not just semantic search.

### Provenance

Every evidence node carries `stageId` for pipeline replay, cleanup, and audit.

### Entity Identity

Every evidence node carries display and normalized name fields for consolidation
clustering — the clustering engine operates on both evidence and interpretation
nodes.

## Interpretation Layer

`ChapterXxx` and `BookXxx` nodes. Identity grouping — "these mentions refer to the
same entity." They are connecting tissue between facts, not knowledge carriers.

### No Coordinates

Interpretation nodes do NOT carry publication coordinates. They don't need to.
A `BookIndividual` is visible because the `IndividualMention` evidence backing it
is within the reader's scope. Spoilergating filters evidence layer, then traverses
to interpretation.

### Same Provenance + Identity

Interpretation nodes share the same `HasProvenance` and `EntityIdentity` interfaces
as evidence nodes — `ConsolidationEngine` operates on both layers uniformly.

## Shared Interfaces

```java
public interface HasProvenance {
    UUID id();
    UUID stageId();
    LocalDateTime createdAt();
    LocalDateTime updatedAt();
}

public interface EntityIdentity {
    String displayName();          // raw LLM extraction, for display/audit
    String primaryName();          // normalized, for consolidation clustering
    List<String> aliases();        // normalized, for consolidation clustering
    List<String> displayAliases(); // raw LLM extraction, audit trail
}
```

## Labels

Every node must carry its domain-specific secondary label (e.g., `IndividualNode`,
`LocationNode`) plus `EntityNode` for cross-kind queries.

## Scene: Bridging Evidence and Timeline

`Scene` is both evidence layer and an `Event` subclass — it bridges the entity graph
and the timeline DAG.

- **Evidence role:** Extracted directly from chapter text via segmentation. No
  consolidation ladder — scenes are concrete, not clustered. Other evidence nodes
  reference scenes via `sceneId`.
- **Timeline role:** Scenes form the timeline DAG backbone via `NEXT_IN_READING_ORDER`
  and `TEMPORAL` edges. Extracted `Event` entities (EventMention → ChapterEvent →
  BookEvent at the interpretation layer) attach to this backbone.

Scenes don't carry `EntityIdentity` — they're not identity-clustered. They do carry
`PublicationCoordinates` as evidence-layer nodes and `stageId` for provenance.

## Spoilergating Pattern

```cypher
// Evidence nodes carry coordinates — filter directly
MATCH (m:IndividualMention)
WHERE m.chapterNumber <= $readThroughChapter
  AND m.bookNumber = $currentBook
MATCH (m)-[:REFERS_TO]->(bi:BookIndividual)
RETURN bi
```

Node types not in scope: `Chunk` (substrate), `Chapter`, `Book` (library
hierarchy), `Stage`/`StageOutput` (pipeline state).

## Implementation Priority

1. `EntityIdentity` + `HasProvenance` — extract shared interfaces, rename fields, add `aliases` (normalized) to all entity nodes. ~72 files.
2. `PublicationCoordinates` on evidence nodes — add coord fields to 6 `*Mention` types, populate at persistence. Enables spoilergating on entity lookups.
3. Label enforcement — verify all nodes carry correct secondary labels.
