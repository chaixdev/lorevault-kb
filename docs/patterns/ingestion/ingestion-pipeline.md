# Ingestion Pipeline Pattern

**Status:** Established

### Design Philosophy
LoreVault ingests narrative chapter text through a staged, event-driven pipeline. This architecture treats ingestion as a series of discrete transformations, from raw text to structured scenes, granular chunks, vector embeddings, and scene-derived entity structures. Each stage operates as a decoupled unit, ensuring that the complex process of understanding narrative structure remains manageable and observable.

Stages communicate asynchronously through Spring application events, which isolates failures and preserves partial progress. If chunking fails during a run, the scene detection results from the previous stage survive. This decoupling allows the system to scale specific parts of the pipeline independently and provides a natural boundary for transactional integrity.

The pipeline uses a centralized `StageDispatcher` that routes `StageTriggeredEvent` to the correct `StageOperation` handler via `@ForStage` annotation. Each handler receives a `StageExecutionContext` carrying `(stageId, jobId, chapterId, bookId, stage)`. The dispatcher sets MDC fields (`stage`, `jobId`, `stageId`) before execution and clears them after. The scene stage remains the most computationally expensive portion of this flow because it includes LLM segmentation/localization, scene persistence, and post-persistence triad analysis.

### Component Map
```mermaid
graph LR
    IngestionService["IngestionService"] -->|"ChapterIngestionEvent"| StageDispatcher["StageDispatcher"]
    StageDispatcher -->|"dispatches"| SceneDetectionHandler["SceneDetectionHandler"]
    SceneDetectionHandler -->|"ScenesDetectedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| ChunkingHandler["ChunkingHandler"]
    StageDispatcher -->|"dispatches"| ChapterIndividualConsolidationHandler["ChapterIndividualConsolidationHandler"]
    StageDispatcher -->|"dispatches"| ChapterLocationConsolidationHandler["ChapterLocationConsolidationHandler"]
    StageDispatcher -->|"dispatches"| ChapterObjectConsolidationHandler["ChapterObjectConsolidationHandler"]
    StageDispatcher -->|"dispatches"| ChapterCollectiveConsolidationHandler["ChapterCollectiveConsolidationHandler"]
    StageDispatcher -->|"dispatches"| ChapterEventConsolidationHandler["ChapterEventConsolidationHandler"]
    ChunkingHandler -->|"ChunksCreatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| EmbeddingHandler["EmbeddingHandler"]
    EmbeddingHandler -->|"EmbeddingsCompletedEvent"| StageDispatcher
    ChapterIndividualConsolidationHandler -->|"ChapterIndividualsConsolidatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| BookIndividualConsolidationHandler["BookIndividualConsolidationHandler"]
    BookIndividualConsolidationHandler -->|"BookIndividualsConsolidatedEvent"| StageDispatcher
    ChapterLocationConsolidationHandler -->|"ChapterLocationsConsolidatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| BookLocationConsolidationHandler["BookLocationConsolidationHandler"]
    BookLocationConsolidationHandler -->|"BookLocationsConsolidatedEvent"| StageDispatcher
    ChapterObjectConsolidationHandler -->|"ChapterObjectsConsolidatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| BookObjectConsolidationHandler["BookObjectConsolidationHandler"]
    BookObjectConsolidationHandler -->|"BookObjectsConsolidatedEvent"| StageDispatcher
    ChapterCollectiveConsolidationHandler -->|"ChapterCollectivesConsolidatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| BookCollectiveConsolidationHandler["BookCollectiveConsolidationHandler"]
    BookCollectiveConsolidationHandler -->|"BookCollectivesConsolidatedEvent"| StageDispatcher
    ChapterEventConsolidationHandler -->|"ChapterEventsConsolidatedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| ChapterEventEmbeddingHandler["ChapterEventEmbeddingHandler"]
    ChapterEventEmbeddingHandler -->|"BookEventCandidatesGeneratedEvent"| StageDispatcher
    StageDispatcher -->|"dispatches"| IngestionPipelineCoordinator["IngestionPipelineCoordinator"]
    IngestionPipelineCoordinator -->|"IngestionCompletedEvent"| Done["Pipeline Complete"]
    
    SceneDetectionHandler --- SceneDetectionService["SceneDetectionService"]
    ChunkingHandler --- TextChunkingService["TextChunkingService"]
    EmbeddingHandler --- EmbeddingService["EmbeddingService"]
    ChapterEventConsolidationHandler --- ChapterEventConsolidationService["ChapterEventConsolidationService"]
    ChapterEventEmbeddingHandler --- ChapterEventEmbeddingService["ChapterEventEmbeddingService"]
    ChapterEventEmbeddingHandler --- BookEventAnnCandidateService["BookEventAnnCandidateService"]
```

