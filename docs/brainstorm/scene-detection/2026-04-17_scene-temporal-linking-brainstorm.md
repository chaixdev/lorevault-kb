# Scene Temporal Linking — Converged Design Brainstorm (April 2026)

**Date:** April 2026  
**Status:** Converged design brainstorm — implementation-guiding, not canonical truth yet

---

## 1. Purpose

This document captures the current converged design direction for fixing scene temporal linking.

It is no longer a broad option survey. It records:

- the diagnosis that still matters
- the settled V1 direction
- the parts explicitly deferred
- the concrete validation slice for V1

This document should guide implementation discussion, but it is not yet a canonical pattern or ADR.

---

## 2. Why This Matters

LoreVault is building toward an Event DAG model where scenes act as temporal anchors and temporal knowledge stays:

- sparse
- evidence-backed
- partial-order oriented
- auditable

The current implementation fails to preserve that direction reliably across chapter boundaries.

The practical result is that cross-chapter scene-to-scene temporal understanding is weak, while the graph can still appear healthy because structural fallback edges make the graph look more temporally informed than it really is.

---

## 3. Current Diagnosis

Only the diagnosis that still matters to the chosen direction is preserved here.

### 3.1 Pre-persistence temporal analysis is the core bottleneck

Today, triad temporal analysis happens before current chapter scenes are durably persisted.

That causes several problems at once:

- temporary scene identities are used during temporal reasoning
- temporal edge persistence expects real persisted scene nodes
- cross-chapter context is harder to resolve cleanly

This is the main reason the temporal stage must move later.

### 3.2 Useful chronology signal is discovered too early and then dropped

Scene analysis already extracts chronology-related information, but that information does not survive cleanly enough into the temporal-linking stage.

That is not the primary bottleneck, but it weakens temporal reasoning and should be preserved on scenes.

### 3.3 Current chapter-local consumption hides the real failure

Current read-side ordering is chapter-scoped, so even if richer cross-chapter temporal understanding exists or partially exists, current consumption paths do not make good use of it.

This broader read-model topic is important, but it is not part of the V1 fix slice.

---

## 4. Settled V1 Direction

The following points are now the working direction for V1.

### 4.1 Temporal analysis moves out of scene persistence

Scene persistence happens first.

Temporal analysis becomes a later stage in the ingestion flow.

This stage runs against persisted scenes and real chapter metadata rather than temporary in-memory scene objects.

### 4.2 Reuse the structured scene-analysis output directly

The first implementation should reuse the already-persisted structured output from scene analysis.

For V1, the durable persisted reuse source is:

- `LlmCallRecord`

This is explicitly a reuse path for expensive inference. V1 does **not** introduce a broader new persistence model just for this problem.

### 4.3 Required structured output is part of ingestion success

For this workflow, reusable structured scene-analysis output is not optional.

Therefore:

- if the required structured output cannot be durably stored during scene analysis, that stage should already fail
- if the later temporal stage cannot retrieve or use that required structured output, chapter ingestion should fail

There is no silent fallback in V1 where temporal enrichment disappears but ingestion still reports success.

### 4.4 Processing context is useful, but not authoritative

An in-memory processing context is still useful as a hot-path handoff mechanism between live ingestion stages.

It may cache parsed structured output so later stages do not need to reread persisted state unnecessarily.

But it is not authoritative.

The intended retrieval rule is:

1. prefer processing context when present
2. otherwise load the required structured output from persisted `LlmCallRecord`

### 4.5 Chronology and timeline-marker data live on scenes

Scenes are temporal anchors.

Therefore chronology-related data and timeline-marker information should live on scenes as scene/event properties, not only inside transient scene-analysis outputs.

This keeps the scene node meaningful as a temporal anchor and aligns with the Event DAG direction.

### 4.6 Structural adjacency is not temporal understanding

The older heuristic `MEETS` fallback is not the right model.

V1 should distinguish:

- **structural adjacency**
- **temporal comprehension**

Structural adjacency should use:

- `(earlier)-[:NEXT_IN_READING_ORDER]->(later)`

This means only that two scenes are neighbors in reading/publication order.

It does **not** claim temporal `MEETS`.

Temporal relations like `MEETS`, `BEFORE`, `OVERLAPS`, and so on should be reserved for inferred or assigned comprehension.

### 4.7 Confidence/source model is simplified

The working confidence/source buckets are now:

- **heuristic** — structural or fallback logic only
- **inferred** — LLM-derived temporal understanding
- **assigned** — human-reviewed or human-set temporal understanding

Conflict or ambiguity is a separate dimension, not one of these buckets.

### 4.8 Candidate artifact is a scene-to-scene relationship

The candidate artifact is not a triad blob and not a separate node.

It is a:

- **scene-to-scene candidate relationship**

The triad is the reasoning context.

The candidate relationship is the persisted proposed outcome.

In graph terms:

- from scene = relationship start node
- to scene = relationship end node

Those are graph structure, not ordinary relationship properties.

### 4.9 Candidate contents are rich but disciplined

V1 candidates should carry:

#### Core proposal fields

- proposed temporal relation
- confidence/certainty

#### Evidence fields

- rationale / explanation
- evidence snippet or equivalent evidence payload
- timeline marker when relevant

#### Provenance fields

- chapter/job provenance
- source `LlmCallRecord` provenance
- enough triad provenance to understand how the proposal was produced

