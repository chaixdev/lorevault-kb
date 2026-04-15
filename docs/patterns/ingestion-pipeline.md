# Ingestion Pipeline Pattern

**Status:** Established

### Design Philosophy
LoreVault ingests narrative chapter text through a staged, event-driven pipeline. This architecture treats ingestion as a series of discrete transformations, from raw text to structured scenes, granular chunks, vector embeddings, and scene-derived entity structures. Each stage operates as a decoupled unit, ensuring that the complex process of understanding narrative structure remains manageable and observable.

Stages communicate asynchronously through Spring application events, which isolates failures and preserves partial progress. If chunking fails during a run, the scene detection results from the previous stage survive. This decoupling allows the system to scale specific parts of the pipeline independently and provides a natural boundary for transactional integrity.

The pipeline uses `@Async` event listeners to ensure stages run in separate threads after the publishing transaction commits. A shared `PipelineStageSupport` class provides consistent failure handling across all stages. It manages failure events, updates job statuses, and classifies errors as retryable or terminal. The two-pass scene detection process, involving initial segmentation and subsequent temporal triad analysis, represents the most computationally expensive portion of this flow.

### Component Map
```mermaid
graph LR
    IngestionService["IngestionService"] -->|"ChapterIngestionEvent"| SceneDetectionHandler["SceneDetectionHandler"]
    SceneDetectionHandler -->|"ScenesDetectedEvent"| ChunkingHandler["ChunkingHandler"]
    SceneDetectionHandler -->|"ScenesDetectedEvent"| ChapterIndividualResolutionHandler["ChapterIndividualResolutionHandler"]
    SceneDetectionHandler -->|"ScenesDetectedEvent"| ChapterLocationResolutionHandler["ChapterLocationResolutionHandler"]
    ChunkingHandler -->|"ChunksCreatedEvent"| EmbeddingHandler["EmbeddingHandler"]
    EmbeddingHandler -->|"EmbeddingsCompletedEvent"| Completion["IngestionCompletionCoordinator"]
    ChapterIndividualResolutionHandler -->|"ChapterIndividualsResolvedEvent"| BookIndividualReductionHandler["BookIndividualReductionHandler"]
    BookIndividualReductionHandler -->|"BookIndividualsReducedEvent"| Completion
    ChapterLocationResolutionHandler -->|"ChapterLocationsResolvedEvent"| BookLocationReductionHandler["BookLocationReductionHandler"]
    BookLocationReductionHandler -->|"BookLocationsReducedEvent"| Completion
    Completion -->|"IngestionCompletedEvent"| Done["Pipeline Complete"]
    
    SceneDetectionHandler -.->|"IngestionFailedEvent"| PipelineStageSupport["PipelineStageSupport"]
    ChunkingHandler -.->|"IngestionFailedEvent"| PipelineStageSupport
    EmbeddingHandler -.->|"IngestionFailedEvent"| PipelineStageSupport
    
    SceneDetectionHandler --- SceneDetectionService["SceneDetectionService"]
    ChunkingHandler --- TextChunkingService["TextChunkingService"]
    EmbeddingHandler --- EmbeddingService["EmbeddingService"]
```

### Sequence Diagram: Full Pipeline Happy Path
```mermaid
sequenceDiagram
    participant Client as "Client"
    participant Controller as "CommandIngestionController"
    participant Service as "IngestionService"
    participant SDH as "SceneDetectionHandler"
    participant SDS as "SceneDetectionService"
    participant CH as "ChunkingHandler"
    participant TCS as "TextChunkingService"
    participant EH as "EmbeddingHandler"
    participant ES as "EmbeddingService"
    participant CIRH as "ChapterIndividualResolutionHandler"
    participant CIRS as "ChapterIndividualResolutionService"
    participant BIRH as "BookIndividualReductionHandler"
    participant BIRS as "BookIndividualReductionService"
    participant CLRH as "ChapterLocationResolutionHandler"
    participant CLRS as "ChapterLocationResolutionService"
    participant BLRH as "BookLocationReductionHandler"
    participant BLRS as "BookLocationReductionService"
    participant ICC as "IngestionCompletionCoordinator"

    Client->>Controller : "POST /api/ingest"
    Controller->>Service : "submitChapter(request)"
    Service->>Service : "validate and create IngestionJob"
    Service-->>Controller : "return JobID"
    Service->>SDH : "publish ChapterIngestionEvent (async)"
    
    SDH->>SDS : "detectScenesInText(chapter)"
    Note over SDS : "Chapter Segmentation<br>Scene Analysis"
    SDS-->>SDH : "return scenes"
    SDH->>SDH : "persist scenes and temporal edges"
    SDH->>CH : "publish ScenesDetectedEvent"
    SDH->>CIRH : "publish ScenesDetectedEvent"
    SDH->>CLRH : "publish ScenesDetectedEvent"
    
    CH->>TCS : "createChunks(scenes)"
    TCS-->>CH : "return chunks"
    CH->>CH : "persist chunks and links"
    CH->>EH : "publish ChunksCreatedEvent"
    
    EH->>ES : "generateEmbeddings(chunks)"
    ES-->>EH : "return vectors"
    EH->>ICC : "publish EmbeddingsCompletedEvent"

    CIRH->>CIRS : "resolveChapter(chapterId)"
    CIRS-->>CIRH : "return chapter result"
    CIRH->>BIRH : "publish ChapterIndividualsResolvedEvent"
    BIRH->>BIRS : "resolveBook(bookId)"
    BIRS-->>BIRH : "return book result"
    BIRH->>ICC : "publish BookIndividualsReducedEvent"

    CLRH->>CLRS : "resolveChapter(chapterId)"
    CLRS-->>CLRH : "return chapter result"
    CLRH->>BLRH : "publish ChapterLocationsResolvedEvent"
    BLRH->>BLRS : "resolveBook(bookId)"
    BLRS-->>BLRH : "return book result"
    BLRH->>ICC : "publish BookLocationsReducedEvent"

    ICC->>ICC : "complete only when all required branches arrive"
    ICC->>Client : "publish IngestionCompletedEvent"
```

