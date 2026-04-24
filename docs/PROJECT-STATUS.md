# LoreVault Project Status

**Last Updated:** April 23, 2026  
**Reviewed Through Commit:** `working tree (uncommitted)`  
**Status:** Active — core ingestion and retrieval slices are stable enough to iterate on event extraction and aggregation  
**Functional Goals:** Expand event extraction, aggregation, and downstream event-aware retrieval while continuing targeted ingestion hardening  
**Technical Goals:** Guard the architecture now that the codebase is split into separate `core` and `web` Maven modules

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.1.4, Neo4j 5.26
- All domain content entities annotated `@Node` directly (no mirror Node classes)
- Internal indirection layers removed — services inject concrete beans/repositories directly
- Maven structure: `lorevault-core` contains the feature-oriented core packages, and `lorevault-web` contains the HTTP/UI edge
- Core package structure: 7 top-level feature-oriented packages under `com.lorevault.api` in `lorevault-core` (`ai/`, `config/`, `content/`, `health/`, `ingestion/`, `library/`, `search/`)
- Edge package structure: `com.lorevault.api.web/**` lives in `lorevault-web`, with `web.command/`, `web.query/`, and `web.ui/` as the canonical edge shape
- `content` is no longer a flat bucket; `content.entities` and `content.timeline` are current semantic subareas, and `support/` plus top-level `timeline/` are no longer part of the active package map
- Scene detection now enforces context-budget checks and deterministic segmented fallback for oversized chapters
- Individual mentions are persisted from scene detection output, with normalized-name and resolution metadata
- Scoped entity resolution is now active for two lanes:
  - `IndividualMention -> ChapterIndividual -> BookIndividual`
  - `LocationMention -> ChapterLocation -> BookLocation`
- Stage-1 event extraction now persists event-mention evidence as groundwork for a future event-resolution lane
- Ingestion completion is coordinated across required post-scene branches: embedding completion, book-level Individual reduction, and book-level Location reduction
- Query routing now distinguishes direct entity lookup from broader narrative Q&A, with entity-aware RAG grounded in scene-level individual and location context
- Retrieval now supports baseline, graph-aware, and hybrid modes, with reciprocal-rank-fusion-style hybrid composition available through the ask surface and operator UI
- Temporal relation handling now uses a practical canonical vocabulary, and scene temporal linking preserves cross-chapter signals through both materialization and read-time ordering
- SSE job streaming is live at `/api/query/jobs/stream`, with keepalives and normalized status-update payloads for ingestion lifecycle events
- A basic operator UI is present under the Thymeleaf `ui/` surface: hierarchical library selection, batch chapter upload, live job visibility, operator actions, a query panel, and retrieval-mode selection

## What Is Done

- Service consolidation plan (Plan A) completed and merged
- Event-driven ingestion refactor (Plan B) completed and merged
- Pragmatic modulith M1 merged and pushed to `origin/main`
- Documentation taxonomy migration in progress
- **M2 complete** — All 6 content domain entities (`Universe`, `Series`, `Book`, `Chapter`, `Scene`, `Chunk`) annotated `@Node`; mirror `*Node` classes deleted; `Neo4jMapper` deleted; repositories typed to domain entities; `Neo4jContentPersistenceAdapter` calls repositories directly
- **M3 complete** — `ContentPersistencePort` deleted; `EmbeddingException` deleted; `ContentPersistencePortTCK` deleted; all 7 integration tests migrated to `@Autowired Neo4jContentPersistenceAdapter`; `PromptRepositoryPort`, `SemanticSearchPort`, `EmbeddingPort`, `TemporalEdgePort` all gone in earlier commits
- **M4 complete** — Spring AI upgraded to 1.1.4 (BOM bump); `EmbeddingModelAdapter` replaced with Spring AI `EmbeddingModel`; `TriadXmlParser` replaced with `.entity(Record.class)` structured output; package structure flattened from layered packages toward the current feature-oriented package map under `com.lorevault.api`
- **M5 complete** — Modularized codebase into separate `lorevault-core` and `lorevault-web` Maven modules; moved HTTP controllers and transport DTO ownership under the `web` module; core services refactored to use domain primitives and core records; eliminated the old `support` package; fixed project-wide Lombok configuration issues across modules; repaired all 300+ tests to align with the new module split.
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
- **Graph-aware and hybrid retrieval shipped** — RAG retrieval now supports baseline, graph-aware, and hybrid modes; the ask surface was split accordingly and the operator UI now exposes a mode selector for retrieval strategy comparison
- **Event extraction groundwork shipped** — stage-1 scene analysis now persists event-mention evidence so event-oriented extraction has a durable foothold in the ingestion pipeline
- **Practical temporal semantics and scene temporal linking shipped** — temporal relation handling now uses a canonical practical vocabulary, triad-edge persistence is normalized, cross-chapter scene context is preserved during temporal analysis, and book-level ordering can now use cross-chapter temporal edges instead of chapter-local concatenation alone
- **Recent ingestion/runtime hardening shipped** — recent fixes serialized follow-up execution for stability, shifted triad-status correlation to stable scene IDs (with scene indexes retained as ordering metadata), aligned chunking with content-property configuration, and kept temporal-edge persistence mechanically consistent
- **Code organization addressed** - refactored code organisation, core/web modules, semantic package structure.
- **Modern domain-modeling follow-up started** — added a narrow `Mention` capability contract implemented by `IndividualMention`, `LocationMention`, and `EventMention`; added focused mention-contract tests; and wired a first concrete search-side consumer path while keeping persisted mention fields flat (no SDN nested value-object migration)
- **Architectural hygiene follow-up slice completed (strong cycle containment)** — completed four bounded passes on `contain-strong-package-cycles-and-event-boundary-gaps`: moved triad-status ownership to ingestion handler with per-triad callback semantics, removed AI→timeline inverter coupling, extracted normalized triad result contracts for ingestion workflows, removed `Scene implements timeline.Event` reverse edge while keeping Scene as the current Event carrier, introduced an ingestion-owned triad artifact lookup seam for timeline provenance reads, and turned architecture profile cycle test from failing (17→8→6→2) to passing (0 current violations in `CorePackageBoundaryArchitectureTest`)

