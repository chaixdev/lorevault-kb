# LoreVault Brainstorm

This directory contains future-facing and exploratory material.

Brainstorm docs are valuable, but they are not current source-of-truth documentation. They capture proposals, sketches, experiment ideas, and possible future directions.

## Naming Convention

All brainstorm filenames **must** use an ISO datetime prefix, not a month/year suffix.

**Format:** `YYYY-MM-DDTHHMM_topic-slug.md`

**Correct:**
- `2026-04-17T1430_async-ingestion-logging-philosophy.md`
- `2026-05-11T0930_orchestration-domain-separation.md`

**Wrong (do not use):**
- `async-ingestion-logging-philosophy-april-2026.md`
- `orchestration-domain-separation-may-2026.md`

The datetime prefix makes files sort chronologically by default, avoids ambiguous month names, and includes time-of-day precision so same-day iterations are distinguishable. Generate the timestamp with `date +%Y-%m-%dT%H%M`.

## Use This Folder For

- feature proposals
- API sketches
- unresolved design explorations
- future work that may later turn into a concept, ADR, or pattern

## Do Not Use This Folder For

- accepted decisions
- current implementation docs
- historical documents that already belong in `../archive/`

When a brainstorm becomes stable enough, either:

- promote its rationale into `../adr/`
- promote its current mechanism into `../patterns/`
- promote its durable abstraction into `../concepts/`

## Current Brainstorms

### Entity modeling

- [Consolidated advisory](entity-modeling/2026-04-11T1210_consolidated-advisory.md) — centralized actionable advice from multi-agent review: staging, kill list, implementation findings, warnings, escalation triggers, and open questions
- [Conceptual model critique](entity-modeling/2026-04-11T1210_concept-model-critique.md) — rigorous review of the pre-implementation concept model (entity types, claims, event DAG, confidence, catalog, CDSL) with kill list, staging recommendation, and implementation gaps
- [Individual extraction MVP spec](entity-modeling/2026-04-11T1210_individual-extraction-mvp-spec.md) — rope-bridge proposal for persisting scene analysis individual extraction into the graph without identity resolution
- [Oracle raw analysis](entity-modeling/2026-04-11T1210_oracle-raw.md) — unedited Oracle reasoning from the deep architectural review

### Individual resolution

- [Mention-to-individual linking brainstorm](entity-pipelines/2026-04-13T2332_mention-to-individual-linking-brainstorm.md) — consolidated exploration of how `IndividualMention` evidence nodes might be linked to canonical `Individual` nodes, including hook points, available graph signals, architecture options, and tradeoff analysis
- [Individual resolution proposal](entity-pipelines/2026-04-13T2332_individual-resolution-proposal.md) — cleaned-up proposed direction for `IndividualMention -> ChapterIndividual -> BookIndividual`, preserving the converged design without the wider brainstorming option space
- [Location extraction proposal](entity-pipelines/2026-04-13T2332_location-extraction-proposal.md) — location entity extraction design
- [Event entity extraction proposal](entity-pipelines/2026-04-20T1131_event-entity-extraction-proposal.md) — active working proposal for `EventMention -> ChapterEvent -> BookEvent` and the emerging direction toward a future root `ResolvedEvent`, including current decisions about scene-relative temporal semantics, DAG participation, and query-root traversal
- [Event entity extraction staged solution design proposal](entity-pipelines/2026-04-20T1131_event-entity-extraction-staged-solution-design-proposal.md) — event-first semantic aggregation design: rolling-triad entity likeness analysis, local aggregate cards, book-wide ANN candidates, semantic verification, chapter as the first spoiler-safe rich aggregate boundary, and later generalization to other entity types
- [Event entity extraction: external research (verbatim)](entity-pipelines/2026-04-20T1131_event-entity-extraction-external-research-verbatim.md) — preserved external surveys on mention→aggregate→resolved ladders, canonical query roots, event-graph analogues, and dual-purpose root nodes
- [Provenance and projection generation model brainstorm](entity-pipelines/2026-04-30T1010_provenance-generation-model-brainstorm.md) — exploratory proposal for generation-backed mutable projections, watermarks, dependency edges, and invalidation waves to make ingestion retries and manual reruns coherent
- [Claim entity linking proposal](entity-pipelines/2026-05-15T0106_claim-entity-linking-proposal.md) — claim-to-entity linking design

### Scene detection

- [Scene detection context budget and segmentation spec](scene-detection/2026-04-11T1210_scene-detection-context-budget-and-segmentation-spec.md) — chapter segmentation context budgeting, segmented processing fallback, and implementation notes
- [Scene detection naming analysis](scene-detection/2026-04-15T1816_scene-detection-naming-analysis.md) — rationale for renaming the old stage terminology before it hardened further; recommends `chapter segmentation` and `scene analysis`
- [Scene temporal linking brainstorm](scene-detection/2026-04-17T1113_scene-temporal-linking-brainstorm.md) — early exploration of scene temporal linking
- [Extra-local temporal resolution brainstorm](scene-detection/2026-04-18T0130_extra-local-temporal-resolution-brainstorm.md) — extended temporal resolution exploration
- [Scene temporal linking solution design proposal](scene-detection/2026-04-18T1113_scene-temporal-linking-solution-design-proposal.md) — implementation-ready V1 design for post-persistence scene temporal linking, triad artifact recovery, structural adjacency separation, and ambiguity handling

