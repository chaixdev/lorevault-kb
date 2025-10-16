# LV-090-8 — Implement EmbeddingHandler [refactor]

**Status:** NOT STARTED

## Context

- Event-driven refactor requires independent handlers for each stage
- Embedding generation currently embedded in main orchestrator
- See handler design in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Current embedding logic coupled with chunking stage
- Can't independently retry embedding failures
- Difficult to monitor embedding progress separately

## Proposal

- Create `EmbeddingHandler` listening for `ChunkingCompletedEvent`
- Extract embedding logic to independent handler
- Publish `EmbeddingCompletedEvent` when embeddings generated

## Scope

### Handler Responsibilities

1. Listen for chunking completed events
2. Set job context from event
3. Update job status to embedding
4. Fetch chunks from database
5. Generate embeddings using embedding service
6. Persist embeddings to database
7. Publish embedding completed event
8. Fail job gracefully on errors
9. Clear job context in cleanup

### Files to Create

- `EmbeddingHandler.java` in event handler package

## Out of Scope

- Removing logic from orchestrator (LV-090-11)
- Embedding model/provider changes
- Batch size optimization
- Parallel chunk embedding (sequential for now)

## Technical Notes

### Requirements

- Use existing embedding service with configured provider
- Track embedding API call costs/duration
- Handle rate limiting from embedding API
- Maintain chunk-to-embedding relationships

### Error Handling

- Empty chunks should skip embedding (log warning)
- API failures should be retried per service retry logic
- Fatal errors should fail entire job
- Track failed chunk IDs for debugging

## Acceptance Criteria

- [ ] Handler listens for `ChunkingCompletedEvent` after commit
- [ ] Runs asynchronously using configured thread pool
- [ ] Sets and clears job context properly
- [ ] Updates status to `EMBEDDING`
- [ ] Fetches chunks from database by IDs
- [ ] Calls embedding service for each chunk
- [ ] Persists embeddings with correct chunk relationships
- [ ] Publishes `EmbeddingCompletedEvent` with embedded chunk IDs
- [ ] Fails job on unrecoverable errors
- [ ] Logs embedding count and vector dimension

## Quality Gates

- [ ] Build passes
- [ ] Unit tests pass with mocked dependencies
- [ ] Integration test verifies embedding
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations

## Testing Strategy

### Unit Tests

- Verify successful embedding flow
- Verify chunk fetching by IDs
- Verify embedding service called for each chunk
- Verify embeddings persisted with relationships
- Verify event published with correct chunk IDs
- Verify context lifecycle
- Verify error handling

### Integration Tests

- Verify event triggers embedding generation
- Verify embeddings persisted to Neo4j
- Verify vector dimensions correct
- Verify job status updates
- Use test embedding provider (not real API calls)

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-3 (events), LV-090-4 (context), LV-090-7 (previous handler)
- **Blocks:** LV-090-10 (cutover)

---

**Estimated Effort:** 3-4 hours  
**Dependencies:** LV-090-3, LV-090-4, LV-090-7  
**Blocks:** LV-090-10
