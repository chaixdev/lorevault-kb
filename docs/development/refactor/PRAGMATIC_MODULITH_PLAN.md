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

### 1.3 Guardrails (to avoid “everything talks to everything”)
This plan intentionally relaxes Clean Architecture for the stable Knowledge Graph core. To keep the modulith maintainable, we apply these rules:

*   **Repository access is module-scoped:** A service/handler may inject repositories for its own module’s aggregates. Cross-module reads should go through a small query façade (see 4.2) or application-level APIs/events.
*   **Transactions stay at the orchestration layer:** Application services/handlers define transactional boundaries; entities/aggregates should not depend on Spring/SDN infrastructure.
*   **No new “God Service” or “God Query Service”:** If a class is trending toward being a universal helper, split it by module responsibility.

### 1.4 SDN Modeling Rules (IDs, relationships, equality)
Annotating domain entities with SDN reduces mapping overhead, but SDN has sharp edges. These rules keep the model predictable:

*   **Stable IDs only:** Prefer explicit IDs (`UUID`/String) with `@Id` (and `@GeneratedValue` only if you truly want SDN-generated IDs). Avoid relying on Neo4j internal IDs.
*   **Relationship properties are first-class:** If a relationship needs properties (ordering, indices, provenance, timestamps), model it with `@RelationshipProperties` instead of “just a list”.
*   **Avoid recursive equality:** Do not include relationships in `equals/hashCode`/`toString`. Equality should be based on the stable ID only.
*   **Ordering is explicit:** If ordering matters (e.g., chunks within a chapter/scene), store it explicitly (relationship property or node field) and test it.

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

### 2.5 Definition of Done (Phase 1)
*   Domain ingestion types are SDN entities (`@Node`) and repositories return domain types.
*   No codepath uses `Neo4jMapper` for ingestion types.
*   No codepath uses `ContentPersistencePort` for ingestion/job/status/LLM call persistence.
*   Unit tests cover idempotent writes and null-safety for ingestion relationships.
*   **Deletion budget hit:** remove the old ingestion `*Node` classes and the matching mapper/adapter/port surface area in the same phase.

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

### 3.4 Definition of Done (Phase 2)
*   Core content hierarchy is SDN-annotated (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`).
*   Relationship-property edges (e.g., ordering/index) use `@RelationshipProperties` where applicable.
*   No `Neo4jMapper` remains for content/library entities.
*   The former “God Port” has been dismantled into module-scoped persistence/query components (or removed where repositories suffice).
*   **Deletion budget hit:** delete the duplicate `*Node` hierarchy and mapper code for these entities.

## 4. Phase 3: Search & AI (Retained Ports)

*   **`SemanticSearchPort`**: Remains.
    *   *Current Implementation:* `Neo4jSemanticSearchAdapter` (uses `Neo4jClient` to query vector index).
    *   *Refactor:* Ensure it works with the new `@Node` annotated `Chunk` class.
*   **`EmbeddingPort`**: Remains as is.

### 4.2 Query Strategy (Phase 5+ critical)
Once the core uses repositories directly, we still need a disciplined way to serve read models and cross-module queries.

*   **Default:** module-scoped repository queries and Spring Data projections for simple reads.
*   **Complex graph reads:** a dedicated query component per area (e.g., `LibraryQueryService`, `IngestionQueryService`) using `Neo4jClient` when needed.
*   **Rule:** Avoid a single “universal query service”. If query APIs are growing, split by bounded context/module.

## 5. Execution Steps for User

1.  **Approve Plan:** Confirm this hybrid approach.
2.  **Execute Phase 1:** I will perform the file edits for the Ingestion feature.
3.  **Verify:** Run tests to ensure no regression.
4.  **Execute Phase 2:** Proceed with Content entities.

## 6. Phase 5+ (Suggested Roadmap Addendum)

### 6.1 Paydown Targets (Bloat Reduction)
*   Remove remaining adapter pass-through layers that simply mirror repository CRUD.
*   Consolidate/query-side patterns into module-scoped query services and projections.
*   Delete dead/legacy implementations (e.g., any unused in-memory search adapter) once confirmed not supported.

### 6.2 Migration Safety
*   Keep changes incremental: one module/aggregate cluster at a time.
*   Maintain idempotency guarantees for event-driven ingestion (retries must not duplicate relationships).
*   Add an integration profile test for relationship counts/order once per major module migration.