V1 should **not** turn the candidate into an unstructured dump of all prompt inputs or raw model payloads.

For V1, candidate links in this stage are inferred by definition, so a separate "source bucket" property is not required on the candidate artifact itself.

### 4.10 Contradiction is a feature, not a failure

Overlapping local triads may produce conflicting inferred judgments for the same scene pair.

That is useful information.

For V1:

- conflicting inferred candidates should be preserved as candidate evidence
- the pair should be treated as ambiguous / inconclusive rather than force-resolved
- this is **not** a fatal pipeline failure

This is a comprehension-quality issue, not an ingestion-integrity issue.

### 4.11 Local scope only for now

V1 is intentionally local.

This work is about making local scene-to-scene temporal linking actually useful, including the chapter-boundary case of:

- last scene of chapter A
- first scene of chapter B

Broader book-scope or non-local temporal-linking strategy is deferred.

---

## 5. V1 Flow

The intended V1 flow is:

### Stage A — Scene detection and scene persistence

- detect scenes
- localize them
- persist scenes with durable IDs
- persist chronology/timeline-marker data on scenes
- persist required structured scene-analysis output in `LlmCallRecord`

### Stage B — Post-persistence temporal candidate creation

- load persisted scenes and chapter metadata
- reuse scene-analysis structured output from processing context when present
- otherwise load it from persisted `LlmCallRecord`
- build local triad context across persisted scenes
- create scene-to-scene candidate relationships

### Stage C — Scene-to-scene temporal linking

- use candidate relationships to materialize useful scene-to-scene temporal links
- preserve ambiguity when inferred candidates conflict
- do not let structural adjacency pretend to settle contested temporal understanding

For this document, “scene-to-scene temporal linking” is the preferred term.

The older “reduction” language is intentionally avoided here because it over-borrows from entity-reduction mechanisms and implies a more settled architecture than we currently have.

### Stage D — Current read behavior

- chapter-local reading order remains simple
- broader non-local temporal read behavior is deferred

---

## 6. What V1 Explicitly Does Not Decide

The following topics are important, but they are not part of this slice.

### 6.1 Broader/global temporal read shape

How local structural adjacency and richer temporal understanding should be consumed in broader timeline queries is deferred.

### 6.2 Non-local or book-wide temporal-linking scope

This brainstorm deliberately stops at local scene-to-scene linking plus the chapter-boundary case.

### 6.3 Deterministic scene identity as a separate strategy

Post-persistence staging removes the urgency of deterministic scene identity for this slice.

The topic is deferred.

### 6.4 Heavier reprocessing or HITL flows

If ambiguous candidate relationships later need:

- a heavier LLM
- richer evidence packaging
- human review

that is future work, not part of V1.

### 6.5 A broader new persistence model for scene-analysis artifacts

V1 intentionally starts with direct reuse of persisted structured output from `LlmCallRecord` rather than inventing a new storage model.

### 6.6 persisting and evaluating events other than "Scene" events

While Scene analysis may return "event" entities, we're are explicitly deferring their handling.

---

## 7. V1 Validation Slice

V1 should be considered successful when this works:

> the last scene of chapter A can link to the first scene of chapter B with an **inferred** scene-to-scene temporal relationship that takes LLM output into account and uses the post-persistence flow

The minimum acceptance slice is:

1. scenes are persisted first
2. required structured scene-analysis output is durably available
3. the later temporal stage reuses that output
4. a cross-chapter scene-to-scene temporal candidate is produced for the A→B boundary case
5. that candidate leads to an **inferred** cross-chapter scene-to-scene temporal relationship rather than only structural adjacency

Failure to access the required structured output should fail chapter ingestion.

Ambiguity in inferred temporal understanding should not fail chapter ingestion, but it should prevent false certainty.

---

## 8. Tests V1 Should Add

At minimum, V1 should add coverage for:

- failure when required structured scene-analysis output is missing or unusable
- post-persistence reuse of scene-analysis structured output from `LlmCallRecord`
- cross-chapter local triad creation using persisted scene identities
- chapter A last scene → chapter B first scene candidate creation
- preservation of ambiguity when overlapping inferred candidates conflict
- distinction between `NEXT_IN_READING_ORDER` structural adjacency and inferred temporal relations

---

## 9. Key Files Relevant To This Direction

- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadBuilderService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadOrchestrationService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionClient.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/LlmCallRecord.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalEdgeWriteRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/DefaultTemporalEdgeService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalReadRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/EventOrderingService.java`

---

## 10. Relationship To Concept Docs

This direction is intentionally aligned with the Event DAG concept docs.

Most importantly:

- scenes remain temporal anchors
- local evidence remains important
- triads remain the local reasoning mechanism
- partial order is preserved instead of flattened into a single master sequence
- structural adjacency is kept separate from temporal comprehension
- future broader reasoning remains a retrieval-time concern rather than a graph-densification exercise

Relevant concept docs:

- `../../concepts/event-dag.md`
- `../../concepts/event-model.md`
- `../../concepts/Narrative event DAG.md`

---

## 11. Related Internal Context

- Planning note: `../../planning/scene-temporal-linking-gaps.md`

If this design stabilizes through implementation, likely next promotion targets are:

- an ADR for the post-persistence temporal stage
- a pattern doc for current-state scene-to-scene temporal linking
