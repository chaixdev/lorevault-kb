# Audit generic caught exceptions that should become meaningful business failures

**Status:** IN PROGRESS

## Summary

LoreVault contains a growing set of generic caught exceptions and broad rethrows (`catch (Exception)`, `throw new RuntimeException(...)`, message-based classification) across core business flows.

Some of these are legitimate infrastructure wrappers, but others likely represent expected business failure modes that should instead be rethrown as typed domain/business exceptions with explicit retryability and structured failure semantics.

## Problem

When expected failure modes are represented as generic runtime errors, the system loses important meaning:

- expected business outcomes can look like unexpected technical crashes
- retryability gets inferred from message text instead of stable semantics
- logs and operator interpretation can overstate severity
- downstream status/failure handling becomes brittle and harder to audit

This is already visible in the scene-localization anchor-mismatch path, and it is likely not the only place where exception intent is underspecified.

## Product Context

- Operators need clear distinction between expected model/content mismatch, retryable transient failures, and true system defects.
- Better failure semantics improve trust in ingestion and analysis pipeline behavior.
- Clear business-exception boundaries reduce confusion during incident review and future maintenance.

## Technical Context

Initial context-gathering already shows broad generic-exception patterns in production code, especially under `lorevault-core` in areas such as:

- `ai`
- `ingestion`
- `search`
- `timeline`
- selected service/config/health paths that may influence business behavior

Concrete patterns observed during initial scan include:

- `catch (Exception e)` in orchestration and pipeline services
- generic `RuntimeException` rethrows that normalize distinct failure types into one shape
- message-based retryability and failure classification
- structured failure precedent already exists via:
  - `TriadAnalysisException`
  - `IngestionFailure`
  - `PipelineStageSupport.extractFailure(...)`