### Sequence Diagram: Full Pipeline Happy Path
> **Note:** This diagram shows the logical event flow between participants. The actual dispatch mechanism is `StageDispatcher` → `StageOperation.execute(ctx)`. Handler methods now receive `StageExecutionContext ctx` instead of plain event payloads.

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
    participant CIRH as "ChapterIndividualConsolidationHandler"
    participant CIRS as "ChapterIndividualConsolidationService"
    participant BIRH as "BookIndividualConsolidationHandler"
    participant BIRS as "BookIndividualConsolidationService"
    participant CLRH as "ChapterLocationConsolidationHandler"
    participant CLRS as "ChapterLocationConsolidationService"
    participant BLRH as "BookLocationConsolidationHandler"
    participant BLRS as "BookLocationConsolidationService"
    participant CORH as "ChapterObjectConsolidationHandler"
    participant CORS as "ChapterObjectConsolidationService"
    participant BORH as "BookObjectConsolidationHandler"
    participant BORS as "BookObjectConsolidationService"
    participant CCRH as "ChapterCollectiveConsolidationHandler"
    participant CCRS as "ChapterCollectiveConsolidationService"
    participant BCRH as "BookCollectiveConsolidationHandler"
    participant BCRS as "BookCollectiveConsolidationService"
    participant CERH as "ChapterEventConsolidationHandler"
    participant CERS as "ChapterEventConsolidationService"
    participant CEEH as "ChapterEventEmbeddingHandler"
    participant CEES as "ChapterEventEmbeddingService"
    participant ANNS as "BookEventAnnCandidateService"
    participant ICC as "IngestionPipelineCoordinator"

    Client->>Controller : "POST /api/ingest"
    Controller->>Service : "submitChapter(request)"
    Service->>Service : "validate and create IngestionJob"
    Service-->>Controller : "return JobID"
    Service->>SDH : "publish ChapterIngestionEvent (async)"
    
    SDH->>SDS : "detectScenesInChapter(jobId, chapter)"
    Note over SDS : "Chapter Segmentation<br>Coordinate localization"
    SDS-->>SDH : "return localized scenes"
    SDH->>SDH : "persist scenes"
    SDH->>SDH : "run triad analysis on persisted scenes"
    SDH->>SDH : "persist TEMPORAL edges + scene-local evidence"
    SDH->>CH : "publish ScenesDetectedEvent"
    SDH->>CIRH : "publish ScenesDetectedEvent"
    SDH->>CLRH : "publish ScenesDetectedEvent"
    SDH->>CORH : "publish ScenesDetectedEvent"
    SDH->>CCRH : "publish ScenesDetectedEvent"
    SDH->>CERH : "publish ScenesDetectedEvent"
    
    CH->>TCS : "createChunks(scenes)"
    TCS-->>CH : "return chunks"
    CH->>CH : "persist chunks and links"
    CH->>EH : "publish ChunksCreatedEvent"
    
    EH->>ES : "generateEmbeddings(chunks)"
    ES-->>EH : "return vectors"
    EH->>ICC : "publish EmbeddingsCompletedEvent"

    CIRH->>CIRS : "resolveChapter(chapterId)"
    CIRS-->>CIRH : "return chapter result"
    CIRH->>BIRH : "publish ChapterIndividualsConsolidatedEvent"
    BIRH->>BIRS : "resolveBook(bookId)"
    BIRS-->>BIRH : "return book result"
    BIRH->>ICC : "publish BookIndividualsConsolidatedEvent"

    CLRH->>CLRS : "resolveChapter(chapterId)"
    CLRS-->>CLRH : "return chapter result"
    CLRH->>BLRH : "publish ChapterLocationsConsolidatedEvent"
    BLRH->>BLRS : "resolveBook(bookId)"
    BLRS-->>BLRH : "return book result"
    BLRH->>ICC : "publish BookLocationsConsolidatedEvent"

    CORH->>CORS : "resolveChapter(chapterId)"
    CORS-->>CORH : "return chapter result"
    CORH->>BORH : "publish ChapterObjectsConsolidatedEvent"
    BORH->>BORS : "resolveBook(bookId)"
    BORS-->>BORH : "return book result"
    BORH->>ICC : "publish BookObjectsConsolidatedEvent"

    CCRH->>CCRS : "resolveChapter(chapterId)"
    CCRS-->>CCRH : "return chapter result"
    CCRH->>BCRH : "publish ChapterCollectivesConsolidatedEvent"
    BCRH->>BCRS : "resolveBook(bookId)"
    BCRS-->>BCRH : "return book result"
    BCRH->>ICC : "publish BookCollectivesConsolidatedEvent"

    CERH->>CERS : "resolveChapter(chapterId)"
    CERS-->>CERH : "return chapter event result"
    CERH->>CEEH : "publish ChapterEventsConsolidatedEvent"
    CEEH->>CEES : "embed chapter events"
    CEES-->>CEEH : "return event vectors"
    CEEH->>ANNS : "generate book event ANN candidates"
    ANNS-->>CEEH : "return candidate pairs"
    CEEH->>ICC : "publish BookEventCandidatesGeneratedEvent"

    ICC->>ICC : "complete only when all required branches arrive"
    ICC->>Client : "publish IngestionCompletedEvent"
