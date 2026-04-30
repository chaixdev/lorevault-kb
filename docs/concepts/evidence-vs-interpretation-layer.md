# Evidence vs Interpretation Layer

**Status:** Conceptual  
**Scope:** Durable ownership boundaries for LoreVault graph knowledge, not a full implementation walkthrough  
**Primary sources:**
- [../brainstorm/query/2026-04-15_robust-qa-strategy-report.md](../brainstorm/query/2026-04-15_robust-qa-strategy-report.md) — explicit evidence/interpretation/runtime split and ownership test
- [../brainstorm/scene-detection/scene-detection-naming-analysis-april-2026.md](../brainstorm/scene-detection/scene-detection-naming-analysis-april-2026.md) — layered naming alignment
- [../brainstorm/devx/2026-04-16_operator-dashboard-and-admin-api-brainstorm.md](../brainstorm/devx/2026-04-16_operator-dashboard-and-admin-api-brainstorm.md) — evidence floor vs disposable derived layers framing
- [entity-claim-model.md](entity-claim-model.md) — claim/provenance-first conceptual direction
- [../patterns/ingestion/entity-resolution-ladder.md](../patterns/ingestion/entity-resolution-ladder.md) — evidence persisted before consolidation (Individual, Location, Object, and Collective lanes)

## Why This Exists

LoreVault needs a stable conceptual boundary between:

- what is canon-bearing, provenance-bearing, and durable
- what is derived to improve retrieval/reasoning and answer serving

Without this boundary, the graph tends to collapse into either:

- over-materialized derived structures that quietly become fake truth, or
- provenance-heavy raw structures forced onto the hot query path

This concept preserves the boundary so implementation can evolve without losing ownership discipline.

## Canonical Terms

Use **evidence layer** and **interpretation layer** as primary terms.

- **Evidence layer** = durable semantic substrate
- **Interpretation layer** = derived serving layer above that substrate

`comprehension layer` may appear as a synonym in discussions, but canonical docs should prefer **interpretation layer** for consistency.

## Core Separation

### Evidence layer owns

- canon-bearing meaning
- provenance and source grounding
- spoiler boundaries/publication-coordinate anchors
- extraction artifacts that must remain auditable

### Interpretation layer owns

- derived read models and projections
- retrieval acceleration structures
- serving packets/summaries and helper structures
- answer-shaping artifacts that are useful but rebuildable

### Ownership test (the hard boundary)

If deleting and rebuilding an artifact from the evidence layer would lose canon meaning, it belongs in **evidence**.

If deleting and rebuilding an artifact would only lose retrieval/serving convenience, it belongs in **interpretation**.

## Concrete Classification Examples

These examples are canonical for LoreVault:

- **Scene** -> **Evidence**
- **Inferred temporal relations** -> **Interpretation**
- **`IndividualMention`** -> **Evidence**
- **`ChapterIndividual` / thin `BookIndividual` aggregate nodes** -> **Interpretation**

In short:

- **Evidence = observed/extracted + provenance-bearing**
- **Interpretation = inferred/aggregated + rebuildable from evidence**

## Relationship to Q&A Runtime

Q&A runtime is not a truth-owning layer.

It consumes evidence and interpretation artifacts to produce answers, but final outputs should always be traceable back to evidence.

## Guardrails (Anti-Patterns to Avoid)

1. Treating interpretation artifacts (packets, aggregates, summaries) as canonical truth.
2. Delaying durable evidence persistence until after consolidation.
3. Allowing answer-serving artifacts to exist without provenance back-links to evidence.

## Relationship To Current Code

This document defines conceptual ownership boundaries.

Some of the shape is reflected in current implementation (for example, evidence-first persistence in resolution ladders), but this file should be read as durable guidance rather than a claim that every interpretation artifact pipeline is fully implemented today.

That is why this document belongs in `concepts/`, not `patterns/`.

## When To Promote Further

Parts of this concept can later feed:

- an ADR if the team formalizes additional hard boundaries or migration policy
- pattern docs when specific interpretation artifacts become stable implemented mechanisms
- rules docs when contributor-facing classification checks become routine in PR reviews
