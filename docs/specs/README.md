# LoreVault Specifications

This directory contains detailed technical specifications that bridge the gap between high-level architecture documentation and implementation.

## Core Specifications

### Data Model & Storage
- **[Neo4j Content Data Model](neo4j-content-data-model.md)**: Hierarchical model for chapters, scenes, and chunks
- **[Text Chunking Specification](text-chunking-specification.md)**: Rolling window algorithm for breaking down content into overlapping segments
- **[Content Hierarchy Integration](content-hierarchy-integration.md)**: Integration patterns and performance optimizations for publication hierarchies

### AI & Intelligence
- **[Scene Detection Specification](scene-detection-specification.md)**: XML-based AI scene boundary detection with two-stage processing
- **[Spoiler-Aware Retrieval Process](spoiler-aware-retrieval-process.md)**: Query processing workflows with spoiler protection

### API & Integration
- **[REST API Specification](rest-api-specification.md)**: HTTP endpoints, request/response formats, and error handling
- **[Health Endpoint Specification](health-endpoint-specification.md)**: Service health monitoring and LLM connectivity validation

### Quality & Testing
- **[Testing Strategy](testing-strategy.md)**: Comprehensive testing approach including unit, integration, and contract testing

## Documentation Guidelines

See **[SPEC_DOCUMENTATION_GUIDELINES.md](SPEC_DOCUMENTATION_GUIDELINES.md)** for standards and templates for creating new specifications.

## Specification Relationships

```
┌─────────────────────────────────────────┐
│            Process Layer                │
├─────────────────────────────────────────┤
│ spoiler-aware-retrieval-process.md      │
│ content-hierarchy-integration.md        │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         AI & Algorithm Layer            │
├─────────────────────────────────────────┤
│ scene-detection-specification.md        │
│ text-chunking-specification.md          │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Data Layer                   │
├─────────────────────────────────────────┤
│ neo4j-content-data-model.md             │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Interface Layer              │
├─────────────────────────────────────────┤
│ rest-api-specification.md               │
│ health-endpoint-specification.md        │
└─────────────────────────────────────────┘
```

## Version History

- **v0.2.0**: Core content storage and segmentation specifications
- **v0.3.0**: ✅ Scene detection and hierarchical structure implementation
- **v0.4.0**: ✅ Clean three-slot LLM configuration (embedding, nlp-small, nlp-big) with manual Spring AI beans
- **v0.5.0+**: Future enhancements and semantic search capabilities