```

### Pipeline Stage Detail

**Stage 1: Chapter Submission** (`IngestionService`)
- Validates the incoming chapter and deduplicates based on a content hash to prevent redundant processing.
- Treats submission-critical lookups as fail-closed workflow boundaries rather than best-effort fallbacks.
- Uses typed submission exceptions for lookup/persistence failures so duplicate-work creation does not proceed from ambiguous repository state.
- Creates an `IngestionJob` with an initial `QUEUED` status to track the lifecycle of the request.
- Publishes a `ChapterIngestionEvent` within a Spring `@Transactional` context.
- The downstream listeners only fire after the initial transaction commits, ensuring the job record is visible to background threads.

**Stage 2: Scene Detection + evidence persistence** (`SceneDetectionHandler`)
- Maintains idempotency by checking for existing scenes in the repository before starting work.
- Executes Chapter Segmentation: Uses an LLM for initial segmentation followed by XML parsing and a 3-tier fallback for coordinate localization.
- Persists localized scenes first so all subsequent processing uses stable scene IDs.
- Executes Scene Analysis post-persistence: Performs triad analysis to establish complex temporal relationships and to extract scene-local entity evidence.
- Automatically creates default sequential temporal edges through the `DefaultTemporalEdgeService`.
- Persists scene-local `IndividualMention`, `LocationMention`, `ObjectMention`, and `CollectiveMention` evidence after real scene IDs exist.
- Classified as retryable for transient LLM, API, or connection timeout errors.

**Stage 3: Chunking** (`ChunkingHandler`)
- Checks the repository to see if chunks already exist for the chapter's scenes to ensure idempotent behavior.
- Extracts raw scene text using the character offsets established during the detection phase.
- Breaks each scene into overlapping segments via the `TextChunkingService` to maintain narrative context across boundaries.
- Adjusts chunk coordinates to be chapter-relative, providing a consistent reference frame for the entire text.
- Persists the resulting chunks and creates explicit links to their parent scenes.

**Stage 4: Embedding branch** (`EmbeddingHandler`)
- Generates vector embeddings for every chunk created in the previous stage using the configured embedding model.
- Treats backend embedding failures and malformed non-empty embedding responses as typed stage failures instead of silently returning `0` updated embeddings.
- Preserves legitimate no-work semantics only when there are no chunks to embed or all embeddings are already current.
- Publishes `EmbeddingsCompletedEvent` with the final scene/chunk/embedding counts and processed chapter length.
- Handles retries for external API failures or network-related connection errors.

**Stage 5: Entity and event resolution branches** (`ChapterIndividualConsolidationHandler`, `BookIndividualConsolidationHandler`, `ChapterLocationConsolidationHandler`, `BookLocationConsolidationHandler`, `ChapterObjectConsolidationHandler`, `BookObjectConsolidationHandler`, `ChapterCollectiveConsolidationHandler`, `BookCollectiveConsolidationHandler`, `ChapterEventConsolidationHandler`)
- `ScenesDetectedEvent` triggers sibling Entity branches after scene persistence.
- Individual flow resolves `IndividualMention -> ChapterIndividual -> BookIndividual`.
- Location flow resolves `LocationMention -> ChapterLocation -> BookLocation`.
- Object flow resolves `ObjectMention -> ChapterObject -> BookObject`.
- Collective flow resolves `CollectiveMention -> ChapterCollective -> BookCollective`.
- Event flow resolves scene-local event evidence into chapter-scoped event structures and emits `ChapterEventsConsolidatedEvent` for event embedding/ANN work.
- Regular entity flows use scoped replacement semantics for their derived projections. Chapter reducers replace chapter-scoped aggregates inside the chapter-resolution transaction. Book reducers serialize by book claim and publish reduced events only after coherent terminal replacement succeeds.

**Stage 6: Event embedding and ANN candidate generation** (`ChapterEventEmbeddingHandler`)
- Listens for `ChapterEventsConsolidatedEvent` after chapter event resolution finishes.
- Generates embeddings for chapter events using the configured event embedding model dimension.
- Generates same-book ANN candidate pairs for event co-reference while excluding events from the current chapter.
- Publishes `BookEventCandidatesGeneratedEvent`, which is the final completion-barrier signal for the chapter event path.

**Stage 7: Coordinated completion** (`IngestionPipelineCoordinator`)
- Waits for all required post-scene branches for the same `(jobId, chapterId)`.
- Current completion contract requires:
  - `EmbeddingsCompletedEvent`
  - `BookIndividualsConsolidatedEvent`
  - `BookLocationsConsolidatedEvent`
  - `BookObjectsConsolidatedEvent`
  - `BookCollectivesConsolidatedEvent`
  - `ChapterEventsConsolidatedEvent`
  - `BookEventCandidatesGeneratedEvent`
- Only then is the job marked complete and `IngestionCompletedEvent` emitted.
- On `IngestionFailedEvent`, the coordinator now removes retained fan-in state for that `(jobId, chapterId)` and treats the key as terminal-failed so late success-branch events cannot recreate stale completion state after a failure.

### Abstract Orchestration Model

At an abstract level, the ingestion pipeline is best understood as a task graph connected by application events.

- A handler performs one bounded task for a specific `(jobId, chapterId)` or `(jobId, bookId)` scope.
- On success, that handler emits one domain-level pipeline event describing what is now durably true enough for downstream work to begin.
- Downstream handlers subscribe to that event and start their own bounded tasks asynchronously after the publishing transaction commits.
- Failure is not modeled as an alternate success path. Instead, failures emit `IngestionFailedEvent` and terminate the affected branch.

This means the pipeline is not a single long-running transaction and not a scheduler timeline. It is a causal event chain where each emitted event unlocks the next unit of work.

### Task -> Event -> Trigger Matrix

| Completed task | Success event emitted | Downstream task(s) triggered |
|---|---|---|
| Chapter submission and job creation (`IngestionService`) | `ChapterIngestionEvent` | Scene detection (`SceneDetectionHandler`) |
| Scene detection, scene persistence, temporal-edge materialization, scene-local evidence persistence (`SceneDetectionHandler`) | `ScenesDetectedEvent` | Chunk creation (`ChunkingHandler`), chapter individual resolution (`ChapterIndividualConsolidationHandler`), chapter location resolution (`ChapterLocationConsolidationHandler`), chapter object resolution (`ChapterObjectConsolidationHandler`), chapter collective resolution (`ChapterCollectiveConsolidationHandler`), chapter event resolution (`ChapterEventConsolidationHandler`) |
| Chunk creation and persistence (`ChunkingHandler`) | `ChunksCreatedEvent` | Embedding generation (`EmbeddingHandler`) |
| Embedding generation (`EmbeddingHandler`) | `EmbeddingsCompletedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Chapter-level individual resolution (`ChapterIndividualConsolidationHandler`) | `ChapterIndividualsConsolidatedEvent` | Book-level individual reduction (`BookIndividualConsolidationHandler`) |
| Book-level individual reduction (`BookIndividualConsolidationHandler`) | `BookIndividualsConsolidatedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Chapter-level location resolution (`ChapterLocationConsolidationHandler`) | `ChapterLocationsConsolidatedEvent` | Book-level location reduction (`BookLocationConsolidationHandler`) |
| Book-level location reduction (`BookLocationConsolidationHandler`) | `BookLocationsConsolidatedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Chapter-level object resolution (`ChapterObjectConsolidationHandler`) | `ChapterObjectsConsolidatedEvent` | Book-level object reduction (`BookObjectConsolidationHandler`) |
| Book-level object reduction (`BookObjectConsolidationHandler`) | `BookObjectsConsolidatedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Chapter-level collective resolution (`ChapterCollectiveConsolidationHandler`) | `ChapterCollectivesConsolidatedEvent` | Book-level collective reduction (`BookCollectiveConsolidationHandler`) |
| Book-level collective reduction (`BookCollectiveConsolidationHandler`) | `BookCollectivesConsolidatedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Chapter event co-reference and chapter event aggregation (`ChapterEventConsolidationHandler`) | `ChapterEventsConsolidatedEvent` | Event embedding and ANN candidate generation (`ChapterEventEmbeddingHandler`) |
| Event embedding and ANN candidate generation (`ChapterEventEmbeddingHandler`) | `BookEventCandidatesGeneratedEvent` | Completion coordination state update (`IngestionPipelineCoordinator`) |
| Completion preconditions satisfied for the job/chapter (`IngestionPipelineCoordinator`) | `IngestionCompletedEvent` | Terminal success notification / downstream consumers |
| Any stage fails (`StageDispatcher`) | `IngestionFailedEvent` | Terminal failure notification / downstream consumers |

