# Event DAG

**Status:** Conceptual  
**Scope:** Durable temporal model, not a full description of current implementation  
**Primary sources:**
- [Narrative event DAG.md](Narrative%20event%20DAG.md) — full triad-only, gazetteer-aware specification
- [event-model.md](event-model.md) — Event/Scene entity subtype definition
- [core-domain-model-and-graph-process-restructured.md](core-domain-model-and-graph-process-restructured.md) — temporal modeling within the broader domain model
- [`../adr/004-keep-the-event-driven-ingestion-pipeline.md`](../adr/004-keep-the-event-driven-ingestion-pipeline.md)

## Why This Exists

LoreVault needs a temporal model that is expressive enough for narrative fiction without pretending that stories arrive as a clean linear timeline.

The event DAG concept preserves the key idea that narrative order is usually a **partial order**, not a single sequence.

## Core Idea

Model temporal knowledge as a sparse, auditable graph of relationships rather than as a fully materialized master timeline.

Key principles from the research work:

- local evidence first
- triads over global ordering
- partial order rather than total order
- no eager transitive materialization
- confidence and evidence travel with temporal edges
- retrieval-time reasoning is preferable to graph blow-up

For the full specification — including the data model (Scene, Event, Landmark, Arc), the end-to-end triad pipeline, edge semantics, sparsity rules, and retrieval-time reasoning — see [Narrative event DAG.md](Narrative%20event%20DAG.md).

For the concrete Event/Scene entity fields, temporal link structure, certainty levels, and weight mappings, see [event-model.md](event-model.md).

## Why It Matters

This concept is strategically valuable because it:

- fits the ambiguity of narrative fiction
- preserves uncertainty instead of flattening it away
- gives a principled path for future timeline features
- helps distinguish publication order from in-universe temporal order

## Relationship To Current Code

LoreVault already contains meaningful temporal and event-oriented code:

- timeline packages and temporal edge logic
- triad-oriented orchestration
- event-driven ingestion choices

But the full conceptual DAG described in the research documents is broader than the current implementation.

In particular, the full model of scene/event/landmark/arc sparsity and retrieval-time temporal composition should be treated as a preserved concept rather than as a statement of present code reality.

That is why this document belongs in `concepts/`, not `patterns/`.

## What Should Remain True Even If Implementation Changes

The most durable ideas here are:

- temporal relationships should remain evidence-backed
- sparse local structure is preferable to dense inferred graphs
- uncertainty should stay explicit
- long-range ordering should be inferred lazily when possible

Those ideas can keep guiding implementation even if the concrete storage model changes.

## Relationship To Patterns And ADRs

- Use `patterns/` for the implemented ingestion and temporal mechanisms that exist today.
- Use ADRs for accepted choices such as keeping event-driven ingestion where it adds value.
- Use this concept doc for the broader temporal model the code may continue growing toward.

## When To Promote Further

This concept can later feed:

- a current-state temporal **pattern** doc once the implementation shape stabilizes
- new **ADRs** if the team makes explicit decisions about scene-as-event modeling, landmark handling, or temporal storage strategy
