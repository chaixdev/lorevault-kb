# Scene Temporal Linking Solution Design Proposal — April 2026

**Date:** April 2026  
**Status:** Historical proposal — pre-shipment design context  
**Purpose:** Preserve the implementation-guiding V1 temporal-linking proposal that preceded the shipped scene-temporal-linking work

---

## Relationship to Earlier Material

This document consolidates and sharpens the converged decisions from the wider temporal-linking brainstorm and the earlier planning investigation that tracked the original gap before the implementation landed.

- `2026-04-17_scene-temporal-linking-brainstorm.md`

Those earlier documents remain useful as diagnosis and option-space history.
This document is preserved as pre-shipment design context. Current shipped status lives in `../../PROJECT-STATUS.md` and any canonical present-state mechanism should be described in the pattern library rather than inferred from this proposal.

---

## Problem

LoreVault already performs scene triad analysis, but the current write path breaks the intended outcome.

Today:

- triad analysis runs before current-chapter scenes are durably persisted
- triad persistence uses temporary in-memory scene identities
- temporal edge writes expect real persisted scene nodes
- default heuristic `MEETS` edges later make the graph look healthier than the inferred chronology actually is

The result is that scene temporal linking appears present, but the durable graph still underuses or loses the most important LLM-derived chronology signal.

---

## Proposed Solution in One View

V1 should introduce a **post-persistence scene temporal linking stage** with these rules:

1. persist scenes first
2. preserve scene-local chronology and marker fields on the scene nodes
3. preserve the authoritative triad structured output during ingestion
4. create structural adjacency separately from temporal meaning
5. infer only local scene-to-scene relationships, including the chapter-boundary case
6. persist a single inferred relationship layer
7. persist ambiguity explicitly without pretending to resolve it

The intended separation is:

- `(:Scene)-[:NEXT_IN_READING_ORDER]->(:Scene)` = structural adjacency only
- `(:Scene)-[:TEMPORAL]->(:Scene)` = inferred or assigned temporal comprehension
- `(:Scene)-[:AMBIGUOUS_RELATION]->(:Scene)` = unresolved conflicting temporal interpretation

---

## Core Decisions

### 1. Scene persistence must happen before temporal linking

The temporal stage must run only after persisted scenes with stable IDs exist.

This is the direct fix for the current write-path defect.

The new ordering is:

1. detect and localize scenes
2. persist scenes
3. persist or confirm required scene-analysis artifacts
4. run temporal linking against persisted scenes

### 2. The authoritative reusable artifact is triad scene-analysis output

The authoritative source for post-persistence temporal linking is the **triad scene-analysis structured output**.

Lookup order:

1. prefer in-memory processing context when available
2. otherwise recover the exact triad output from the related `LlmCallRecord`

This proposal does **not** introduce a broader new storage model for triad artifacts in V1.

### 3. Recoverability of triad output is part of ingestion correctness

The system must be able to recover the exact structured triad output for a persisted scene.

That means V1 depends on all of the following being true:

- the relevant `LlmCallRecord` exists
- its `responseBody` is persisted
- its stored body is not truncated below recoverable usefulness
- it remains linkable to the correct triad status metadata

If the later temporal stage cannot recover the required triad artifact, chapter ingestion must fail.

There is no silent fallback where structural edges remain but inferred temporal understanding disappears.

### 4. Processing context is an optimization, not the source of truth

An in-memory processing context may carry already-parsed triad outputs between adjacent ingestion stages.

That is useful for hot-path efficiency, but it is not authoritative.

The authority is still the recoverable triad artifact described above.

### 5. Scene-local chronology data belongs on scenes

Chronology-related fields already discovered during scene analysis should survive onto scene nodes.

For V1, the scene node should retain scene-local temporal-anchor information such as:

- chronology
- chronology certainty
- chronology marker / timeline marker

This keeps the scene meaningful as a temporal anchor even before pairwise scene-to-scene inference is considered.

### 6. Structural adjacency is not temporal comprehension

The current heuristic `MEETS` fallback conflates reading order with inferred chronology.

V1 must separate them.

Structural adjacency should use:

- `(earlier)-[:NEXT_IN_READING_ORDER]->(later)`

This says only that two scenes are neighbors in publication / reading order.

It does **not** assert `MEETS`, `BEFORE`, or any other Allen relation.

### 7. Certainty and provenance are separate axes

V1 should keep the existing certainty model for temporal judgment strength:

- `Explicit`
- `StronglyImplied`
- `WeaklyImplied`
- `Heuristic`

And it should model provenance separately:

- `heuristic`
- `inferred`
- `assigned`

These should not be collapsed into one field.

### 8. V1 uses one inferred relationship layer only

V1 should not create both:

- candidate scene-to-scene links
- final materialized scene-to-scene links

That is too much model surface for the first slice.

Instead, V1 should persist only one inferred relationship layer.

That layer is the durable outcome of post-persistence temporal linking.

