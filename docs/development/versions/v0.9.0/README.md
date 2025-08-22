# v0.9.0: Scene→Event (Timeline) Development

**Status**: Active Development  
**Milestone**: Promote Scenes to first-class Events with temporal relationships

## Phase Overview

### [Research](research/)

Requirements exploration and technical discovery for the Scene→Event transformation:

- **[Scope Definition](research/scope.md)** - Milestone boundaries and acceptance criteria
- **[Event Model](research/event-model.md)** - Entity design with dual labels and temporal relationships
- **[API Design](research/timeline-apis.md)** - Timeline query endpoints and responses
- **[Testing Strategy](research/tests-and-qa.md)** - Quality assurance and validation approach

### [Planning](planning/)

Implementation roadmap and delivery strategy:

- **[Implementation Plan](planning/v0.9.0-scene-to-event-entity-plan.md)** - Complete milestone roadmap with 0.8.x increments

### [Implementation](implementation/)

To be populated during development.

Implementation notes, patterns discovered, and lessons learned will be documented here as development progresses.

## Key Decisions

- **Reingestion-only**: No backfill/rollback tooling during development
- **Dual labels**: `:Event:Scene` nodes preserve existing hierarchy
- **Single edge type**: TEMPORAL relationships with property-based classification
- **Chapter-level spoiler gating**: Timeline queries respect chapter boundaries
- **Incremental delivery**: 0.8.2 → 0.8.6 with clear acceptance criteria

## Development Status

- ✅ **Research Phase**: Requirements and model exploration complete
- ✅ **Planning Phase**: Implementation roadmap defined
- ⏳ **Implementation Phase**: Ready to begin development

## Next Steps

1. Implement 0.8.2: Event entity shell and storage readiness
2. Implement 0.8.3: Dual-write ingestion (Scene→Event)
3. Implement 0.8.4: Skeleton timeline edges with default MEETS relationships
4. Implement 0.8.5: LLM temporal upgrades and neighbors API
5. Implement 0.8.6: Timeline query and GraphRAG natural language querying

Target completion: End of Q3 2025
