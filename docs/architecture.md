# LoreVault: System Architecture Overview

### Introduction to the Architecture

This document set describes the software architecture for the **LoreVault** system. The primary goal is to create an intelligent, automated system that builds a structured and searchable knowledge base from unstructured narrative text.

The architecture is designed to be robust, scalable, and maintainable, leveraging the Spring Boot ecosystem. It treats the AI components not as magic boxes, but as specialized services with distinct responsibilities, integrated into a familiar enterprise application pattern.

A key architectural choice is the use of the **Command Query Responsibility Segregation (CQRS)** pattern. This is a natural fit for the system's workload:

- **Commands:** The "write" side of the system involves complex, resource-intensive, and potentially long-running processing tasks (e.g., "Process this chapter"). These operations don't need to return data immediately.
    
- **Queries:** The "read" side involves serving the structured, processed data to users, which needs to be fast and efficient.
    

Separating these two paths simplifies the design, improves performance, and enhances scalability. This documentation uses the Rozanski & Woods viewpoints and perspectives style to describe the system from various architectural standpoints.

### 1. Context Viewpoint

This viewpoint describes the relationships, dependencies, and interactions between the LoreVault system and its environment. It defines the system's boundaries and identifies its external dependencies.

System Scope and Responsibilities:

The LoreVault system is responsible for ingesting raw text (chapters), orchestrating a multi-step AI pipeline to extract and synthesize information, and persisting that information in a structured and searchable format. It exposes its capabilities via a REST API. The system's internal architecture is designed to minimize reliance on external AI services to manage cost and latency.

External Dependencies:

The system relies on external, third-party Large Language Model (LLM) services. From an architectural perspective, these are treated as remote, stateless, and potentially non-deterministic service dependencies. They are specialized "processors" that the system calls only when programmatic logic is insufficient. langextract is considered an internal, local library, not an external system.

Diagram: System Context

This diagram shows the LoreVault system as a single box in the center, interacting with its users and the external AI services it depends on.

```mermaid
graph TD
    subgraph "External Systems"
        A[Embedding Model API]
        B[Synthesis & Reasoning LLM API]
    end

    subgraph "LoreVault System"
        LV[LoreVault API]
    end

    User[API Client / User] -- "1. Submits chapter via REST API (Command)" --> LV
    LV -- "2. Sends text for vectorization (when new)" --> A
    LV -- "3. Sends filtered text + context for analysis (when necessary)" --> B
    User -- "4. Queries for processed lore (Query)" --> LV

    style LV fill:#D5E8D4,stroke:#82B366,stroke-width:2px
    style User fill:#DAE8FC,stroke:#6C8EBF,stroke-width:2px
```

### 2. Functional Viewpoint

This viewpoint describes the system's runtime functional elements, their responsibilities, interfaces, and the interactions between them. This view has been updated to incorporate `langextract` as a core part of the pre-processing stage.

Architectural Pattern: Command Query Responsibility Segregation (CQRS)

The system is structured around a CQRS pattern. The Command Path is explicitly designed with a local, intelligent pre-processing stage to protect and optimize the use of expensive AI components.

**Key Functional Components:**

- **API Gateway:** Exposes the REST endpoints. It routes Commands to the `Orchestration Service` and serves Queries by reading from the `Query Service`.
    
- **Orchestration Service:** The central coordinator of the Command path. Its primary role is to execute the high-level workflow, delegating specific tasks to the local and remote services.
    
- **Local Extraction & Filtering Service:** A critical component responsible for all deterministic, low-cost tasks. It wraps the `langextract` library (via JNI or a similar integration method). Its responsibilities include:
    
    - Text cleaning and chunking.
        
    - Change detection (via hashing) to prevent re-processing of known content.
        
    - Using `langextract` to perform high-fidelity mention extraction and clustering, producing a definitive list of all entities mentioned in a text chunk.
        