## What Is Next

M1–M4 are complete. The architecture is now a two-module, feature-oriented modulith with direct Spring AI integration and no leftover hexagonal-style indirection. Recent work has shifted from structural cleanup to operator-facing product slices, retrieval-mode expansion, timeline correctness, ingestion/runtime hardening, and follow-up boundary hygiene.

Current focus:
- Iterate on event extraction and event aggregation while preserving ingestion reliability and retrieval grounding

Near-term execution slices:
1. **Event extraction iteration**
   - Build on the current EventMention groundwork with better extraction quality, stronger boundaries, and clearer durable semantics for event evidence captured during ingestion
2. **Event aggregation and graph shaping**
   - Define and implement the next aggregation layer that groups extracted event evidence into more useful chapter/book-level structures without overcommitting to premature ontology complexity
3. **Ingestion reliability follow-up**
   - Resolve remaining cases where ingestion state can stick in intermediate states, especially around async completion signaling and status persistence alignment
4. **Retrieval and timeline quality follow-up**
   - Continue validating temporal-linking behavior and explore how event-aware retrieval should interact with existing baseline, graph-aware, and hybrid modes
5. **Architectural hygiene follow-up**
   - Maintain and extend executable guardrails now that top-level cycle checks are green; focus on preventing regressions and tightening boundary semantics around newly introduced workflow seams
6. **Domain-modeling follow-up continuation**
   - Continue the bounded modern-Java modeling pass by validating where additional narrow capability contracts are justified, while deferring value-object extraction until an explicit SDN-compatible migration path is planned

Broader planned directions remain intact after these slices:
- Broader entity extraction (Collectives and later claims)
- Broader event modeling beyond the current Scene-as-Event carrier
- Production hardening (observability, rate limiting, error budgets)
- Improved candidate generation and scoring for identity resolution after the current deterministic ladder

## Active Architectural Direction

- Keep Neo4j for graph + vectors
- Keep Spring AI current
- Prefer direct services and repositories over internal indirection layers
- Flatten toward feature-oriented packages
- Keep event-driven ingestion where it adds real value

## Open Decisions

All 4 decisions from the original modulith plan are resolved. No architectural decision is currently blocking feature work; the main open question is next-feature sequencing.

## Canonical Entry Points

- [Architecture](architecture/README.md)
- [Planning](planning/README.md)
- [Development Workflow](rules/development-workflow.md)
- [Patterns](patterns/README.md)
- [Entity Resolution Ladder](patterns/ingestion/entity-resolution-ladder.md)
- [Architecture Decisions](adr/README.md)
- [Concepts](concepts/README.md)
- [Rules](rules/README.md)
- [Brainstorm](brainstorm/README.md)

## Historical / Transitional Notes

Documentation taxonomy cleanup is in progress. Historical material largely lives in [Archive](archive/), while active work now centers on [Planning](planning/), [Brainstorm](brainstorm/), and the top-level canonical docs.
