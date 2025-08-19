# LoreVault: An Agentic Knowledge Ingestion Service

## Project Proposal (Comprehensive)

### Executive Summary

LoreVault is an intelligent, service-oriented system designed to automatically build and maintain a comprehensive lore database for fictional universes. The system provides a RESTful API for ingesting unstructured narrative content (e.g., chapters) and transforms it into a structured, queryable, and semantically indexed knowledge graph using Neo4j. This GraphRAG architecture combines the power of graph relationships with vector semantic search to create a unified knowledge base that serves as a central "source of truth" for lore, accessible to a variety of potential client applications.

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

- **v0.5.0: Neo4j Data Model Foundation** 📋
    - **Goal:** Implement core Neo4j schema and basic content storage without search
    - **Deliverable:** Universe→Series→Book→Chapter→Scene→Chunk nodes stored in Neo4j with Spring Data Neo4j
    - **Tasks:** Neo4j setup, Spring Data Neo4j integration, basic content data model implementation, PostgreSQL to Neo4j migration scripts

- **v0.6.0: Publication Coordinates & Hierarchy** 📋
    - **Goal:** Add publication ordering and coordinate materialization for spoiler-aware access
    - **Deliverable:** Complete hierarchy with bookOrder, chapterOrder, sceneIndex, and materialized coordinates on chunks
    - **Tasks:** Coordinate calculation logic, hierarchy validation, coordinate materialization service, basic hierarchy APIs

- **v0.7.0: Vector Search Integration** ✅ **COMPLETED**
    - **Goal:** Add semantic search capabilities without spoiler filtering
    - **Deliverable:** Vector embeddings stored in Neo4j, basic semantic search API endpoint
    - **Tasks:** ✅ Embedding generation pipeline, ✅ Linear in-memory semantic search, ✅ Search API implementation
    - **Achievement:** POST /api/search/semantic endpoint with natural language query support

- **v0.8.0: RAG Question Answering** 📋
    - **Goal:** Add intelligent question answering over retrieved chunk content
    - **Deliverable:** Natural language question answering with source attribution
    - **Tasks:** LLM integration port, RAG service implementation, POST /api/ask endpoint, citation logic

- **v0.9.0 Timeline construction with Scenes as Event entities**

- **v0.10.0: Spoiler-Aware Search** 📋
    - **Goal:** Implement publication coordinate filtering for spoiler-safe search
    - **Deliverable:** Search API that respects user reading progress and filters results appropriately
    - **Tasks:** User progress tracking, spoiler filtering logic, oversample-and-filter search pipeline, progress-aware APIs

- **v1.0.0: MVP with vector search** 📋
    - **Goal:** NLQ on chapter content
    - **Deliverable:** Complete API for chapter ingestion, hierarchical storage, and spoiler-aware search
    - **Tasks:** API documentation, performance validation, error handling polish, deployment readiness
