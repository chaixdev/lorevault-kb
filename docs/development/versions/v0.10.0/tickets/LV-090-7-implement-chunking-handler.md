# LV-090-7 — Implement ChunkingHandler [refactor]

**Status:** NOT STARTED

## Context

- Event-driven refactor requires independent handlers for each stage
- Text chunking currently embedded in main orchestrator
- See handler design in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Current chunking logic coupled with embedding stage
- Can't independently monitor chunking progress
- Difficult to retry chunking failures without re-running entire pipeline

## Proposal

- Create `ChunkingHandler` listening for `TriadAnalysisCompletedEvent`
- Extract chunking logic to independent handler
- Publish `ChunkingCompletedEvent` with chunk IDs when complete

## Scope

### Handler Responsibilities

1. Listen for triad analysis completed events
2. Set job context from event
3. Update job status to chunking
4. Fetch scenes from database
5. Chunk scene text using chunking service
6. Persist chunks to database
7. Publish chunking completed event with chunk IDs
8. Fail job gracefully on errors
9. Clear job context in cleanup

### Files to Create

- `ChunkingHandler.java` in event handler package

## Out of Scope

- Removing logic from orchestrator (LV-090-11)
- Chunking algorithm changes
- Chunk overlap/size configuration changes
- Parallel scene chunking (sequential for now)

## Technical Notes

### Requirements

- Fetch scenes in correct order (preserve chapter flow)
- Use existing text chunking service
- Track chunk counts for observability
- Maintain scene-to-chunks relationships

### Error Handling

- Empty scenes should skip chunking (log warning)
- Chunking failures should fail entire job
- Partial failures should be logged with scene context

## Acceptance Criteria

- [ ] Handler listens for `TriadAnalysisCompletedEvent` after commit
- [ ] Runs asynchronously using configured thread pool
- [ ] Sets and clears job context properly
- [ ] Updates status to `CHUNKING`
- [ ] Fetches scenes from database by IDs
- [ ] Calls text chunking service for each scene
- [ ] Persists chunks with correct scene relationships
- [ ] Publishes `ChunkingCompletedEvent` with chunk IDs
- [ ] Fails job on errors
- [ ] Logs total chunk count on completion

## Quality Gates

- [ ] Build passes
- [ ] Unit tests pass with mocked dependencies
- [ ] Integration test verifies chunking
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations

## Testing Strategy

### Unit Tests

- Verify successful chunking flow
- Verify scene fetching and ordering
- Verify chunking service called for each scene
- Verify chunks persisted with scene relationships
- Verify event published with chunk IDs
- Verify context lifecycle
- Verify error handling

### Integration Tests

- Verify event triggers chunking
- Verify chunks persisted to Neo4j with correct relationships
- Verify job status updates
- Verify chunk count matches expectations

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-3 (events), LV-090-4 (context), LV-090-6 (previous handler)
- **Blocks:** LV-090-10 (cutover)

---

**Estimated Effort:** 3-4 hours  
**Dependencies:** LV-090-3, LV-090-4, LV-090-6  
**Blocks:** LV-090-10
