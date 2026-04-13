# LoreVault Project Status

**Last Updated:** April 2026  
**Status:** Active — M1/M2/M3/M4 complete; spoiler-aware search, budgeted scene detection, and scoped individual resolution shipped  
**Primary Direction:** Simplify architecture, preserve mechanical sympathy, reduce indirection

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.1.4, Neo4j 5.26
- All domain content entities annotated `@Node` directly (no mirror Node classes)
- Internal port/adapter indirection removed — services inject concrete beans/repositories directly
- Package structure: 10 top-level feature-oriented packages in `lorevault-api` (`ai/`, `config/`, `content/`, `health/`, `ingestion/`, `library/`, `search/`, `support/`, `timeline/`, `web/`)
- Scene detection now enforces context-budget checks and deterministic segmented fallback for oversized chapters
- Individual mentions are persisted from scene detection output, with normalized-name and resolution metadata
- Scoped identity resolution is now active: `IndividualMention -> ChapterIndividual -> BookIndividual`
- Ingestion completion is coordinated across two post-scene branches: embedding completion and book-level identity reduction

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation taxonomy migration in progress
- **M2 complete** — All 6 content domain entities (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`) annotated `@Node`; mirror `*Node` classes deleted; `Neo4jMapper` deleted; repositories typed to domain entities; `Neo4jContentPersistenceAdapter` calls repositories directly
- **M3 complete** — `ContentPersistencePort` deleted; `EmbeddingException` deleted; `ContentPersistencePortTCK` deleted; all 7 integration tests migrated to `@Autowired Neo4jContentPersistenceAdapter`; `PromptRepositoryPort`, `SemanticSearchPort`, `EmbeddingPort`, `TemporalEdgePort` all gone in earlier commits
- **M4 complete** — Spring AI upgraded to 1.1.4 (BOM bump); `EmbeddingModelAdapter` replaced with Spring AI `EmbeddingModel`; `TriadXmlParser` replaced with `.entity(Record.class)` structured output; package structure flattened from layered packages to 10 top-level feature packages in `lorevault-api`
- **Spoiler-aware search shipped** — Per-request `SpoilerVisibility` DTO accepted on `/api/query/ask/vector` and `/api/query/ask/rag`; `ANY()` Cypher predicate filters chunks beyond the reader's per-series read-through position; `UnconfiguredSeriesPolicy` defaults to `HIDE`; oversample multiplier configurable in `application.yml`; documented in ADR 006
- **Budgeted scene detection shipped** — Pass-1 scene detection now checks model context budget before full-chapter submission and falls back to deterministic segmentation when needed; segment-boundary risk is tagged for later reconciliation
- **Scoped individual resolution shipped** — Triad extraction now persists `IndividualMention` evidence per scene, automatic chapter-level resolution groups mentions into `ChapterIndividual`, and automatic book-level reduction links those chapter identities into thin `BookIndividual` nodes
- **Coordinated ingestion completion shipped** — `IngestionCompletedEvent` is now emitted only after both branches triggered from `ScenesDetectedEvent` finish: `ChunksCreatedEvent -> EmbeddingsCompletedEvent` and `ChapterIndividualsResolvedEvent -> BookIndividualsReducedEvent`
- **Ingestion hardening shipped** — recent follow-up fixes standardized embeddings on 3072 dimensions, persisted `chunkIndex` on `HAS_CHUNK` relationships for deterministic ordering, tightened scene-localization retry behavior when too many scenes are dropped, and removed cartesian-product warning patterns from graph-link queries

## What Is Next

M1–M4 are complete. The architecture is now a flat, feature-oriented modulith with direct Spring AI integration and no port/adapter indirection. Recent feature work has shifted from structural cleanup to ingestion quality and retrieval correctness.

Current focus:
- Clarify the near-term execution plan as iterative product-facing slices rather than one large next tranche

Near-term execution slices:
1. **Location entity extraction**
   - Add one additional entity type so the next query/product work is not overly anchored on individuals
2. **Entity-aware Q&A improvements**
   - Improve query behavior against at least two entity types instead of building a character-only vertical
3. **Unified SSE diagnostics feed**
   - Add a normalized live stream for job progress, warnings, failures, and completion notifications
4. **Basic UI for chapter upload + SSE status visibility**
   - Build a minimal user-facing/operator-facing surface that can ingest chapters and watch progress in real time
5. **Additional entity types**
   - Extend beyond individuals and locations once the first entity-aware product slice proves the pattern

Broader planned directions remain intact after these slices:
- Broader entity extraction (Locations, Collectives, and later claims)
- Timeline modeling with Scene-as-Event entities
- Production hardening (observability, rate limiting, error budgets)
- Improved candidate generation and scoring for identity resolution after the current deterministic ladder

## Active Architectural Direction

- Keep Neo4j for graph + vectors
- Keep Spring AI current
- Prefer direct services and repositories over internal port/adapter indirection
- Flatten toward feature-oriented packages
- Keep event-driven ingestion where it adds real value

## Open Decisions

All 4 decisions from the original modulith plan are resolved. No architectural decision is currently blocking feature work; the main open question is next-feature sequencing.

## Canonical Entry Points

- `docs/architecture/README.md`
- `docs/development/current/`
- `docs/development/current/refactor-roadmap.md`
- `docs/development/current/m2-m4-implementation-plan.md`
- `docs/patterns/README.md`
- `docs/patterns/individual-resolution-ladder.md`
- `docs/adr/README.md`
- `docs/concepts/README.md`
- `docs/rules/README.md`
- `docs/development/research/spring-ai-keep-vs-drop-analysis.md`

## Historical / Transitional Notes

Documentation taxonomy cleanup is in progress. Historical material largely lives in `docs/archive/`, but canonical guidance is still being promoted into `docs/adr/`, `docs/patterns/`, `docs/concepts/`, and `docs/rules/`.
