# LoreVault REST API Design Philosophy

**Purpose**: Core design principles for consistent endpoint development. For current API documentation, see `/api/docs`.

## Current API Structure

### Command Endpoints (`/api/command/`)
- `/api/command/ingest` - Submit content files for processing
- `/api/command/library/create-*` - Create universe/series/book hierarchy

### Query Endpoints (`/api/query/`)
- `/api/query/jobs` - Monitor processing jobs and status
- `/api/query/ask/*` - Q&A operations (vector search, RAG)
- `/api/query/health` - System health and diagnostics

## Core Principles

### CQRS Architecture
- **Commands**: `POST /api/command/{domain}/{action}` - State changes only
- **Queries**: `GET|POST /api/query/{domain}[/{resource}]` - Data retrieval only

### Async Operations
Heavy operations return `202 Accepted` with `jobId` for progress tracking via `/api/query/jobs/{jobId}`.

### Response Patterns
- **Commands**: Always include `jobId`, timestamps, resource identifiers
- **Queries**: Include metadata (pagination, metrics), support filtering
- **Errors**: Structured with `code`, `message`, actionable `details`

### Domain Organization
Endpoints grouped by business capability with consistent patterns per domain.

### Validation Philosophy
- Fail fast at API boundary
- Domain-specific error codes
- Clear validation messages
- Type safety throughout

### Performance Categories
- **Immediate** (< 200ms): Status checks, simple queries
- **Interactive** (< 1s): Complex searches  
- **Background** (async): Processing, heavy computation

## Implementation Checklist

When adding endpoints:
- [ ] Follows CQRS command/query separation
- [ ] Uses appropriate async pattern for heavy operations
- [ ] Includes comprehensive validation and error handling
- [ ] Has `@Tag` annotation for OpenAPI organization
- [ ] Follows domain-specific patterns
- [ ] Supports pagination where applicable

## OpenAPI Integration
All endpoints auto-documented at `/api/docs` with live schemas and examples.
