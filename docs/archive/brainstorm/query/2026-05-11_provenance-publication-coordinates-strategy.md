# Provenance and Publication Coordinates Lifecycle Strategy

**Status:** Brainstorm — not an accepted ADR or current implementation contract  
**Date:** 2026-05-11  
**Scope:** Consolidates the current architectural understanding of provenance, `PublicationCoordinates`, spoiler gating, relation claims, mentions, chunks, scenes, and future read projections.

---

## Why this exists

LoreVault uses publication position to prevent spoilers: a result is safe only if it comes from material the reader has reached. The same idea generalizes beyond spoiler safety to other knowledge-graph domains: include only vetted sources, replay knowledge up to a boundary, or reconstruct the state visible from a particular point in an evidence timeline.

`PublicationCoordinates` were introduced early for this purpose, but the implementation has drifted. Some paths derive coordinates through `Scene -> Chapter`; some use UUID filters like `bookId`; some conceptual docs assume `pubCoords` on claims or projected edges; and `RelationClaim` planning introduced flat `pub*` fields that appear speculative rather than wired.

This document captures the working strategy before promoting anything into `docs/concepts/`, `docs/patterns/`, or an ADR.

---

## Core distinction

The central rule is to separate four concepts that have been blurred:

| Concept | Answers | Example | Source-of-truth posture |
|---|---|---|---|
| Identity reference | Which graph node is this attached to? | `sceneId`, `chapterId`, `bookId`, `chunkId` | Stable persisted references |
| Publication coordinates | Where is this in reader-visible publication order? | universe, series, book number, chapter number, scene index, sortable key/ordinal | Canonical ordering model, derived or owned explicitly |
| Evidence provenance | Where did this assertion/extraction come from? | source, chunk, scene, extraction index, evidence text, certainty, source trust | Evidence-layer metadata |
| Materialized read coordinate | How do we make a hot query cheap and safe? | indexed `pubOrdinal` on a projected edge/read model | Derived interpretation-layer optimization |

The mistake to avoid is treating one concept as if it automatically substitutes for the others. `sceneId` is not a publication coordinate. `PublicationCoordinates` are not complete provenance. Denormalized `pubOrdinal` fields are not canonical truth unless a materialization rule says so.

---

## Current observed drift

The current system appears to use several strategies at once:

- `Chapter` owns persisted publication-position fields and exposes a transient `PublicationCoordinates` value object.
- `Scene` stores `chapterId` and `sceneIndex`; its full effective coordinates are derivable from `Scene + Chapter`.
- Chunks and semantic search results derive coordinates through graph traversal to `Scene` and/or `Chapter`.
- Entity and event mentions store provenance identifiers such as `sceneId`, `chapterId`, and eventually `bookId`; they do not own a flat coordinate snapshot.
- Relation-claim planning discusses `pubCoords`, and earlier design notes listed flat `pubUniverse`, `pubSeries`, `pubBookNumber`, `pubChapterNumber`, `pubSceneIndex`, and `pubKey` fields, but the implemented Phase 0 relation claim model did not populate such fields.
- Established spoiler-aware retrieval currently gates vector results using chapter-level fields, not a uniform coordinate-resolution contract across every retrieval path.

This does not mean the `PublicationCoordinates` concept is wrong. It means the project lacks a documented lifecycle rule: when coordinates are owned, derived, resolved into DTOs, or materialized for query performance.

---

## Working model

### 1. Chapter owns chapter-level publication position

`Chapter` is the natural source of truth for universe, series, book title, chapter title, book number, and chapter number. These are publication hierarchy facts, not facts about individual mentions or claims.

### 2. Scene owns scene-local order

`Scene` owns `sceneIndex` within a chapter. A scene-level coordinate is therefore the composition of:

```text
Chapter publication position + Scene sceneIndex
```

The current `PublicationCoordinates` value object does not yet express scene index, `pubOrdinal`, or `pubKey`. That is a conceptual mismatch to resolve before using it as the universal coordinate type.

### 3. Evidence nodes carry anchors, not speculative coordinate copies

Raw evidence substrate nodes — mentions, relation claims, event mentions, object mentions, and similar extraction artifacts — should reliably carry provenance anchors such as `sceneId`, `chapterId`, `bookId`, and extraction-local ordering where applicable.

`chunkId` is not part of the default durable semantic anchor. Current `RelationClaim` and `*Mention` records do not carry `chunkId`; they anchor to scene/chapter/book instead, and this should remain the default posture. A scene is the lowest durable narrative provenance boundary for semantic evidence. Chunks are processing and retrieval artifacts whose boundaries may change as chunking, embedding, or context-window strategy evolves.