### 9. Ambiguity is durable but non-fatal

Conflicting local triad judgments are useful information.

They should not fail ingestion.

Instead, the system should persist:

- `(:Scene)-[:AMBIGUOUS_RELATION]->(:Scene)`

That relationship should carry conflicting hypothesis data as properties so a later reconciliation process can review it.

This is a deliberate V1 compromise for local implementation speed.
It should be treated as a tactical serialization boundary, not necessarily the long-term canonical ambiguity model.

### 10. V1 stays local

This slice covers only:

- same-chapter adjacent scene linking
- cross-chapter boundary linking from chapter A last scene to chapter B first scene

Broader non-local temporal reasoning and read-side redesign are deferred.

---

## Target V1 Outcomes

V1 is successful when all of the following are true:

1. scene temporal linking runs only after persisted scenes exist
2. the temporal stage can reuse authoritative triad output from context or `LlmCallRecord`
3. chronology marker data is preserved on scene nodes
4. structural adjacency is stored separately from inferred temporal meaning
5. the chapter-boundary case can produce an inferred cross-chapter temporal relationship
6. ambiguous results are preserved without blocking ingestion
7. missing required triad artifacts fail chapter ingestion

---

## Graph Shape for V1

### Scene node additions

Persist or expose these scene-local temporal anchor fields on `Scene`:

- `chronology`
- `chronologyCertainty`
- `chronologyMarker`

Exact property names can follow Java naming conventions, but the semantics should match those fields.

### Structural adjacency relationship

```text
(earlier:Scene)-[:NEXT_IN_READING_ORDER]->(later:Scene)
```

Meaning:

- same reading/publication adjacency only
- no temporal claim beyond neighborhood

### Inferred temporal relationship

```text
(from:Scene)-[:TEMPORAL]->(to:Scene)
```

Required properties:

- `temporalRelation`
- `certainty`
- `provenance`
- `rationale`
- `evidenceSnippet` or equivalent evidence text field
- `timelineMarker` when relevant
- `jobId`
- `chapterId`
- `statusRecordId` and/or `llmCallRecordId` as provenance references
- enough triad provenance to understand how the edge was produced

### Ambiguous relationship

```text
(from:Scene)-[:AMBIGUOUS_RELATION]->(to:Scene)
```

Required properties:

- `provenance = inferred`
- `ambiguous = true`
- a serialized payload of conflicting hypotheses
- evidence snippets / rationale per hypothesis
- certainty per hypothesis
- triad provenance
- job / chapter / LLM call provenance

Important V1 rule:

- do **not** force `AMBIGUOUS_RELATION` through the current scalar `TEMPORAL` upsert path

It needs its own write logic.

---

## Artifact Recovery Contract

Given any persisted scene, the system must be able to recover the exact triad structured output used for post-persistence temporal linking.

The current durable join path is:

1. `Scene.id`
2. `Scene.chapterId`
3. `IngestionJob` for that chapter
4. `StatusRecord` with `SCENE_TRIAD_ANALYSIS` and triad metadata in properties
5. `LlmCallRecord` linked to that status record
6. `LlmCallRecord.responseBody`

V1 should treat this lookup path as a hard contract.

### Required identifiers

The implementation must preserve enough linkage to reliably move between:

- scene
- chapter
- job
- triad status record
- LLM call record
- structured triad output

### Failure rule

These conditions must be treated as ingestion failure:

- no recoverable `LlmCallRecord` for a required triad
- no persisted response body for that record
- irrecoverably truncated body
- missing scene-to-status linkage for the required triad

---

## Proposed Runtime Flow

## Stage A — Chapter segmentation and scene persistence

1. detect scene boundaries
2. localize scenes within chapter text
3. parse scene-analysis outputs
4. persist scenes with durable IDs
5. persist chronology marker fields on scenes
6. ensure triad scene-analysis LLM output is durably logged

Important rule:

- no scene-to-scene temporal edge inference should run before this stage finishes

## Stage B — Structural adjacency creation

After persisted scenes exist:

1. create `NEXT_IN_READING_ORDER` between consecutive scenes in a chapter
2. create chapter-boundary `NEXT_IN_READING_ORDER` between chapter A last scene and chapter B first scene when appropriate for the current ingestion scope

This stage is structural only.

## Stage C — Post-persistence temporal linking

For each local adjacent pair in scope:

1. assemble triad context from persisted scenes
2. load triad structured output from processing context when present
3. otherwise recover it from linked `LlmCallRecord`
4. interpret the triad output for the target scene pair
5. persist either:
   - one inferred `TEMPORAL` relationship, or
   - one `AMBIGUOUS_RELATION`

## Stage D — Normal ingestion continuation

After temporal linking succeeds:

- continue the normal event-driven ingestion flow

If temporal linking fails because required triad artifacts are missing or unusable:

- fail chapter ingestion

---

## Codebase Mapping

These are the main current seams this proposal is expected to reshape.

