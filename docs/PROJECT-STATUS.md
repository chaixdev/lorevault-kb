# LoreVault Project Status

**Last Updated:** May 11, 2026  
**Reviewed Through Commit:** `3dc3d29`  
**Status:** Active — Phase 0 of relation evidence harvesting is implemented and validated with improved extraction data (30 relation claims from 5 chapters, 0% action-verb noise, 27% StronglyImplied certainty); ChapterIndividualCandidate constructor bug fixed; WebMvcTest failures fixed  
**Functional Goals:** Advance relation extraction from Phase 0 evidence into Phase 1 catalog matching; continue using the step execution API for iterative development  
**Technical Goals:** Preserve retry-safe ingestion semantics; use the agentic API loop for implement-inspect-improve cycles; plan Phase 1 catalog matching based on Phase 0 extraction data

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
- `lorevault-core` now uses capability-oriented internal packages instead of the old per-context `application/domain/infrastructure` split: `content/{association,chapter,chunk,mention,scene,timeline}`, `ingestion/{completion,content,events,job,pipeline,resolution,scene,submission,triad}`, `library/{book,series,service,universe}`, `search/{extraction,model,rag,semantic}`, and `ai/{chunking,embedding,llm}` plus shared local support packages where they remain semantically justified
- Scene detection now enforces context-budget checks and deterministic segmented fallback for oversized chapters
- Individual mentions are persisted from scene detection output, with normalized-name and resolution metadata
- Scoped regular entity resolution is now active for four lanes:
  - `IndividualMention -> ChapterIndividual -> BookIndividual`
  - `LocationMention -> ChapterLocation -> BookLocation`
  - `ObjectMention -> ChapterObject -> BookObject`
  - `CollectiveMention -> ChapterCollective -> BookCollective`
- Stage-1 event extraction now persists event-mention evidence, and the full event lane now carries chapter events through embedding, same-book ANN candidate generation, LLM semantic merge verification, and BookEvent write path — Stages 1–6 are shipped end to end
- Scene analysis now persists Individual, Location, Object, and Collective mention evidence end to end from triad-analysis output
- Ingestion completion is coordinated across required post-scene branches: embedding completion, book-level Individual reduction, book-level Location reduction, book-level Object reduction, book-level Collective reduction, chapter event resolution, and event ANN candidate generation
- Query routing now distinguishes direct entity lookup from broader narrative Q&A, with entity-aware RAG grounded in scene-level individual and location context
- Search and submission workflows now fail closed more consistently: typed lookup/backend failures remain distinct from legitimate empty retrieval outcomes or new-work creation paths
- Retrieval now supports baseline, graph-aware, and hybrid modes, with reciprocal-rank-fusion-style hybrid composition available through the ask surface and operator UI
- Temporal relation handling now uses a practical canonical vocabulary, and scene temporal linking preserves cross-chapter signals through both materialization and read-time ordering
- Async ingestion handlers have been realigned with transaction guidance, and recent status/LLM-call persistence fixes reduced mismatch risk between pipeline progress and durable records
- Architecture boundaries between `ai`, `content`, and `ingestion` are now enforced more explicitly in code, with follow-up test alignment on the current ingestion/triad flow
- Neo4j semantic-search test wiring and related book-reduction claim persistence paths have received another stabilization pass
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
- **Object and Collective resolution lanes shipped** — scene-analysis Object and Collective outputs now flow through triad normalization into persisted mentions, chapter-scoped aggregates, book-scoped aggregates, manual rerun endpoints, schema/index support, and ingestion completion fan-in
- **Strong cycle containment completed** — completed four bounded passes of architectural cycle-containment work: moved triad-status ownership to ingestion handler with per-triad callback semantics, removed AI→timeline inverter coupling, extracted normalized triad result contracts for ingestion workflows, removed `Scene implements timeline.Event` reverse edge while keeping Scene as the current Event carrier, introduced an ingestion-owned triad artifact lookup seam for timeline provenance reads, and turned architecture profile cycle test from failing (17→8→6→2) to passing (0 current violations in `CorePackageBoundaryArchitectureTest`)
- **Capability-oriented package reorg shipped** — completed the bounded `lorevault-core` package restructure across `ingestion`, `content`, `ai`, `library`, and `search`, replacing legacy internal layered buckets with capability-owned packages and adding a narrow `search.model` seam to keep `rag` and `semantic` cycle-free
- **Stage 4 event path shipped** — chapter events now continue beyond extraction into vector embedding and same-book ANN candidate generation, and ingestion completion now explicitly waits on the event branch alongside the regular entity lanes
- **Stages 5–6 event path shipped** — LLM semantic merge verification (Stage 5) evaluates each ANN candidate pair and decides MERGE / KEEP_SEPARATE / UNRESOLVED; BookEvent write path (Stage 6) clusters MERGE decisions and writes thin `BookEvent` nodes plus `ChapterEvent -[:REFERS_TO]-> BookEvent` edges; the event entity resolution pipeline is now end-to-end from EventMention through BookEvent
- **Post-split architecture and ingestion hardening continued** — recent follow-up commits enforced architecture boundaries across `ai`, `content`, and `ingestion`; aligned async ingestion handlers with transaction rules; tightened LLM-call/status persistence and book-reduction claim handling; stabilized semantic-search test wiring; refreshed individual-resolution coverage to match the current triad-analysis flow; and codified retry-safe handler ownership guidance
- **Step execution API surface shipped** — controllers, DTOs, event mapper, query endpoint, StepKey/StepDefinition/StepCatalog, *Operation interfaces, curl-driven skill, and supporting docs/rules; enables agentic step-by-step pipeline execution for iterative development
- **Relation evidence harvesting Phase 0 shipped** — scene analysis now emits open-ended inter-entity relation claims; `RelationClaim` nodes persisted with provisional type IDs, provenance, and evidence text; 33 unique relation claims extracted from 5 chapters across 11 scenes; Individual→Individual (39%) and Individual→Collective (24%) dominate; all claims Explicit certainty; extraction quality is high with no over-extraction; Phase 0 decision point met: proceed to Phase 1 catalog matching

