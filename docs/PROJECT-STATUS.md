# LoreVault Project Status

**Last Updated:** May 23, 2026
**Status:** Active — Durable ingestion orchestration shipped. Catalog M0+M1+M2 shipped. Dual-database architecture in place. n8n + AWS cloud-native strategy defined in companion brainstorm docs. Terminology alignment task identified. Code walkthrough cleanup plan parked.
**Functional Goals:** Complete ingestion pipeline hardening: Concept entity lane, relation evidence harvesting to shippable state, terminology alignment (resolution → reduction). Then: AWS Phase 1 foundation → n8n sprint (retrieval + HITL) → AWS native pipeline (SQS, DynamoDB, Step Functions).
**Technical Goals:** Enforce true domain isolation through Maven module boundary; Spring Modulith `CLOSED` module verification; Testcontainers PostgreSQL integration test suite; each module owns its DB transactions (catalog: PostgreSQL REQUIRES_NEW, core: Neo4j).

## What LoreVault Is

LoreVault is a lore-ingestion and retrieval system for fictional universes. It ingests narrative content, detects scenes, chunks content, generates embeddings, and supports semantic search and RAG over a Neo4j-backed knowledge graph.

## Current State

- Core pipeline works end to end
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.1.4, Neo4j 5.26
- All domain content entities annotated `@Node` directly (no mirror Node classes)
- Internal indirection layers removed — services inject concrete beans/repositories directly
- Maven structure: `lorevault-core` contains the feature-oriented core packages, `lorevault-web` contains the HTTP/UI edge, and `lorevault-catalog` is a standalone closed module with its own PostgreSQL database
- Core package structure: 7 top-level feature-oriented packages under `com.lorevault.api` in `lorevault-core` (`ai/`, `config/`, `content/`, `health/`, `ingestion/`, `library/`, `search/`)
- Catalog package structure: `com.lorevault.catalog` (no `api` segment) with public API surface and `internal` package enforced by `@ApplicationModule(CLOSED)` and ArchUnit rules
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
- **Durable ingestion orchestration shipped** — replaced in-memory `IngestionCompletionCoordinator` and `StatusRecord` with Neo4j-backed `Stage`/`StageOutput`/`StageDag` orchestrator. 15-stage pipeline DAG with fan-in barrier evaluation via conditional Cypher, stale trigger/RUNNING recovery (`@Scheduled`), cascade invalidation for rerun, idempotency via `StageOutput` nodes, `StageTriggeredEvent`/`StageCompletedEvent` universal lifecycle events. Deleted `IngestionJob`/`StatusRecord`/`IngestionCompletionCoordinator`. 13 handlers unified on `onTrigger` → guard → idempotency → execute → emit pattern. 4 commits on `feature/durable-ingestion-orchestration`, smoke-tested end-to-end. See: `docs/planning/2026-05-22T2300_durable-ingestion-orchestration.md`, `docs/reviews/2026-05-23T1200_durable-ingestion-orchestration-implementation-review.md`
- **Relation catalog M0–M3 shipped** — `lorevault-catalog` Maven submodule with closed module boundary (`@ApplicationModule(CLOSED)`, ArchUnit rules); public API types (`RelationCatalogService`, `RelationCatalogDefinition`, `RelationCatalogId`, `RelationQuery`, `RelationKindSignature`, `EmbeddingFunction`); PostgreSQL backend with one evolving wipe-state Flyway V1 schema, `ON CONFLICT DO NOTHING` idempotency, HikariCP connection pooling, pgvector extension, `embedding vector(1536)`, and HNSW cosine index; dual-database transaction boundary (catalog: PostgreSQL `REQUIRES_NEW`, core: Neo4j); `RelationClaim` updated with `catalogId` + resolved `definitionKey`; degradation mode when catalog is disabled; Testcontainers PostgreSQL integration tests; `NoOpRelationCatalogService` for catalog-disabled state; `CatalogHealthIndicator` bean (surfaces at `/actuator/health`); `pgvector/pgvector:pg16` Docker image

## What Is Next

### Phase A: Complete ingestion pipeline hardening

Near-term execution slices before pivoting to AWS/n8n:

