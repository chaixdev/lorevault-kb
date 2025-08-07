# Core Data Model Specification (v0.3.0)

**Purpose**: This specification defines the logical data structures, relationships, and domain-driven design patterns for storing the core content within the LoreVault system. It details the hierarchical model using aggregate root patterns to represent chapters, scenes, and chunks of narrative text.

**Scope**: This document covers the data model for ingesting and storing the primary source text and its subdivisions, implemented using Domain-Driven Design (DDD) aggregate patterns. This includes the `Chapter` aggregate root, `Scene` and `Chunk` entities, as well as the `PublicationCoordinates` component. The model for storing extracted, synthesized entity profiles (e.g., `CHARACTERS`, `LOCATIONS` from the architecture document) is out of scope for this specification.

**Dependencies**:
- **Architecture Document**: Information Viewpoint (03-information-viewpoint.md)
- **Process Specification**: Content Ingestion Process (content-ingestion-process.md)
- **AI Integration**: Scene Detection Service (v0.3.0+)

## Process Overview

The LoreVault system creates a "structured and searchable knowledge base from unstructured narrative text" using a three-tiered hierarchy: `Chapter` -> `Scene` -> `Chunk`, implemented with Domain-Driven Design aggregate patterns.

### Version Evolution
- **v0.2.0**: Direct Chapter → Chunk relationship with deterministic text chunking
- **v0.3.0**: AI-powered Scene detection creating Chapter → Scene → Chunk hierarchy with DDD aggregates

### Design Goals
1. **Granular AI Processing**: Semantically coherent chunks optimized for RAG
2. **Advanced Features**: Coordinate system enabling spoiler prevention and progress tracking
3. **Data Consistency**: Aggregate boundaries ensuring transactional consistency and business rule encapsulation

The model persists in PostgreSQL with `pgvector` extensions for embeddings.

## Domain Model Architecture

### Aggregate Root Pattern Implementation

`Chapter` serves as the **Aggregate Root** with complete encapsulation:

- **Encapsulated Collections**: Private `List<Scene>` and `List<Chunk>` with read-only public access via `Collections.unmodifiableList()`
- **Controlled Mutations**: All modifications through aggregate methods (`addScene()`, `addChunk()`, `addChunkToScene()`, etc.)
- **Automatic Relationship Management**: Bidirectional JPA relationships maintained transparently
- **Invariant Enforcement**: Scene must belong to chapter before chunk association; cascade operations for removals
- **Transaction Boundaries**: All aggregate modifications are atomic

### ChunkRepository Design Rationale

Despite Chapter being the aggregate root, `ChunkRepository` is maintained for specific technical requirements:

**Performance Optimization**: Vector similarity searches require direct, optimized database access; batch operations (embedding generation, reprocessing) benefit from direct repository access; read-heavy query services need efficient patterns that bypass full aggregate loading.

**Processing Pipeline Separation**: Chunk embeddings update independently during model upgrades; content analysis services require direct chunk access; migration operations need direct chunk manipulation.

**Implementation Guidelines**: Use Chapter aggregate for structural changes; use ChunkRepository for RAG queries, embeddings, and analysis workflows.

### Domain Class Diagram

```mermaid
classDiagram
    direction TB
    
    class Chapter {
        <<Aggregate Root>>
        +PublicationCoordinates coordinates
        +String chapterTitle, rawText, contentHash
        +addScene(index, start, end, summary) Scene
        +addChunkToScene(scene, number, start, end, hash) Chunk
        +getScenes() List~Scene~
        +getChunks() List~Chunk~
    }
    
    class Scene {
        +Integer sceneIndex
        +Long startCharacterOffset, endCharacterOffset
        +String contextSummary
        #addChunk(chunk) void
    }
    
    class Chunk {
        +Integer chunkNumberInChapter
        +Integer startCharInChapter, endCharInChapter
        +String contentHash
        +Vector embedding
    }
    
    class PublicationCoordinates {
        <<Value Object>>
        +String universe, series
        +Integer bookNumber, partNumber, chapterNumber
    }

    class ChunkRepository {
        <<Repository>>
        +findByChapterIdOrderByChunkNumber()
        +findByContentHash()
        +existsByChapterId()
    }

    Chapter "1" *-- "1" PublicationCoordinates
    Chapter "1" *-- "*" Scene
    Chapter "1" *-- "*" Chunk
    Scene "1" -- "*" Chunk
    Chapter -.-> ChunkRepository : "performance access"
```