## What Is Next

Near-term execution slices:
1. **Q&A and retrieval quality validation — immediate next focus**
    - Run representative lore questions against baseline, graph-aware, and hybrid retrieval using the richer four-lane entity graph
    - Classify failures as missing graph data, missing typed edges, missing retrieval paths, answer assembly gaps, or spoiler-gating issues
    - Use the findings as the decision point for whether the next implementation slice should be event extraction tuning, relation evidence harvesting/catalog discovery, Concept, or retrieval assembly
2. **Event extraction and resolution tuning — evidence-triggered follow-up**
    - Return to prompt/coref/reduction tuning when validation shows event questions fail because of over-extraction, missed merges, weak temporal qualifiers, or unstable canonicalization
3. **Relation evidence harvesting and catalog discovery**
    - Let scene analysis emit open-ended inter-entity relation claims, preserve raw relation names and usage hints, and use an in-process catalog module to return candidate relation IDs/correlation scores while unmatched observations accumulate for clustering and later promotion
4. **Concept entity lane**
   - Decide and implement the remaining regular entity ladder for Concept when validation shows species/category/technology questions are blocked by missing Concept anchors
5. **Event aggregation and graph shaping**
    - Define and implement the next aggregation layer that groups extracted event evidence into more useful chapter/book-level structures without overcommitting to premature ontology complexity
6. **Ingestion reliability and provenance follow-up**
    - Continue bounded retry-safety hardening, then evaluate the proposed ProjectionGeneration and StageRun DAG models for durable invalidation and crash recovery
7. **Web transport boundaries and type visibility**
    - Tighten the remaining edge-boundary leaks, type-visibility hotspots, and architecture-facing doc drift now that executable guardrails and strong cycle containment are in place
8. **Revisit domain modeling with modern Java contracts and value objects**
    - Continue the bounded modern-Java modeling pass by validating where additional narrow capability contracts are justified, while deferring value-object extraction until an explicit SDN-compatible migration path is planned

Broader planned directions remain intact after these slices:
- Broader entity extraction (Concept and later claims)
- Broader event modeling beyond the current Scene-as-Event carrier
- Production hardening (observability, rate limiting, error budgets)
- Improved candidate generation and scoring for identity resolution after the current deterministic ladder

## Active Architectural Direction

- Keep Neo4j for graph + vectors
- Keep Spring AI current
- Prefer direct services and repositories over internal indirection layers
- Keep feature-oriented top-level packages and capability-oriented internal packages
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
