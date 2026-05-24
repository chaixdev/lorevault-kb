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

If nine records share `(UUID id, boolean success, int processed, int created, String message)` and differ only by entity-type name (`ChapterIndividualResolutionResult`, `BookObjectResolutionResult`, etc.), the type tag carries no information. The caller knows it called `individualResolutionService` — it doesn't need the return type to remind it.

The test: if you renamed all nine records to `ResolutionResult`, would any caller behavior change? If no, the type tags are noise.

### 3. Is the type consumed by more than one caller?

If a record is created by service A, consumed exactly once by handler B, and immediately repackaged into another type (like `StepResult`), cut the record. Have the service return `StepResult` directly, or return the payload directly.

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

When reviewing existing code, the same questions apply in reverse: if a record existed for a coherent reason that has since disappeared (caller refactored away, unused fields accumulated), eliminate it.