### Event Semantics By Boundary

The important contract is not just that an event was emitted, but what downstream handlers are allowed to assume when they receive it.

**`ChapterIngestionEvent`**
- Means the ingestion job and chapter already exist and the initial submission transaction committed.
- Allows async pipeline work to begin against durable identifiers.

**`ScenesDetectedEvent`**
- Means scenes for the chapter are durably persisted and can be referenced by stable scene IDs.
- Also means scene-level temporal materialization and scene-local evidence persistence have already happened far enough for downstream chunking and entity-resolution branches to trust the scene graph.
- This is the main fan-out point in the pipeline.

**`ChunksCreatedEvent`**
- Means chunk nodes and scene-to-chunk links exist.
- Allows embedding generation to treat chunk persistence as complete for that chapter.

**`ChapterIndividualsConsolidatedEvent` / `ChapterLocationsConsolidatedEvent` / `ChapterObjectsConsolidatedEvent` / `ChapterCollectivesConsolidatedEvent`**
- Mean the chapter-scoped reduction pass is complete for that evidence type.
- Allow the corresponding book-scoped reduction step to rebuild the book-level aggregate.

**`ChapterEventsConsolidatedEvent`**
- Means chapter-scoped event resolution is complete.
- Allows event embedding and ANN candidate generation to begin.
- Also contributes to the completion barrier so the coordinator can observe the event-resolution stage separately from downstream candidate generation.

