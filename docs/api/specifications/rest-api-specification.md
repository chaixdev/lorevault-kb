# LoreVault REST API Design Philosophy

**Purpose**: Core design principles for consistent endpoint development. For current API documentation, see `/api/docs`.

## Current API Structure

### Command Endpoints (`/api/command/`)

**Ingestion Commands**
- `POST /api/command/ingest` - Submit content files for processing (replaces legacy `/api/v1/chapters`)
  - Request: Multipart form with content files and metadata
  - Response: `202 Accepted` with `jobId` for tracking
  - Supports chapter, scene, and bulk content ingestion

**Library Management Commands**  
- `POST /api/command/library/create-universe` - Create universe hierarchy node
- `POST /api/command/library/create-series` - Create series within universe
- `POST /api/command/library/create-book` - Create book within series

### Query Endpoints (`/api/query/`)

**Job Management Queries**
- `GET /api/query/jobs` - List ingestion jobs with filtering support
  - Query parameters: `status`, `limit`, `offset`, `universeId`, `seriesId`
- `GET /api/query/jobs/{id}` - Get specific job status and detailed progress

**Search & Q&A Queries**
- `POST /api/query/ask/vector` - Semantic search over chunk content
  - Natural language queries with vector similarity matching
  - Returns ranked chunks with relevance scores
- `POST /api/query/ask/rag` - RAG-based question answering  
  - Intelligent answers with source attribution and citations
  - Leverages semantic search for context retrieval

**System Health Queries**
- `GET /api/query/health` - System health diagnostics and component status
- `GET /api/query/health/llm` - LLM service connectivity and model status

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
