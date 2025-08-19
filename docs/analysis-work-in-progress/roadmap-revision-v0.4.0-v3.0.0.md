###
THIS ANALYSIS IS WORK IN PROGRESS AND SUBJECT TO BE DISCARDED COMPLETELY
###

# LoreVault Roadmap Revision: v0.4.0–v3.0.0

**Date:** August 17, 2025  
**Status:** Approved  
**Context:** Integration of Claims–Entities refinement model into project roadmap

## Executive Summary

This revision restructures the v0.4.0–v3.0.0 roadmap to prioritize graph-first development, integrate the Claims–Entities model with skeleton timeline, and enable parallel development of Knowledge Graph (KG) and Retrieval epics. Key changes include moving temporal reasoning and advanced features to v2.0.0, enabling earlier v1.0.0 delivery, and adding lightweight UI validation at each milestone.

## Major Redistribution Changes

### Scope Rebalancing: 1.0.0 vs 2.0.0

**v1.0.0 Content Graph & Search (GA Foundation)**
- **Scope:** Production-ready ingestion pipeline, basic aggregation, spoiler gating v1, vector search v1, minimal admin UI, schema freeze
- **Exit Criteria:** p50 read <200ms on dev, zero known data-loss bugs, green e2e ingest, ops runbook
- **Timeline:** Earlier delivery with reduced scope

**v2.0.0 Advanced Reasoning & Curation**
- **Scope:** Temporal reasoning v2, identity resolution v2, aggregation v2, incremental recompute, reviewer workflow v2, search v2
- **Rationale:** Heavy lifting moved here allows 1.0 to be a stable foundation while 2.0 delivers advanced capabilities

### Graph-First Priority

The refined Claims–Entities model drives earlier delivery of:
- Events-as-Scenes with skeleton timeline (v0.5.x)
- Raw→normalized claims mapping (v0.6.0)
- Edge materialization and confidence aggregation (v0.6.1)
- Publication coordinates and spoiler gating (v0.7.x)

Vector search moves later (v0.9.x) to build on a stable graph foundation.

## Current Status Snapshot (as of Aug 17, 2025)

- v0.5.0 Neo4j Data Model Foundation: DONE (Chapter/Scene/Chunk persisted; ingestion green)
- v0.6.0 Publication Coordinates & Hierarchy: MOSTLY DONE
  - Chapter has universe/series/bookNumber/chapterNumber; Scene carries sceneIndex
  - Pending: materialized publication coordinates on Chunk for fast spoiler filters (optional optimization)
- v0.7.0 Vector Search Integration: IN PROGRESS
  - Embedding pipeline implemented; embeddings persisted on Chunk (embedding, hash, embeddedAt)
  - Pending: vector index setup + retrieval API; search method currently stubbed
- HITL draft/apply: DEFERRED (see proposal doc; revisit post 1.0)

Implication: proceed to close out 0.6.0 with chunk-level coordinate materialization (if needed), then complete 0.7.0 by adding retrieval surfaces and vector index. Keep small UI crumbs in parallel.

## Detailed Version Plan

### v0.5.x Graph Backbone and Evidence

**v0.5.0 Skeleton Timeline + Raw Claims**
- **Deliverables:**
  - Event nodes for scenes with default MEETS temporal relations
  - Raw Claims JSON schema + storage with validation
  - Certainty enum→weight mapping pipeline
  - Scene segmentation integration with Event projection
- **UI Crumbs:** Timeline skeleton viewer + raw claims table
- **Green Gates:** Unit tests for schema/weights, ingest→events/claims counts, admin pages render
- **Timeline:** 2-3 weeks

**v0.5.1 Temporal Relations Enrichment**
- **Deliverables:**
  - Allen interval relations (before/during/contains/overlaps/meets/equals) between consecutive scenes
  - Rationale text storage with source offsets
  - Cross-chapter/cross-book temporal linking
- **UI Crumbs:** Edge type coloring + rationale snippet display
- **Timeline:** 2 weeks

### v0.6.x Vocabulary and Facts

**v0.6.0 Catalog & Normalization**
- **Deliverables:**
  - Catalog service for properties (P:*) and relations (R:*)
  - Raw→normalized claims mapping APIs and batch processing
  - Vector + keyword search for taxonomy alignment
- **UI Crumbs:** Normalization preview (description→canonical mapping)
- **Timeline:** 3 weeks

**v0.6.1 Edge Materialization & Confidence**
- **Deliverables:**
  - Aggregate claims to materialize facts (ascriptions/relations/comparisons)
  - Polarity (assert/deny) handling and contested flags
  - Confidence thresholds and idempotency rules
- **UI Crumbs:** Entity "fact sheet" with support/deny bars and citations
- **Timeline:** 3 weeks

### v0.7.x Spoiler-Aware Graph Reads

**v0.7.0 Vector Retrieval (Complete the Embeddings Story)**
- **Deliverables:**
  - Neo4j vector index (or interim in-memory linear retrieval) wired to persisted Chunk.embeddings
  - Search service + REST endpoint (topK with threshold, optional chapter/book filter)
  - Basic scoring logs/metrics; guardrails for tiny corpora
- **UI Crumbs:** Minimal search box returning chunks with source snippets
- **Timeline:** 2-3 weeks

