# Individual Extraction MVP Spec — April 2026

**Date:** April 2026  
**Status:** Proposed  
**Purpose:** Persist useful individual-extraction data already returned by the pass 2 LLM response, without identity resolution

---

## Problem

LoreVault's pass 2 scene-analysis prompt already asks the LLM to extract entities for the current scene, including individuals.

Today that work is effectively wasted:

- the prompt requests entity output
- test fixtures show the model can return it
- but the application only maps temporal fields from the structured response
- individual data is never parsed into the domain model or persisted to Neo4j

This means LoreVault is already paying for useful extraction work during triad analysis but gets no graph value from it.

---

## Current State

### Prompt behavior

The current pass 2 system prompt lives at:

- `lorevault-api/src/main/resources/prompts/scene-detection-pass2.txt`

It asks for:

- temporal relations between `previous -> current` and `current -> next`
- `current_scene_entities`

Entity categories currently requested by the prompt are:

- Individual
- Collective
- Object
- Location
- Concept
- Event

For individuals, the prompt currently asks for:

- aliases
- physical properties
- age
- description

### Runtime behavior

The pass 2 triad flow currently works like this:

1. `TriadOrchestrationService` builds triads and renders the pass 2 prompt
2. `SceneDetectionClient.detectScenesPass2Triad(...)` sends the structured call
3. Spring AI maps the result into `TriadStructuredResult`
4. `TriadEdgePersistenceService` persists only temporal edges

Current limitation:

- `TriadStructuredResult` only contains:
  - `timelineMarker`
  - `previousToCurrent`
  - `currentToNext`

So the existing pass 2 entity output is discarded.

### Key judgment

This existing work should **not** be discarded.

It is already a useful rope bridge:

- the model is already being asked for individuals
- the current scene is already the extraction target
- the triad context may improve local disambiguation
- the system already has a structured pass 2 call site where this data can be captured

What should be discarded is the idea of using the full current entity schema as a large first implementation. The MVP should narrow to the smallest useful slice.

---

## Decision Summary

Implement a first MVP for **Individual extraction only**.

Rules:

- leverage the existing pass 2 LLM output
- persist only extracted `Individual` data
- ignore collectives, locations, objects, concepts, and events for now
- do **not** do identity matching, deduplication, alias resolution, or chapter/global merging
- create one provisional `Individual` node per extracted individual per scene
- use generated UUIDs for all persisted `Individual` nodes
- persist only after final `Scene` nodes are saved
- map extracted individuals to persisted scenes by chapter-local `sceneIndex`
- do not touch event DAG logic as part of this work

This is intentionally a **rope bridge** design, not a canonical entity system.

---

## Scope

### In scope

- capturing individual data from pass 2 output
- extending the structured pass 2 DTO so individual data is available to application code
- carrying extracted individual data forward until real scene persistence completes
- persisting provisional `Individual` nodes
- creating `Scene -> Individual` mention links
- preserving useful textual fields already returned by the LLM

### Out of scope

- entity identity resolution
- cross-scene or cross-chapter merging
- alias matching
- canonical entity registry/catalog work
- location extraction
- collective extraction
- object/concept/event extraction
- claim extraction
- any changes to temporal DAG semantics or event modeling
- invented confidence math beyond what the response already gives explicitly
- invented text offsets if the response does not provide them

---

## Why This Shape

The highest-value observation from the current codebase is simple:

> pass 2 already asks for individuals, but the result is thrown away.

This makes individual persistence the best next step because it:

- reuses an LLM call the system already makes
- avoids introducing a second extraction pass prematurely
- delivers immediate graph value
- avoids the hardest part of entity systems: identity mistakes

The most important constraint is:

> a bad merge is worse than a missed entity

So this MVP deliberately biases toward under-merging by doing **no merging at all**.

---

## Proposed Data Source

Continue using the existing pass 2 triad analysis prompt.

Do **not** add a separate entity-extraction prompt in this MVP.

Reasoning:

- lower implementation cost
- preserves existing triad-context behavior
- avoids paying for another LLM call before validating the usefulness of the current output
- keeps the first implementation focused on persistence rather than prompt redesign

The only required extraction target in this MVP is:

- `current_scene_entities.individuals`

All other returned entity blocks should be ignored.

---

## Structured Output Strategy

Do **not** add a second raw-XML parsing path for pass 2.

Instead:

- extend the existing structured pass 2 DTO shape
- continue using Spring AI `.entity(...)`
- include only the minimum additional structure needed to carry current-scene individuals

Recommended direction:

- keep the existing temporal fields unchanged
- add a `currentSceneEntities` object
- inside it, add an `individuals` collection
- ignore the other pass 2 entity categories in application code for now

This keeps the application on one pass 2 integration path instead of maintaining both:

- structured DTO mapping
- manual pass 2 XML parsing

---

## Persistence Timing

Persist extracted individuals **after final scene persistence**, not during triad analysis.

Reason:

- triad analysis currently works with temporary scene objects during orchestration
- `SceneProcessingService.persistDetectedScenes(...)` is where real `Scene` nodes are created and linked
- persisting after final scene save avoids temporary-ID reconciliation

Bridge strategy:

- store extracted individuals keyed by chapter-local `sceneIndex`
- after scenes are saved, resolve `sceneIndex -> persisted Scene.id`
- then persist individuals and mention links using real scene IDs

This is the cleanest bridge between existing triad analysis and durable graph persistence.

---

## Graph Shape

### Node

Persist provisional nodes as:

- `(:Individual)`

Recommended node properties:

- `id: UUID`
- `provisional: true`
- `source: "ai-pass2"`
- `displayName: String`
- `aliases: List<String>`
- `description: String`
- `age: String`
- `physicalProperties: String`
- `createdAt`
- `updatedAt`

