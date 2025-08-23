# LV-084-4 — Publication Hierarchy Command Endpoints [technical story]

## Context

You are implementing command endpoints to establish publication hierarchy (Universe/Series/Book) before chapter ingestion, eliminating string-based matching risks identified in LV-084-1.

**Required reading:**

- `/docs/development/versions/v0.9.0/planning/tickets/LV-084-1-default-temporal-edges.md` (dev notes section)
- `/docs/architecture/02-functional-viewpoint.md` (CQRS patterns)
- `/lorevault-api/src/main/java/com/lorevault/api/web/command/` (existing command controller patterns)
- `/lorevault-api/src/main/java/com/lorevault/api/domain/content/` (Book, Series, Universe domain models)

## Problem

Current chapter ingestion relies on string-based PublicationCoordinates matching, creating risk of misspellings fragmenting the knowledge graph. Need explicit publication hierarchy with stable IDs.

## Requirements

### Command Endpoints (DDD/CQRS compliant)

Design commands that express **business intent**, not CRUD operations:

1. **Create Universe** - Register a new fictional universe for content management
2. **Create Series** - Register a series within an existing universe  
3. **Create Book** - Register a book for a universe (optionally within a series)

### Constraints

- **Follow existing patterns:** Study `CommandIngestionController` and `IngestionService`
- **Domain-driven design:** Commands should express business operations, not data manipulation
- **Idempotency:** Safe to retry; return existing IDs if already established
- **Minimal invariants:** Universe names unique globally; series names unique within universe; book titles unique within series
- **No legacy support:** Clean slate implementation - existing coordinate-based ingestion will be replaced

### Technical Implementation

- New command controller following existing `/api/command/` patterns
- New application service handling business logic
- Extend existing `ContentPersistencePort` or create new port for hierarchy operations
- Update `SubmitChapterRequest` to require `bookId` - remove coordinate-based fallback
- Neo4j relationships: `(:Universe)-[:HAS_SERIES]->(:Series)-[:HAS_BOOK]->(:Book)-[:HAS_CHAPTER]->(:Chapter)`

## Deliverables

1. **Command DTOs and responses** following existing request/response patterns
2. **Command controller** with proper validation and error handling
3. **Application service** with business logic and idempotency
4. **Updated persistence layer** (port extensions, Neo4j adapter, repositories)
5. **Integration tests** using Testcontainers pattern from existing command tests
6. **Updated chapter ingestion** to validate `bookId` and return errors for missing books

## Acceptance Criteria

- [ ] Can establish universe, series, and book hierarchy via commands
- [ ] Commands are idempotent (repeated calls return same IDs)
- [ ] Chapter ingestion requires valid `bookId`, returns 400 if not found
- [ ] String-based coordinate matching removed entirely from ingestion flow
- [ ] Integration tests cover happy path and validation scenarios
- [ ] Existing ingestion tests updated to use new bookId-based request format

## Quality Gates

- [ ] Maven test suite passes (`mvn test`)
- [ ] Integration tests with Testcontainers validate end-to-end flow
- [ ] Architecture compliance (`@Tag("architecture")` tests pass)
- [ ] Command endpoints follow established controller patterns and error handling

## Out of Scope

- Book metadata management (authors, publication dates, etc.)
- Complex book relationships or versioning
- Query endpoints for browsing hierarchy (focus on command-side only)
- Backward compatibility with coordinate-based ingestion

## Notes

Keep implementation lean and focused on solving the chapter ingestion ID problem. Avoid over-engineering book domain - just provide stable UUID-based identity for robust chapter linking.
