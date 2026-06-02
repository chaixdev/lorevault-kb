# Record Design Guidance

**Status:** Active

Records make type creation free — but free types proliferate past diminishing returns. This guidance defines when a record earns its existence vs when it's scaffolding that should be eliminated.

## The Three Gates

Apply these before creating or keeping a record:

### 1. Are all fields consumed by at least one caller?

A field that is set, logged, boxed into a record, and never read again is dead weight. It adds noise to callers that must read the record to discover which fields matter.

```java
// Wrong — two count fields are set, logged inside the service, boxed, never read again
public record DefaultResult(int edgesCreated, int crossChapterEdges, List<Boundary> boundaries) {}

// Correct — return what the caller actually needs
public List<Boundary> createAllDefaults(UUID bookId) { ... }
```

### 2. Does the type carry information callers don't already have?

If nine records share `(UUID id, boolean success, int processed, int created, String message)` and differ only by entity-type name (`ChapterIndividualConsolidationResult`, `BookObjectConsolidationResult`, etc.), the type tag carries no information. The caller knows it called `individualConsolidationService` — it doesn't need the return type to remind it.

The test: if you renamed all nine records to `ConsolidationResult`, would any caller behavior change? If no, the type tags are noise.

### 3. Is the type consumed by more than one caller?

If a record is created by service A, consumed exactly once by handler B, and immediately repackaged into another type (like `StageResult`), cut the record. Have the service return `StageResult` directly, or return the payload directly.

A record that exists only to carry data three lines to a constructor is scaffolding — not a domain type.

## Positive cases: records that earn their existence

- **Heterogeneous payload bundles:** `SceneRelationshipOutcome` carries 7 distinct entity lists — each consumed independently by different persistence services. Multiple consumers, fields used independently, type carries information callers couldn't derive.
- **External boundary types:** DTOs that cross module or process boundaries (HTTP request/response objects, event payloads) are justified by their boundary role even if consumed once.
- **Genuine generic containers:** `LibraryResult<T>` carries heterogeneous data (the entity + a boolean) that has semantic meaning as a unit.

## Smells

| Smell | Fix |
|---|---|
| All fields are `int` counts logged by the producer, never read by consumers | Remove unused fields; return the remaining payload directly |
| Multiple records share identical field shapes with different entity-type names | Collapse into a single generic record, or eliminate if callers don't need the type tag |
| Record is created by a service, unwrapped by its immediate caller, fields passed to a constructor | Cut the record; have the service return the constructor's type directly |
| Record has exactly one caller that immediately destructures it | Return the destructured values directly or collapse into the caller |

## Decision rule

Before creating a record, answer: what caller will use this type as a coherent value? If the answer is "it's just an intermediate carrier," don't create it.

## Provenance Fields on `@Node` Entities

Domain nodes created during pipeline execution carry a `stageId` provenance property. The pattern differs between records and `@Data` classes:

### Records: `stageId` as a record component

For `@Node` records, add `stageId` as a record component with `@Property("stageId")`. Place it after the scope ID and before business fields:

```java
public record ChapterIndividual(
        @Id UUID id,
        UUID chapterId,
        @Property("stageId") UUID stageId,  // after scope ID
        String displayName,
        String normalizedName,
        Integer mentionCount,
        @CreatedDate LocalDateTime createdAt,
        @LastModifiedDate LocalDateTime updatedAt
) {}
```

This breaks all construction sites — every `new ChapterIndividual(...)` call must add `ctx.stageId()` (or `null` for pre-existing data). The trade-off is explicit: records have no setters, so the field must be a component.

### `@Data` classes: `stageId` as a field with setter

For `@Data` classes with `@PersistenceCreator` (Scene, Chunk), add `stageId` as a field with `@Property("stageId")` and a Lombok-generated setter. Do **not** add it to the `@PersistenceCreator` constructor — these classes already have 15+ parameters:

```java
@Data
@Node("Scene")
public class Scene {
    @Property("stageId")
    private UUID stageId;  // set via scene.setStageId(ctx.stageId())

    // ... existing fields ...
}
```

Set `stageId` after construction, before persistence:

```java
List<Scene> toSave = scenesWithCoords.stream()
        .map(swc -> {
            Scene scene = new Scene(...);
            scene.setStageId(ctx.stageId());
            return scene;
        }).toList();
sceneRepo.saveAll(toSave);
```

### Why the split?

Records are immutable — the only way to set a field is via the canonical constructor. `@Data` classes with `@PersistenceCreator` already have a large constructor; adding a 16th parameter increases error risk. The setter approach is safer for `@Data` classes while the record approach is the only option for records.

See ADR-015 (stage node provenance over StageOutput nodes).

When reviewing existing code, the same questions apply in reverse: if a record existed for a coherent reason that has since disappeared (caller refactored away, unused fields accumulated), eliminate it.
