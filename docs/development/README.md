# Development Documentation

## Structure Overview

Development documentation is organized by version with clear phases for each development cycle:

### Version Folders
- **[v0.9.0/](v0.9.0/)** - Scene→Event (Timeline) milestone
- **[v0.8.0/](v0.8.0/)** - Current version development history

### Phase Structure (per version)
- **research/** - Requirements exploration, model research, and technical discovery
- **planning/** - Implementation roadmaps, design decisions, and delivery plans  
- **implementation/** - Implementation notes, patterns discovered, and lessons learned

### Current State Documentation
- **[data-model/](data-model/)** - Current database schemas and entity models
- **[processes/](processes/)** - Current business process specifications

## Configuration & Setup

- **[Multi-Provider LLM Configuration](multi-provider-llm-configuration.md)** - AI provider setup and configuration
- **[Release Notes](RELEASE_NOTES.md)** - Version history and changes
- **[Releasing Process](../.github/instructions/RELEASING.md)** - Release procedures and guidelines
- **[Spec Documentation Guidelines](SPEC_DOCUMENTATION_GUIDELINES.md)** - Standards for technical documentation

## Development Philosophy

Follow the **Version-Driven Development** principles:

1. **Research First**: Explore requirements and technical options in research/ folders
2. **Plan Incrementally**: Break down milestones into testable, deliverable increments
3. **Document Implementation**: Capture patterns, decisions, and lessons in implementation/ folders
4. **Maintain Current State**: Keep data-model/ and processes/ as living documentation of current system

## Navigation Guide

### For Current Development (v0.9.0)
- **Understanding scope**: Start with `v0.9.0/research/scope.md`
- **Implementation plan**: See `v0.9.0/planning/v0.9.0-scene-to-event-entity-plan.md`
- **Technical details**: Browse all files in `v0.9.0/research/`

### For Historical Context
- **Previous research**: See `v0.8.0/research/` for foundational explorations
- **Testing evolution**: See `v0.8.0/testing/` for testing strategy development

### For Current System Understanding
- **Data structures**: See `data-model/neo4j-content-data-model.md`
- **Business processes**: Browse `processes/` for current specifications

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
