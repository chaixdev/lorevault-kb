# LoreVault Documentation

## 📁 Documentation Structure

### Core Documentation
- **[Project Summary](project_summary.md)** - Vision, roadmap, and feature overview
- **[Multi-Provider LLM Configuration](multi-provider-llm-configuration.md)** - AI provider setup

### Architecture & Design
- **[Architecture](architecture/)** - System architecture viewpoints following ISO/IEC/IEEE 42010
- **[Data Model](data-model/)** - Database schemas, entity models, and relationships
- **[Processes](processes/)** - Core business process specifications

### API Documentation  
- **[API](api/)** - REST API specifications and Postman collections
  - `specifications/` - OpenAPI specs and endpoint documentation
  - `collections/` - Postman collections for testing

### Development Resources
- **[Development](development/)** - Testing strategies, guidelines, and research
  - `planning/` - Roadmaps, proposals, and analysis
  - `research/` - Experiments, prototypes, and explorations

### Visual Documentation
- **[Diagrams](diagrams/)** - System diagrams and visual documentation

## 🔄 Migration Notes

This structure was reorganized in v0.7.1 to improve navigation and separate concerns:
- API-related content consolidated under `api/`
- Data model specifications separated from API specs
- Research and planning content organized under `development/`
- Eliminated duplicate schema files and temp directories

## 📖 Navigation

For specific documentation needs:
- **API Development**: Start with `api/specifications/rest-api-specification.md`
- **System Understanding**: Begin with `architecture/README.md` 
- **Data Integration**: See `data-model/neo4j-content-data-model.md`
- **Testing**: Reference `development/testing-strategy.md`
- **Future Planning**: Browse `development/planning/`

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Documentation Boundaries
- **Never move files between top-level folders** without explicit user request
- **Maintain folder structure consistency** - each folder has a specific purpose
- **Update cross-references** when moving or renaming files
- **Follow naming conventions**: kebab-case for files, consistent README patterns

### When Adding New Documentation
- **API changes**: Add to `api/specifications/` 
- **Data model changes**: Add to `data-model/` with schemas in `schemas/`
- **Process changes**: Add to `processes/`
- **Research/experiments**: Add to `development/research/`
- **Planning documents**: Add to `development/planning/`

### Cross-Reference Updates
- **Always update references** when moving files
- **Check architecture documents** for specification links
- **Update import paths** in code if documentation paths change
- **Verify Postman collections** still reference correct spec files

### Consistency Rules
- All folders must have a README.md explaining their purpose
- Use relative paths for internal documentation links
- Maintain the "Update Instructions" section in all READMEs
- Follow the established folder hierarchy pattern