**`BookIndividualsConsolidatedEvent` / `BookLocationsConsolidatedEvent` / `BookObjectsConsolidatedEvent` / `BookCollectivesConsolidatedEvent` / `EmbeddingsCompletedEvent` / `BookEventCandidatesGeneratedEvent`**
- Mean one of the required post-scene branches has finished.
- Do not individually complete the ingestion job.
- Instead, they contribute to the completion barrier tracked by `IngestionPipelineCoordinator`.

**`IngestionCompletedEvent`**
- Means all required branches for the chapter have finished and the job can be treated as terminally successful.

**`IngestionFailedEvent`**
- Means the current stage failed and the job was transitioned to a terminal failed state with structured failure details.
- It is a terminal branch outcome, not a retry command.

### Failure Semantics

- `StageDispatcher` wraps handler execution in an error boundary, catches unchecked exceptions, and converts them to `StageResult.failure()` or `StageResult.retryableFailure()`.
- Typed workflow failures carrying structured `IngestionFailure` payloads are treated as first-class stage outcomes.
- Known business failures are preserved into status/failure events instead of being flattened into generic runtime errors or false-success counters.
- This is especially important for chapter submission, scene detection/localization, and embedding generation, where the current implementation now fails closed for ambiguous or malformed outcomes.

### Fan-out and Join Shape

