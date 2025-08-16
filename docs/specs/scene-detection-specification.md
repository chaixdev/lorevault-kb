# Scene Detection Specification

**Version**: v0.3.1  
**Status**: ✅ IMPLEMENTED  
**Updated**: 2025-08-08

## Purpose

Defines the AI-powered scene detection system that analyzes narrative text to identify semantic scene boundaries and creates a hierarchical Chapter → Scene → Chunk structure. The system uses external LLM APIs with XML-based structured output for reliable parsing of prose content containing dialogue and complex narrative elements.

## Scope

**In Scope:**
- AI-powered semantic scene boundary detection
- XML-based LLM response parsing for reliability with prose content
- Two-stage processing: AI identification + precise coordinate localization
- Retry mechanisms and error handling for external API calls
- Integration with the Chapter aggregate root pattern

**Out of Scope:**
- Entity extraction and character/location identification (future versions)
- Real-time scene detection during ingestion (current implementation is batch-based)
- Manual scene boundary editing interfaces

## Key Dependencies

- External Services: Spring AI ChatClient with manual configuration targeting nlp-small slot for scene analysis
- Core Components: PromptLoaderService, SceneDetectionService, ChapterRepository
- Data Model: Chapter aggregate, Scene entity
- Configuration: Manual Spring AI bean configuration with three-slot model structure (embedding, nlp-small, nlp-big)

## Overview

Two-stage approach separates AI analysis from precise text coordinate calculation:

1. Stage 1 — AI Analysis: External LLM analyzes chapter text and identifies scene boundaries using start/end snippets.
2. Stage 2 — Coordinate Localization: Java code locates exact character positions of the identified snippets.

## Architecture

### Component Overview

```
SceneDetectionService
├── Stage 1: AI Scene Identification
│   ├── PromptLoaderService (XML template)
│   ├── ChatClient (Spring AI)
│   └── XML Response Parser
└── Stage 2: Coordinate Localization
    ├── Snippet Pattern Matching
    └── Scene Entity Creation
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| XML Output Format | More reliable than JSON for prose content with dialogue, quotes, and special characters |
| Two-Stage Processing | Separates expensive AI calls from deterministic coordinate calculation |
| Snippet-Based Identification | Allows AI to identify scenes without requiring exact character positions |
| Retry Mechanisms | Handles transient failures from external AI services |
| Regex-Based XML Parsing | Lightweight parsing with CDATA support |

## Scene Detection Process

### Stage 1: AI Scene Identification

The LLM receives a structured prompt with the chapter text and returns XML describing detected scenes.

Output schema:
```xml
<scenes>
  <scene>
    <scene_index>1</scene_index>
    <context_summary>Brief description of the scene</context_summary>
    <start_snippet>First few words of the scene</start_snippet>
    <end_snippet>Last few words of the scene</end_snippet>
  </scene>
</scenes>
```

### Stage 2: Coordinate Localization

1. Pattern matching: locate start_snippet and end_snippet in chapter text
2. Validation: ensure snippets are found and in correct order
3. Scene creation: create Scene entities with precise coordinates
4. Aggregate update: add scenes to Chapter aggregate root

## XML Response Parsing

- Extract <scene> elements with DOTALL regex
- Handle CDATA sections within text fields
- Whitespace normalization and markdown wrapper tolerance
- Graceful degradation on invalid XML or missing fields

## Retry and Resilience

Application-level retry with exponential backoff (e.g., 1s, 2s, 4s) and Spring AI retry configuration.

Example properties:
```properties
spring.ai.retry.max-attempts=3
spring.ai.retry.backoff.initial-delay=1s
spring.ai.retry.backoff.multiplier=2
spring.ai.retry.backoff.max-delay=10s
logging.level.com.lorevault.api.service.SceneDetectionService=TRACE
logging.level.com.lorevault.api.service.LlmHealthCheckService=TRACE
```

## Integration with Core Data Model

- Chapter (Aggregate Root) → Scene → Chunk
- Bidirectional JPA relationships; position coordinates stored on entities

## Prompt Engineering

Template uses {{chapterText}} substitution with explicit XML schema and examples. Delimiters chosen to avoid XML conflicts.

## Testing Strategy

- Unit tests for XML parsing and coordinate localization
- Error handling and fallback behavior
- Integration with Chapter aggregate

## Configuration

Environment variables and properties required for Spring AI ChatClient and model access (provider-specific keys configured separately).

## Performance Considerations

- Two-stage approach minimizes external API usage
- Batch per chapter; coordinate localization < 100ms for typical inputs

## Implementation Status

- XML-based scene detection prompt template
- Regex-based XML response parser
- Two-stage processing architecture
- Retry mechanisms with exponential backoff
- Integration with Chapter aggregate
- Unit tests and logging

Related specifications: Core Data Model, Content Ingestion Process, REST API (health endpoints).
