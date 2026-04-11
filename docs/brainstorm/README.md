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
- [Individual extraction MVP spec — April 2026](entity-modeling/individual-extraction-mvp-spec-april-2026.md) — rope-bridge proposal for persisting pass 2 individual extraction into the graph without identity resolution
- [Oracle raw analysis](entity-modeling/oracle_raw.md) — unedited Oracle reasoning from the deep architectural review

### Individual resolution

- [Mention-to-individual linking brainstorm — April 2026](individual-resolution/mention-to-individual-linking-brainstorm-april-2026.md) — consolidated exploration of how `IndividualMention` evidence nodes might be linked to canonical `Individual` nodes, including hook points, available graph signals, architecture options, and tradeoff analysis

### Scene detection

- [Scene detection context budget and segmentation spec — April 2026](scene-detection/scene-detection-context-budget-and-segmentation-spec-april-2026.md) — pass 1 context budgeting, segmented processing fallback, and implementation notes

### Architecture

- [Pragmatic modulith plan](architecture/pragmatic-modulith-plan.md)
- [Event-driven architecture plan](architecture/event-driven-architecture-plan.md)
