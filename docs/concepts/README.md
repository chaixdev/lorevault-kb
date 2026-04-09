# LoreVault Concepts

This directory contains durable conceptual models.

Concept docs preserve important ideas that can continue guiding the system even when the implementation is partial, evolving, or different in detail.

## Use This Folder For

- foundational domain models
- conceptual system shapes worth preserving
- important abstractions that should not be lost in the archive

## Do Not Use This Folder For

- current implementation walkthroughs
- accepted decision records
- loose future ideas that have not yet earned conceptual status

## Concept Docs

- [Event DAG](event-dag.md) — durable temporal model (context and interpretation)
- [Entity-claim model](entity-claim-model.md) — durable claim-first knowledge model (context and interpretation)

## Primary Source Research

These documents preserve the original research in full detail. The concept docs above provide context and interpretation; these provide the specifics.

- [Narrative event DAG.md](Narrative%20event%20DAG.md) — triad-only, gazetteer-aware Event DAG specification
- [event-model.md](event-model.md) — Event/Scene entity subtype definition
- [Entity-Event-Claim-model.md](Entity-Event-Claim-model.md) — four-bin claim model, CDSL grammar, and output schemas
- [model_and_CDSL.md](model_and_CDSL.md) — consolidated playbook (claim model + CDSL from a different angle)
- [core-domain-model-and-graph-process-restructured.md](core-domain-model-and-graph-process-restructured.md) — end-to-end domain model, ingestion pipeline, and spoiler-aware querying