- **v1.0.1: separate raw chapter & chunks & embeddings into postgres/pgvector as dedicated vector store, organise code around projection rerun for updated projection logic. keep neo nodes only with**
- **v1.1.0: Individual` Entity Extraction**
- **v1.2.0: Entity Projection**
- **v1.2.0: Actions modelling and projection**
- **v1.3.0: Claims modelling and projection**
- **v1.4.0: aggregate claims**
- **v1.5.0: Edge materialization for aggregate claims (canonical consensus graph)**

- **v2.0.0: Entity Knowledge Graph Foundation** 📋
    - **Goal:** Establish entity extraction and graph relationship patterns using Characters as the primary entity type
    - **Deliverable:** Character extraction, identity resolution, entity merging, and source attribution with publication coordinates
    - **Tasks:** Character extraction pipeline, entity deduplication logic, graph relationship modeling, spoiler-aware entity queries

- **v3.0.0: Interactive Entity Browser** 📋
    - **Goal:** Web UI for browsing extracted entities and their relationships
    - **Deliverable:** React/Vue application with entity detail views, relationship visualization, and spoiler-safe browsing
    - **Tasks:** Frontend development, entity detail components, graph visualization, citation and source navigation

- **v4.0.0: Multi-Entity Knowledge Graph** 📋
    - **Goal:** Expand to all core entity types using established patterns from Characters
    - **Deliverable:** Support for Locations, Items, Factions, Events with full relationship modeling
    - **Tasks:** Extend extraction pipeline to new entity types, enhance relationship discovery, build comprehensive entity APIs

- **v5.0.0: Timeline & Temporal Reasoning** 📋
    - **Goal:** Canonical timeline structure with scenes and events positioned temporally
    - **Deliverable:** Timeline visualization, temporal queries, and chronological consistency validation
    - **Tasks:** Timeline data model, temporal relationship extraction, chronological ordering algorithms, timeline browsing interface

## 2. System Interaction & Architecture

### API-First, CQRS-based Architecture

The system is built as a service, prioritizing a clean separation of concerns using the Command Query Responsibility Segregation (CQRS) pattern. Clients interact with the system through a well-defined REST API.

**Commands (The "Write" Path):** Clients submit new content for processing via a command endpoint (e.g., POST /api/ingestion/chapters). This triggers a complex, asynchronous background process that performs the entire knowledge extraction and synthesis pipeline. This path is optimized for thoroughness and data integrity.

**Queries (The "Read" Path):** Clients retrieve processed information via query endpoints (e.g., GET /api/entities/{type}/{id}). This path is optimized for high-performance, low-latency reads directly from the structured database.

### Automated Workflow

1. An API client (e.g., a script, a web UI) sends chapter text to the LoreVault API's command endpoint.
2. The API acknowledges the request immediately (HTTP 202 Accepted) and initiates an asynchronous processing task.
3. The backend LoreMaster Agent executes the full pipeline, updating the central knowledge graph in the Neo4j database.
4. Client applications can then immediately query the API to access the updated, cross-referenced information through both graph traversals and semantic vector search.

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
- **Cost-Effective AI Pre-processing:** Utilize efficient, cost-effective models for fast entity extraction and initial analysis before engaging more expensive models for complex reasoning.
- **RAG-Powered Conflict Detection:** Utilize a Graph-Augmented Generation (GraphRAG) pattern to identify contradictions or inconsistencies between new information and the existing knowledge graph.
- **Timeline Management:** Maintain chronological consistency across all entries.

### 3.3 Graph-Native Data Management

- **Graph Entity Storage:** Store all curated entity data as nodes and relationships in Neo4j, leveraging native graph traversal capabilities.
- **Semantic Vector Integration:** Use Neo4j's native vector indexing to store embeddings of source text and entity descriptions, enabling powerful semantic search within the graph context.
- **Relationship-First Modeling:** Model complex interpersonal and organizational relationships as first-class graph edges with properties.
- **Alias Management:** Track multiple names and spellings for each entity using graph relationships.
- **Confidence Scoring:** Rate the reliability of extracted information and relationships.

### 3.4 Automated Cross-Referencing

- **Graph Relationship Modeling:** Document complex interpersonal and organizational relationships as native graph connections with rich properties.
- **Contextual Connections:** Leverage both graph traversals and semantic vectors to discover implied or indirect relationships between entities.
- **Dynamic Updates:** Automatically update relationships as new information emerges through graph algorithms.
- **Relationship Inference:** Use graph analysis patterns to suggest missing or implicit connections.

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

- **Schema Compliance Checking:** Ensure all data written to the graph database conforms to the defined node and relationship models.
- **Required Field Validation:** Enforce data completeness rules for core entity information.
- **Cross-reference Integrity:** Verify that relationships point to valid, existing graph nodes.
- **Timeline Consistency Analysis:** Check for chronological paradoxes or inconsistencies in event data using graph traversals.

### 5.2 Conflict Management & Disambiguation

- **Automatic Detection:** Proactively identify contradictory information during the synthesis phase.
- **Intelligent Resolution:** Leverage an LLM with graph context to attempt to resolve conflicts based on relationship patterns.
- **Human Review Queue:** Flag complex or unresolvable conflicts in the graph database for human review.
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
- Define and implement the core Neo4j graph schema and Spring Data Neo4j entities for Characters.
- Set up the asynchronous processing pipeline for chapter ingestion.
- **Integrate Cost-Effective AI for entity extraction** - Deploy efficient models for cost-effective pre-processing.
- Implement basic entity extraction for Characters using cost-effective models.
- Implement simple, GraphRAG-powered conflict detection using more capable models only when necessary.

### Phase 2: Enhancement

**Goal:** Expand the breadth of knowledge and improve quality assurance.

**Tasks:**
- Add support for multiple entity types (Locations, Items, Factions) as graph nodes.
- Implement advanced relationship modeling in the graph database and API.
- Build out the Quality Assurance pipeline, including a robust Review Queue system.
- Enhance the /api/search endpoint with both graph traversal and semantic vector search.

### Phase 3: Optimization

**Goal:** Refine performance, usability, and advanced features.

**Tasks:**

- Optimize graph queries and indexing for performance at scale.
- Refine the AI prompts and models for higher accuracy and lower cost.
- **Optimize AI Model Usage** - Fine-tune model selection and usage patterns for optimal cost-performance balance.
- Implement advanced query capabilities (e.g., complex graph traversals, timeline queries).
- Develop comprehensive API documentation.

## 8. Technical Implementation Notes

### 8.1 Cost-Effective AI Integration

**AI Model Strategy:**
- **Model Selection:** Choose efficient, cost-effective models that provide good performance for entity extraction and basic analysis.
- **Cost Benefits:** Optimize model usage to minimize operational costs while maintaining quality.
- **Performance:** Target fast inference times (< 1 second per chunk) to enable responsive processing.
- **Fallback Strategy:** Implement graceful degradation with alternative models or providers if primary choices fail.

### 8.2 Hybrid AI Architecture

**Two-Tier Processing:**
1. **Tier 1 (Cost-Effective):** Efficient, cost-effective models for entity extraction, text classification, and basic analysis
2. **Tier 2 (Capability-Focused):** More capable models for complex reasoning, synthesis, conflict resolution, and graph relationship inference

**Benefits:**
- **Cost Optimization:** 80% of processing handled by cost-effective models
- **Quality Assurance:** Complex reasoning handled by more capable models when needed
- **Reduced Latency:** Efficient model selection minimizes processing time

## 9. Future Horizons

Beyond the core v1-v5 roadmap, the architecture supports advanced capabilities for long-term evolution:

- **v6.0: Natural Language Query Engine:** Plain English queries (e.g., "What is Kaladin's relationship with Bridge Four?") with synthesized answers and citations using GraphRAG patterns

- **v7.0: Proactive Knowledge Analysis:** 
    - **Knowledge Gap Detection:** Analyze graph structure to identify missing or implied relationships
    - **Consistency Validation:** Automated detection of timeline conflicts and entity contradictions
    - **Smart Re-processing:** Continuously improve entity extraction with newer models

- **v8.0+: Advanced Knowledge Synthesis:**
    - **Multi-Modal Ingestion:** Images (maps, character art), audio (audiobooks) 
    - **Generative Content:** Wiki articles, timelines, relationship summaries generated from graph data
    - **Multi-Tenant Universes:** Support multiple isolated fictional universes
    - **Configurable Entity Schemas:** Customize entity types per universe (magic systems vs. technologies)
