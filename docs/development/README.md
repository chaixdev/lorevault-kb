# Development Resources

## Configuration & Setup

- **[Multi-Provider LLM Configuration](multi-provider-llm-configuration.md)** - AI provider setup and configuration

## Testing & Quality

- **[Testing Strategy](testing-strategy.md)** - Service-level testing approach with minimal infrastructure
- **[Spec Documentation Guidelines](SPEC_DOCUMENTATION_GUIDELINES.md)** - Standards for technical documentation

## Planning & Analysis

### Roadmap Evolution
- **[HITL Ingestion Proposal](planning/hitl-ingestion-proposal.md)** - Human-in-the-loop workflow design  
- **[HITL QA](planning/hitl-qa.md)** - Interactive question answering proposals
- **[Roadmap Revision v0.4.0-v3.0.0](planning/roadmap-revision-v0.4.0-v3.0.0.md)** - Long-term feature planning

## Research & Experiments

### Data Model Research
- **[Entity-Claims Model](research/entity-claims-model.md)** - Knowledge representation experiments
- **[Core Domain Model](research/core-domain-model-and-graph-process-restructured.md)** - Graph structure analysis
- **[Claims Examples & Schema](research/claims.*)** - Experimental claim formats

### Prototypes & Examples  
- **[Sample Chapter](research/sample_chapter.md)** - Test content for development
- **[Multi-Model Example](research/multi-model-example.yml)** - Configuration patterns
- **[Discussion Notes](research/discussion.md)** - Design decision rationale

## Development Philosophy

Follow the **LLM Development Plan** principles:
1. **Context First**: Read architecture and specs before coding
2. **Test-Driven**: Write service tests before implementation  
3. **Iterative Design**: Present alternatives and collaborate on solutions
4. **Clean Integration**: Leverage existing ports and Spring Boot patterns

## 🔧 Update Instructions

**For LLM Assistants and Contributors:**

### Folder Boundaries
- **Root level**: Core development practices, testing strategies, documentation guidelines
- **planning/**: Strategic documents, roadmaps, and formal proposals
- **research/**: Experiments, prototypes, and exploratory work
- **Do not add**: API specs (go to `../api/`), final data models (go to `../data-model/`)

### When Adding Development Documentation
- **Testing changes**: Update `testing-strategy.md`
- **New guidelines**: Update `SPEC_DOCUMENTATION_GUIDELINES.md`
- **Strategic planning**: Add to `planning/` with clear naming
- **Experiments**: Add to `research/` with date prefixes if temporary

### Content Organization Rules
- **planning/**: Formal documents that influence development direction
- **research/**: Temporary or experimental content that may be archived
- **Root level**: Stable development practices and guidelines

### Cross-Reference Requirements
- **Reference implementations**: Use relative paths to actual code
- **Link to specs**: Use `../api/`, `../data-model/`, `../processes/` as appropriate
- **Architecture context**: Use `../architecture/` for design decisions

### Version Control Practices
- Keep research files for historical context
- Archive obsolete planning documents rather than deleting
- Maintain clear distinction between active vs. historical content
- Use semantic versioning for guideline documents
