# LoreVault Project Status

**Last Updated:** April 2026  
**Status:** Active — M1 merged and pushed, M2–M4 plan written, ready to implement  
**Primary Direction:** Simplify architecture, preserve mechanical sympathy, reduce indirection

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- 153 production Java files, 78 test files
- 263 tests passing
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.0, Neo4j 5.26

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation migration completed: canonical docs promoted, historical material archived to `docs/archive/`, ADRs and pattern library established
- M2–M4 implementation plan written to `docs/development/current/m2-m4-implementation-plan.md`

## What Is Next

Ready to implement. Work order:

1. **M2** — Annotate 6 content domain entities with `@Node`, migrate repositories, rewrite adapter without mapper, delete mirror Node classes and Neo4jMapper
2. **M3** — Kill all 5 remaining ports and adapters; services inject repositories directly
3. **M4** — Spring AI upgrade (1.0 → 1.1.4), replace EmbeddingModelAdapter with `Neo4jVectorStore`, replace TriadXmlParser with structured output, flatten to 12 feature packages

See `docs/development/current/m2-m4-implementation-plan.md` for full slice-by-slice details.

## Active Architectural Direction

- Keep Neo4j for graph + vectors
- Keep Spring AI and upgrade it
- Prefer direct services and repositories over internal port/adapter indirection
- Flatten toward feature-oriented packages
- Keep event-driven ingestion where it adds real value

## Open Decisions

- Relationship field placement on domain content entities (must resolve before M2 starts)
- InMemorySemanticSearchAdapter test replacement strategy (M3)
- Provider JSON Schema support confirmation before M4.3 (structured output)
- Neo4jVectorStore / Neo4j 5.26 HNSW compatibility check before M4.2

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