### Entity Definitions

**PublicationCoordinates** (Value Object): Embedded coordinates defining precise chapter location within fictional universe.

**Chapter** (Aggregate Root): Complete chapter entity containing raw text, metadata, and managing scene/chunk collections. All structural modifications must go through aggregate methods.

**Scene**: AI-identified semantic subdivision with character offset boundaries and context summary. Collections managed exclusively through Chapter aggregate.

**Chunk**: Granular text subdivision optimized for RAG processing. Supports both direct chapter association (v0.2.0 compatibility) and scene association (v0.3.0+). Accessed via both aggregate methods and direct repository patterns.

## State Management

**Aggregate Lifecycle**: Chapters created as complete aggregates; structural modifications via aggregate methods; transaction boundaries ensure atomic operations.

**Content Immutability**: Raw text treated as immutable; updates create new aggregate instances with new content hashes; legacy chapters archived while maintaining referential integrity.

**Processing Separation**: Aggregate handles structural relationships; individual chunk processing (embeddings, analysis) uses repository access patterns.

## Interface Specifications

### Core Entities

| Entity | Key Attributes | Notes |
|--------|----------------|-------|
| `PublicationCoordinates` | universe, series, bookNumber, partNumber, chapterNumber | Embeddable value object |
| `Chapter` | id (UUID), coordinates, chapterTitle, rawText, contentHash, embedding | Aggregate root with collection management |
| `Scene` | id (UUID), sceneIndex, contextSummary, startCharacterOffset, endCharacterOffset | AI-detected semantic boundaries |
| `Chunk` | id (UUID), chunkNumberInChapter, startCharInChapter, endCharInChapter, contentHash, embedding | RAG-optimized text segments |

### Aggregate Operations

| Operation | Parameters | Purpose |
|-----------|------------|---------|
| `addScene()` | index, startOffset, endOffset, summary | Create scene with character boundaries |
| `addChunkToScene()` | scene, chunkNumber, startChar, endChar, contentHash | Associate chunk with specific scene |
| `getScenes()` / `getChunks()` | none | Read-only collection access |

### Repository Patterns

**ChunkRepository**: Direct access for vector queries (`findByContentHash`, `existsByChapterId`), batch operations, and performance-critical RAG searches.

**Chapter Access**: All structural modifications via aggregate methods; lazy-loaded collections for efficient querying.

## Technical Requirements

### Error Handling
- Constraint violations for invalid parent relationships
- Duplicate content rejection via `content_hash` validation  
- Invalid coordinate validation (endChar ≥ startChar)

### Performance Requirements
- Multi-column indexes on coordinate fields for spoiler-prevention filtering
- Hash indexing on `content_hash` for deduplication
- Vector indexing (HNSW/IVFFlat) on chunk embeddings for RAG queries

### Integration Points
- **Content Ingestion**: Primary writer creating Chapter aggregates
- **RAG Synthesis**: Vector searches on chunks table via ChunkRepository
- **Query Service**: Coordinate-based filtering and relational queries

## Validation Criteria

### Aggregate Pattern Compliance
- Chapter properly encapsulates collections with read-only access
- All structural modifications go through aggregate methods
- Scene protected methods only accessible within aggregate boundary

### Data Integrity  
- Chunks/Scenes require valid Chapter relationships
- Scene-associated chunks must belong to same chapter
- Duplicate content_hash rejection

### Performance Targets
- Sub-second coordinate-based filtering for spoiler-prevention
- Efficient vector searches via ChunkRepository for RAG operations
- Acceptable aggregate loading performance with lazy collections

### Backward Compatibility (v0.2.0 → v0.3.0)
- Legacy direct chapter-chunk relationships preserved
- Migration maintains data integrity
- Existing ChunkRepository access patterns functional
