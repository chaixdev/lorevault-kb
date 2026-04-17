# API Documentation

## Live API Documentation

### Auto-Generated OpenAPI Specification

The LoreVault API provides **live, auto-generated documentation** that is always up-to-date:

- **📋 OpenAPI JSON**: `http://localhost:18080/api/docs` - Import into Postman/tools
- **🌐 Interactive Docs**: `http://localhost:18080/api/swagger-ui.html` - Browse and test endpoints
- **📦 SDK Generation**: Use OpenAPI spec for client library generation

**Benefits**:
- ✅ Always synchronized with code changes
- ✅ Complete request/response schemas
- ✅ Ready for Postman import
- ✅ Interactive endpoint testing

### Using the Auto-Generated Documentation

**For API Testing**:
1. Start the LoreVault API (`mvn -pl lorevault-api spring-boot:run`)
2. Import OpenAPI spec into Postman from `http://localhost:18080/api/docs`
3. All endpoints, schemas, and examples are automatically included

**For Development**:
- Browse interactive documentation at `/api/swagger-ui.html`
- Explore endpoint organization by domain tags
- Test requests directly from the browser interface

## Design Philosophy

### API Design Specifications
- **[REST API Design Philosophy](specifications/rest-api-specification.md)** - CQRS patterns, naming conventions, and design principles
- **[Health Endpoint Philosophy](specifications/health-endpoint-specification.md)** - Health monitoring patterns and diagnostics

These guides focus on **consistency principles** for adding new endpoints rather than detailed API specs (which are auto-generated).

## API Evolution

### Current Version: v0.8.3
- **OpenAPI 3.0 Integration**: Auto-generated documentation and Postman collections
- **CQRS Architecture**: Clear command/query separation (`/api/command/*` and `/api/query/*`)
- **RAG Question Answering**: Advanced Q&A with citations and metadata
- **Comprehensive Health Checks**: Hierarchical system diagnostics

### Version History
- **v0.8.0**: RAG question answering with citations
- **v0.7.1**: CQRS restructuring with async job processing
- **v0.7.0**: Initial vector search endpoints

### Future Enhancements
- **v0.9.0**: Spoiler-aware filtering and advanced entity queries
- **v1.0.0**: Production authentication and advanced lore exploration

## Migration from Manual Collections

**Previous Workflow** (Deprecated):
- ❌ Manually maintained Postman collections
- ❌ Static API specifications requiring updates
- ❌ Risk of documentation drift from implementation

**Current Workflow** (Recommended):
- ✅ Import live OpenAPI specification into Postman
- ✅ Always current with latest endpoint changes
- ✅ Complete schemas and validation automatically included

## 🔧 Development Guidelines

### For Contributors Adding New Endpoints

**Step 1: Follow Design Philosophy**
- Review [REST API Design Philosophy](specifications/rest-api-specification.md)
- Use established CQRS patterns and naming conventions
- Add appropriate `@Tag` annotations for OpenAPI organization

**Step 2: Validate Auto-Documentation**
- Ensure endpoint appears in auto-generated docs
- Verify request/response schemas are complete
- Test Postman import from `/api/docs` endpoint

**Step 3: Update Philosophy Guides** 
- Document new patterns in design philosophy guides
- Update principles if introducing new endpoint categories
- Maintain consistency guidelines for future development

### Folder Structure
- **specifications/**: Design philosophy and consistency guides
- **~~collections/~~**: Removed (replaced by auto-generated OpenAPI)

### Cross-Reference Requirements
- **Data models**: Reference canonical data-model or pattern docs when available rather than archived implementation notes
- **Processes**: Reference canonical pattern docs when available rather than archived workflow notes  
- **Architecture**: Reference `../architecture/` for system design
- **Conceptual models**: Reference `../concepts/` when API behavior depends on preserved domain concepts rather than current implementation details
- **Contributor guidance**: Reference `../rules/` for durable conventions rather than restating repo-wide rules locally

This approach ensures API documentation remains accurate, accessible, and maintainable while reducing manual overhead.
