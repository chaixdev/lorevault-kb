# LV-090-6 — Implement TriadAnalysisHandler [refactor]

**Status:** NOT STARTED

## Context

- Event-driven refactor requires independent handlers for each stage
- Triad analysis currently embedded in scene detection service
- Bug exists: triad analysis runs before scenes persisted
- See handler design in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- Current architecture runs triad analysis before scenes are persisted (ordering bug)
- Triad logic coupled with scene detection retry logic
- Can't independently monitor or retry triad analysis stage

## Proposal

- Create `TriadAnalysisHandler` listening for `SceneDetectionCompletedEvent`
- Extract triad orchestration logic to independent handler
- Ensure scenes are persisted BEFORE triad analysis begins
- Publish `TriadAnalysisCompletedEvent` when complete

## Scope

### Handler Responsibilities

1. Listen for scene detection completed events
2. Set job context from event
3. Update job status to triad analysis in progress
4. Perform triad analysis for each scene (character, location, event extraction)
5. Persist triad results to database
6. Publish triad analysis completed event
7. Fail job gracefully on errors
8. Clear job context in cleanup

### Files to Create

- `TriadAnalysisHandler.java` in event handler package

## Out of Scope

- Removing logic from existing services (LV-090-11)
- Retry logic changes (use existing retry-aware service)
- Triad data model changes
- Parallel scene analysis (sequential for now)

## Technical Notes

### Key Requirements

- Must fetch persisted scenes from database (not rely on in-memory state)
- Use existing triad orchestration service via port
- Update job status before and after analysis
- Handle partial failures (some scenes succeed, others fail)

### Error Handling

- LLM failures should be retried by existing retry logic
- Fatal errors should fail entire job
- Log all triad analysis calls for audit trail

## Acceptance Criteria

- [ ] Handler listens for `SceneDetectionCompletedEvent` after transaction commit
- [ ] Runs asynchronously using configured thread pool
- [ ] Sets and clears job context properly
- [ ] Updates status to `SCENE_TRIAD_ANALYSIS`
- [ ] Fetches scenes from database by IDs from event
- [ ] Calls triad analysis for each scene
- [ ] Persists triad results (characters, locations, events)
- [ ] Publishes `TriadAnalysisCompletedEvent` with scene IDs
- [ ] Fails job on unrecoverable errors
- [ ] Logs INFO for start/completion, ERROR for failures

## Quality Gates

- [ ] Build passes
- [ ] Unit tests pass with mocked dependencies
- [ ] Integration test verifies event handling
- [ ] JaCoCo coverage >85%
- [ ] No Checkstyle/SpotBugs violations

## Testing Strategy

### Unit Tests

- Verify successful triad analysis flow
- Verify scene fetching by IDs
- Verify triad service called for each scene
- Verify event published with correct scene IDs
- Verify context lifecycle (set/clear)
- Verify error handling fails job appropriately

### Integration Tests

- Verify event triggers triad analysis
- Verify triad data persisted to Neo4j
- Verify job status progression
- Verify async completion

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** LV-090-3 (events), LV-090-4 (context port), LV-090-5 (previous handler)
- **Blocks:** LV-090-10 (cutover)

---

**Estimated Effort:** 3-4 hours  
**Dependencies:** LV-090-3, LV-090-4, LV-090-5  
**Blocks:** LV-090-10
