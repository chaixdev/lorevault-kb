# Graph Shape Specification

**Status:** Active

## Purpose

This specification is the canonical present-state reference for LoreVault graph shape.

It describes:

- core node categories used in current ingestion and retrieval
- core relationship patterns and their direction semantics
- where temporal meaning lives for each graph pattern
- how to correctly read graph shape without inverting semantics

This document is intentionally implementation-facing and complements:

- `../../adr/010-practical-allen-relation-usage.md` for temporal relation policy decisions
- `../../concepts/temporal-relation-semantics.md` for contributor/operator interpretation rules
- pattern docs that explain stage-level workflows (`../ingestion/ingestion-pipeline.md`, `../ingestion/triad-analysis.md`, `../ingestion/entity-resolution-ladder.md`)

## Scope

This document focuses on graph shape and graph-reading semantics, not full storage DDL.

It covers:

- scene/chunk structural graph
- scene-to-scene temporal graph
- scene-local mention evidence graph (individual/location/event mention)
- chapter/book scoped resolution ladders

It does not define:

- full index/constraint inventory
- full property schemas for every node type
- speculative future event-DAG promotion beyond currently implemented shape

## Canonical Reading Rule

For any directed relation, read semantics from source to target.

- `A -[:REL]-> B` means relation is interpreted as **A relative to B**.

For temporal relation labels, follow ADR-010:

- `A -[:TEMPORAL {relationType: BEFORE}]-> B` means **A happens before B**.

See: `../../adr/010-practical-allen-relation-usage.md`.
Operational checklist guidance lives in: `../../concepts/temporal-relation-semantics.md`.

## Core Graph Shape

### 1) Structural content hierarchy

- `(:Chapter)-[:HAS_SCENE]->(:Scene)`
- `(:Scene)-[:HAS_CHUNK]->(:Chunk)`

This shape is structural and does not imply temporal semantics.

### 2) Scene-to-scene temporal shape

- `(:Scene)-[:TEMPORAL {relationType, certaintyLevel, timelineMarker}]->(:Scene)`

Temporal meaning is carried by:

- edge direction
- `relationType`

Read exactly as source-to-target temporal statement.

### 3) Scene-to-mention evidence shape

- `(:Scene)-[:MENTIONS]->(:IndividualMention)`
- `(:Scene)-[:MENTIONS]->(:LocationMention)`
- `(:Scene)-[:MENTIONS]->(:EventMention)`

`MENTIONS` is an evidence-link relation:

- it asserts that the source scene mentions the target evidence node
- it is not itself a temporal-edge type

### 4) Resolution ladders (implemented)

Individual ladder:

- `(:Scene)-[:MENTIONS]->(:IndividualMention)`
- `(:IndividualMention)-[:REFERS_TO]->(:ChapterIndividual)`
- `(:ChapterIndividual)-[:REFERS_TO]->(:BookIndividual)`

Location ladder:

- `(:Scene)-[:MENTIONS]->(:LocationMention)`
- `(:LocationMention)-[:REFERS_TO]->(:ChapterLocation)`
- `(:ChapterLocation)-[:REFERS_TO]->(:BookLocation)`

Event mention stage-1 evidence:

- `(:Scene)-[:MENTIONS]->(:EventMention)`

## Directionality Ergonomics: Scene -> EventMention

### Problem this section resolves

When inspecting the graph, users can misread scene-relative temporal meaning as if it were target-relative when temporal qualifiers are stored on mention data rather than on a `TEMPORAL` edge.

### Correct reading model

For the pattern:

- `(:Scene)-[:MENTIONS]->(:EventMention {sceneRelativeRelation: X, ...})`

interpretation is:

- the scene mentions this event evidence
- `sceneRelativeRelation` describes temporal stance **of the scene-local mention context**, not an inverse statement about scene ordering

In other words:

- do not read `sceneRelativeRelation=BEFORE` as "EventMention happened before Scene" by graph edge direction
- read it as scene-local extracted qualifier attached to the mention evidence

This is why scene-to-scene temporal ordering must remain on explicit `TEMPORAL` edges where direction + relation label are unambiguous.

## How To Read Common Patterns

### Pattern A: Scene temporal order

`S1 -[:TEMPORAL {relationType: BEFORE}]-> S2`

Read as: `S1` happens before `S2`.

### Pattern B: Scene mentions event evidence

`S1 -[:MENTIONS]-> E1` and `E1.sceneRelativeRelation = BEFORE`

Read as: scene `S1` contains an event mention with extracted scene-relative temporal qualifier `BEFORE`.

Do **not** reinterpret this as an inverted scene-event ordering edge.

### Pattern C: Scene mentions identity/location evidence

`S1 -[:MENTIONS]-> M1` then `M1 -[:REFERS_TO]-> C1`

Read as: evidence mention in scene rolls up to scoped identity aggregate.

## Validation Guidelines

When reviewing persisted graph data:

1. For timeline questions, inspect `TEMPORAL` edges first.
2. Treat `MENTIONS` as evidence-link shape, not timeline edges.
3. For event mention semantics, inspect mention properties (such as `sceneRelativeRelation`) as extraction metadata.
4. Avoid inferring scene ordering from `MENTIONS` direction.

## Cross-References

- `../../adr/010-practical-allen-relation-usage.md` — canonical temporal semantics and normalization policy
- `../../concepts/temporal-relation-semantics.md` — contributor/operator interpretation and validation rules
- `../ingestion/triad-analysis.md` — triad inference and temporal edge persistence shape
- `../ingestion/ingestion-pipeline.md` — when scenes, mentions, and temporal edges are persisted
- `../ingestion/entity-resolution-ladder.md` — entity evidence ladder (Individual and Location lanes)
