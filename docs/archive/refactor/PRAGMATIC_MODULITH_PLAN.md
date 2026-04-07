# Refactoring Plan: Pragmatic Modulith & Hybrid Architecture

**Date:** December 27, 2025
**Status:** Approved
**Goal:** Reduce architectural bloat by adopting a "Pragmatic Modulith" approach for core graph entities while retaining Ports & Adapters for volatile infrastructure (AI, Vector Search).

## 1. Core Philosophy

### 1.1 Architectural Layers
Based on the separation of concerns:
1.  **The Knowledge Graph (Core):** The domain model itself. It stands on its own as the source of truth.
    *   *Implementation:* Domain Entities annotated with `@Node`. Tightly coupled to the Graph structure.
2.  **AI/LLM Automation (Builder):** Services that automate the construction of the KG. These are input mechanisms.
    *   *Implementation:* `LlmPort`, `IngestionService`. Kept abstract to allow swapping LLM providers.
3.  **Access/Projections (Exposition):** How the KG is consumed (CRUD, RAG, Document Views).
    *   *Implementation:* API Controllers, `SemanticSearchPort`. Kept abstract to allow different search backends (Neo4j Vector, pgvector, etc.).

### 1.2 Implementation Strategy
*   **Pragmatic Persistence:** Domain entities for core graph structures (`Chapter`, `Scene`, `IngestionJob`) will be annotated directly with Spring Data Neo4j (`@Node`). This eliminates the `Neo4jMapper` and the duplicate "Node" class hierarchy.
*   **Direct Repository Usage:** Domain Services will inject Spring Data Repositories directly. The `ContentPersistencePort` (the "God Port") will be dismantled.
*   **Retained Abstractions:**
    *   `EmbeddingPort`: To allow switching embedding models (OpenAI, Gemini, etc.).
    *   `SemanticSearchPort`: To allow switching vector storage (Neo4j, pgvector, Pinecone).
    *   `LlmPort`: To allow switching LLM providers.

## 2. Phase 1: Ingestion Feature (Pilot)

We will start by refactoring the `IngestionJob` and related entities.

### 2.1. Merge Domain & Persistence Models
*   **Target:** `com.lorevault.api.domain.ingestion.*`
*   **Action:**
    *   Annotate `IngestionJob` with `@Node("IngestionJob")`.
    *   Annotate `StatusRecord` with `@Node("StatusRecord")`.
    *   Annotate `LlmCallRecord` with `@Node("LlmCallRecord")`.
    *   Move relationship fields (e.g., `@Relationship`) from the old `*Node` classes to these domain classes.

### 2.2. Update Repositories
*   **Target:** `com.lorevault.api.infrastructure.persistence.neo4j.repository.*`
*   **Action:**
    *   Update `IngestionJobGraphRepository` to return `IngestionJob` instead of `IngestionJobNode`.
    *   Update `StatusRecordGraphRepository` to return `StatusRecord`.
    *   Update `LlmCallRecordGraphRepository` to return `LlmCallRecord`.

### 2.3. Refactor Service
*   **Target:** `IngestionJobService`
*   **Action:**
    *   Inject `IngestionJobGraphRepository`, `StatusRecordGraphRepository` directly.
    *   Remove calls to `ContentPersistencePort`.
    *   Remove `Neo4jMapper` usage for these types.

### 2.4. Cleanup
*   **Delete:** `IngestionJobNode`, `StatusRecordNode`, `LlmCallRecordNode`.
*   **Delete:** Corresponding methods in `ContentPersistencePort`, `Neo4jContentPersistenceAdapter`, and `Neo4jMapper`.

## 3. Phase 2: Library & Content (Core Graph)

Once Phase 1 is stable, we apply the same pattern to the core content hierarchy.

### 3.1. Merge Domain & Persistence
*   **Target:** `com.lorevault.api.domain.content.*`
*   **Action:**
    *   Annotate `Universe`, `Series`, `Book`, `Chapter`, `Scene` with `@Node`.
    *   **Special Case: `Chunk`**:
        *   Annotate `Chunk` with `@Node("Chunk")`.
        *   Keep `embedding` field on `Chunk` for now (Neo4j storage).
        *   *Future Proofing:* When we move to pgvector, we will likely remove the `embedding` field from the Neo4j `@Node` and have the `SemanticSearchPort` handle the vector storage/retrieval separately.

### 3.2. Update Repositories
*   Update `ChapterGraphRepository`, `SceneGraphRepository`, etc., to use domain types.

### 3.3. Refactor Services
*   Update `LibraryService`, `IngestionService`, `SceneProcessingService` to use repositories directly.

## 4. Phase 3: Search & AI (Retained Ports)

*   **`SemanticSearchPort`**: Remains.
    *   *Current Implementation:* `Neo4jSemanticSearchAdapter` (uses `Neo4jClient` to query vector index).
    *   *Refactor:* Ensure it works with the new `@Node` annotated `Chunk` class.
*   **`EmbeddingPort`**: Remains as is.

## 5. Execution Steps for User

1.  **Approve Plan:** Confirm this hybrid approach.
2.  **Execute Phase 1:** I will perform the file edits for the Ingestion feature.
3.  **Verify:** Run tests to ensure no regression.
4.  **Execute Phase 2:** Proceed with Content entities.