- **AI Client Components:** A set of specialized clients for targeted AI tasks.
    
    - `EmbeddingClient`: Generates vectors for _new or changed_ text chunks only.
        
    - `SynthesisClient`: The primary interface to the powerful LLM. It is only called when the `Local Extraction & Filtering Service` has identified entities that require analysis. It receives the text chunk and the list of entities to focus on, then performs the RAG-powered synthesis and structured data extraction.
        
- **Query Service:** A simple, read-only service that provides fast access to the persisted entity data for the Query path.
    
- **Persistence Service:** Provides a repository-based interface to the database.
    

Diagram: Functional Components with LangExtract

This diagram highlights the new Local Extraction & Filtering Service acting as an intelligent gatekeeper before the Orchestration Service engages the expensive AI components.

```mermaid
graph TD
    subgraph "API Layer"
        API[REST API Gateway]
    end

    subgraph "Application Core (Command Path)"
        OS[Orchestration Service]
        LEFS[Local Extraction & Filtering Service]
        AC[AI Client Components]
    end

    subgraph "Application Core (Query Path)"
        QS[Query Service]
    end

    subgraph "Persistence Layer"
        DB[(PostgreSQL Database)]
    end

    User[API Client] -- "Command (POST /chapters)" --> API
    API -- "1. Invokes Command" --> OS
    OS -- "2. Pre-process & Extract Mentions" --> LEFS
    LEFS -- "3. Return clustered entity mentions" --> OS
    OS -- "4. Coordinate AI Pipeline (only for chunks with entities)" --> AC
    AC -- "5. Calls External LLMs" --> External_LLMs[External LLM APIs]
    OS -- "6. Persists Results" --> DB

    User -- "Query (GET /entities)" --> API
    API -- "A. Invokes Query" --> QS
    QS -- "B. Reads Data" --> DB

    style LEFS fill:#F8CECC,stroke:#B85450,stroke-width:2px
    style QS fill:#DAE8FC,stroke:#6C8EBF
```

### 3. Information Viewpoint

This viewpoint describes the data architecture of the system. The data models remain the same, but the process for generating the data is now more reliable due to the improved pre-processing.

Data Storage Strategy:

The system utilizes a unified PostgreSQL database that serves two distinct roles, managed by a single persistence layer.

1. **Structured Data Store (The "Source of Truth"):** Standard relational tables store the canonical, structured information for each lore entity (Characters, Locations, etc.).
    
2. **Vector Store (The "Semantic Index"):** The `pgvector` extension stores numerical vector embeddings of source text chunks and curated entity descriptions to enable semantic search.
    

Information Flow:

The flow of information is a multi-stage process. Raw text is first processed by the Local Extraction & Filtering Service which uses langextract to create a high-fidelity list of entity mentions. This list acts as a set of "candidate tasks." Only these qualified tasks are then sent for AI analysis, ensuring that expensive LLM calls are focused and necessary. The resulting structured information is what finally gets persisted in the database.

Diagram: High-Level Entity Relationship Model

The ERD is structurally unchanged, as the final persisted data model is the same.

```mermaid
erDiagram
    CHARACTERS {
        UUID id PK
        String name
        String status
        String description "Curated summary text"
    }

    LOCATIONS {
        UUID id PK
        String name
        String region
        String description "Curated summary text"
    }

    SOURCE_CHUNKS {
        UUID id PK
        String source_chapter
        String text_content "Raw text from the chapter"
        vector embedding "Vector of the text_content"
    }

    ENTITY_EMBEDDINGS {
        UUID entity_id FK
        String entity_type
        vector embedding "Vector of the entity's description"
    }

    CHARACTERS ||--o{ ENTITY_EMBEDDINGS : "has"
    LOCATIONS ||--o{ ENTITY_EMBEDDINGS : "has"
```

### 4. Concurrency Viewpoint

This viewpoint describes how the system handles concurrent requests and manages its processing tasks. The sequence diagram is updated to show the new, more intelligent `langextract`-based filtering step.

Processing Model: Asynchronous Command Execution with Intelligent Pre-filtering

The asynchronous processing model is critical. The initial local extraction steps are fast, but the overall process remains potentially long-running.

