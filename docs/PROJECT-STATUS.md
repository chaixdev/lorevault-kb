# LoreVault Project Status

**Last Updated:** April 2026  
**Status:** Active, post-refactor baseline established  
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
- Pragmatic modulith M1 merged locally on `main` (push to remote pending)
- Documentation migration completed: canonical docs promoted, historical material archived to `docs/archive/`, ADRs and pattern library established

## What Is Next

1. Push merged M1 baseline to remote
2. Upgrade Spring AI from 1.0.0 to 1.1.4
3. Continue modulith work with M2/M3
4. Migrate structured output from XML to JSON when provider support is confirmed

## Active Architectural Direction

- Keep Neo4j for graph + vectors for now
- Keep Spring AI and upgrade it
- Prefer direct services and repositories over internal port/adapter indirection
- Flatten toward feature-oriented packages
- Keep event-driven ingestion where it adds real value

## Open Decisions

- Push/rollout timing for merged M1 baseline on `main`
- Exact package flattening path
- Timing of Spring AI upgrade vs modulith M2
- Final provider path (OpenRouter / Nebius / Gemma 4 availability)

## Canonical Entry Points

- `docs/architecture/README.md`
- `docs/development/current/`
- `docs/development/current/refactor-roadmap.md`
- `docs/patterns/README.md`
- `docs/adr/README.md`
- `docs/development/research/spring-ai-keep-vs-drop-analysis.md`

## Historical / Transitional Notes

Documentation migration is complete. All historical material (refactor plans, version-scoped tickets, research) has been moved to `docs/archive/`. Version directories under `docs/development/versions/` retain only README redirect stubs.