Important constraint:

- one extracted individual block in one scene becomes one `Individual` node
- repeated names across scenes do **not** imply reuse of a node

### Relationship

Persist scene links as:

- `(:Scene)-[:MENTIONS]->(:Individual)`

Recommended relationship properties:

- `source: "ai-pass2"`
- `createdAt`
- optional `extractionIndex` if ordering is useful

The relationship should remain intentionally thin in the MVP.

Do not add speculative evidence offsets or confidence fields unless the extracted data reliably supports them.

---

## Naming Rule

For MVP, choose the persisted `displayName` using a simple deterministic rule:

- first non-blank alias, if present
- otherwise skip the extracted individual as invalid

Why:

- simple
- deterministic
- compatible with the current prompt shape
- avoids premature canonicalization rules

This rule can be upgraded later if the prompt shape changes to provide a stronger primary-name field.

---

## Service Shape

Recommended implementation flow:

1. Extend pass 2 structured result mapping to expose `current_scene_entities.individuals`
2. During triad orchestration, collect extracted individuals keyed by `sceneIndex`
3. Continue existing temporal-edge flow unchanged
4. After `SceneProcessingService.persistDetectedScenes(...)` saves final scenes:
   - resolve persisted scenes by `sceneIndex`
   - create `Individual` nodes
   - create `MENTIONS` relationships from scene to individual

Recommended separation of responsibilities:

- `TriadOrchestrationService`: extraction capture only
- `SceneProcessingService`: final scene persistence
- new individual persistence service/repository layer: `Individual` node and `MENTIONS` relationship persistence

This keeps temporal and entity work adjacent but not entangled.

---

## Failure Behavior

This MVP should not introduce a new ingestion-wide hard failure mode beyond existing pass 2 behavior.

Recommended behavior:

- if pass 2 succeeds but individual extraction block is absent or empty: persist no individuals and continue
- if an extracted individual has no usable alias/display name: skip that individual and continue
- if individual persistence fails after scenes are saved: fail the ingestion step only if the repository write itself fails, not because the LLM returned sparse individual data

The bias should be:

- preserve core ingestion
- treat individual persistence as an additive enrichment layer

---

## Tradeoffs

Pros:

- immediate value from an existing LLM response
- low implementation risk
- no identity-matching mistakes
- clean separation from event DAG work
- easy to inspect and validate in Neo4j

Cons:

- duplicate real-world characters across scenes are expected
- provisional nodes are not yet a usable canonical entity catalog
- some prompt fields may be noisy or inconsistently populated
- triad prompt remains broader than the MVP actually uses

These tradeoffs are acceptable because the purpose of this iteration is to validate that current pass 2 individual output is useful enough to deserve a later canonical entity system.

---

## Success Criteria

This proposal is successful if, after implementation, LoreVault can:

- persist pass 2 extracted individuals into Neo4j
- show which provisional individuals were extracted for each scene
- do so without any cross-scene identity logic
- leave temporal/event behavior unchanged
- give us enough observed graph data to evaluate whether later steps should include:
  - locations
  - collectives
  - evidence-span tightening
  - manual merge/split workflows
  - eventual canonical entity resolution

---

## Follow-On Work Deliberately Deferred

If the MVP proves useful, likely next steps are:

1. add `Location` extraction persistence
2. add `Collective` extraction persistence
3. improve pass 2 prompt shape for persistence-grade evidence
4. add manual review workflows for provisional individuals
5. design a true identity layer for canonical entity matching

Those are explicitly **not** part of this proposal.

---

## Implementation Notes (April 2026)

- Implemented pass2 individual capture by extending `TriadOrchestrationService.TriadStructuredResult` with `currentSceneEntities.individuals` using structured `.entity(...)` mapping only (no raw pass2 XML parser added).
- Preserved temporal triad behavior: temporal analysis records and `TriadEdgePersistenceService` flow remain unchanged in semantics and write shape.
- Added `TriadOutcome` in orchestration and threaded extracted individuals as chapter-local `sceneIndex -> individuals` data.
- Updated scene detection pipeline contract to return both:
  - detected scenes, and
  - extracted per-scene individuals (`SceneDetectionService.SceneDetectionOutcome`).
- Kept persistence ordering as approved: Individuals persist only after final `Scene` save in scene detection stage handling.
- Added provisional graph persistence for individuals:
  - `(:Individual)` node via new `Individual` node type and `IndividualGraphRepository`
  - `(:Scene)-[:MENTIONS]->(:Individual)` link via repository query.
- Structured triad call logging now persists readable serialized response bodies instead of placeholder object references.
- Enforced deterministic MVP display-name rule exactly:
  - first non-blank alias => `displayName`
  - otherwise extracted individual is skipped.
- Cleanup correction applied: removed `BeanWrapperImpl` reflection usage from production scene/triad/handler flow and restored typed accessor usage to align with repository style.
- Corrected provisional graph semantics after implementation: the first persisted layer now represents `IndividualMention` evidence nodes rather than canonical `Individual` entities.
- Adopted mention-ready metadata on provisional nodes for later reconciliation work:
  - `sceneId`
  - `chapterId`
  - `normalizedName`
  - `resolutionStatus`
  - `extractionIndex`
- Deferred persisted `bookId` on mention nodes for now; current mention evidence keeps `chapterId`, and book scope can be resolved via chapter lookup during the first reconciliation pass without widening this MVP refactor.
- Deferred canonical `Individual` creation entirely for now; current writes remain mention-only and preserve the future `IndividualMention -> REFERS_TO -> Individual` path.
- Kept scope intentionally narrow:
  - persisted only pass2 `current_scene_entities.individuals`
  - no matching/dedup/merge logic
  - no location/collective/object/concept/event persistence
  - no event DAG or temporal edge semantics changes.
