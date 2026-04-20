# Event (Scene) — Entity Subtype

> **Research only - not an implementation target**

Purpose

Define the Event model as the canonical representation of Scenes as temporal anchors. We dual-label nodes as :Event:Scene to keep current Chapter→Scene hierarchies intact while enabling Event-first timelines.

Entity Hierarchy

- Entity (base)
  - Event (subtype); 0.9.0 only uses eventType=SCENE

Identifiers

- eventId: ULID/UUID (string)
- externalKeys: optional stable references to legacy Scene IDs

Semantic (Event) Fields

- eventType: "SCENE"
- title: string — short label
- description: string — compact scene summary
- flags: set of EventFlag — { Flashback, ParallelPOV, Dream }

Segmentation (Scene) Fields

- sceneIndex: int — position within chapter
- startOffset, endOffset: int — optional byte/char offsets within chapter content
- firstChunkId, lastChunkId: optional alternative to offsets
- publication coordinates (via existing chapter/series/book structures)

Relationships

- Chapter→HAS_SCENE→Event: reuse existing hierarchy (no OCCURS_IN needed)
- Event→HAS_CHUNK→Chunk: reuse existing linkage

Temporal Links (Edges)

- temporalRelation: BEFORE | MEETS | OVERLAPS | DURING | STARTS | FINISHES | EQUALS
  - Practical inferred use deprecates `MEETS` and `EQUALS`
  - Structural adjacency should use `NEXT_IN_READING_ORDER`
  - Legacy inferred `EQUALS` should be treated as a coarse overlap case unless manually justified
- certainty: CertaintyLevel — { Explicit, StronglyImplied, WeaklyImplied, Heuristic }
- weight: double — calibrated numeric (mapping table)
- rationale: string — minimal quote/justification text
- evidence: offsets { start, end } and/or sourceChunkId (optional)
- source: CHAPTER_SEQUENCE | LLM_TEMPORAL | MANUAL

Provenance

- Keep provenance minimal on nodes; retain certainty and evidence on temporal edges
- Optionally store raw LLM temporal assessment as immutable evidence (deferred if not needed)

Enums

- TemporalRelation = BEFORE | MEETS | OVERLAPS | DURING | STARTS | FINISHES | EQUALS
  - `MEETS` and `EQUALS` remain in the conceptual vocabulary, but are deprecated for normal inferred use
- CertaintyLevel = Explicit | StronglyImplied | WeaklyImplied | Heuristic
- EventFlag = Flashback | ParallelPOV | Dream

Weight Mapping (initial)

- Explicit → 0.95
- StronglyImplied → 0.8
- WeaklyImplied → 0.6
- Heuristic → 0.5 (historical default for consecutive MEETS; current structural adjacency should not rely on Allen `MEETS`)

Notes

- Historical implementations used MEETS@Heuristic between consecutive scenes; practical current guidance prefers `NEXT_IN_READING_ORDER` for structure and inferred temporal labels only when evidence supports them
- Legacy inferred `EQUALS` should be reconsidered as `OVERLAPS` unless a manual or specialized workflow can justify exact shared boundaries
- Edges should form a DAG for inferred temporal relations; deprecated inferred `EQUALS` should not be relied on as a normal durable case
