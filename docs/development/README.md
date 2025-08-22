# Development Documentation

## Structure Overview

Development documentation is organized into current system state and version-specific development cycles:

### Current System Documentation
- **[current/](current/)** - Current system state, configuration, and processes

### Version-Specific Development
- **[versions/](versions/)** - Version-organized development with clear phases
  - **[v0.9.0/](versions/v0.9.0/)** - Scene→Event (Timeline) milestone
  - **[v0.8.0/](versions/v0.8.0/)** - Foundation development history

### Phase Structure (per version)
- **research/** - Requirements exploration, model research, and technical discovery
- **planning/** - Implementation roadmaps, design decisions, and delivery plans  
- **implementation/** - Implementation notes, patterns discovered, and lessons learned

## Current System State

### [Current Documentation](current/)
- **[data-model/](current/data-model/)** - Current database schemas and entity models
- **[processes/](current/processes/)** - Current business process specifications
- **[testing/](current/testing/)** - Current testing strategies and practices
- **[configuration/](current/configuration/)** - System configuration and setup guides

### Configuration & Setup
- **[Multi-Provider LLM Configuration](current/configuration/multi-provider-llm-configuration.md)** - AI provider setup
- **[Release Notes](current/RELEASE_NOTES.md)** - Version history and changes
- **[Releasing Process](RELEASING.md)** - Release procedures and guidelines  
- **[Spec Documentation Guidelines](current/SPEC_DOCUMENTATION_GUIDELINES.md)** - Documentation standards

## Development Philosophy

Follow the **Version-Driven Development** principles:

1. **Research First**: Explore requirements and technical options in research/ folders
2. **Plan Incrementally**: Break down milestones into testable, deliverable increments
3. **Document Implementation**: Capture patterns, decisions, and lessons in implementation/ folders
4. **Maintain Current State**: Keep data-model/ and processes/ as living documentation of current system

## Navigation Guide

### For Current Development (v0.9.0)
- **Understanding scope**: Start with `versions/v0.9.0/research/scope.md`
- **Implementation plan**: See `versions/v0.9.0/planning/v0.9.0-scene-to-event-entity-plan.md`
- **Technical details**: Browse all files in `versions/v0.9.0/research/`

### For Historical Context
- **Previous research**: See `versions/v0.8.0/research/` for foundational explorations
- **Testing evolution**: See `versions/v0.8.0/testing/` for testing strategy development

### For Current System Understanding
- **Data structures**: See `current/data-model/neo4j-content-data-model.md`
- **Business processes**: Browse `current/processes/` for current specifications
- **Testing practices**: See `current/testing/` for current strategies

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Version Boundaries
- **Active development**: Add to current milestone folder (v0.9.0)
- **Historical preservation**: Never delete previous version folders
- **Current state**: Update data-model/ and processes/ as system evolves

### Phase Guidelines
- **research/**: Requirements, models, API proposals, open questions
- **planning/**: Roadmaps, implementation plans, delivery schedules
- **implementation/**: Code patterns, deployment notes, post-implementation analysis

### When Adding Documentation
- **New research**: Add to current version research/ folder
- **Implementation plans**: Add to current version planning/ folder
- **System changes**: Update current state folders (data-model/, processes/)
- **Configuration**: Update root-level files

### Cross-Reference Rules
- **Within version**: Use relative paths within version folders
- **Cross-version**: Use absolute paths from development/ root
- **External docs**: Use `../api/`, `../architecture/` as appropriate

### Version Migration
- **New milestone**: Create new version folder with research/planning/implementation structure
- **Archive previous**: Leave previous version folders intact for historical reference
- **Update current**: Move data-model/ and processes/ content as system evolves