### Pipeline Stage Detail

**Stage 1: Chapter Submission** (`IngestionService`)
- Validates the incoming chapter and deduplicates based on a content hash to prevent redundant processing.
- Creates an `IngestionJob` with an initial `QUEUED` status to track the lifecycle of the request.
- Publishes a `ChapterIngestionEvent` within a Spring `@Transactional` context.
- The downstream listeners only fire after the initial transaction commits, ensuring the job record is visible to background threads.

**Stage 2: Scene Detection + evidence persistence** (`SceneDetectionHandler`)
- Maintains idempotency by checking for existing scenes in the repository before starting work.
- Executes Chapter Segmentation: Uses an LLM for initial segmentation followed by XML parsing and a 3-tier fallback for coordinate localization.
- Executes Scene Analysis: Performs triad analysis to establish complex temporal relationships and to extract scene-local entity evidence.
- Automatically creates default sequential temporal edges through the `DefaultTemporalEdgeService`.
- Persists scene-local `IndividualMention` and `LocationMention` evidence after real scene IDs exist.
- Classified as retryable for transient LLM, API, or connection timeout errors.

**Stage 3: Chunking** (`ChunkingHandler`)
- Checks the repository to see if chunks already exist for the chapter's scenes to ensure idempotent behavior.
- Extracts raw scene text using the character offsets established during the detection phase.
- Breaks each scene into overlapping segments via the `TextChunkingService` to maintain narrative context across boundaries.
- Adjusts chunk coordinates to be chapter-relative, providing a consistent reference frame for the entire text.
- Persists the resulting chunks and creates explicit links to their parent scenes.

**Stage 4: Embedding branch** (`EmbeddingHandler`)
- Generates vector embeddings for every chunk created in the previous stage using the configured embedding model.
- Publishes `EmbeddingsCompletedEvent` with the final scene/chunk/embedding counts and processed chapter length.
- Handles retries for external API failures or network-related connection errors.

**Stage 5: Entity reduction branches** (`ChapterIndividualResolutionHandler`, `BookIndividualReductionHandler`, `ChapterLocationResolutionHandler`, `BookLocationReductionHandler`)
- `ScenesDetectedEvent` triggers sibling Entity branches after scene persistence.
- Individual flow resolves `IndividualMention -> ChapterIndividual -> BookIndividual`.
- Location flow resolves `LocationMention -> ChapterLocation -> BookLocation`.
- Both flows use conservative delete-and-rebuild semantics for their scoped reduction steps.

**Stage 6: Coordinated completion** (`IngestionCompletionCoordinator`)
- Waits for all required post-scene branches for the same `(jobId, chapterId)`.
- Current completion contract requires:
  - `EmbeddingsCompletedEvent`
  - `BookIndividualsReducedEvent`
  - `BookLocationsReducedEvent`
- Only then is the job marked complete and `IngestionCompletedEvent` emitted.

### Failure Handling
The `PipelineStageSupport.runStage()` utility wraps every stage in a try-catch block to provide uniform error management. When a failure occurs, the system emits an `IngestionFailedEvent` and updates the job status to `FAILED`. This update includes a structured `IngestionFailure` object containing the error type, message, and diagnostic properties.

Each handler defines its own retryability logic. For instance, LLM provider errors are marked as retryable, while a missing chapter entity is treated as a terminal state. The system specifically unwraps `TriadAnalysisException` to preserve granular details about which part of the temporal analysis failed. To prevent cascading failures in the event-driven loop, exceptions are recorded and swallowed by the handler rather than being rethrown.

### Idempotency
To support safe event re-delivery and manual restarts, each handler verifies its current state before performing work. The `SceneDetectionHandler` queries `sceneRepo.findByChapterId()` and skips processing if scenes are already present. Similarly, the `ChunkingHandler` uses `chunkRepo.existsForChapterViaScenes()` to determine if chunking is already finished. The scoped entity-reduction handlers use deterministic delete-and-rebuild behavior, and book-level reduction is serialized per book where needed. These checks allow the pipeline to resume from the last successful stage if interrupted.

### Boundaries
- **Triad analysis details** — The internal logic for temporal triad classification is documented in the Triad Analysis Pattern.
- **Observability model** — The tracking of job statuses and LLM call records is handled by the Observability Pattern.
- **Scene coordinate localization** — The 3-tier fallback matching logic resides within the `SceneProcessingService` and is not covered here.
- **LLM prompt templates** — Templates are managed by the `PromptRepository` and are external to the pipeline flow.
- **Content hierarchy** — The management of Universes, Series, and Books is the responsibility of the `LibraryService`.

### Primary References
- `../development/current/processes/scene-detection-specification.md`
- `../development/current/processes/triad-orchestration.md`
- `../adr/004-keep-the-event-driven-ingestion-pipeline.md`
- `../development/current/data-model/ingestion-job-and-status.md`