**v0.7.1 Publication Coordinates & Spoiler Gating**
- **Deliverables:**
  - Compute/publication coordinates finalized and applied to reads (as-of filtering)
  - Query-time spoiler filters across retrieval endpoints
- **UI Crumbs:** "View as of progress X" toggle on timeline/search
- **Timeline:** 2-3 weeks

### v0.8.x Character-Centric Entity Graph

**v0.8.0 Characters Foundation**
- **Deliverables:**
  - Character profiles, aliases, core relationships
  - Import path from normalized claims to entity nodes/edges
  - Entity resolution and deduplication for Characters
- **UI Crumbs:** Minimal entity browser for Characters (list/detail with timeline and facts)
- **Timeline:** 3-4 weeks

**v0.8.1 Locations + Items Bootstrap**
- **Deliverables:**
  - Apply same patterns to Locations and Items entity types
  - Validate taxonomy breadth and normalization patterns
- **Timeline:** 2-3 weeks

### v0.9.x Search Integration

**v0.9.0 Vector Search**
- **Deliverables:**
  - Chunk/entity/edge embeddings generation
  - Neo4j vector index setup
  - Hybrid search API implementation
- **UI Crumbs:** Search box with spoiler filtering
- **Timeline:** 3-4 weeks

**v0.9.1 Fulltext + Performance Tuning**
- **Deliverables:**
  - Lucene integration for fulltext search
  - Combined ranking algorithms
  - Performance optimization and basic SLOs
- **Timeline:** 2-3 weeks

### v1.0.0 Content Graph & Search Hardening

**Deliverables:**
- Schema v1 freeze with migration paths
- Error handling and observability polish
- API documentation and client SDKs
- Performance validation and SLO establishment
- Backup/restore and operational runbooks

**Timeline:** 3-4 weeks

### v2.x Entity Knowledge Graph Expansion

**v2.0.0 Advanced Reasoning & Curation (HITL deferred here)**
- **Deliverables:**
  - Temporal reasoning v2: non-consecutive event links, constraint solving, transitive closure
  - Identity resolution v2: probabilistic aliasing, merge/split tooling
  - Aggregation v2: source credibility weights, contradiction handling, retraction propagation
  - Incremental recompute: CDC-driven re-aggregation
  - Reviewer workflow v2 (HITL): roles, batch actions, audit log, draft/apply flow
  - Search v2: hybrid ranking, graph-informed reranking

**v2.1.0–v2.4.0 Entity Type Maturity**
- Apply v2 machinery to Characters, Locations, Items/Artifacts, Factions/Organizations
- Each delivers: normalization patterns, aggregation thresholds, targeted UI views, golden tests

**v3.0.0 Interactive Entity Browser**
- Consolidated UI over stable APIs
- Entity graph exploration, timeline navigation, spoiler-aware views
- Performance pass and UX polish

## Parallel Development Strategy

### Knowledge Graph Epic vs Retrieval Epic

**Decoupling Points:**
- Retrieval can start meaningfully after v0.5.0 with Read Facade over Events and Raw Claims
- First stable user-facing API targets post-v0.6.1 when Facts are materialized
- Public API v1 freeze occurs post-v0.7.0 with spoiler gating

**API Stability Levels:**
- **Alpha (post-0.5.0):** Experimental endpoints (`/api/exp/*`) for Events and Raw Claims
- **Beta (post-0.6.1):** Versioned API (`/api/v1/*`) with Facts and normalized Claims
- **GA (post-0.7.0):** Spoiler-aware public API with frozen v1 contracts

### Recommended Team Structure

**KG Team Focus:** v0.5.x–v0.7.x ingestion pipeline, claims processing, aggregation
**Retrieval Team Focus:** Read APIs, DTOs, pagination, UI components, search integration

**Handoff Artifacts:**
- Contract tests and fixture datasets
- Repository interfaces with test doubles
- Capabilities endpoint for runtime feature detection

## Success Metrics

### Per-Milestone Gates
- **Technical:** mvn test green, schema validation passing, e2e smoke tests
- **Functional:** UI crumb demonstrates new capability, data quality spot checks
- **Performance:** Basic latency bounds (p50 < 200ms for reads)

### Major Version Exit Criteria
- **v1.0.0:** Production deployment ready, SLOs met, zero critical bugs, API docs complete
- **v2.0.0:** Advanced reasoning features validated, curator workflow complete
- **v3.0.0:** Full-featured entity browser, accessibility compliance, performance tuned

## Risk Mitigation

**Dependency Risks:**
- Claims→Facts pipeline complexity: incremental delivery with UI validation at each step
- Neo4j schema evolution: explicit migration scripts and backward compatibility tests
- LLM cost/latency: batch processing and cost monitoring from v0.5.0

**Timeline Risks:**
- Scope creep in v2.0.0 advanced features: strict acceptance criteria and timeboxing
- Parallel team coordination: clear API contracts and weekly sync on interface changes

## Next Steps

1. Update architecture and specs documents to reflect this roadmap
2. Create project epics and initial sprints for v0.5.0
3. Define API contracts for KG/Retrieval team handoff
4. Set up initial project structure with module boundaries
5. Establish CI/CD pipeline with per-milestone gates

---

**Approved by:** Technical Lead Team  
**Next Review:** Post v0.6.0 delivery