### Scene detection and persistence

- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/SceneDetectionHandler.java`

### Triad analysis and artifact capture

- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadBuilderService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadOrchestrationService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionClient.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/LlmCallRecord.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/LlmCallLoggingService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/StatusRecord.java`

### Temporal persistence and reads

- `lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalEdgeWriteRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/DefaultTemporalEdgeService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalReadRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/EventOrderingService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalGraphRepository.java`

### Ingestion failure semantics

- `lorevault-api/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/TriadAnalysisException.java`
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionFailure.java`

---

## Implementation Plan

An implementation agent should execute this proposal in the following order.

### Slice 1 — Make the artifact recoverable and scene-local data durable

Deliver:

- persist chronology marker fields on scenes
- verify triad scene-analysis output remains durably recoverable through `LlmCallRecord`
- add or tighten recovery helpers for scene -> triad artifact lookup
- make missing or unrecoverable triad artifact a hard ingestion failure

Why first:

- all later temporal linking depends on recoverable authoritative artifacts

### Slice 2 — Separate structural adjacency from temporal meaning

Deliver:

- add `NEXT_IN_READING_ORDER`
- stop using heuristic `TEMPORAL/MEETS` as the meaning of structural adjacency in the new path
- create adjacency only after scenes are persisted

Why second:

- it establishes the graph shape that later temporal inference will build on

### Slice 3 — Post-persistence temporal linking for local pairs

Deliver:

- run temporal linking after scene persistence
- infer scene-to-scene relationships for adjacent pairs
- support chapter-boundary A-last -> B-first inference
- persist inferred `TEMPORAL` relationships with certainty + provenance
- persist `AMBIGUOUS_RELATION` when conflicting local judgments exist

Why third:

- it is the first end-to-end value slice for real temporal comprehension

### Slice 4 — Stabilize tests and minimal consumers

Deliver:

- tests for recoverability, failure semantics, adjacency separation, boundary inference, and ambiguity persistence
- minimal read-side tolerance for the new relationship types where required by tests or existing ingestion invariants

Why last:

- this keeps consumer adjustment scoped to what V1 truly needs

---

## Agent Execution Checklist

An implementation agent should consider the work complete only when all of these are true.

### Ingestion / persistence

- persisted scenes carry chronology marker data
- triad artifacts can be recovered deterministically for required pairs
- failure to recover required triad artifacts fails ingestion cleanly

### Graph shape

- `NEXT_IN_READING_ORDER` is created separately from inferred temporal meaning
- inferred temporal links are stored only after persistence
- ambiguous outcomes are stored as `AMBIGUOUS_RELATION`

### Boundary case

- chapter A last scene -> chapter B first scene can yield inferred cross-chapter temporal output

### Verification

- all new tests pass
- pre-existing unrelated failures, if any, are called out explicitly

---

## Required Test Coverage

At minimum, implementation should add or update tests for:

- failure when required structured triad output is missing or unusable
- deterministic recovery of triad structured output for a persisted scene
- post-persistence reuse of scene-analysis structured output from `LlmCallRecord`
- chronology marker persistence on scenes
- creation of `NEXT_IN_READING_ORDER` separately from inferred temporal relations
- cross-chapter local triad creation using persisted scene identities
- chapter A last scene -> chapter B first scene inferred temporal linking
- ambiguity persistence as `AMBIGUOUS_RELATION`
- non-fatal handling of ambiguous temporal judgment

---

## Non-Goals

This proposal does **not** decide or implement:

- broader book-wide temporal read behavior
- retrieval-time multi-hop temporal reasoning
- non-local scene-to-scene linking beyond adjacent scope
- reified ambiguity or evidence-node models
- a larger new persistence model for scene-analysis artifacts
- events other than scene-level temporal linking
- human review / reconciliation workflows beyond durable ambiguity capture

---

## Why This Shape Fits LoreVault Now

This proposal fits the current project situation because it:

- fixes the real defect path without demanding a full timeline redesign
- stays aligned with the Event DAG direction of sparse, evidence-backed partial order
- preserves explicit uncertainty
- avoids pretending structural reading order is already temporal understanding
- gives an implementation agent a bounded path to completion

---

## Final Recommendation

The proposed direction is:

- persist scenes first
- make triad artifact recovery an explicit ingestion contract
- preserve chronology markers on scenes
- create `NEXT_IN_READING_ORDER` as structural adjacency only
- infer one durable scene-to-scene temporal layer post-persistence
- persist ambiguity as `AMBIGUOUS_RELATION`
- fail ingestion when required triad artifacts are missing
- defer broader read-model redesign

In delivery terms, that means:

1. lock recoverability and failure semantics first
2. separate structural adjacency second
3. ship post-persistence local temporal linking third
4. leave broader read-side and reconciliation work for later

This is the cleanest current proposal for getting scene temporal linking to a truthful, bounded V1 that an implementation agent can ship.
