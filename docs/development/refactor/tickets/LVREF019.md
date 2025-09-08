# LVREF019: Add Minimal OpenAPI Documentation Support

**Priority**: Low  
**Effort**: 2 hours  
**Risk**: Low  
**Phase**: 4 (Consolidate System Services) - Can run parallel  
**Dependencies**: None

## Problem Statement

The LoreVault API currently lacks OpenAPI documentation, making it difficult for developers to:

- Import API endpoints into Postman for testing
- Understand available endpoints and their request/response formats
- Generate client SDKs or explore the API interactively

## Current State

```java
// REST API endpoints exist but no OpenAPI documentation
@RequestMapping("/api/command/ingest")     // Ingestion endpoints
@RequestMapping("/api/query/jobs")         // Job query endpoints  
@RequestMapping("/api/query/health")       // Health check endpoints
@RequestMapping("/api/command/library")    // Library management
@RequestMapping("/api/query")              // Ask/RAG endpoints
```

**Missing**: OpenAPI 3.0 specification generation and endpoint for Postman integration.

## Target State

```java
// Minimal OpenAPI configuration enabled
@SpringBootApplication
public class LoreVaultApiApplication {
    // OpenAPI metadata configured
    // Automatic endpoint generation at /v3/api-docs
}

// Organized endpoint documentation
@Tag(name = "Ingestion", description = "Content ingestion operations")
@Tag(name = "Health", description = "System health monitoring")  
@Tag(name = "Query", description = "Content query operations")
```

## Implementation Steps

1. Add SpringDoc OpenAPI dependency to project
2. Configure basic OpenAPI metadata (title, version, description)
3. Add controller tags for endpoint organization
4. Configure OpenAPI endpoint paths and behavior
5. Disable Swagger UI for production environments

## Acceptance Criteria

- [x] SpringDoc OpenAPI dependency added to project
- [x] OpenAPI 3.0 specification generated automatically
- [x] API documentation accessible at `/v3/api-docs` endpoint
- [x] Basic API metadata configured (title, version, description)
- [x] Main controller groups tagged for organization
- [x] Postman can import API specification from `/v3/api-docs`
- [x] Swagger UI available for development (optional)
- [x] No impact on application startup time or performance
- [x] Documentation excludes internal/actuator endpoints

## Files to Modify

**Files to UPDATE**:

- `lorevault-api/pom.xml` - Add SpringDoc dependency
- `LoreVaultApiApplication.java` - Add OpenAPI configuration annotations
- `application.yml` - Configure OpenAPI endpoints and behavior
- Main controller classes - Add @Tag annotations for grouping

**Files to CREATE**:

- None (SpringDoc auto-generates OpenAPI specification)

## Testing Strategy

### Manual Testing

1. Start application and verify OpenAPI JSON endpoint responds
2. Test Postman import from OpenAPI specification  
3. Validate all REST endpoints appear in documentation
4. Confirm no application startup errors or performance impact

### Validation Criteria

- OpenAPI JSON validates against OpenAPI 3.0 specification
- All REST endpoints appear in documentation  
- Postman successfully imports and can make requests
- No application startup errors or warnings

## Risk Assessment

**Low Risk** - SpringDoc is non-intrusive and widely adopted.

**Potential Issues**:

- Dependency conflicts (unlikely - clean SpringDoc integration)
- Swagger UI security exposure (mitigated by production disable)
- Documentation accuracy (minimal impact - auto-generated)

**Benefits**:

- Improved developer experience for API testing
- Better API discoverability and documentation
- Foundation for future API client generation
- Zero maintenance overhead (auto-generated)

## Success Criteria

✅ **Primary Goal**: Postman can import API specification and make requests  
✅ **Secondary Goal**: Clean, organized API documentation with minimal effort  
✅ **Technical Goal**: Zero impact on application performance or security