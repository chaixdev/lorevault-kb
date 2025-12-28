# Architectural Bloat Analysis

**Date:** December 27, 2025
**Status:** Superseded
**Goal:** Identify architectural bottlenecks hindering feature velocity and propose simplifications aligned with the project vision.

> **Note (Dec 28, 2025):** This document is kept for historical context (the original diagnosis), but the actionable direction is now captured in the approved plan:
> - [docs/development/refactor/PRAGMATIC_MODULITH_PLAN.md](docs/development/refactor/PRAGMATIC_MODULITH_PLAN.md)

## Executive Summary

The project has successfully transitioned to a modulith/event-driven approach, which is a strong foundation. However, significant "architectural bloat" remains in the persistence layer and data mapping strategies. The strict adherence to "Clean Architecture" principles (Ports & Adapters) has led to a "God Port" anti-pattern and excessive boilerplate mapping, which increases the friction for adding new features.

## 1. The "God Port" Anti-Pattern

**Component:** `ContentPersistencePort`

This interface has become a massive collection of unrelated methods. It violates the Interface Segregation Principle (ISP).

*   **Symptoms:**
    *   It handles **everything**: Chapters, Scenes, Chunks, Jobs, Status Records, LLM Calls, Queries, and the entire Publication Hierarchy (Universe, Series, Book).
    *   Any change to the data model (e.g., adding a field to `Scene`) forces a recompilation of this massive interface and its implementation.
    *   It makes testing difficult because mocking `ContentPersistencePort` requires dealing with dozens of irrelevant methods.

**Recommendation (updated):** Dismantle the "God Port" by moving stable Knowledge Graph persistence to direct repository usage (module-scoped), while retaining ports only for truly volatile infrastructure (LLM, embeddings, semantic/vector search). See the approved plan for execution details.

## 2. Adapter Complexity & Coupling

**Component:** `Neo4jContentPersistenceAdapter`

Because of the "God Port," this adapter is a monolith.

*   **Symptoms:**
    *   It injects **11 different repositories**.
    *   It mixes transactional boundaries for disparate domains (e.g., updating a Job status vs. creating a Universe).
    *   It is 400+ lines of code that is purely "pass-through" logic, delegating to Spring Data repositories.

**Recommendation (updated):** Prefer direct repository usage for stable core graph persistence. Where an adapter remains, it should exist only to encapsulate genuine infrastructure volatility or complex query mechanics (e.g., vector search via `Neo4jClient`).

## 3. Mapping Overhead

**Component:** `Neo4jMapper`, `*.domain.*` vs `*.infrastructure.persistence.neo4j.model.*`

There is a strict separation between "Domain Entities" and "Persistence Nodes," even though they are nearly identical.

*   **Symptoms:**
    *   `Neo4jMapper` is a large file full of repetitive `toDomain` / `toNode` methods.
    *   Adding a field requires changes in 3 places: Domain Class, Node Class, and Mapper.
    *   This "tax" on every field change slows down development significantly.

**Recommendation:**
*   **Merge Domain and Persistence Models:** For a project of this scale using an OGM (Object Graph Mapper) like Spring Data Neo4j, it is often more pragmatic to annotate the Domain entities directly with `@Node`.
*   This eliminates the `Neo4jMapper` entirely and reduces the file count by ~30%.
*   "Pure" domain logic can still exist on these annotated entities.

## 4. Service Layer & Event Handling

**Observation:**
The move to `ChunkingHandler` and `SceneDetectionHandler` is good. However, there is still a mix of "Services" that might just be helpers for these handlers.

*   `SceneProcessingService`: Consolidates XML parsing, coordinate localization, and persistence. This is a good facade, but ensure it doesn't become another "God Service".
*   `TriadOrchestrationService`: Good encapsulation of complex logic.

**Recommendation:**
*   Continue the trend of moving logic into Event Handlers.
*   Ensure Services are "Domain Services" (business logic) and not just "Transaction Scripts" that call the persistence port.

## 5. Legacy/Dead Code

**Component:** `InMemorySemanticSearchAdapter`

*   **Observation:** Marked as "v0.7.0 implementation". If the project is now using Neo4j native vector search, this code and its tests are technical debt.
*   **Recommendation:** Verify if `lorevault.search.provider=memory` is still a supported use case. If not, delete it.

## Summary of Proposed Actions

This document’s recommendations are now represented (and expanded with guardrails and definitions-of-done) in:

- [docs/development/refactor/PRAGMATIC_MODULITH_PLAN.md](docs/development/refactor/PRAGMATIC_MODULITH_PLAN.md)

If you need a short “why we changed direction” summary:

1. **Eliminate `Neo4jMapper`** by annotating domain entities with SDN (`@Node`).
2. **Dismantle the "God Port"** by using module-scoped repositories for the stable core graph, retaining ports for volatile infrastructure.
3. **Remove dead code** (e.g., `InMemorySemanticSearchAdapter`) once confirmed unsupported.
4. **Keep adapters only where they add leverage** (complex queries, backend volatility), otherwise prefer repositories directly.

These changes will significantly reduce the "lines of code per feature" metric and make the codebase easier to navigate.
