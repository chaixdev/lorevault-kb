# LoreVault Specifications

This directory contains detailed technical specifications that bridge the gap between high-level architecture documentation and implementation.

## Core Specifications

### Data Model & Storage
- **[Core Data Model](core-data-model.md)**: Hierarchical model for chapters, scenes, and chunks
- **[Text Chunking Specification](text-chunking-specification.md)**: Rolling window algorithm for breaking down content into overlapping segments

### AI & Intelligence
- **[Scene Detection Specification](scene-detection-specification.md)**: XML-based AI scene boundary detection with two-stage processing

### Process & Workflow
- **[Content Ingestion Process](content-ingestion-process.md)**: Complete pipeline from HTTP request to entity storage
- **[Ingestion Job Lifecycle](ingestion-job-lifecycle.md)**: Job tracking and status management with event-sourcing pattern

### API & Integration
- **[REST API Specification](rest-api-specification.md)**: HTTP endpoints, request/response formats, and error handling
- **[API Structure Specification](api-structure-specification.md)**: Service layer organization and component interaction
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
│ content-ingestion-process.md            │
│ ingestion-job-lifecycle.md              │
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
│ core-data-model.md                      │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Interface Layer              │
├─────────────────────────────────────────┤
│ rest-api-specification.md               │
│ api-structure-specification.md          │
│ health-endpoint-specification.md        │
└─────────────────────────────────────────┘
```

## Version History

- **v0.2.0**: Core content storage and segmentation specifications
- **v0.3.0**: ✅ Scene detection and hierarchical structure implementation
- **v0.4.0+**: Future AI enhancement and vector embeddings
