# Contain strong package cycles and event-boundary gaps

**Status:** DONE

## Summary

LoreVault's second-pass architectural review identified a small set of strong package-cycle and orchestration seams that deserve their own bounded follow-up item.

This planning item focuses only on the strongest candidates: `ai ↔ ingestion`, `content.timeline ↔ ai`, `ingestion ↔ content.timeline`, and the direct orchestration seam where scene detection still performs analysis and persistence fan-out through direct calls instead of the event pipeline shape already used elsewhere.

## Problem

The current module split is sound, but some of the most important workflow boundaries inside `lorevault-core` are still coupled through direct cross-package calls.

The strongest issues identified in the review are:

- `ai ↔ ingestion` remains a real bidirectional package cycle rather than just a documented historical note
- `content.timeline` is involved in strong two-way coupling with both `ai` and `ingestion`, which risks turning it into a cross-feature integration sink instead of a contained content subarea
- `SceneDetectionHandler` still performs a large direct-call fan-out into AI analysis and downstream persistence concerns before the rest of the event-driven ingestion flow takes over
- `SceneRelationshipAnalysisService` still reaches back into ingestion lifecycle ownership by updating ingestion job state directly

These seams are stronger candidates for bounded decoupling work than the weaker book-reduction follow-up areas, which are intentionally excluded from this item.

## Product Context

- Contributors need the ingestion and analysis flow to remain understandable enough that new event-extraction and aggregation work does not deepen existing structural debt by accident.
- Operators and maintainers benefit when the most active workflow path has clearer ownership boundaries and fewer hidden cross-package side effects.
- A bounded follow-up on the strongest seams reduces the chance that future feature work turns known architectural debt into default practice.

## Technical Context

Second-pass review findings highlighted these concrete strong candidates:

1. **`ai ↔ ingestion`**
   - `lorevault-core/src/main/java/com/lorevault/api/ai/application/SceneRelationshipAnalysisService.java`
   - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneDetectionService.java`
   - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/SceneDetectionHandler.java`

2. **`content.timeline ↔ ai`**
   - `lorevault-core/src/main/java/com/lorevault/api/content/timeline/application/SceneTemporalRelationshipPersistenceService.java`
   - `lorevault-core/src/main/java/com/lorevault/api/ai/application/SceneRelationshipAnalysisService.java`

