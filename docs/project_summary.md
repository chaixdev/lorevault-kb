# LoreVault: An Agentic Knowledge Ingestion Service

## Project Proposal (Comprehensive)

### Executive Summary

LoreVault is an intelligent, service-oriented system designed to automatically build and maintain a comprehensive lore database for fictional universes. The system provides a RESTful API for ingesting unstructured narrative content (e.g., chapters) and transforms it into a structured, queryable, and semantically indexed knowledge base within a unified PostgreSQL database. This service acts as a central "source of truth" for lore, accessible to a variety of potential client applications.

## 1. Project Vision

**Primary Goal:** Create an intelligent, automated lore master that extracts, organizes, synthesizes, and maintains knowledge from fictional works, exposed as a robust and scalable backend service.

**Core Value Proposition:** Transform the tedious task of manual note-taking and lore tracking into an automated, intelligent process that provides a consistent, high-quality, and programmatically accessible knowledge base that grows richer over time.

### Project Roadmap

The project will be developed in major versions, each delivering a significant piece of functionality:

- **v0.1.0: API Shell & Basic Job Lifecycle** ✅
    - **Goal:** Submit chapter text via API, get job ID back, track job status

- **v0.2.0: Content Storage & Segmentation** ✅
    - **Goal:** Jobs actually process text and store Chapter/Chunk entities in database

- **v0.3.0: Scene Detection & Hierarchical Structure** ✅
    - **Goal:** AI-powered scene boundary detection creating Chapter → Scene → Chunk hierarchy using external LLM APIs with XML-based reliable parsing

- **v0.4.0: Production Polish & Architecture** ✅
    - **Goal:** Feature-oriented package structure, enhanced XML parsing, comprehensive testing, and production-ready scene detection pipeline

- **v0.5.0: Vector Embeddings & Semantic Search** 📋
    - **Goal:** pgvector integration, embedding generation, and semantic search capabilities

- **v0.6.0: Entity Extraction & Recognition** 📋
    - **Goal:** Character/location extraction, entity persistence, and relationship tracking

- **v0.7.0: Entity Intelligence & Synthesis** 📋
    - **Goal:** RAG-based entity merging, enhanced profiles, and cross-reference resolution

- **v1.0.0: Complete Lore Ingestion System** 📋
    - **Goal:** End-to-end demo: submit story chapters, get back queryable character profiles and semantic knowledge base

- **v2.0: Multi-Entity Knowledge Base**
    - **Goal:** Support for all core entity types (Characters, Locations, Factions, Items, Events) with full CRUD APIs
    - **Deliverable:** Complete REST API documentation showing endpoints for all entity types. Demo that ingests a complex story and produces queryable entities of all types with relationships
    - **Tasks:** Extend synthesis pipeline to all entity types, implement relationship modeling, build comprehensive query APIs, add advanced search capabilities

- **v3.0: Interactive Web Application**
    - **Goal:** Polished web UI with the signature "Annotated Reader Mode" feature
    - **Deliverable:** Web application where users can paste a chapter and see it rendered with clickable entity annotations. Clicking annotations shows entity details and cross-references
    - **Tasks:** Build React/Vue frontend, implement annotated reader component, create entity detail views, integrate with LoreVault API

## 2. System Interaction & Architecture

### API-First, CQRS-based Architecture

The system is built as a service, prioritizing a clean separation of concerns using the Command Query Responsibility Segregation (CQRS) pattern. Clients interact with the system through a well-defined REST API.

**Commands (The "Write" Path):** Clients submit new content for processing via a command endpoint (e.g., POST /api/ingestion/chapters). This triggers a complex, asynchronous background process that performs the entire knowledge extraction and synthesis pipeline. This path is optimized for thoroughness and data integrity.

**Queries (The "Read" Path):** Clients retrieve processed information via query endpoints (e.g., GET /api/entities/{type}/{id}). This path is optimized for high-performance, low-latency reads directly from the structured database.

### Automated Workflow

1. An API client (e.g., a script, a web UI) sends chapter text to the LoreVault API's command endpoint.
2. The API acknowledges the request immediately (HTTP 202 Accepted) and initiates an asynchronous processing task.
3. The backend LoreMaster Agent executes the full pipeline, updating the central knowledge base in the PostgreSQL database.
4. Client applications can then immediately query the API to access the updated, cross-referenced information.

## 3. Core Functionality