1. **Cleanup from durable orchestration walkthrough** (parked)
   - 10 cleanup items identified in `docs/planning/2026-05-23T1530_submission-flow-cleanup.md`
   - Key items: `StageDispatcher` to remove 52 duplicated injection points, collapse `submitChapter`/`prepareChapter`, migrate legacy domain events to `StageCompletedEvent`, ban `var`, container-class guidance
   - `SceneDetectionHandler` decomposition parked in `docs/planning/2026-05-23T1600_scene-detection-handler-decomposition.md`

2. **Concept entity lane**
   - Implement the 6th regular entity ladder for Concept using the established `Mention → ChapterEntity → BookEntity` pattern
   - Covers species, technologies, artifact classes, doctrines, roles, and other narrative-significant categories
   - See: `docs/planning/2026-04-30T1237_concept-resolution-lane.md`
   - This is mostly mechanical application of patterns already established by the Individual, Location, Object, Collective, and Event lanes

3. **Relation evidence harvesting to shippable state**
   - Phase 0 is complete: `RelationClaim` nodes extracted and persisted, prompt engineering tuned, idempotency guards in place
   - Catalog M0–M3 is complete: exact + semantic matching resolves relation claims to stable catalog definitions
   - Remaining catalog-adjacent work is Phase 2 stable `REL` edge projection
   - Get promoted relation types into queryable typed edges while preserving `RelationClaim` as evidence/provenance
   - See: `docs/planning/2026-05-07T1917_relation-evidence-harvesting.md`, `docs/planning/2026-05-13T2027_relation-catalog-module.md`

4. **Terminology alignment: resolution/reduction → consolidation**
   - The pipeline uses three terms (resolution, reduction, aggregate) for the same class of operation
   - Rename chapter-level `*Resolution*` and book-level `*Reduction*` handlers, services, results, events → `*Consolidation*`
   - See: `docs/planning/2026-05-20T1536_entity-pipeline-terminology-alignment.md` — **decided: consolidation**
   - Apply before Concept lane implementation so the new lane uses canonical terminology

5. **Relation edge projection**
    - Use promoted catalog definitions to project queryable typed relation edges from persisted `RelationClaim` evidence
    - Keep `RelationClaim` as evidence/provenance; typed edges are the query acceleration layer
    - See: `docs/planning/2026-05-07T1917_relation-evidence-harvesting.md`, `docs/planning/2026-05-13T2027_relation-catalog-module.md`

### Phase B: AWS cloud-native foundation

6. **ECS Fargate deployment**
   - Containerize `lorevault-web`, deploy to ECS Fargate with ALB, VPC, security groups
   - IAM roles with least-privilege for ECS tasks — this may be the actual first AWS task before containers
   - Secrets Manager for API keys, CloudWatch structured logging
   - See: `docs/brainstorm/aws-cloud-native/2026-05-11T2027_aws-cloud-native-learning-path.md`

### Phase C: n8n sprint — retrieval and interaction

6. **HITL review gates**
   - `PENDING_REVIEW` job status, `ReviewController`, n8n human-in-the-loop workflow
   - Multi-channel notifications (Slack) via n8n's 400+ connectors

7. **Agentic retrieval MVP**
   - Cypher-as-tool endpoint (`POST /api/query/generate-cypher`) — Spring generates + executes validated Cypher
   - n8n LangChain Agent with tools pointing to Spring endpoints
   - See: `docs/brainstorm/n8n/2026-05-19T2154_strategic-n8n-enhancement.md`

### Phase D: AWS native pipeline

8. **SQS pipeline stages** — replace in-process Spring events with distributed message queues
9. **DynamoDB job state** — replace in-memory `ConcurrentHashMap` with conditional writes and TTL
10. **Step Functions orchestration** — replace `IngestionCompletionCoordinator` fan-in with state machine

### Deferred

Broader planned directions remain intact after these slices:
- Entity browser UI (Wikia-style entity browsing)
- Annotated reader (chapter text with graph-derived annotations)
- Q&A retrieval quality validation (run representative lore questions against retrieval modes)
- Event extraction and resolution tuning (evidence-triggered follow-up)
- Broader entity modeling (Concept and later claims)
- Production hardening (observability, rate limiting, error budgets)

### Sequencing rationale