3. **`ingestion ↔ content.timeline`**
   - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/SceneDetectionHandler.java`
   - `lorevault-core/src/main/java/com/lorevault/api/content/timeline/application/SceneTemporalRelationshipPersistenceService.java`

4. **Direct-call fan-out that does not yet match the event spine**
   - `SceneDetectionHandler` directly calls `SceneRelationshipAnalysisService`, `SceneTemporalRelationshipPersistenceService`, `IndividualPersistenceService`, `LocationPersistenceService`, and `EventPersistenceService` before the rest of the ingestion event chain continues
   - `SceneRelationshipAnalysisService` directly updates ingestion job state through `IngestionJobService`

Relevant architectural context:

- `docs/patterns/ingestion/ingestion-pipeline.md` documents the current event-driven ingestion spine and explicitly treats handler fan-out as event-based workflow
- `docs/patterns/codebase-topology.md` documents `ai ↔ ingestion` as known debt and says new coordination should prefer events
- `docs/rules/code-organization-guidance.md` defines `content.timeline` as part of `content`, not as a broad cross-feature orchestration layer
- the weaker book-level reduction services were reviewed separately and are intentionally not part of this item because they already operate as event-driven consumers and do not need to be pulled into the first pass

## Scope

- Revisit only the strongest cycle candidates and event-boundary gaps from the second-pass review.
- Assess how to reduce or contain `ai ↔ ingestion` without broadening the change into a repo-wide architecture rewrite.
- Assess how to keep `content.timeline` from remaining a bidirectional dependency sink between AI analysis and ingestion workflow.
- Revisit the scene-detection fan-out seam so downstream analysis/materialization work aligns better with the event-driven ingestion pattern already documented in the repo.
- Revisit the AI-to-ingestion lifecycle callback where analysis code updates ingestion job state directly.
- Leave enough context for a later brainstorm or implementation pass to decide exact event shapes and ownership boundaries.

## Out of Scope

- The weaker book-reduction candidates (`BookIndividualReductionService`, `BookLocationReductionService`) which will stay as-is for now
- A full elimination of all package coupling inside `lorevault-core`
- New Maven modules or a broad module taxonomy change
- A repo-wide migration of all direct calls to events
- Detailed event schema design in this planning item itself

## Known Constraints / Prior Findings

- `ai ↔ ingestion` is already acknowledged as known debt in the topology docs, but the second-pass review confirmed it is still a live cycle in code.
- `content.timeline` is supposed to be a content-owned subarea, not a general-purpose integration layer.
- The ingestion pipeline already has an established event spine with `@TransactionalEventListener(AFTER_COMMIT)` at scene detection and downstream `@EventListener + @Async("ingestionTaskExecutor")` handlers.
- Because that event infrastructure already exists, the strongest orchestration seam is not greenfield; it is a mismatch between current direct-call orchestration and the repo's own prevailing ingestion workflow pattern.
- This planning item is intentionally narrower than the broader architectural-hygiene guardrails item and should not absorb unrelated transport-boundary or visibility cleanup.

## Open Questions

- Which part of the current scene-detection fan-out should become a downstream event boundary first: AI triad analysis, temporal persistence, entity mention persistence, or some combination?
- What is the smallest useful boundary that removes AI-owned mutation of ingestion job state without making the status model less observable?
- Should `content.timeline` be treated as a pure materialization/persistence area, or does it still need a feature-owned orchestration role somewhere in the current design?
- How much of the current direct-call chain can be re-scoped without changing ingestion completion semantics or branch counting in the existing coordinator?

## Success Criteria

- Future brainstorm or implementation work can address the strongest cycle and event-boundary seams from a bounded planning item rather than rediscovering them from scratch.
- The scope clearly isolates the strong candidates from weaker follow-up areas that are intentionally deferred.
- Contributors can tell which current direct-call seams are architectural priorities and why they matter.
- A later implementation pass can reduce or contain the targeted cycles without needing to reopen the full modulith-hygiene problem space.

## Links

- Related planning item: `architectural-hygiene-guardrails-for-modulith-boundaries.md`
- Related pattern: `../patterns/codebase-topology.md`
- Related pattern: `../patterns/ingestion/ingestion-pipeline.md`
- Related rules: `../rules/code-organization-guidance.md`
- Related rules: `../rules/lorevault-module-conventions.md`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api/ai`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api/content/timeline`

## Progress Notes

### Pass 1 (bounded first slice) - completed

- Removed direct ingestion lifecycle ownership from `SceneRelationshipAnalysisService` by deleting its `IngestionJobService` dependency and in-method triad status updates.
- Moved triad-stage status ownership to `SceneDetectionHandler` before triad analysis execution (`IngestionStatus.SCENE_TRIAD_ANALYSIS`).
- Kept triad analysis behavior intact (targeted unit test suite still green after constructor/test updates).

### Pass 1b (Oracle-guided adjustment) - completed

- Preserved the ownership move but tightened granularity to maintain observability/provenance contracts.
- `SceneRelationshipAnalysisService` now accepts an ingestion-owned triad-start callback (`Consumer<Map<String,Object>>`) and invokes it once per triad with existing triad metadata (`triadIndex`, `currentSceneId`, scene indexes).
- `SceneDetectionHandler` now passes the per-triad callback and emits `SCENE_TRIAD_ANALYSIS` status **per triad** (ingestion-owned) instead of a chapter-level single update.
- Added a focused test asserting per-triad callback metadata emission.

### Verification evidence

- `mvn -pl lorevault-web -am clean test -Dtest=SceneRelationshipAnalysisServiceTest -Dsurefire.failIfNoSpecifiedTests=false` → **PASS** (9 tests, 0 failures).
- `mvn -pl lorevault-web -am clean test -Dtest=SceneRelationshipAnalysisServiceTest,SceneDetectionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false` → **PASS** (19 tests, 0 failures).
- `mvn -pl lorevault-web -am clean test -P architecture-tests` → **expected FAIL** in cycle-free architecture test, but with reduced violation count from earlier baseline.
  - Previous baseline (guardrail pass): **17** cycle violations.
  - After pass 1 + pass 1b adjustment: **8** cycle violations.

### Pass 2 (bounded ai->content.timeline cut) - completed

- Removed `ai.application -> content.timeline.application` dependency by eliminating `TriadRelationInverter` usage from `SceneRelationshipAnalysisService`.
- Inlined the inversion logic inside `SceneRelationshipAnalysisService` (`invertPrevToCurr`) so AI no longer imports timeline utility code for triad relation inversion.
- Preserved behavior and triad contract outputs while reducing one direct cross-package edge.

### Verification evidence (pass 2)

- `mvn -pl lorevault-web -am clean test -Dtest=SceneRelationshipAnalysisServiceTest,SceneDetectionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false` → **PASS** (19 tests, 0 failures).
- `mvn -pl lorevault-web -am clean test -P architecture-tests` → **expected FAIL** in cycle-free architecture test, with additional reduction:
  - After pass 1 + pass 1b: **8** cycle violations.
  - After pass 2: **6** cycle violations.

### Next bounded slice candidates

- Isolate triad DTO contracts out of `SceneRelationshipAnalysisService` nested types into a neutral contract package to reduce both `ingestion.infrastructure -> ai.application` and `content.timeline -> ai.application` coupling.
- After DTO extraction, reassess if the remaining `ai - infrastructure -> ingestion - infrastructure` logging dependency should be inverted behind an ingestion-owned port/event to further collapse `ai`↔`ingestion` infra cycles.

### Pass 3 (bounded triad contract extraction) - completed

- Extracted normalized triad outcome/extraction contracts from `SceneRelationshipAnalysisService` into a shared triad contract type for downstream consumers.
- Updated downstream consumers to use the shared normalized contracts:
  - `SceneTemporalRelationshipPersistenceService`
  - `IndividualPersistenceService`
  - `LocationPersistenceService`
  - `EventPersistenceService`
  - `SceneDetectionHandler`
- Migrated affected unit tests to the new `TriadAnalysisModels.*` types.
- Preserved Oracle-guided boundary: raw AI structured response DTOs (`TriadStructuredResult`, `TriadRelation`, `TriadCurrentSceneEntities`) remain owned by `SceneRelationshipAnalysisService`; only post-normalization handoff contracts were extracted.

### Verification evidence (pass 3)

- `mvn -pl lorevault-web -am clean test-compile -DskipTests` → **PASS**.
- `mvn -pl lorevault-web -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SceneRelationshipAnalysisServiceTest,SceneDetectionHandlerTest,IndividualPersistenceServiceTest,LocationPersistenceServiceTest,EventPersistenceServiceTest,SceneTemporalRelationshipPersistenceServiceTest test` → **PASS** (36 tests, 0 failures).
- `mvn -pl lorevault-web -am clean test -P architecture-tests` → **expected FAIL** in cycle-free architecture test, with further reduction:
  - After pass 2: **6** cycle violations.
  - After pass 3: **2** cycle violations.

### Remaining cycle theme after pass 3

- Remaining violations are now concentrated in structural ownership seams, not AI triad DTO leakage:
  - `content.entities ↔ content.timeline` (entity/timeline mutual dependency)
  - `content.timeline -> ingestion.infrastructure -> content.entities` (timeline observability lookups through ingestion infra repositories, plus ingestion infra writes against content entities)

### Pass 4 (bounded structural seam cut) - completed

- Removed the reverse `content.entities -> content.timeline` edge by deleting `content.timeline.domain.Event` and removing `Scene implements Event` (kept Scene as the current Event carrier via labels and event-oriented getters).
- Removed direct `content.timeline -> ingestion.infrastructure` repository dependencies by introducing an ingestion-owned lookup seam:
  - Added `ingestion.domain.TriadAnalysisArtifactLookup`.
  - Added `ingestion.infrastructure.GraphTriadAnalysisArtifactLookup` delegating to existing ingestion repositories.
  - Updated `SceneTemporalRelationshipPersistenceService` to depend on the ingestion-owned lookup seam instead of direct ingestion infrastructure repositories.
- Preserved triad provenance/error behavior by reusing the same underlying repository queries and preserving existing failure semantics in timeline service.

### Verification evidence (pass 4)

- `mvn -pl lorevault-web -am test-compile -DskipTests` → **PASS**.
- `mvn -pl lorevault-web -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SceneTemporalRelationshipPersistenceServiceTest,SceneDetectionHandlerTest,SceneRelationshipAnalysisServiceTest,IndividualPersistenceServiceTest,LocationPersistenceServiceTest,EventPersistenceServiceTest test` → **PASS** (36 tests, 0 failures).
- `mvn -pl lorevault-web -am clean test -P architecture-tests` → **PASS** (all architecture tests green; cycle-free rule no longer violated).

### Pass 4 follow-up (ownership + terminology correction) - completed

- Moved normalized triad workflow/result records from `content.entities.triad.TriadAnalysisModels` to `ingestion.application.result.TriadAnalysisModels` so the contracts now live with ingestion workflow ownership rather than canonical graph entity state.
- Renamed the ingestion-owned provenance seam to remove legacy port/adapter wording from live code:
  - `TriadAnalysisArtifactLookupPort` → `TriadAnalysisArtifactLookup`
  - `TriadAnalysisArtifactLookupAdapter` → `GraphTriadAnalysisArtifactLookup`
- Clarified current Event direction without introducing a new Event implementation:
  - `Scene` remains the current persisted Event carrier through labels and event-oriented accessors
  - current docs now describe broader Event modeling as future work rather than implying Scene-as-Event is still only planned.
- Updated current-surface docs and metadata to remove stale internal port/adapter wording and align rule language with the bounded ingestion-owned lookup seam.
- Applied Oracle follow-up for current-doc drift:
  - replaced the stale `ContentPersistencePort` ingestion sequence in `docs/architecture/02-functional-viewpoint.md`
  - removed remaining current-surface port wording in `docs/PROJECT-STATUS.md`
  - aligned rules wording so the small same-module lookup seam reads as a justified ownership seam instead of a policy contradiction.

### Verification evidence (pass 4 follow-up)

- `mvn clean compile` → **PASS**.
- `mvn -pl lorevault-web -am -Dtest=SceneRelationshipAnalysisServiceTest,SceneDetectionHandlerTest,IndividualPersistenceServiceTest,LocationPersistenceServiceTest,EventPersistenceServiceTest,SceneTemporalRelationshipPersistenceServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (41 tests, 0 failures).
- `lsp_diagnostics` clean for changed production files:
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/result/TriadAnalysisModels.java`
  - `lorevault-core/src/main/java/com/lorevault/api/content/entities/Scene.java`
  - `lorevault-core/src/main/java/com/lorevault/api/content/timeline/application/SceneTemporalRelationshipPersistenceService.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/domain/TriadAnalysisArtifactLookup.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/infrastructure/GraphTriadAnalysisArtifactLookup.java`
  - `docs/architecture/02-functional-viewpoint.md`
  - `docs/PROJECT-STATUS.md`
  - `docs/rules/service-design-principles.md`
  - `docs/rules/coding-standards.md`
- Oracle review completed:
  - initial review found one MUST_FIX (stale `ContentPersistencePort` sequence doc) and two wording-alignment SHOULD_FIX items
  - all review findings were applied in the same pass.
