# LoreVault Project Status

**Last Updated:** April 2026  
**Status:** Active — M1/M2/M3 complete, M4 is next  
**Primary Direction:** Simplify architecture, preserve mechanical sympathy, reduce indirection

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- 248 tests passing
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.0, Neo4j 5.26
- All domain content entities annotated `@Node` directly (no mirror Node classes)
- All ports and adapters deleted — services inject concrete beans/repositories directly
- Package structure: still layered (`service/`, `infrastructure/`, `application/`), M4 will flatten

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation migration completed
- **M2 complete** — All 6 content domain entities (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`) annotated `@Node`; mirror `*Node` classes deleted; `Neo4jMapper` deleted; repositories typed to domain entities; `Neo4jContentPersistenceAdapter` calls repositories directly
- **M3 complete** — `ContentPersistencePort` deleted; `EmbeddingException` deleted; `ContentPersistencePortTCK` deleted; all 7 integration tests migrated to `@Autowired Neo4jContentPersistenceAdapter`; `PromptRepositoryPort`, `SemanticSearchPort`, `EmbeddingPort`, `TemporalEdgePort` all gone in earlier commits

## What Is Next

**M4** — Spring AI upgrade, structured output, package flatten:

1. **M4.1** — Spring AI upgrade `1.0 → 1.1.4` (bump BOM, fix any breaking API changes)
2. **M4.2** — Replace `EmbeddingModelAdapter` raw RestTemplate with Spring AI `EmbeddingModel` bean
3. **M4.3** — Replace `TriadXmlParser` with Spring AI `.entity(Record.class)` structured output
4. **M4.4** — Flatten from ~42 packages to 12 feature packages

See `docs/development/current/m2-m4-implementation-plan.md` for full slice-by-slice details.

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
