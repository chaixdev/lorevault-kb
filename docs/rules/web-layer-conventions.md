# Web Layer Conventions

**Status:** Active  
**Scope:** LoreVault REST API and UI controller layer

## Core Rules

### Controllers must not inject repositories or data-access beans

Web controllers are pure delegation facades. They must not inject or call
`*Repository`, `Neo4jClient`, `Neo4jTemplate`, `JdbcTemplate`, or any
other data-access bean directly. All data access goes through the service
layer — the web boundary translates HTTP concerns to service calls and
maps service results to HTTP responses.

**Why:** Controller-level repository injection is a layering violation that
creates TOCTOU race conditions (the controller checks existence outside any
transaction, then calls a service that assumes the check result still holds).
It also couples the web layer to persistence technology — if the repository
interface changes, every controller injecting it must be updated.

```java
// Wrong — controller injects repository for existence check
if (chapterGraphRepository.findById(chapterUuid).isEmpty()) {
    return ResponseEntity.notFound().build();
}
StageResult result = chapterOperation.execute(ctx);

// Correct — let the service/operation handle the not-found case
StageResult result = chapterOperation.execute(ctx);
// The service returns a failure result; the controller maps it to 404 or 200
```

### UI controllers and API controllers must use the same pipeline interfaces

Every consolidation or pipeline operation exposed through both the REST API
and the UI must go through the same `StageOperation` or `IngestionService`
beans. UI controllers must not inject domain services directly (e.g.,
`ChapterIndividualConsolidationService`) and construct `StageExecutionContext`
manually.

**Why:** The pipeline manages stage lifecycle, job tracking, `stageId`
provenance (for cleanup and replay), and event publication. Bypassing the
pipeline produces entities with no `stageId`, untracked jobs, and missing
completion events — making cleanup and replay silently broken.

```java
// Wrong — UI controller injects consolidation service directly
private final ChapterIndividualConsolidationService consolidationService;
StageExecutionContext ctx = new StageExecutionContext(
    UUID.randomUUID(), UUID.randomUUID(), chapterId, null, stage);
consolidationService.consolidate(ctx, chapterId);

// Correct — delegate to the same StageOperation the API uses
private final StageOperation chapterIndividualOperation;
StageResult result = chapterIndividualOperation.execute(ctx);
```

### All controllers must use structured `ErrorResponse` for errors

Every controller method that returns an error must use the project's
structured `ErrorResponse` pattern — a builder that produces a body with
`code`, `message`, `details`, `timestamp`, and `path`. Empty response
bodies on error (`ResponseEntity.badRequest().build()`) or plain strings
are not acceptable.

REST API and UI controllers must agree on HTTP status codes for equivalent
failure modes (e.g., entity-not-found is 404 in both, not 500 in the UI
while being 404 in the API).

```java
// Wrong — empty body, client has no diagnostic information
return ResponseEntity.badRequest().build();

// Correct
return ResponseEntity.badRequest().body(ErrorResponse.builder()
    .code("INVALID_CHAPTER_ID")
    .message("Chapter ID must be a valid UUID")
    .timestamp(LocalDateTime.now())
    .path("/api/command/ingest/chapters/" + chapterId + "/detect-scenes")
    .build());
```

### Controllers must not contain business logic

Controllers translate HTTP concerns to service calls. They must not contain:
- Conditional logic based on operation outcome beyond success/failure mapping
- Event publishing decisions (the `fireEvents` flag should be passed to the
  service; the service decides whether and how to publish)
- Entity existence checks (delegate to the service)
- Data transformation beyond format conversion (HTTP → domain DTO)

```java
// Wrong — controller decides event publishing logic
if (fireEvents && result.success()) {
    stepEventMapper.publishCompletionEvent(stage, jobId, chapterId, result);
}

// Correct — pass fireEvents to the service; service owns the decision
StageResult result = stageOperation.execute(ctx, fireEvents);
// controller maps result to HTTP response only
```
