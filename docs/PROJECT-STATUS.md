# LoreVault Project Status

**Last Updated:** April 24, 2026  
**Reviewed Through Commit:** `18c2309`  
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
- Search and submission workflows now fail closed more consistently: typed lookup/backend failures remain distinct from legitimate empty retrieval outcomes or new-work creation paths
- Retrieval now supports baseline, graph-aware, and hybrid modes, with reciprocal-rank-fusion-style hybrid composition available through the ask surface and operator UI
- Temporal relation handling now uses a practical canonical vocabulary, and scene temporal linking preserves cross-chapter signals through both materialization and read-time ordering
- SSE job streaming is live at `/api/query/jobs/stream`, with keepalives and normalized status-update payloads for ingestion lifecycle events
- A basic operator UI is present under the Thymeleaf `ui/` surface: hierarchical library selection, batch chapter upload, live job visibility, operator actions, a query panel, and retrieval-mode selection

## What Is Done

- Early foundation work completed: service consolidation, the event-driven ingestion refactor, and the first pragmatic modulith pass.
- Core modernization through M2–M5 is complete: direct `@Node` domain mapping, removal of legacy internal indirection, Spring AI structured output adoption, and the `lorevault-core` / `lorevault-web` module split with feature-oriented package ownership.
- Product foundation shipped across search, ingestion, and operator flows: spoiler-aware search, budgeted scene detection, scoped individual/location resolution, coordinated ingestion completion, entity-aware Q&A, graph-aware/hybrid retrieval, SSE diagnostics streaming, the basic operator UI, and supporting ingestion/runtime hardening.
- **Event extraction groundwork shipped** — stage-1 scene analysis now persists event-mention evidence so event-oriented extraction has a durable foothold in the ingestion pipeline
- **Practical temporal semantics and scene temporal linking shipped** — temporal relation handling now uses a canonical practical vocabulary, triad-edge persistence is normalized, cross-chapter scene context is preserved during temporal analysis, and book-level ordering can now use cross-chapter temporal edges instead of chapter-local concatenation alone
- **Recent ingestion/runtime hardening shipped** — recent fixes serialized follow-up execution for stability, shifted triad-status correlation to stable scene IDs (with scene indexes retained as ordering metadata), aligned chunking with content-property configuration, and kept temporal-edge persistence mechanically consistent
- **Exception-semantics hardening shipped** — scene detection, chapter submission, semantic search/entity lookup, embedding generation, and query/UI boundaries now preserve typed business-failure meaning instead of collapsing known failure modes into generic runtime errors, false-success counters, or misleading no-evidence responses
- **Initial modern domain-modeling slice landed** — added a narrow `Mention` capability contract implemented by `IndividualMention`, `LocationMention`, and `EventMention`; added focused mention-contract tests; and wired a first concrete search-side consumer path while keeping persisted mention fields flat (no SDN nested value-object migration)
- **Strong cycle containment completed** — completed four bounded passes of architectural cycle-containment work: moved triad-status ownership to ingestion handler with per-triad callback semantics, removed AI→timeline inverter coupling, extracted normalized triad result contracts for ingestion workflows, removed `Scene implements timeline.Event` reverse edge while keeping Scene as the current Event carrier, introduced an ingestion-owned triad artifact lookup seam for timeline provenance reads, and turned architecture profile cycle test from failing (17→8→6→2) to passing (0 current violations in `CorePackageBoundaryArchitectureTest`)
- **Scene/triad ownership clarification shipped** — moved scene and triad workflow semantics out of legacy `ai` ownership into ingestion-owned packages: triad orchestration/builders now live under `ingestion.application.triad`, scene-stage workflow result carriers now live under `ingestion.application.scene`, stage failures now live under `ingestion.domain`, and `ai` stays focused on generic LLM infrastructure such as `LlmClient` and retry strategy wiring

## What Is Next

Near-term execution slices:
1. **Event extraction iteration**
   - Build on the current EventMention groundwork with better extraction quality, stronger boundaries, and clearer durable semantics for event evidence captured during ingestion
2. **Event aggregation and graph shaping**
   - Define and implement the next aggregation layer that groups extracted event evidence into more useful chapter/book-level structures without overcommitting to premature ontology complexity
3. **Ingestion reliability follow-up**
   - Resolve remaining cases where ingestion state can stick in intermediate states, especially around async completion signaling and status persistence alignment
4. **Retrieval and timeline quality follow-up**
   - Validate temporal-linking behavior and explore how event-aware retrieval should interact with existing baseline, graph-aware, and hybrid modes
5. **Web transport boundaries and type visibility**
   - Tighten the remaining edge-boundary leaks, type-visibility hotspots, and architecture-facing doc drift now that executable guardrails and strong cycle containment are in place
6. **Revisit domain modeling with modern Java contracts and value objects**
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
