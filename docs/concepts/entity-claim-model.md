# Entity-Claim Model

**Status:** Conceptual  
**Scope:** Durable conceptual model, not current implementation truth  
**Primary sources:**
- [Entity-Event-Claim-model.md](Entity-Event-Claim-model.md) — four-bin claim model, vocabulary catalogs, confidence formula, projection edges, output schemas, and the Claim DSL (CDSL)
- [model_and_CDSL.md](model_and_CDSL.md) — consolidated playbook covering the same model and CDSL from a slightly different angle
- [core-domain-model-and-graph-process-restructured.md](core-domain-model-and-graph-process-restructured.md) — end-to-end domain model including entity taxonomy, ingestion pipeline, claim extraction, confidence aggregation, and spoiler-aware querying
- [`../development/current/data-model/schemas/claims.schema.json`](../development/current/data-model/schemas/claims.schema.json)

## Why This Exists

LoreVault needs a way to preserve narrative knowledge without forcing premature certainty.

The entity-claim model captures an important long-term direction: treat extracted assertions as evidence-bearing claims first, then aggregate and project the stable parts into a graph suitable for retrieval.

This model is important enough to preserve even though the current codebase only partially implements it.

## Core Idea

Separate:

- **entities** — the canonical things in the world
- **claims** — assertions about those things, grounded in text and source context
- **projection** — the read-optimized graph shape derived from accumulated evidence

This avoids forcing a single truth too early, which matters in narrative text with unreliable viewpoints, gradual revelation, and conflicting testimony.

For the full data model — including claim bins, vocabulary catalogs, ingestion pipeline, confidence aggregation, projection edges, output schemas, the CDSL grammar, and worked examples — see the primary source documents listed above.

## Why It Matters

This concept supports several long-term goals:

- preserving provenance instead of collapsing everything into one "fact"
- handling uncertainty and contradiction explicitly
- making spoiler-aware gating possible at the evidence layer
- allowing offline aggregation before graph projection
- keeping extraction and curation decoupled from query-time traversal

## Relationship To Current Code

This is **not** the current full implementation model.

Today, LoreVault clearly implements:

- content hierarchy and retrieval over `Universe → Series → Book → Chapter → Scene → Chunk`
- spoiler-aware retrieval using publication coordinates
- ingestion, scene detection, chunking, embeddings, and temporal work

But the full claim-first persistence and projection model remains only partially reflected in the codebase.

That makes this document a **concept**, not a **pattern**.

## Relationship To Current Schema

The current claims schema is useful as a bridge between concept and implementation:

- it preserves `subjectId`, certainty, polarity, qualifiers, offsets, and publication coordinates
- it allows either canonical IDs (`propertyId`, `relTypeId`) or descriptive placeholders (`propertyDescription`, `relationDescription`)
- it keeps claim payloads structurally constrained even before the full conceptual model is implemented end to end

The schema should be treated as a practical implementation foothold, not proof that the entire conceptual model is already realized.

## What This Document Is Not

This document does **not** claim that LoreVault currently has:

- a complete claim aggregation pipeline
- a finished endorsement/confidence rollup implementation
- a fully realized projection layer for all conceptual claim bins

Those ideas remain valuable, but they belong to future implementation work.

## When To Promote Further

Parts of this concept can later graduate into:

- a **pattern**, if claim-first persistence and projection become implemented mechanisms
- an **ADR**, if the team explicitly chooses this model over competing approaches
- a **rules** doc, if claim schema conventions and vocabulary governance become stable contributor guidance