The runtime shape is:

1. **Single entry** at chapter submission.
2. **Single first worker** for scene detection.
3. **Fan-out** after `ScenesDetectedEvent` into:
   - chunk/embedding branch
   - individual resolution branch
   - location resolution branch
   - object resolution branch
   - collective resolution branch
   - chapter event resolution branch, followed by event embedding and ANN candidate generation
4. **Join** inside `IngestionPipelineCoordinator`, which waits for the required completion-barrier events.
5. **Single terminal outcome**, either `IngestionCompletedEvent` or `IngestionFailedEvent`.

This join is intentionally event-based rather than call-stack-based. No branch directly calls another branch's service to declare the whole job complete.

### Concurrency and Timing View

The following Mermaid Gantt is an illustrative runtime view of how the async branches overlap after scene detection completes.

- It is not a scheduling guarantee.
- It does not imply fixed durations.
- It exists to make fan-out, concurrency, and join behavior easier to reason about.

```mermaid
gantt
    title "Illustrative ingestion concurrency view"
    dateFormat X
    axisFormat "%L"

    section "Entry"
    "Chapter submission" :done, submit, 0, 1
    "Scene detection and scene persistence" :active, scenes, after submit, 4

    section "Fan-out after ScenesDetectedEvent"
    "Chunk creation" :chunks, after scenes, 2
    "Chapter individual resolution" :chapterIndividuals, after scenes, 3
    "Chapter location resolution" :chapterLocations, after scenes, 3
    "Chapter object resolution" :chapterObjects, after scenes, 3
    "Chapter collective resolution" :chapterCollectives, after scenes, 3
    "Chapter event resolution" :chapterEvents, after scenes, 3

    section "Downstream branch work"
    "Embedding generation" :embeddings, after chunks, 3
    "Book individual reduction" :bookIndividuals, after chapterIndividuals, 2
    "Book location reduction" :bookLocations, after chapterLocations, 2
    "Book object reduction" :bookObjects, after chapterObjects, 2
    "Book collective reduction" :bookCollectives, after chapterCollectives, 2
    "Event embedding and ANN candidates" :eventCandidates, after chapterEvents, 2

    section "Join"
    "Completion coordination" :join, after embeddings, 1
    "Completion coordination waits for individual branch" :milestone, after bookIndividuals, 0
    "Completion coordination waits for location branch" :milestone, after bookLocations, 0
    "Completion coordination waits for object branch" :milestone, after bookObjects, 0
    "Completion coordination waits for collective branch" :milestone, after bookCollectives, 0
    "Completion coordination waits for event candidates" :milestone, after eventCandidates, 0
    "Ingestion completed" :milestone, complete, after join, 0
```

Read this diagram as:

- scene detection is the first substantial worker stage
- `ScenesDetectedEvent` creates the main parallel fan-out
- chunking, chapter individual resolution, chapter location resolution, chapter object resolution, chapter collective resolution, and chapter event resolution can overlap
- embedding generation depends on chunking only
- book-level reductions depend on their corresponding chapter-level reductions only
- event embedding and ANN candidate generation depends on chapter event resolution only
- overall completion still waits for all required completion-barrier events, even if one branch finished much earlier than the others

This view is especially useful for reasoning about:

- where concurrency actually begins
- which stages are serial dependencies versus parallel siblings
- where a stalled branch prevents `IngestionCompletedEvent`
- which stages are likely long poles in end-to-end latency

### What This Pattern Deliberately Abstracts Away

This document describes the orchestration contract, not every internal sub-step.