The pipeline hardening items (A1 cleanup + A2 concept lane + A3 relation harvesting) close the remaining open work in the ingestion pipeline, leaving a coherent state before the AWS pivot. The AWS → n8n → AWS sequence (Phases B–D) reflects the insight from the [n8n strategy doc](brainstorm/n8n/2026-05-19T2154_strategic-n8n-enhancement.md): n8n teaches operational patterns (retry, HITL, agent loops) in hours to days; AWS teaches platform skills (IAM, VPC, SQS semantics, DynamoDB conditional writes) that n8n can't teach. The interleaved sequence uses n8n's speed for pattern learning, then returns to AWS for platform depth.

## Active Architectural Direction

- Keep Neo4j for graph + vectors; PostgreSQL for catalog definitions
- Keep Spring AI current
- Prefer direct services and repositories over internal indirection layers
- Keep feature-oriented top-level packages and capability-oriented internal packages
- Keep event-driven ingestion where it adds real value
- **New:** Ingestion pipeline (deterministic DAG) stays in Spring Modulith. Retrieval + interaction (agentic tool-calling, HITL, notifications) will be n8n-hosted with Spring providing tools via REST endpoints. Pipeline infrastructure (SQS, DynamoDB, Step Functions) will migrate to AWS in a dedicated clone. See companion docs for the strategy:
    - [n8n Ingestion-Retrieval Boundary Strategy](brainstorm/n8n/2026-05-19T2154_strategic-n8n-enhancement.md)
    - [AWS Cloud-Native Learning Path](brainstorm/aws-cloud-native/2026-05-11T2027_aws-cloud-native-learning-path.md)

## Open Decisions

- **Legacy domain events:** 14 domain-specific events (`ScenesDetectedEvent`, `ChunksCreatedEvent`, etc.) still exist but handlers now publish `StageCompletedEvent` instead. `JobStatusBroadcaster` still listens to old events but never receives them. Migration planned — see `docs/planning/2026-05-23T1530_submission-flow-cleanup.md` issue #10.

- **Terminology alignment:** The pipeline will use **consolidation** as the canonical term for both chapter-level and book-level entity pipeline steps. Rename `*Resolution*` → `*Consolidation*` and `*Reduction*` → `*Consolidation*`. Decision made; implementation deferred. See: `docs/planning/2026-05-20T1536_entity-pipeline-terminology-alignment.md`
- **n8n deployment model:** Self-hosted Docker alongside LoreVault, or n8n Cloud? Decision deferred to n8n sprint phase.
- **AWS clone strategy:** How much of `lorevault-kb` domain logic should be shared as a library vs. rewritten for `lorevault-aws`? Decision deferred to AWS foundation phase.
- **Cypher Template Catalog:** Should this become a fourth Maven module following the Relation Catalog pattern? Deferred — depends on agentic retrieval usage patterns.

## Canonical Entry Points

- [Architecture](architecture/README.md)
- [Planning](planning/README.md)
- [Durable Ingestion Orchestration Plan](planning/2026-05-22T2300_durable-ingestion-orchestration.md)
- [Durable Orchestration Implementation Review](reviews/2026-05-23T1200_durable-ingestion-orchestration-implementation-review.md)
- [Development Workflow](rules/development-workflow.md)
- [Patterns](patterns/README.md)
- [Entity Resolution Ladder](patterns/ingestion/entity-resolution-ladder.md)
- [Architecture Decisions](adr/README.md)
- [ADR-012: Dual-database transaction boundary](adr/012-dual-database-transaction-boundary.md)
- [Concepts](concepts/README.md)
- [Rules](rules/README.md)
- [Brainstorm](brainstorm/README.md)
- [n8n Ingestion-Retrieval Boundary Strategy](brainstorm/n8n/2026-05-19T2154_strategic-n8n-enhancement.md) — strategic n8n enhancement plan
- [AWS Cloud-Native Learning Path](brainstorm/aws-cloud-native/2026-05-11T2027_aws-cloud-native-learning-path.md) — AWS deployment strategy
- [Entity Pipeline Terminology Alignment](planning/2026-05-20T1536_entity-pipeline-terminology-alignment.md) — resolution → reduction terminology proposal

## Historical / Transitional Notes

Documentation taxonomy cleanup is in progress. Historical material largely lives in [Archive](archive/), while active work now centers on [Planning](planning/), [Brainstorm](brainstorm/), and the top-level canonical docs.
