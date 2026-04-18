# LoreVault Project Status

**Last Updated:** April 17, 2026  
**Status:** Active — M1/M2/M3/M4 complete; entity-aware Q&A, unified SSE job feed, and operator UI slices shipped  
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
- Scoped entity resolution is now active for two lanes:
  - `IndividualMention -> ChapterIndividual -> BookIndividual`
  - `LocationMention -> ChapterLocation -> BookLocation`
- Ingestion completion is coordinated across required post-scene branches: embedding completion, book-level Individual reduction, and book-level Location reduction
- Query routing now distinguishes direct entity lookup from broader narrative Q&A, with entity-aware RAG grounded in scene-level individual and location context
- SSE job streaming is live at `/api/query/jobs/stream`, with keepalives and normalized status-update payloads for ingestion lifecycle events
- A basic operator UI is present under the Thymeleaf `ui/` surface: hierarchical library selection, batch chapter upload, live job visibility, operator actions, and a minimal query panel

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation taxonomy migration in progress
- **M2 complete** — All 6 content domain entities (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`) annotated `@Node`; mirror `*Node` classes deleted; `Neo4jMapper` deleted; repositories typed to domain entities; `Neo4jContentPersistenceAdapter` calls repositories directly
- **M3 complete** — `ContentPersistencePort` deleted; `EmbeddingException` deleted; `ContentPersistencePortTCK` deleted; all 7 integration tests migrated to `@Autowired Neo4jContentPersistenceAdapter`; `PromptRepositoryPort`, `SemanticSearchPort`, `EmbeddingPort`, `TemporalEdgePort` all gone in earlier commits
- **M4 complete** — Spring AI upgraded to 1.1.4 (BOM bump); `EmbeddingModelAdapter` replaced with Spring AI `EmbeddingModel`; `TriadXmlParser` replaced with `.entity(Record.class)` structured output; package structure flattened from layered packages to 10 top-level feature packages in `lorevault-api`
- **Spoiler-aware search shipped** — Per-request `SpoilerVisibility` DTO accepted on `/api/query/ask/vector` and `/api/query/ask/rag`; `ANY()` Cypher predicate filters chunks beyond the reader's per-series read-through position; `UnconfiguredSeriesPolicy` defaults to `HIDE`; oversample multiplier configurable in `application.yml`; documented in ADR 006
- **Budgeted scene detection shipped** — Chapter segmentation now checks model context budget before full-chapter submission and falls back to deterministic segmentation when needed; segment-boundary risk is tagged for later reconciliation
- **Scoped individual resolution shipped** — Triad extraction now persists `IndividualMention` evidence per scene, automatic chapter-level resolution groups mentions into `ChapterIndividual`, and automatic book-level reduction links those chapter identities into thin `BookIndividual` nodes
- **Scoped Location resolution shipped** — Triad extraction now persists `LocationMention` evidence per scene, automatic chapter-level resolution groups mentions into `ChapterLocation`, and automatic book-level reduction links those chapter locations into thin `BookLocation` nodes
- **Coordinated ingestion completion shipped** — `IngestionCompletedEvent` is now emitted only after all required branches triggered from `ScenesDetectedEvent` finish: `ChunksCreatedEvent -> EmbeddingsCompletedEvent`, `ChapterIndividualsResolvedEvent -> BookIndividualsReducedEvent`, and `ChapterLocationsResolvedEvent -> BookLocationsReducedEvent`
- **Ingestion hardening shipped** — recent follow-up fixes standardized embeddings on 3072 dimensions, persisted `chunkIndex` on `HAS_CHUNK` relationships for deterministic ordering, tightened scene-localization retry behavior when too many scenes are dropped, and removed cartesian-product warning patterns from graph-link queries
- **Entity-aware Q&A shipped** — query handling now classifies questions into at least two lanes: direct entity lookup through Cypher templates and narrative Q&A through vector-seeded graph expansion; search and RAG responses include scene-level individual and location context for better grounding
- **Unified SSE diagnostics feed shipped** — ingestion job updates now stream via `SseEmitter` from `/api/query/jobs/stream`, with normalized event payloads, keepalive comments, and chapter-ingestion event alignment for the live feed
- **Basic operator UI shipped** — the Thymeleaf operator console now supports hierarchical universe/series/book selection, batch chapter upload, live job refresh plus SSE event console, expandable job details, operator re-resolution actions, and a minimal RAG query panel
- **Async ingestion lifecycle logging shipped** — ingestion completion coordination and downstream reduction handlers now emit more detailed async lifecycle logging to improve operator/debug visibility

## What Is Next

M1–M4 are complete. The architecture is now a flat, feature-oriented modulith with direct Spring AI integration and no port/adapter indirection. Recent feature work has shifted from structural cleanup to product-facing operator surfaces, ingestion observability, and retrieval correctness.

Current focus:
- Harden the newly shipped operator-facing ingestion/query slice and continue expanding entity-aware retrieval beyond the first two entity lanes

Near-term execution slices:
1**Operator UI deepening**
   - Turn the current shell into a more complete operator surface with richer query workflows, better action feedback, and tighter integration with ingestion diagnostics
2**Ingestion reliability follow-up**
   - Resolve remaining cases where ingestion state can stick in intermediate states and keep event/status reporting mechanically aligned
3**Explore and research NLP tools** to improve entity extraction, both ingestion and q&a side.
   - 

Broader planned directions remain intact after these slices:
- Broader entity extraction (Collectives and later claims)
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
- `docs/planning/README.md`
- `docs/rules/development-workflow.md`
- `docs/patterns/README.md`
- `docs/patterns/individual-resolution-ladder.md`
- `docs/patterns/location-resolution-ladder.md`
- `docs/adr/README.md`
- `docs/concepts/README.md`
- `docs/rules/README.md`
- `docs/brainstorm/README.md`

## Historical / Transitional Notes

Documentation taxonomy cleanup is in progress. Historical material largely lives in `docs/archive/`, while active work now centers on `docs/planning/`, `docs/brainstorm/`, and the top-level canonical docs.
