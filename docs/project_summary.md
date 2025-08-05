# LoreVault: An Agentic Knowledge Ingestion Service

## Project Proposal (Comprehensive)

### Executive Summary

LoreVault is an intelligent, service-oriented system designed to automatically build and maintain a comprehensive lore database for fictional universes. The system provides a RESTful API for ingesting unstructured narrative content (e.g., chapters) and transforms it into a structured, queryable, and semantically indexed knowledge base within a unified PostgreSQL database. This service acts as a central "source of truth" for lore, accessible to a variety of potential client applications.

## 1. Project Vision

**Primary Goal:** Create an intelligent, automated lore master that extracts, organizes, synthesizes, and maintains knowledge from fictional works, exposed as a robust and scalable backend service.

**Core Value Proposition:** Transform the tedious task of manual note-taking and lore tracking into an automated, intelligent process that provides a consistent, high-quality, and programmatically accessible knowledge base that grows richer over time.

## 2. System Interaction & Architecture

### API-First, CQRS-based Architecture

The system is built as a service, prioritizing a clean separation of concerns using the Command Query Responsibility Segregation (CQRS) pattern. Clients interact with the system through a well-defined REST API.

**Commands (The "Write" Path):** Clients submit new content for processing via a command endpoint (e.g., POST /api/processing-jobs). This triggers a complex, asynchronous background process that performs the entire knowledge extraction and synthesis pipeline. This path is optimized for thoroughness and data integrity.

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
- Implement basic entity extraction for Characters.
- Implement simple, RAG-powered conflict detection.

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
- Implement advanced query capabilities (e.g., complex filtering, timeline queries).
- Develop comprehensive API documentation.
