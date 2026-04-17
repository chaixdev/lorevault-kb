# LoreVault Brainstorm

This directory contains future-facing and exploratory material.

Brainstorm docs are valuable, but they are not current source-of-truth documentation. They capture proposals, sketches, experiment ideas, and possible future directions.

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

- [Consolidated advisory — April 2026](entity-modeling/consolidated-advisory-april-2026.md) — centralized actionable advice from multi-agent review: staging, kill list, implementation findings, warnings, escalation triggers, and open questions
- [Conceptual model critique — April 2026](entity-modeling/concept-model-critique-april-2026.md) — rigorous review of the pre-implementation concept model (entity types, claims, event DAG, confidence, catalog, CDSL) with kill list, staging recommendation, and implementation gaps
- [Individual extraction MVP spec — April 2026](entity-modeling/individual-extraction-mvp-spec-april-2026.md) — rope-bridge proposal for persisting scene analysis individual extraction into the graph without identity resolution
- [Oracle raw analysis](entity-modeling/oracle_raw.md) — unedited Oracle reasoning from the deep architectural review

### Individual resolution

- [Mention-to-individual linking brainstorm — April 2026](entity-pipelines/mention-to-individual-linking-brainstorm-april-2026.md) — consolidated exploration of how `IndividualMention` evidence nodes might be linked to canonical `Individual` nodes, including hook points, available graph signals, architecture options, and tradeoff analysis
- [Individual resolution proposal — April 2026](entity-pipelines/individual-resolution-proposal-april-2026.md) — cleaned-up proposed direction for `IndividualMention -> ChapterIndividual -> BookIndividual`, preserving the converged design without the wider brainstorming option space

### Scene detection

- [Scene detection context budget and segmentation spec — April 2026](scene-detection/scene-detection-context-budget-and-segmentation-spec-april-2026.md) — chapter segmentation context budgeting, segmented processing fallback, and implementation notes
- [Scene detection naming analysis — April 2026](scene-detection/scene-detection-naming-analysis-april-2026.md) — rationale for renaming the old stage terminology before it hardened further; recommends `chapter segmentation` and `scene analysis`

### Architecture

- [Pragmatic modulith plan](architecture/pragmatic-modulith-plan.md)
- [Event-driven architecture plan](architecture/event-driven-architecture-plan.md)
- [Async ingestion logging philosophy brainstorm — April 2026](architecture/2026-04-17_async-ingestion-logging-philosophy-brainstorm.md) — broader proposal for correlation, lifecycle logs, MDC/context propagation, and phased logging rollout around the async ingestion pipeline

### Query and retrieval

- [Robust Q&A strategy report — April 2026](query/2026-04-15_robust-qa-strategy-report.md) — long-horizon strategy analysis: substrate vs mycelium vs fruiting body, question-space ambition, concept-model fit, bottleneck map, and phased direction for growing LoreVault into a robust lore Q&A system
- [Graph-aware Q&A design — April 2026](query/2026-04-12_graph-aware-qa-design-april-2026.md) — applies external GraphRAG pattern research to LoreVault's schema: vector-seeded entity expansion, Cypher template registry, intent router, spoiler-safe entity injection, and a prioritized implementation plan
- [Multi-entity retrieval: external research — April 2026](query/2026-04-12_multi-entity-retrieval-external-research.md) — pressure-tests the shortestPath() proposal against 10+ external sources; verdict: scene co-occurrence beats path traversal for narrative "X+Y at Z" questions; includes Java/Spring/Neo4j stack mapping and staged implementation plan

### DevX and operator tooling

- [Operator dashboard and admin API brainstorm — April 2026](devx/2026-04-16_operator-dashboard-and-admin-api-brainstorm.md) — exploratory direction for the operator dashboard: timeline-based job visibility, SSE-backed status updates, selective stage reset/retrigger actions, and scoping the current API surface under an admin namespace while the end-user API remains undecided
- [Python question-understanding tooling research — April 2026](query/python-question-understanding/2026-04-16_python-question-understanding-tooling-research.md) — practicality-first survey of Python NLP tooling for LoreVault query understanding, focused on integration feasibility, shortlist recommendations, and first experiments
