# Functional Viewpoint (v0.7.0 Current State)

**Stakeholders:** Developers, architects, testers  
**Concerns:** Implemented system functionality, component responsibilities, interfaces, deferred roadmap items

## Scope Clarification
Current release (v0.7.0) delivers: chapter ingestion, scene detection, text chunking, embedding generation, semantic search, ingestion job + status tracking, and graph persistence in Neo4j.  
Deferred to v0.8.0+: knowledge entity extraction, RAG-based question answering, hybrid AI orchestration, CQRS read model specialization.

## Overview
The functional architecture provides chapter ingestion with hierarchical decomposition and semantic search capabilities. A synchronous REST submission triggers creation of an ingestion job; background processing performs scene detection, chunking, and embedding generation, persisting results as graph nodes. Semantic search endpoints enable natural language queries over chunk content using vector similarity.

## Implemented Components

### API Layer
- REST Controllers
  - POST /api/v1/chapters : submit chapter content (idempotent via contentHash)
  - GET  /api/v1/chapters/{id} : fetch stored chapter (basic)
  - GET  /api/v1/ingestion/jobs/{id}/status : job + recent status records
  - POST /api/search/semantic : semantic search over chunk content using natural language queries
  - GET  /api/search/semantic/status : availability status for semantic search functionality

### IngestionService
- Orchestrates chapter submission workflow
- Creates or reuses Chapter (dedupe via contentHash uniqueness constraint)
- Creates IngestionJob + initial StatusRecord (QUEUED → PROCESSING → COMPLETED / FAILED)
- Invokes SceneDetectionService then chunking logic; persists Scene and Chunk nodes via persistence port
- Handles failure path with FAILED status creation

### SceneDetectionService
- Wraps current LLM / heuristic scene boundary detection (single external call tier only)
- Returns ordered list of scene spans used for persistence

### Chunking Logic
- Derives smaller Chunks from Scenes (windowing / length rules)
- Stored as Chunk nodes linked to their Scene for downstream vectorization (future)

### Persistence Adapter (Neo4jContentPersistenceAdapter)
- Implements ContentPersistencePort
- Persists Chapters, Scenes, Chunks, IngestionJobs, StatusRecords
- Applies uniqueness constraint for Chapter.contentHash (via startup initializer)

### GraphModelMapper (Transitional)
- Simple POJO → Node conversion helper (slated for removal when inline creation adopted)

### Status Tracking
- StatusRecord nodes append-only; adapter returns recent records for job progress display

## Minimal Processing Flow (Implemented)
```mermaid
timeline
    Submitted : Chapter Received : IngestionJob CREATED (QUEUED)
    Processing : Status -> PROCESSING : Scene Detection
    Processing : Chunk Generation : Persist Scenes & Chunks
    Completion : Status -> COMPLETED
```

## Sequence Diagram (Current Ingestion)
```mermaid
sequenceDiagram
    participant Client
    participant API as ChapterController
    participant Ing as IngestionService
    participant Scene as SceneDetectionService
    participant Persist as ContentPersistencePort
    Client->>API: POST /chapters (text)
    API->>Ing: submitChapter(text)
    Ing->>Persist: findOrCreateChapter(hash)
    Ing->>Persist: createJob + QUEUED status
    Ing->>Persist: updateJobStatus(PROCESSING)
    Ing->>Scene: detectScenes(chapterText)
    Scene-->>Ing: scenes
    Ing->>Ing: chunkScenes()
    Ing->>Persist: addScenesToChapter()
    Ing->>Persist: addChunksToChapter()
    Ing->>Persist: updateJobStatus(COMPLETED)
    Ing-->>API: Job summary
    API-->>Client: 202 Accepted (job id)
```

## Quality Attributes (Current Focus)
- Simplicity: Minimized scope to accelerate datastore migration
- Integrity: Idempotent chapter submission via content hash
- Observability: Basic status records (no distributed tracing yet)
- Testability: Unit tests + Neo4j Testcontainer integration tests

## Deferred Components (v0.8.0+ Roadmap)
(Original design elements retained here for continuity; not yet implemented)
- CQRS specialization (separate optimized query services)
- Hybrid Local + External AI orchestration layer
- Knowledge entity extraction & graph enrichment pipeline
- RAG-based question answering over retrieved chunks
- Event / job queue abstraction (currently inline method calls)

### Deferred Diagram References (Removed for Clarity)
Previous diagrams showing: full CQRS gateway, job queue/worker pool, multi-tier AI pipeline, vector-enhanced RAG flow. These will be reinstated once corresponding capabilities are implemented.

## Rationale for Current Scope
- Semantic search provides foundation for future RAG-based question answering
- Linear in-memory scoring establishes ports & adapters pattern for future optimization
- Embeddings infrastructure enables knowledge entity extraction in next milestone
- Early delivery enables iterative optimization before adding complex reasoning layers

## Risks & Mitigations (Current Scope)
- Adapter inefficiency (in-memory filtering) → Plan: replace with targeted Cypher (next iteration)
- Over-reliance on transitional mapper → Plan: remove after inline node construction refactor
- Limited status insight (no progress percentages) → Future: fine-grained stage events

## Planned Near-Term Improvements
1. Replace adapter in-memory filtering with Cypher queries
2. Remove unused legacy repository stubs & deprecated ChunkService  
3. Inline node creation (drop GraphModelMapper)
4. Add RAG-based question answering endpoint (v0.8.0)

---
(Updated for v0.7.0 to reflect semantic search implementation; future sections clearly marked as deferred.)