Chunk traceability is still useful, but it belongs in extraction/run metadata or an optional evidence-source mapping unless exact chunk-level replay becomes a first-class requirement. It is useful for reproducing prompts, debugging bad extractions, invalidating a specific processing unit, or supporting sub-scene citation; it should not couple every durable mention/claim record to a mutable chunking strategy by default.

They should not own copied `pubUniverse` / `pubSeries` / `pubBookNumber` / `pubChapterNumber` / `pubSceneIndex` / `pubKey` fields by default. Those fields become stale if upstream publication metadata changes and create a second source of truth unless there is an explicit materialization rule.

### 4. Retrieval boundaries must expose effective coordinates

Avoiding flat fields on raw evidence does **not** mean every caller should rediscover coordinates differently. Any retrieval result that can reach answer generation, citation rendering, graph expansion, or spoiler filtering should expose resolved effective `PublicationCoordinates` in a uniform result shape.

That resolution may happen by traversal, projection query, service assembly, or read-model lookup. The important rule is that the boundary is explicit: once data leaves retrieval and enters answer assembly, it should already carry the coordinates needed for spoiler safety and citation.

### 5. Materialization is allowed, but only as a read-model decision

Denormalized coordinate fields are legitimate when there is a named hot read path, an index strategy, and a backfill/invalidation rule. Examples that might eventually justify materialization:

- replay all relation claims visible before a reader boundary
- filter projected `REL` / `HAS_PROPERTY` edges by `pubOrdinal`
- serve large graph expansions without repeated traversal
- export or cache spoiler-gated evidence packets

Those are interpretation-layer optimizations, not default properties of the raw evidence substrate.

---

## RelationClaim and Mention implications

The `RelationClaim implements Mention` question and the `pubCoords` drift are related symptoms: both blur evidence anchoring with lifecycle semantics.

Entity/event/object/location/collective mentions share an entity-resolution lifecycle. For them, methods such as `displayName()`, `normalizedName()`, and `resolutionStatus()` describe the same kind of thing.

`RelationClaim` has a different lifecycle. It is a proposition:

```text
subject + relation phrase + object + evidence + certainty + provenance
```

Phase 1 catalog matching should give relation claims their own relation-catalog status vocabulary instead of borrowing entity mention resolution semantics. Keeping `(Scene)-[:CONTAINS]->(RelationClaim)` as provenance is still coherent: the edge says the scene contains this evidence artifact. The risky part is Java-level substitutability if generic mention consumers start treating relation claims as entity-resolution participants.

The clean direction before Phase 1 is therefore:

- Keep `CONTAINS` as the scene-to-evidence provenance edge (renamed from `MENTIONS` to decouple from the Java `Mention` interface).
- Treat `Mention` as entity-mention-specific unless a narrower shared interface is explicitly defined.
- Let `RelationClaim` stand as relation evidence with its own catalog-matching lifecycle.
- Do not replace claim-vs-mention conflation with `SceneProvenance` vs `PublicationCoordinates` duplication. Provenance may contain coordinates; it should not define a second coordinate system.

---

## Lifecycle strategy

### Ingestion

At extraction time, the system knows the chapter and scene. Persist evidence with stable provenance anchors:

- `sceneId`
- `chapterId`
- `bookId` where available in that stage
- `extractionIndex` or equivalent local ordering
- evidence text / source / certainty / model-output metadata

Do not stamp full flat publication coordinates onto every raw evidence node just because the data is available. If the coordinates are needed later, resolve them from the anchors.

### Evidence storage

Evidence-layer nodes should preserve auditable extraction facts and provenance anchors. Their job is to survive reprocessing, support traceability, and provide the substrate for interpretation.

`PublicationCoordinates` are part of the evidence contract as an effective property, but not necessarily as physically duplicated fields on every evidence node.

### Resolution and catalog matching

Entity mention resolution and relation catalog matching should use provenance anchors for scoping, deduplication, and diagnostics. They should not overload `PublicationCoordinates` to mean lifecycle state.

For relation claims specifically, catalog matching should operate on raw relation phrase, relation description, subject/object kinds, evidence text, certainty, and provenance anchors. Stable relation type IDs should not be smuggled through a generic `normalizedName()` contract.

### Retrieval

Retrieval must produce spoiler-safe candidates. It may resolve coordinates by traversal:

```text
Evidence -> Scene -> Chapter
Chunk -> Scene -> Chapter
Projected edge -> provenance/evidence -> Scene -> Chapter
```

or by a documented read model. The required outcome is the same: retrieval outputs must carry effective coordinates in a canonical shape before answer generation.

### Answer assembly

The final-answer layer should not infer coordinates from UUIDs or raw graph paths. It should receive already-resolved coordinates and citations. This keeps spoiler gating auditable and prevents each answer path from inventing its own coordinate logic.

### Projection and replay

Future projected edges such as `REL`, `HAS_PROPERTY`, or comparison edges may carry `pubOrdinal`/`pubKey` if they are materialized read models. Their coordinates are derived from evidence provenance, not independently authored facts.

