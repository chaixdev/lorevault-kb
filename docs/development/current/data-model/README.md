# Data## References

- [Neo4j Graph Database](https://neo4j.com/) - Primary graph storage technology
- [Content Hierarchy Integration](content-hierarchy-integration.md) - Publication coordinate materialization
- [Spring Data Neo4j](https://spring.io/projects/spring-data-neo4j) - OGM framework used

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Folder Boundaries
- **Root level**: High-level data model documentation and entity relationships
- **schemas/**: JSON schemas, Cypher scripts, and formal data definitions
- **Do not add**: API specs (go to `../api/`), processing logic (go to `../processes/`)

### When Adding Data Model Documentation
- **Entity changes**: Update `neo4j-content-data-model.md`
- **New schemas**: Add to `schemas/` folder with clear naming
- **Hierarchy changes**: Update `content-hierarchy-integration.md`
- **Migration scripts**: Add to `schemas/` with version prefix

### Cross-Reference Requirements
- **Link to API specs**: Use `../api/specifications/` paths
- **Reference processes**: Use `../processes/` paths
- **Architecture context**: Use `../architecture/` paths

### Schema Management
- Use semantic versioning for schema files (v1.0.0, v1.1.0)
- Document breaking changes in migration scripts
- Keep examples current with actual implementation
- Validate JSON schemas against actual dataModel Documentation

## Core Data Specifications

### Database Design
- **[Neo4j Content Data Model](neo4j-content-data-model.md)** - Graph database schema and relationships
- **[Content Hierarchy Integration](content-hierarchy-integration.md)** - Publication coordinates and hierarchy
- **[LLM Call Records](llm-call-records.md)** - Concise reference for LLM request/response logging nodes

### Schema Definitions

- **[Claims Schema](schemas/claims.schema.json)** - JSON schema for knowledge claims (future feature)
- **[Claims Examples](schemas/claims.examples.json)** - Example claim structures

## Data Architecture

The LoreVault data model centers around a hierarchical content structure:

- **Universe** → **Series** → **Book** → **Chapter** → **Scene** → **Chunk**

Each level maintains publication coordinates for spoiler-aware retrieval and supports vector embeddings for semantic search.

## Integration Points

- **Ingestion**: Content flows from file upload through chunking to graph storage
- **Retrieval**: Semantic search operates on chunk embeddings with hierarchy context
- **QA**: RAG system retrieves relevant chunks and synthesizes answers with source attribution