### Architecture

- [Pragmatic modulith plan](architecture/2026-04-11T1210_pragmatic-modulith-plan.md)
- [Event-driven architecture plan](architecture/2026-04-11T1210_event-driven-architecture-plan.md)
- [Async ingestion logging philosophy brainstorm](architecture/2026-04-17T0855_async-ingestion-logging-philosophy-brainstorm.md) — broader proposal for correlation, lifecycle logs, MDC/context propagation, and phased logging rollout around the async ingestion pipeline
- [StageRun DAG observability and recovery brainstorm](architecture/2026-04-30T1010_stage-run-dag-observability-and-recovery-brainstorm.md) — proposal to replace the linear `StatusRecord` chain with persisted stage-run DAG orchestration, stage-run events, fan-in status, and JVM crash recovery hooks
- [Orchestration / domain separation](architecture/2026-05-11T2027_orchestration-domain-separation.md) — explores whether and how to separate pipeline orchestration from domain logic, evaluates options from package convention through Spring Modulith to Maven module extraction, and recommends defining swappable seams (JobStateStore, PipelineEventPublisher, RelationCatalog, ChapterContentStore) before any structural split

### Query and retrieval

- [Graph-aware Q&A design](query/2026-04-12T1816_graph-aware-qa-design.md) — applies external GraphRAG pattern research to LoreVault's schema and now frames typed relation work as relation evidence harvesting plus catalog discovery before stable graph-aware routing
- [Multi-entity retrieval: external research](query/2026-04-12T1816_multi-entity-retrieval-external-research.md) — pressure-tests the shortestPath() proposal against 10+ external sources; verdict: scene co-occurrence beats path traversal for narrative "X+Y at Z" questions; includes Java/Spring/Neo4j stack mapping and staged implementation plan
- [Robust Q&A strategy report](query/2026-04-15T1816_robust-qa-strategy-report.md) — long-horizon strategy analysis: substrate vs mycelium vs fruiting body, question-space ambition, concept-model fit, bottleneck map, and phased direction for growing LoreVault into a robust lore Q&A system
- [Retrieval-oriented chunking and context packing](query/2026-04-20T1131_retrieval-oriented-chunking-and-context-packing.md) — chunking and context packing for retrieval
- [Claims model extensions parked](query/2026-05-05T1911_claims-model-extensions-parked.md) — parked relation/claims extensions including ClaimedEvent, hearsay chains, provisional relation clustering, and future catalog-module expansion
- [Claims event-sourcing proposal](query/2026-05-05T1911_event-sourcing-claims-proposed.md) — proposes append-only relation claims, per-boundary replay, and derived relation projections for spoiler-aware relation state
- [Provenance and publication coordinates lifecycle strategy](query/2026-05-11T2027_provenance-publication-coordinates-strategy.md) — consolidates the working distinction between provenance anchors, effective `PublicationCoordinates`, retrieval result contracts, and deliberate read-model materialization

### AWS cloud-native deployment

- [AWS cloud-native learning path](aws-cloud-native/2026-05-11T2027_aws-cloud-native-learning-path.md) — phased plan to transform LoreVault into a cloud-native AWS deployment as a learning project, covering ECS Fargate, S3, SQS/SNS, DynamoDB, Step Functions, CloudWatch, X-Ray, and IaC — mapped to specific CV gaps

### DevX and operator tooling

- [Operator dashboard and admin API brainstorm](devx/2026-04-16T0855_operator-dashboard-and-admin-api-brainstorm.md) — exploratory direction for the operator dashboard: timeline-based job visibility, SSE-backed status updates, selective stage reset/retrigger actions, and scoping the current API surface under an admin namespace while the end-user API remains undecided
- [Agentic TDD workflow brainstorm](devx/2026-04-24T1507_agentic-tdd-workflow-brainstorm.md) — agentic test-driven development workflow exploration
- [Python question-understanding tooling research](nlp/2026-04-16T1113_python-question-understanding-tooling-research.md) — practicality-first survey of Python NLP tooling for LoreVault query understanding, focused on integration feasibility, shortlist recommendations, and first experiments
- [NLP tooling research results](nlp/2026-04-18T1113_nlp-tooling-research-results.md) — results from NLP tooling evaluation

### n8n integration

- [Strategic n8n enhancement](n8n/2026-05-19T2154_strategic-n8n-enhancement.md) — maps the ingestion-vs-retrieval boundary between Spring Modulith and n8n, defines the Cypher-as-tool pattern for agentic retrieval, and sequences implementation against the AWS cloud-native learning path with explicit phase gates