### 3.1 Automated Entity Extraction

- **Character Recognition:** Identify and track all characters mentioned in text.
- **Location Mapping:** Extract and categorize geographical and architectural locations.
- **Item Cataloging:** Track weapons, artifacts, documents, and other significant objects.
- **Faction Management:** Identify organizations, houses, groups, and political entities.
- **Event Documentation:** Capture and chronologically organize significant plot events.

### 3.2 Intelligent Knowledge Synthesis

- **Progressive Revelation:** Build entity profiles gradually as new information is revealed.
- **Status Tracking:** Monitor changes in character status, relationships, and affiliations.
- **Local AI Pre-processing:** Utilize Gemma 3B for fast, local entity extraction and initial analysis before engaging expensive external LLMs.
- **RAG-Powered Conflict Detection:** Utilize a Retrieval-Augmented Generation (RAG) pattern to identify contradictions or inconsistencies between new information and the existing knowledge base.
- **Timeline Management:** Maintain chronological consistency across all entries.

### 3.3 Structured & Semantic Data Management

- **Relational Entity Storage:** Store all curated entity data in structured PostgreSQL tables, enforcing a consistent schema.
- **Semantic Vector Indexing:** Use pgvector to store embeddings of source text and entity descriptions, enabling powerful semantic search capabilities.
- **Alias Management:** Track multiple names and spellings for each entity.
- **Confidence Scoring:** Rate the reliability of extracted information.

### 3.4 Automated Cross-Referencing

- **Relationship Modeling:** Document complex interpersonal and organizational relationships using relational database principles (e.g., join tables).
- **Contextual Connections:** Leverage the semantic index to discover implied or indirect relationships between entities.
- **Dynamic Updates:** Automatically update relationships as new information emerges.

## 4. Supported Entity Types

The system is designed to extract and manage a wide variety of entity types to build a rich and comprehensive knowledge base.

- **Characters:** Profiles including aliases, status, relationships, affiliations, traits, and skills.
- **Locations:** Geographical and political information, descriptions, and associated historical events.
- **Factions/Organizations:** Structures, leadership, goals, alliances, and member rosters.
- **Items/Artifacts:** Descriptions, properties, ownership history, and significance.
- **Events:** Chronological placement, participants, locations, causes, and outcomes.
- **Chapter Summaries:** Structured abstracts of source material with key entity cataloging.
- **Concepts/Ideas:** Abstract philosophies, belief systems, and cultural practices.
- **Technologies:** Technological systems, weapons, transportation, and scientific principles.
- **Species/Races:** Biological/cultural characteristics, origins, and inter-species relationships.
- **Languages:** Linguistic families, geographic distribution, and writing systems.
- **Religions/Beliefs:** Theological systems, doctrines, practices, and hierarchies.
- **Laws/Regulations:** Legal frameworks, judicial systems, and governance structures.

## 5. Quality Control & Data Integrity

### 5.1 Automated Validation

- **Schema Compliance Checking:** Ensure all data written to the database conforms to the defined entity models.
- **Required Field Validation:** Enforce data completeness rules for core entity information.
- **Cross-reference Integrity:** Verify that relationships point to valid, existing entities.
- **Timeline Consistency Analysis:** Check for chronological paradoxes or inconsistencies in event data.

### 5.2 Conflict Management & Disambiguation

- **Automatic Detection:** Proactively identify contradictory information during the synthesis phase.
- **Intelligent Resolution:** Leverage an LLM to attempt to resolve conflicts based on context.
- **Human Review Queue:** Flag complex or unresolvable conflicts in the database for human review.
- **Change History Preservation:** Maintain a history of all updates with source attribution.

### 5.3 Confidence Assessment

- **Source-based Reliability Scoring:** Assign a confidence score to extracted information based on the clarity of the source text.
- **Uncertainty Flagging:** Mark ambiguous information for careful consideration.

## 6. Integration Capabilities

### 6.1 API-First Architecture

The system is fundamentally a service. The RESTful API is the primary and sole point of integration, providing maximum flexibility.

**Decoupled Clients:** Allows any number of client applications to be built on top of the LoreVault API, such as:

- A static website generator for a public-facing wiki.
- A plugin for knowledge management tools like Obsidian or Logseq.
- A dedicated web-based lore browser for interactive exploration.

**Format Agnostic:** While the API will primarily use JSON, it can be extended to serve data in other formats as needed.

### 6.2 Source Material Flexibility

