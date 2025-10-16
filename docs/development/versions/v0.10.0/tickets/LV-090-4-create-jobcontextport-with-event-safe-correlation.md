# LV-090-4 — Create JobContextPort with Event-Safe Correlation [refactor]

**Status:** NOT STARTED

## Context

- Current `ThreadLocalJobContextAdapter` uses `ThreadLocal` for job correlation
- Fragile across async boundaries (`@Async` handlers lose context)
- Event-driven architecture needs reliable job context propagation
- See context propagation analysis in: `../../refactor/event-driven-ingestion-refactor-v0100.md`

## Problem

- `ThreadLocal` values don't propagate to new threads spawned by `@Async`
- Manual `setContext()`/`clearContext()` calls error-prone
- No validation that context is set before port calls
- Potential context leaks if `clearContext()` not called (thread pool reuse)

## Proposal

- Create new `JobContextPort` interface with event-aware correlation
- Implement `EventJobContextAdapter` that extracts context from events
- Replace `ThreadLocalJobContextAdapter` usage in ports
- Deprecate old implementation (don't remove yet for backward compatibility)

## Scope

## Technical Notes

## Technical Notes

### Port Interface Requirements

The port should provide:
- Method to set context from event data (job ID, chapter ID, publication coordinates)
- Methods to retrieve current job ID and chapter ID
- Method to retrieve publication coordinates
- Method to clear context (for cleanup)
- Method to check if context is set (for validation)
- Should throw exception if accessed without being set

### Implementation Requirements

- Thread-safe context storage
- Validation that context is set before access
- Clear error messages when context not available
- Logging for context lifecycle (set/clear)
- Structured context data using value objects

### Value Object Requirements

- `JobContext`: Encapsulates job ID, chapter ID, and coordinates
- `PublicationCoordinates`: Encapsulates publication ID and volume ID
- Both should be immutable with validation

### Port Integration

Ports that currently use context should be updated to use new interface:
- Content persistence port
- Scene detection port
- Embedding port

### Deprecation Strategy

- Mark existing `ThreadLocalJobContextAdapter` as deprecated
- Keep it for backward compatibility during migration
- Remove in v0.11.0 after full cutover

## Out of Scope

- Event handlers (LV-090-5 through LV-090-9) - they will use new port
- Removing `ThreadLocalJobContextAdapter` (keep for backward compatibility during migration)
- Distributed tracing integration (Sleuth/Brave) - future enhancement
- MDC (Mapped Diagnostic Context) integration - consider for LV-090-14

## Technical Notes

## Testing Strategy

### Unit Tests

- Verify context can be set and retrieved correctly
- Verify exception thrown when context not set
- Verify clear removes context properly
- Verify thread isolation (context in one thread doesn't affect another)
- Verify value objects validate required fields

### Integration Tests

- Update existing port integration tests to set context before port calls
- Verify ports fail with clear error if context not set

## Links

- **Planning:** `../../refactor/event-driven-ingestion-refactor-v0100.md`
- **Architecture:** `../../refactor/ARCHITECT-RECOMMENDATIONS.md`
- **Depends On:** None (can implement independently)
- **Blocks:** LV-090-5 through LV-090-9 (handlers need context port)

---

**Estimated Effort:** 3-4 hours  
**Dependencies:** None  
**Blocks:** LV-090-5, LV-090-6, LV-090-7, LV-090-8, LV-090-9