1. **Request Reception:** The API controller immediately hands off the command for background processing and returns `HTTP 202 Accepted`.
    
2. **Local Extraction:** The first steps in the background thread are the deterministic checks (hashing) and the call to the `Local Extraction & Filtering Service`. This service uses `langextract` to find all entity mentions. Many text chunks may be discarded at this stage if they contain no entities, saving all downstream costs.
    
3. **Targeted AI Processing:** Only if a chunk contains entity mentions does the `Orchestration Service` proceed with the RAG loop and the expensive call to the `SynthesisClient`.
    

Diagram: Sequence Diagram for Chapter Ingestion (with LangExtract)

This diagram shows the new langextract step providing a high-quality filter.

```mermaid
sequenceDiagram
    participant Client
    participant API as REST API
    participant OS as Orchestration Service
    participant LEFS as Local Extraction Service (LangExtract)
    participant AC as AI Clients
    participant DB as PostgreSQL

    Client->>+API: POST /chapters (chapter text)
    API->>OS: processChapter(command)
    Note right of API: Returns HTTP 202 Accepted
    API-->>-Client: OK
    
    activate OS
    OS->>LEFS: 1. Chunk, hash & extract mentions
    activate LEFS
    LEFS->>DB: Check for existing hashes
    LEFS-->>OS: 2. Return clustered entity mentions for new chunks
    deactivate LEFS

    opt If chunks contain entity mentions
        OS->>AC: 3. Generate Embeddings for new chunks
        activate AC
        AC->>DB: Store new chunk vectors
        deactivate AC
        
        loop For Each Entity Mentioned in a Chunk
            OS->>DB: 4. Retrieve context (RAG-Retrieve)
            OS->>AC: 5. Synthesize & Extract Profile (RAG-Generate)
            activate AC
            AC->>DB: 6. Persist final entity record
            deactivate AC
        end
    end
    deactivate OS
```

### 5. Deployment Viewpoint

This viewpoint describes the physical environment into which the system will be deployed. The core deployment model is unchanged, but the main application container now has an additional local dependency.

Deployment Strategy: Containerization

The system is designed to be deployed as a set of containers for consistency and scalability.

**Runtime Components:**

1. **LoreVault Application Container:** A Docker container running the Spring Boot application JAR. This container must also include the `langextract` compiled library and any necessary runtime dependencies (e.g., C++ standard libraries) for it to function.
    
2. **PostgreSQL Container:** A Docker container running the PostgreSQL database instance with the `pgvector` extension enabled.
    
3. **External LLM Services:** Remote API endpoints for the powerful synthesis and reasoning models.
    

Configuration Management:

All external service configurations (database URLs, LLM API keys) will be managed via environment variables or a configuration service.

Diagram: Deployment Model

The diagram remains the same, with the understanding that the "LoreVault App Container" now has an internal, compiled dependency on langextract.

```mermaid
graph TD
    subgraph "User's Network"
        User[API Client]
    end

    subgraph "Cloud / On-Premise Infrastructure"
        LB[Load Balancer]
        
        subgraph "Application Cluster"
            App1[LoreVault App Container 1]
            App2[LoreVault App Container 2]
            AppN[...]
        end

        subgraph "Database Service"
            DB[(PostgreSQL Container)]
        end
        
        User -- "HTTPS" --> LB
        LB --> App1
        LB --> App2
        LB --> AppN
        
        App1 -- "JDBC" --> DB
        App2 -- "JDBC" --> DB
        AppN -- "JDBC" --> DB
    end

    subgraph "Third-Party Services (Internet)"
        LLM_API[LLM Service APIs]
    end

    App1 -- "HTTPS API Calls" --> LLM_API
    App2 -- "HTTPS API Calls" --> LLM_API
    AppN -- "HTTPS API Calls" --> LLM_API

    style App1 fill:#D5E8D4,stroke:#82B366
    style App2 fill:#D5E8D4,stroke:#82B366
    style AppN fill:#D5E8D4,stroke:#82B366
    style DB fill:#FFF2CC,stroke:#D6B656
```