For event-sourced relation state, replay should filter by effective publication boundary. Whether that boundary is evaluated by traversal or by an indexed projection is an implementation decision that belongs to the projection/read-model layer.

---

## Proposed coordinate contract

The eventual canonical coordinate shape should likely include:

- `universe`
- `series`
- `bookTitle`
- `bookNumber`
- `chapterTitle`
- `chapterNumber`
- optional `sceneIndex`
- derived sortable `pubKey`
- optional numeric `pubOrdinal`

Open design point: the current Java `PublicationCoordinates` class is chapter-scoped. Either it should evolve to represent effective chapter-or-scene coordinates, or a second wrapper should compose it with scene-local order without duplicating its fields.

The preferred direction is composition with one canonical coordinate concept, not parallel types that each encode universe/book/chapter/scene independently.

---

## Materialization rule of thumb

Use this test before adding flat coordinate fields to a node or relationship:

1. **Named read path:** Which query becomes simpler or faster?
2. **Boundary:** Is this raw evidence or a derived read model?
3. **Ownership:** Which upstream entity owns the canonical coordinate values?
4. **Backfill:** How are existing rows/nodes populated?
5. **Invalidation:** What changes force recomputation?
6. **Indexing:** Which indexes make the materialized fields useful?
7. **DTO contract:** Does the result boundary expose the same canonical coordinate shape?

If these questions do not have concrete answers, prefer provenance anchors plus coordinate resolution by traversal.

---

## Candidate promotion path

This brainstorm captures direction. Durable docs get written *after* implementation lands, not before:

1. `docs/concepts/publication-coordinates.md`  
   Defines identity references vs effective publication coordinates vs provenance. Written once the code reflects this separation.

2. `docs/patterns/publication-coordinate-resolution.md`  
   Defines how retrieval and projection paths resolve coordinates and which DTOs must carry them. Written once a retrieval path demonstrates the pattern.

3. Follow-up ADR to `docs/adr/006-spoiler-aware-search-design.md`  
   Records the decision that raw evidence nodes store provenance anchors by default, while coordinate materialization requires a named read-model rationale. Written once the refactoring is complete and tested.

---

## Immediate Phase 1 guardrails

Before relation catalog matching hardens the current shape:

- Do not add or rely on unwired flat `pub*` fields on `RelationClaim`.
- Confirm whether any existing `RelationClaim.pub*` fields are actually present, populated, indexed, or queried before removing or documenting them as fossils.
- Keep relation catalog status separate from entity mention `resolutionStatus`.
- Prefer explicit `RelationClaim` queries for relation-catalog work rather than generic mention queries.
- Ensure any retrieval surface that can feed an answer returns resolved coordinates, even if the backing entity stores only IDs.

---

## Open questions

1. Should `PublicationCoordinates` become scene-aware, or should scene order be represented by a composed `LocatedPublicationCoordinates` / `EvidenceLocation` wrapper?
2. Should `pubOrdinal` be globally monotonic per universe, per series, or per book?
3. Should `pubKey` be canonical storage, derived display, or both?
4. Which read path first justifies materialized coordinates: relation replay, projected graph edges, graph expansion packets, or export/cache boundaries?
5. ~~Should the term `MENTIONS` remain the generic scene-to-evidence edge if `Mention` becomes entity-mention-specific in Java?~~ **Working conclusion:** `MENTIONS` is a provenance edge ("this scene contains this evidence artifact"), not a type assertion. The Java interface `Mention` and the Neo4j edge label `MENTIONS` serve different purposes and should not be coupled. The naming collision is confusing domain language. Direction: rename the edge to `CONTAINS` to make the provenance semantics explicit and decouple it from the Java `Mention` interface. `CONTAINS` reads naturally for both entity mentions and relation claims, and avoids collision with the existing `REFERS_TO` resolution edge.
6. If exact extraction replay becomes first-class, should chunk traceability live in run metadata, an evidence-source mapping, or explicit `chunkId` fields on selected evidence types? **Working conclusion:** `chunkId` is not part of the default durable semantic anchor. Scenes are the lowest durable narrative provenance boundary. Chunk traceability belongs in extraction/run metadata or an optional evidence-source mapping, not coupled to every mention/claim record by default.

---

## Working conclusion

`PublicationCoordinates` remain the right concept, but they need a lifecycle contract. Raw evidence should be anchored to scene/chapter/book identity; effective coordinates should be resolved consistently at retrieval/result boundaries; and denormalized coordinate fields should be deliberate read-model materializations, not speculative scaffolding.

This preserves spoiler safety without turning every evidence node into a second copy of chapter metadata, and it keeps future relation claims, catalog matching, and projected graph edges from hardening accidental Phase 0 shortcuts into enduring architecture.