- The API ingestion endpoint is designed to accept various text formats.
- The core processing logic is built for chapter-by-chapter ingestion, ideal for sequential narratives.

## 7. Project Scope & Timeline

### Phase 1: Foundation 

**Goal:** Establish the core ingestion pipeline and API.

**Tasks:**

- Implement core REST API (Command & Query endpoints) using CQRS.
- Define and implement the core PostgreSQL schema and JPA entities for Characters.
- Set up the asynchronous processing pipeline for chapter ingestion.
- **Integrate Gemma 3B for local entity extraction** - Deploy lightweight local model for cost-effective pre-processing.
- Implement basic entity extraction for Characters using the local Gemma 3B model.
- Implement simple, RAG-powered conflict detection using external LLMs only when necessary.

### Phase 2: Enhancement

**Goal:** Expand the breadth of knowledge and improve quality assurance.

**Tasks:**
- Add support for multiple entity types (Locations, Items, Factions).
- Implement advanced relationship modeling in the database and API.
- Build out the Quality Assurance pipeline, including a robust Review Queue system.
- Enhance the /api/search endpoint with both structured and semantic search.

### Phase 3: Optimization

**Goal:** Refine performance, usability, and advanced features.

**Tasks:**

- Optimize database queries and indexing for performance at scale.
- Refine the AI prompts and models for higher accuracy and lower cost.
- **Optimize Gemma 3B inference** - Fine-tune local model performance and explore model quantization for faster execution.
- Implement advanced query capabilities (e.g., complex filtering, timeline queries).
- Develop comprehensive API documentation.

## 8. Technical Implementation Notes

### 8.1 Local AI Integration

**Gemma 3B Implementation:**
- **Model Hosting:** Gemma 3B will be deployed locally within the application container using ONNX Runtime or similar inference framework.
- **Cost Benefits:** Local execution eliminates per-request costs for initial entity extraction, making the system economically viable for high-volume processing.
- **Performance:** Fast inference times (< 1 second per chunk) enable real-time processing feedback.
- **Fallback Strategy:** If local model fails, system can gracefully fall back to external API for entity extraction.

### 8.2 Hybrid AI Architecture

**Two-Tier Processing:**
1. **Tier 1 (Local):** Gemma 3B for entity extraction, text classification, and basic analysis
2. **Tier 2 (External):** Powerful LLMs (GPT-4, Claude) for complex reasoning, synthesis, and conflict resolution

**Benefits:**
- **Cost Optimization:** 80% of processing handled locally at minimal cost
- **Quality Assurance:** Complex reasoning still handled by state-of-the-art models
- **Reduced Latency:** Local processing eliminates network round-trips for common tasks

## 9. Future Horizons

While the v1-v3 roadmap delivers a complete and powerful product, the architecture is designed to support even more advanced capabilities in the future. These represent potential long-term goals for v4.0 and beyond.

- **v4.0: Natural Language Query Engine:** Introduce a "magic" search capability allowing users to ask questions in plain English (e.g., "What is Kevin Jenkins's relationship with the HDF?") and receive a synthesized answer with citations. This would involve building a sophisticated query-understanding and answer-synthesis pipeline.

- **v5.0: The Proactive Agent:** Evolve the system from a passive service to a proactive knowledge partner. This could include features like:
    - **Knowledge Gap Analysis:** The agent analyzes its own knowledge graph to find implied but unconfirmed relationships, flagging them for review.
    - **Intelligent Re-processing:** The agent can automatically re-process entities with newer, more powerful AI models to continuously improve the quality of the knowledge base.

- **Beyond v5.0: The Multi-Modal & Generative Agent:**
    - **Multi-Modal Ingestion:** Expand beyond text to ingest images (maps, character art) and audio (audiobooks), using vision and transcription models to enrich the knowledge base.
    - **Generative Content:** Enable the agent to use its structured knowledge to generate new content on demand, such as wiki articles, timelines, or "what if" scenario analyses.

- **The Configurable & Multi-Tenant Universe:**
    - **Multi-Tenancy:** Evolve the system to support multiple, isolated "universes" under a single deployment.
    - **Configurable Entity Schemas:** Allow administrators to define which entity types are relevant for a specific universe. For example, a fantasy universe could track "magic systems" while a sci-fi universe tracks "technologies" and "spaceships," and realistic fiction would track neither. This would make the system adaptable to any genre.
