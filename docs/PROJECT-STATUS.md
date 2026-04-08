# LoreVault Project Status

**Last Updated:** April 2026  
**Status:** Active — M1/M2/M3/M4 complete  
**Primary Direction:** Simplify architecture, preserve mechanical sympathy, reduce indirection

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- 186 tests passing
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.1.4, Neo4j 5.26
- All domain content entities annotated `@Node` directly (no mirror Node classes)
- All ports and adapters deleted — services inject concrete beans/repositories directly
- Package structure: 12 feature-oriented packages (`ai/`, `config/`, `content/`, `health/`, `ingestion/`, `library/`, `search/`, `support/`, `timeline/`, `web/`)

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation migration completed
- **M2 complete** — All 6 content domain entities (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`) annotated `@Node`; mirror `*Node` classes deleted; `Neo4jMapper` deleted; repositories typed to domain entities; `Neo4jContentPersistenceAdapter` calls repositories directly
- **M3 complete** — `ContentPersistencePort` deleted; `EmbeddingException` deleted; `ContentPersistencePortTCK` deleted; all 7 integration tests migrated to `@Autowired Neo4jContentPersistenceAdapter`; `PromptRepositoryPort`, `SemanticSearchPort`, `EmbeddingPort`, `TemporalEdgePort` all gone in earlier commits
- **M4 complete** — Spring AI upgraded to 1.1.4 (BOM bump); `EmbeddingModelAdapter` replaced with Spring AI `EmbeddingModel` bean; `TriadXmlParser` replaced with `.entity(Record.class)` structured output; package structure flattened from ~44 layered packages to 12 feature packages

## What Is Next

M1–M4 are complete. The architecture is now a flat, feature-oriented modulith with direct Spring AI integration and no port/adapter indirection.

Next direction candidates:
- Timeline modeling with Scene-as-Event entities
- Spoiler-aware search using publication coordinates
- Entity extraction (Characters, Locations, etc.)
- Production hardening (observability, rate limiting, error budgets)

## Active Architectural Direction

- Keep Neo4j for graph + vectors
- Keep Spring AI and upgrade it
- Prefer direct services and repositories over internal port/adapter indirection
- Flatten toward feature-oriented packages
- Keep event-driven ingestion where it adds real value

## Open Decisions

All 4 decisions from the original plan are resolved. No open decisions blocking M4.

## Canonical Entry Points

- `docs/architecture/README.md`
- `docs/development/current/`
- `docs/development/current/refactor-roadmap.md`
- `docs/development/current/m2-m4-implementation-plan.md`
- `docs/patterns/README.md`
- `docs/adr/README.md`
- `docs/development/research/spring-ai-keep-vs-drop-analysis.md`

## Historical / Transitional Notes

Documentation migration is complete. All historical material (refactor plans, version-scoped tickets, research) has been moved to `docs/archive/`. Version directories under `docs/development/versions/` retain only README redirect stubs.