- The internal triad window logic and temporal relation classification stay in the Triad Analysis Pattern.
- The append-only status model and LLM call correlation stay in the Observability Pattern.
- Per-service heuristics such as segmentation fallback, coordinate localization, or reduction internals are intentionally summarized here rather than exhaustively specified.
- **Concurrency and ordering guarantees** — The runtime threading model, cross-chapter ordering guarantees, late boundary repair behavior, and known concurrent-submission gaps are documented in the [Ingestion Concurrency Model](ingestion-concurrency-model.md).

The goal of this document is to make the causal event graph legible: which task runs, which event it emits, and which downstream tasks that event unlocks.

### Failure Handling
The `StageDispatcher.dispatch()` method wraps handler execution in an error boundary that performs: (1) atomic `TRIGGERED→RUNNING` guard, (2) idempotency check (`isAlreadyCompleted`), (3) handler execution with error boundary, (4) `StageCompletedEvent` emission. When a failure occurs, the dispatcher converts the exception to `StageResult.failure()` or `StageResult.retryableFailure()`, emits an `IngestionFailedEvent`, and updates the job status to `FAILED`. This update includes a structured `IngestionFailure` object containing the error type, message, and diagnostic properties.

Each handler defines its own retryability logic. For instance, LLM provider errors are marked as retryable, while a missing chapter entity is treated as a terminal state. The system specifically unwraps `TriadAnalysisException` to preserve granular details about which part of the temporal analysis failed. To prevent cascading failures in the event-driven loop, exceptions are recorded and swallowed by the handler rather than being rethrown.

### Retry and Replay Safety

LoreVault does not treat "idempotent handler" as a blanket claim. Strict idempotency means running the same operation multiple times has the same effect as running it once. Some current and future reducers may involve LLM-assisted analysis where a retry can legitimately produce a different latest projection.

The pipeline standard is therefore retry safety: a handler owns a defined projection scope, leaves that owned projection coherent on success, emits downstream events only after that coherent state exists, and treats failure or deferred work as something other than alternate success.

State checks such as existing-scene or existing-chunk lookups are useful guards, but they are not the whole contract. When a handler replaces or invalidates owned output, downstream projections that depend on that output must be rebuilt, marked stale, or otherwise prevented from being treated as current. See [Handler Retry-Safety](handler-retry-safety.md) and [Handler Design Contract](../../rules/handler-design-contract.md).

### Stage Provenance and Cleanup

Every domain node created during pipeline execution carries `stageId` as a `@Property("stageId")` on the node. The `StageExecutionContext` flows as an explicit method parameter from handler → service → repository.

The `deleteDataByStageId(stageId)` method cleans up all nodes and relationships created by a stage:

```cypher
MATCH (n {stageId: $stageId}) DETACH DELETE n
MATCH ()-[r {stageId: $stageId}]->() DELETE r
```

This enables safe stage replay: delete the previous stage's output, then re-run. See ADR-014 (explicit parameter threading) and ADR-015 (stage node provenance).

### Boundaries
- **Triad analysis details** — The internal logic for temporal triad classification is documented in the Triad Analysis Pattern.
- **Observability model** — The tracking of job statuses and LLM call records is handled by the Observability Pattern.
- **Scene coordinate localization** — The 3-tier fallback matching logic resides within the `SceneProcessingService` and is not covered here.
- **LLM prompt templates** — Templates are managed by the `PromptRepository` and are external to the pipeline flow.
- **Content hierarchy** — The management of Universes, Series, and Books is the responsibility of the `LibraryService`.
- **Ingestion concurrency model** — Threading executors, cross-chapter ordering guarantees, late boundary repair, and concurrent-submission gaps are documented in the [Ingestion Concurrency Model](ingestion-concurrency-model.md).

### Primary References
- `../../adr/004-keep-the-event-driven-ingestion-pipeline.md`
- `../../adr/013-stage-dispatcher-architecture.md`
- `../../adr/014-explicit-context-parameter-threading.md`
- `../../adr/015-stage-node-provenance.md`
- `../../adr/016-event-driven-pipeline-boundary.md`

---

## Contributor Constraints

### Executor Binding

