# API Documentation

## REST API Specifications

### Current API (v0.7.1+)
- **[REST API Specification](specifications/rest-api-specification.md)** - Complete CQRS API documentation
- **[Health Endpoint Specification](specifications/health-endpoint-specification.md)** - System health monitoring

## Postman Collections

### Testing Collections
- **[LoreVault API v0.8.0 CQRS](collections/LoreVault-API-v0.8.0-CQRS.postman_collection.json)** - Comprehensive CQRS endpoint testing
- **[Legacy Collection](collections/LoreVault_API_Collection.postman_collection.json)** - Pre-CQRS collection (deprecated)

## API Evolution

- **v0.7.0**: Initial vector search endpoints
- **v0.7.1**: CQRS restructuring (`/api/command/*` and `/api/query/*`)
- **v0.8.0**: RAG question answering with citations (in development)

Import the Postman collections to get started with API testing immediately.

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Folder Boundaries
- **specifications/**: Only REST API endpoint documentation and OpenAPI specs
- **collections/**: Only Postman collections and testing artifacts
- **Do not add**: Data models (go to `../data-model/`), process specs (go to `../processes/`)

### When Adding API Documentation
- **New endpoints**: Update `specifications/rest-api-specification.md`
- **New collections**: Add to `collections/` with descriptive naming
- **Breaking changes**: Document migration notes in the main spec

### Cross-Reference Requirements
- **Link to data models**: Use `../data-model/` paths
- **Reference processes**: Use `../processes/` paths  
- **Architecture context**: Use `../architecture/` paths

### Naming Conventions
- Collections: `ProjectName-API-vX.Y.Z-Description.postman_collection.json`
- Specifications: Use kebab-case with `.md` extension
- Always include version information for collections