Representative candidate hotspots from the initial scan include:

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneProcessingService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneDetectionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/infrastructure/LlmClient.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/EmbeddingService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/triad/SceneRelationshipAnalysisService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionJobService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/*ResolutionHandler.java`
- `lorevault-core/src/main/java/com/lorevault/api/search/RagService.java`
- `lorevault-core/src/main/java/com/lorevault/api/search/Neo4jSemanticSearch.java`

Notable findings from the initial scan:

- `SceneDetectionService` is currently the strongest hotspot: broad catch/rethrow, retry semantics inferred partly from message text, and several business-meaningful failure modes flattened into `RuntimeException`.
- `LlmClient` also encodes AI failure meaning through message text (`empty response`, `failed permanently`, etc.) rather than typed semantics.
- `IngestionService` mixes broad catch/wrap and fallback behavior (`bestEffortLookup`) that may hide some meaningful business-state failures.
- `RagService`, `Neo4jSemanticSearch`, and `CypherTemplateRegistry` include places where failures degrade to empty/false results, potentially blurring “no result” vs “backend/business failure.”

Strong current precedent already exists and should guide the audit baseline:

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/domain/TriadAnalysisException.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionFailure.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/events/IngestionFailedEvent.java`
- related tests in `lorevault-web/src/test/java/com/lorevault/api/api/ai/*` and `.../ingestion/*`

## Scope

- Perform a deep audit of generic caught exceptions and broad runtime rethrows in business-relevant code paths.
- Classify each candidate as one of:
  - correct infrastructure wrapper
  - expected business failure that should become a typed exception
  - unexpected technical defect that should remain crash-like/error-severity
  - unclear / needs deeper design decision
- Identify existing exception/failure patterns worth standardizing on.
- Produce a prioritized shortlist of high-value conversions to typed business exceptions.

## Out of Scope

- Converting every generic exception in one pass.
- Replacing low-level Java/API guard clauses that are clearly programmer-error validation.
- Broad exception-framework redesign across the entire codebase.
- UI error presentation redesign.

## Known Constraints / Prior Findings

- Not every generic exception is wrong; some infrastructure and boundary adapters may reasonably wrap lower-level failures.
- The codebase already has a useful precedent for structured business failure handling (`TriadAnalysisException` + `IngestionFailure`).
- Explore findings suggest the best audit baseline is: typed business exception + structured failure payload + explicit retryability/status propagation, rather than string-based classification.
- Message-based classification is currently present in parts of the retry and ingestion pipeline, which suggests semantics are sometimes encoded too late.
- The scene-localization anchor-mismatch case established an important nuance: a failure can be expected, business-meaningful, and still intentionally retryable.
- This audit should distinguish:
  - business-meaningful + retryable
  - business-meaningful + non-retryable
  - technical/infrastructure/transient
  - programmer defect / invariant violation

## Open Questions

- Should the repository standardize on one common pattern for typed business failures (for example: exception + structured payload), or allow feature-local variations?
- Which areas should be audited first: ingestion/ai only, or all core business flows in one pass?
- Where should retryability live: on the exception type, in structured failure metadata, or in stage-local classification logic?
- Should some current `IllegalStateException` / `RuntimeException` cases remain unchanged because they truly indicate defects rather than business outcomes?

## Success Criteria

- A repo-wide candidate list exists for generic caught exceptions that likely deserve business-exception semantics.
- Each candidate is classified by intent and priority rather than treated as one undifferentiated cleanup task.
- The audit identifies a consistent precedent/pattern for future implementations.
- The next implementation steps can be executed as focused, low-risk follow-up tickets or batches.

## Progress Notes

### Pass 1 (scene-detection semantics hardening) - completed

- Added typed business exception `SceneDetectionException` in `ingestion.domain` with `IngestionFailure` payload.
- Refactored `ingestion.application.scene.SceneDetectionService` to surface business-meaningful scene-detection failures through `SceneDetectionException` rather than flattening key paths into generic runtime wrappers.
- Updated `ingestion.application.pipeline.PipelineStageSupport.extractFailure(...)` to understand `SceneDetectionException` payload semantics for stable stage-level failure extraction.
- Updated targeted scene detection tests to assert typed-failure behavior where applicable.

### Pass 2 (failure-carrier unification + chapter persistence semantics) - completed

- Added shared marker contract `IngestionFailureCarrier` in `ingestion.domain`.
- Updated existing typed business exceptions to implement this contract:
  - `TriadAnalysisException`
  - `SceneLocalizationException`
  - `SceneDetectionException`
- Simplified `PipelineStageSupport.extractFailure(...)` to use the carrier contract instead of exception-specific branching.
- Added `ChapterPersistenceException` for chapter-persistence failure semantics in `IngestionService#createNewChapter(...)`.
- Preserved validation semantics by rethrowing `IllegalArgumentException` for book-not-found validation (not persistence failure).

### Verification evidence

- `mvn -Dsurefire.failIfNoSpecifiedTests=false -Dtest=IngestionServiceTest,SceneDetectionServiceTest,SceneDetectionHandlerTest test` → **PASS** (24 tests, 0 failures).
- `mvn test-compile -DskipTests` → **PASS**.

### Next bounded slice candidates

- Continue highest-impact generic-catch conversion in `SceneProcessingService` and adjacent AI orchestration paths where expected business outcomes are still represented by generic runtime wrapping.
- Revisit selected search degradation paths (`RagService`, `Neo4jSemanticSearch`) to separate true "no result" from backend/business failure semantics without broad behavior changes.

### Pass 3 (typed retryable code semantics in scene detection stage) - completed

- Converted key scene-segmentation retry paths in `SceneDetectionService` from generic `RuntimeException` throws to typed `SceneDetectionException` with stable `IngestionFailure` codes:
  - `SCENE_SEGMENTATION_XML_EMPTY`
  - `SCENE_COORDINATE_LOCALIZATION_EMPTY`
  - `SCENE_COORDINATE_LOCALIZATION_DROPPED_SCENES`
  - `SCENE_SEGMENT_NO_LOCALIZABLE_SCENES`
  - `SCENE_SEGMENTED_FALLBACK_EMPTY`
- Preserved existing retry-loop behavior by keeping retry-exhaustion wrapping semantics (`SCENE_DETECTION_RETRY_EXHAUSTED`) and adding explicit passthrough handling where needed.
- Updated `SceneDetectionHandler.isRetryableError(...)` to classify retryability by typed `SceneDetectionException.failure().code()` for known retryable scene-detection codes, reducing reliance on message-string heuristics.
- Added focused handler coverage for typed retryable scene-detection code classification.

### Verification evidence (pass 3)

- `mvn -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SceneDetectionServiceTest,SceneDetectionHandlerTest test` → **PASS** (16 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneDetectionService.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/SceneDetectionHandler.java`
  - `lorevault-web/src/test/java/com/lorevault/api/ingestion/SceneDetectionHandlerTest.java`

### Pass 4 (SceneProcessingService parsing-catch hardening) - completed

- Narrowed broad XML parsing catch in `SceneProcessingService.parseSceneDetectionXml(...)` from generic `catch (Exception)` to parser-specific checked failures:
  - `ParserConfigurationException`
  - `SAXException`
  - `IOException`
- Added explicit null/blank XML response guard that returns empty results with warning log, preserving fallback behavior while removing avoidable generic exception handling in the parser path.
- Updated XML byte conversion to `StandardCharsets.UTF_8` and narrowed `parseXmlDocument(...)` throws signature accordingly.
- Added dedicated parser-focused test coverage in a new `SceneProcessingServiceTest`:
  - null/blank input returns empty
  - malformed XML returns empty
  - valid XML parses expected scene payload

### Verification evidence (pass 4)

- `mvn -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SceneProcessingServiceTest,SceneDetectionServiceTest,SceneDetectionHandlerTest test` → **PASS** (19 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneProcessingService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/ingestion/application/scene/SceneProcessingServiceTest.java`

### Pass 5 (SceneProcessingService localization-catch narrowing) - completed

- Narrowed the broad localization catch in `SceneProcessingService.localizeSceneCoordinates(...)` from `catch (Exception)` to `catch (RuntimeException)` while preserving the typed business failure mapping to `SceneLocalizationException`.
- Kept existing behavior for known localization business failures (`SceneLocalizationException`) unchanged via explicit passthrough.
- Added focused regression coverage for missing-anchor localization failure in `SceneProcessingServiceTest` to verify typed failure semantics remain stable.

### Verification evidence (pass 5)

- `mvn -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SceneProcessingServiceTest,SceneDetectionServiceTest,SceneDetectionHandlerTest test` → **PASS** (20 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneProcessingService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/ingestion/application/scene/SceneProcessingServiceTest.java`

### Pass 6 (search no-result vs backend-failure disambiguation in hybrid RAG) - completed

- Added bounded failure-disambiguation in `RagService.handleNarrativeQaHybrid(...)`:
  - Each hybrid branch (vector + graph) now returns a branch outcome containing both results and a failure flag.
  - If **both** branches fail, the service now returns a dedicated failure response:
    - `"Search backend failures prevented retrieval for this question."`
    - zero citations
    - metadata `chunksRetrieved=0`, `chunksUsed=0`
- Preserved existing tolerant behavior for partial degradation:
  - If one branch fails but the other succeeds, hybrid fusion still proceeds with available evidence.
  - If branches succeed but evidence is empty, existing no-evidence response remains unchanged.
- Added focused regression coverage in `RagServiceTest` for the both-branches-fail path.

### Verification evidence (pass 6)

- `mvn -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RagServiceTest,Neo4jSemanticSearchIntegrationTest test` → **PASS** (executed set passed; `RagServiceTest` reported 9 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/search/application/RagService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/search/RagServiceTest.java`

### Pass 7 (typed semantic-search backend failure semantics) - completed

- Added typed business exception `SemanticSearchException` in `search.domain` carrying `IngestionFailure` payload semantics.
- Refactored `Neo4jSemanticSearch.search(...)` to stop collapsing backend exceptions into empty results:
  - removed `catch (Exception) -> return List.of()` ambiguity
  - now throws `SemanticSearchException` with code `SEMANTIC_SEARCH_BACKEND_UNAVAILABLE`, stage `SEMANTIC_SEARCH`, and index context (`chunk_embedding_idx`).
- Preserved true zero-hit behavior: successful backend queries with no matches still return empty result lists; only backend failures now propagate typed failure semantics.
- Added bounded caller handling in `RagService.handleNarrativeQaVectorOnly(...)`:
  - catches `SemanticSearchException`
  - returns existing dedicated failure response (`"Search backend failures prevented retrieval for this question."`) instead of misleading no-evidence response.
- Kept hybrid behavior unchanged from pass 6 semantics:
  - partial branch failure remains tolerant
  - both-branch failure still returns dedicated backend-failure answer.

### Verification evidence (pass 7)

- `mvn -Dtest=RagServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (13 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/search/domain/SemanticSearchException.java`
  - `lorevault-core/src/main/java/com/lorevault/api/search/infrastructure/Neo4jSemanticSearch.java`
  - `lorevault-core/src/main/java/com/lorevault/api/search/application/RagService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/search/RagServiceTest.java`

### Pass 7b (post-review hardening) - completed

- Applied Oracle-driven hardening for remaining ambiguity and over-classification gaps in search failure semantics.
- Updated `RagService.handleNarrativeQaHybrid(...)` to avoid ambiguous no-evidence responses when retrieval degraded due to backend failure:
  - if either branch fails and fused evidence is empty, return dedicated backend-failure response
  - if one branch fails but surviving branch yields evidence, preserve degraded-success behavior.
- Narrowed backend-failure translation in `Neo4jSemanticSearch.search(...)`:
  - catch narrowed from broad `Exception` to backend-oriented `DataAccessException | Neo4jException`
  - prevents accidental relabeling of unrelated mapping/programmer defects as backend-unavailable business failures.
- Added focused hybrid regression coverage in `RagServiceTest`:
  - one branch fails + other empty → failure answer
  - one branch fails + other has evidence → successful degraded answer with citation.

### Verification evidence (pass 7b)

- `mvn -Dtest=RagServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (15 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/search/infrastructure/Neo4jSemanticSearch.java`
  - `lorevault-core/src/main/java/com/lorevault/api/search/application/RagService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/search/RagServiceTest.java`

### Pass 8 (entity-lookup fallback disambiguation) - completed

- Hardened the search entity-lookup path so backend/query failures no longer masquerade as empty no-match results.
- Added typed business exception `EntityLookupException` in `search.domain` carrying `IngestionFailure` semantics for query-execution failures.
- Updated `CypherTemplateRegistry.execute(...)`:
  - backend-oriented query failures (`DataAccessException | Neo4jException`) now throw `EntityLookupException` with code `ENTITY_LOOKUP_QUERY_FAILED`
  - unknown `templateId` now throws `IllegalStateException` instead of being treated like a business lookup miss or silently returning empty results
  - genuine zero-row query outcomes still return empty lists.
- Updated `RagService.handleEntityLookup(...)` semantics:
  - fallback to narrative QA now occurs only on genuine empty template results
  - template/query failures propagate instead of silently rerouting the request into narrative QA.
- Added focused regression coverage:
  - `RagServiceTest` asserts entity-lookup query failure does not fall back to narrative QA
  - new `CypherTemplateRegistryTest` locks registry boundary semantics for unknown template IDs and backend query failures.

### Verification evidence (pass 8)

- `mvn clean -Dtest=RagServiceTest,CypherTemplateRegistryTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (14 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- Oracle review completed with no MUST_FIX findings; one refinement applied:
  - unknown internal template IDs are now classified as invariant defects (`IllegalStateException`) rather than `EntityLookupException`.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/search/domain/EntityLookupException.java`
  - `lorevault-core/src/main/java/com/lorevault/api/search/infrastructure/CypherTemplateRegistry.java`
  - `lorevault-core/src/main/java/com/lorevault/api/search/application/RagService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/search/RagServiceTest.java`
  - `lorevault-web/src/test/java/com/lorevault/api/search/CypherTemplateRegistryTest.java`

### Pass 9 (chapter-submission lookup fail-closed semantics) - completed

- Hardened `IngestionService` chapter-submission lookup behavior so lookup ambiguity no longer degrades into duplicate-work creation.
- Added typed business exception `ChapterSubmissionLookupException` in `ingestion.domain` implementing `IngestionFailureCarrier`.
- Replaced fallback semantics in `IngestionService` for submission-critical read paths:
  - content-hash lookup → `CHAPTER_HASH_LOOKUP_FAILED`
  - active-job existence lookup → `CHAPTER_ACTIVE_JOB_LOOKUP_FAILED`
  - recent-job lookup → `CHAPTER_RECENT_JOB_LOOKUP_FAILED`
- Applied Oracle-requested follow-up to close the remaining duplicate-work hole:
  - when `hasActiveJob == true` but the most recent job id cannot be resolved, the service now fails closed with `CHAPTER_ACTIVE_JOB_ID_MISSING`
  - it no longer creates a new ingestion job in that inconsistent state.
- Removed the now-unused `bestEffortLookup(...)` helper from `IngestionService`.
- Preserved separation of concerns:
  - lookup failures use `ChapterSubmissionLookupException`
  - chapter creation/persistence failures still use `ChapterPersistenceException`.

### Verification evidence (pass 9)

- `mvn clean -Dtest=IngestionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (11 tests, 0 failures).
- `mvn clean compile` → **PASS**.
- Oracle review completed with one required follow-up, applied in the same pass:
  - active-job-known but recent-job-id-missing now fails closed instead of creating duplicate work.
- `lsp_diagnostics` (error severity) clean for:
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/domain/ChapterSubmissionLookupException.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/IngestionService.java`
  - `lorevault-web/src/test/java/com/lorevault/api/ingestion/IngestionServiceTest.java`

### Pass 10 (embedding pipeline fail-closed semantics) - completed

- Hardened the ingestion embedding pipeline so backend embedding failure no longer degrades into `0` updated embeddings and false completion.
- Added typed business exception `EmbeddingGenerationException` in `ai.domain` implementing `IngestionFailureCarrier`.
- Updated `EmbeddingService.generateVectors(...)`:
  - backend call failures now throw `EmbeddingGenerationException` with code `EMBEDDING_BACKEND_UNAVAILABLE`
  - non-empty embedding work with empty/malformed response semantics now fails closed instead of being treated like no work.
- Added explicit response validation in `EmbeddingService` for non-empty requests:
  - empty vector response → `EMBEDDING_RESPONSE_EMPTY`
  - mismatched vector count → `EMBEDDING_RESPONSE_COUNT_MISMATCH`
- Preserved legitimate no-work semantics:
  - no chunks still returns `0`
  - all chunks already up to date still returns `0`
- Updated `EmbeddingHandler.isRetryableError(...)` to classify `EMBEDDING_BACKEND_UNAVAILABLE` as retryable via typed failure semantics instead of relying only on message substrings.
- Added focused regression coverage:
  - `EmbeddingServiceTest` asserts backend failure, empty response, and mismatched-count response all surface typed failure instead of `0`
  - `EmbeddingHandlerTest` asserts backend-unavailable and empty-response failures mark the stage failed and do not publish `EmbeddingsCompletedEvent`.

### Verification evidence (pass 10)

- `mvn clean compile` → **PASS**.
- `mvn clean -Dtest=EmbeddingServiceTest,EmbeddingHandlerTest,SystemHealthServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (24 tests, 0 failures).
- Oracle review completed with one MUST_FIX follow-up, applied in the same pass:
  - non-empty embedding work with empty/mismatched response is now treated as failure rather than false success.
- `lsp_diagnostics` clean for changed production files:
  - `lorevault-core/src/main/java/com/lorevault/api/ai/domain/EmbeddingGenerationException.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ai/application/EmbeddingService.java`
  - `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/EmbeddingHandler.java`
  - note: test-file LSP remained stale/intermittent despite clean Maven compile/test success.

### Pass 11 (controller/API boundary search-failure semantics) - completed

- Hardened the JSON ask/search API boundary in `AskController` so known typed search failures no longer collapse into generic `500` responses.
- Updated `lorevault-web/src/main/java/com/lorevault/api/web/query/ask/AskController.java`:
  - `/api/query/ask/vector` now maps `SemanticSearchException` to `503 Service Unavailable`
  - graph-aware / RAG controller paths now map propagated `SemanticSearchException` and `EntityLookupException` to `503 Service Unavailable`
  - unexpected generic exceptions still follow the existing `500` path.
- Preserved bounded scope and existing service behavior:
  - this pass only clarified boundary semantics for propagated typed failures
  - `RagService` fallback/degradation behavior remains unchanged
  - defensive `503` catches were kept where safe even if some paths are not yet commonly exercised.
- Added focused WebMvc regression coverage in `AskControllerWebMvcTest`:
  - semantic-search failure on `/api/query/ask/vector` returns `503`
  - entity-lookup failure on `/api/query/ask/graph-aware` returns `503`.

### Verification evidence (pass 11)

- `mvn clean compile` → **PASS**.
- `mvn clean -Dtest=AskControllerWebMvcTest,UiQueryControllerTest,RagServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (32 tests, 0 failures).
- Oracle review completed with no MUST_FIX findings:
  - verdict: safe to keep as-is
  - note: the widened typed catches on some RAG endpoints are currently more defensive than contractual because those service paths often absorb failures before they reach the controller.
- `lsp_diagnostics` clean for changed production file:
  - `lorevault-web/src/main/java/com/lorevault/api/web/query/ask/AskController.java`
  - note: earlier stale LSP/import noise was not reproduced after verification.

### Pass 12 (HTMX-safe UI query boundary semantics) - completed

- Hardened `UiQueryController` so typed search failures are handled at the UI boundary instead of bubbling through the default error page path.
- Updated `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiQueryController.java`:
  - catches `SemanticSearchException` on `/ui/query/ask/vector`
  - catches `SemanticSearchException | EntityLookupException` on `/ui/query/ask/rag`, `/ui/query/ask/graph-aware`, and `/ui/query/ask/hybrid`
  - returns a dedicated UI fragment `ui/query :: queryError` with user-facing error messaging for typed business failures
  - preserves generic unexpected failures as uncaught so they still flow through the existing error path.
- Added `queryError` fragment to `lorevault-web/src/main/resources/templates/ui/query.html` for HTMX fragment rendering.
- Applied Oracle-requested contract refinement in the same pass:
  - removed forced HTTP `503` status from the UI fragment response because current HTMX flow does not have explicit non-2xx fragment-swap handling
  - final UI contract is `200 + error fragment` for typed search business failures, which keeps the fragment renderable in the current dashboard flow.
- Added focused WebMvc regression coverage in `UiQueryControllerTest`:
  - vector semantic-search failure returns the error fragment
  - graph-aware entity-lookup failure returns the error fragment.

### Verification evidence (pass 12)

- `mvn compile` → **PASS**.
- `mvn clean -Dtest=UiQueryControllerTest,AskControllerWebMvcTest,RagServiceTest,SemanticSearchServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → code-semantics pass verified; one rerun hit a Maven `clean` target-delete race while another build still held `target/`, but the follow-up compile and focused test reruns were clean.
- `mvn -Dtest=UiQueryControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` → **PASS** (6 tests, 0 failures).
- Oracle review completed with one MUST_FIX follow-up, applied in the same pass:
  - the UI error fragment now renders through the existing HTMX flow by returning a normal fragment response instead of HTTP `503`.
- `lsp_diagnostics` remained stale/intermittent for the typed exception imports in `UiQueryController` and `UiQueryControllerTest` even after file sync, but the checked-in source matched the Oracle-fixed contract and Maven compile/tests passed.

## Links

- Related rule: `../rules/exception-semantics.md`
- Related planning: `stuck-ingestion-status.md`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneProcessingService.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/application/scene/SceneDetectionService.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionFailure.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