Executor binding is managed by `StageDispatcher`, which routes `SCENE_SEGMENTATION`
to `sceneDetectionTaskExecutor` and all other stages to `ingestionLaneTaskExecutor`.
Individual handlers use `@ForStage(StageKey.X)` instead of `@Async @EventListener`.

```java
// Required — handler registration
@ForStage(StageKey.SCENE_SEGMENTATION)
@Component
public class SceneDetectionHandler implements StageOperation {
    @Override
    public StageResult execute(StageExecutionContext ctx) { ... }
}

// Wrong — old pattern, no longer used
@Async("ingestionLaneTaskExecutor")
@EventListener
public void onScenesDetected(ScenesDetectedEvent event) { ... }
```

`ingestionLaneTaskExecutor` is the named bean defined in `IngestionTaskExecutorConfig`.
Do not use bare `@Async` in any class in the `ingestion` package.

### Correlation Fields

Every ingestion event class must carry both `jobId` and `correlationId`.

- `jobId` — stable identifier for the ingestion job, used for status tracking and log
  aggregation across all pipeline stages.
- `correlationId` — per-request trace identifier, propagated into MDC on every thread
  that handles the event.

Neither field is optional. Events without both fields cannot be traced through async
log lines spanning multiple handlers and threads.

### Handler Ownership and Retry Safety

Every ingestion handler must follow the [Handler Design Contract](../../rules/handler-design-contract.md).

At minimum, a handler change must make clear:

- which nodes and relationships the handler owns
- what upstream projections it depends on
- which downstream stages become stale when its output changes
- what its success event means
- how retryable, deferred, empty, and terminal-failure outcomes differ

Do not publish success-shaped downstream events for claim contention, retry exhaustion,
or work that did not reach a coherent terminal state.

### Transactional Event Scoping

`SceneDetectionHandler` is invoked by `StageDispatcher` via `StageOperation.execute(ctx)`,
which routes the `StageTriggeredEvent` to the handler through the appropriate executor.
The `AFTER_COMMIT` scoping is handled by the event publication mechanism, not by individual
handler annotations.

All downstream handlers (`ChunkingHandler`, `EmbeddingHandler`,
`ChapterLocationConsolidationHandler`, `ChapterIndividualConsolidationHandler`,
`ChapterObjectConsolidationHandler`, `ChapterCollectiveConsolidationHandler`,
`ChapterEventConsolidationHandler`, `ChapterEventEmbeddingHandler`) are registered
with `@ForStage` and invoked through `StageDispatcher`, which manages executor
binding centrally.

**Why:** The `@TransactionalEventListener(AFTER_COMMIT)` prevents scene detection from
firing if the chapter ingestion transaction rolls back. This scoping is now enforced
by the event publication mechanism rather than individual handler annotations.
Downstream handlers process work that is already durably committed — they do not need
the publication-side transaction guarantee. Do not propagate `AFTER_COMMIT` further
downstream.

### Fan-In Coordinator

`IngestionPipelineCoordinator` expects exactly seven completion-barrier events before
firing `IngestionCompletedEvent`:

1. Embedding path: `ChunkingHandler → EmbeddingHandler`
2. Location path: `ChapterLocationConsolidationHandler → BookLocationConsolidationHandler`
3. Individual path: `ChapterIndividualConsolidationHandler → BookIndividualConsolidationHandler`
4. Object path: `ChapterObjectConsolidationHandler → BookObjectConsolidationHandler`
5. Collective path: `ChapterCollectiveConsolidationHandler → BookCollectiveConsolidationHandler`
6. Chapter event resolution: `ChapterEventConsolidationHandler`
7. Chapter event embedding and ANN candidate generation: `ChapterEventEmbeddingHandler`

The chapter event path has two completion-barrier events. `ChapterEventsConsolidatedEvent`
records that chapter event resolution finished and unlocks event embedding/ANN work.
`BookEventCandidatesGeneratedEvent` records that event vectors and same-book ANN
candidate pairs are ready.

**Do not add a new pipeline branch without updating the coordinator's expected count.**
The coordinator uses an atomic counter. Adding a branch without incrementing the expected
count causes premature completion — the job completes before all work is done.

If a branch fails, the coordinator must still reach a terminal state. Unhandled branch
failures leave the job permanently in `IN_PROGRESS`.
