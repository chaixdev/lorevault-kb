# LoreVault Documentation

## � Start Here

New to LoreVault? Begin with these essential documents:

- **[Implementation State Report](IMPLEMENTATION_STATE.md)** — Current feature status, gaps, and progress
- **[Project Summary](project_summary.md)** — Vision, features, and roadmap overview
- **[Release Notes](development/current/RELEASE_NOTES.md)** — Latest changes and version history
- **[Architecture Overview](architecture/README.md)** — System design and technical foundations

## �📁 Documentation Structure

### Core Documentation

- **[Implementation State Report](IMPLEMENTATION_STATE.md)** - Current feature status, what's working, and what's planned
- **[Project Summary](project_summary.md)** - Vision, roadmap, and feature overview

### Architecture & Design

- **[Architecture](architecture/)** - System architecture viewpoints following ISO/IEC/IEEE 42010

### API Documentation

- **[API](api/)** - REST API specifications and Postman collections
  - `specifications/` - OpenAPI specs and endpoint documentation
  - `collections/` - Postman collections for testing

### Development Resources

- **[Development](development/)** - Version-organized development documentation
  - **[Current](development/current/)** - Active system documentation
    - `data-model/` - Current database schemas and entity models
  - `processes/` - Current business process specifications
    - `testing/` - Active testing strategies and patterns
    - `configuration/` - System configuration and setup guides
  - **[Versions](development/versions/)** - Historical milestone development
    - `v0.9.0/` - Scene→Event (Timeline) milestone
      - `research/` - Requirements and model exploration
      - `planning/` - Implementation roadmap and design
      - `implementation/` - Implementation notes and patterns
    - `v0.8.0/` - Foundation development history
      - `research/` - Foundational research and explorations  
      - `planning/` - Roadmaps, proposals, and analysis

## 🔄 Migration Notes

This structure has evolved to improve navigation and separate concerns:

- **v0.7.1**: API-related content consolidated under `api/`; data model specifications separated from API specs; research and planning content organized under `development/`
- **v0.8.1**: Development documentation restructured with versioned organization (v0.8.0/, v0.9.0/)  
- **Latest**: Further refinement with `current/` and `versions/` separation; active documentation in `current/`, historical milestones in `versions/`

## 📖 Navigation

For specific documentation needs:

- **API Development**: Start with `api/specifications/rest-api-specification.md`
- **System Understanding**: Begin with `architecture/README.md`
- **Data Integration**: See `development/current/data-model/neo4j-content-data-model.md`
- **Current Processes**: Reference `development/current/processes/`
- **Testing**: See `development/current/testing/testing-strategy-v2-concise.md`
- **Configuration**: See `development/current/configuration/multi-provider-llm-configuration.md`
- **v0.9.0 Planning**: Browse `development/versions/v0.9.0/planning/`
- **Historical Research**: See `development/versions/v0.8.0/research/`

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Documentation Boundaries

- **Never move files between top-level folders** without explicit user request
- **Maintain folder structure consistency** - each folder has a specific purpose
- **Update cross-references** when moving or renaming files
- **Follow naming conventions**: kebab-case for files, consistent README patterns

### When Adding New Documentation

- **API changes**: Add to `api/specifications/`
- **Data model changes**: Add to `development/current/data-model/` with schemas in `schemas/`
- **Process changes**: Add to `development/current/processes/`
- **Configuration changes**: Add to `development/current/configuration/`
- **Testing updates**: Add to `development/current/testing/`
- **Research/experiments**: Add to `development/versions/{current_version}/research/`
- **Planning documents**: Add to `development/versions/{current_version}/planning/`

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
