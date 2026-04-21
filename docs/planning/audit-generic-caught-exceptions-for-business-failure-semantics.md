# Audit generic caught exceptions that should become meaningful business failures

**Status:** NOT STARTED

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

- `lorevault-core/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/SceneDetectionClient.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/EmbeddingService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ai/TriadOrchestrationService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionJobService.java`
- `lorevault-core/src/main/java/com/lorevault/api/ingestion/*ResolutionHandler.java`
- `lorevault-core/src/main/java/com/lorevault/api/search/RagService.java`
- `lorevault-core/src/main/java/com/lorevault/api/search/Neo4jSemanticSearch.java`

Notable findings from the initial scan:

- `SceneDetectionService` is currently the strongest hotspot: broad catch/rethrow, retry semantics inferred partly from message text, and several business-meaningful failure modes flattened into `RuntimeException`.
- `SceneDetectionClient` also encodes AI failure meaning through message text (`empty response`, `failed permanently`, etc.) rather than typed semantics.
- `IngestionService` mixes broad catch/wrap and fallback behavior (`bestEffortLookup`) that may hide some meaningful business-state failures.
- `RagService`, `Neo4jSemanticSearch`, and `CypherTemplateRegistry` include places where failures degrade to empty/false results, potentially blurring “no result” vs “backend/business failure.”

Strong current precedent already exists and should guide the audit baseline:

- `lorevault-core/src/main/java/com/lorevault/api/ai/TriadAnalysisException.java`
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

## Links

- Related planning: `scene-localization-anchor-mismatch-should-raise-business-failure.md`
- Related planning: `stuck-ingestion-status.md`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/IngestionFailure.java`
- Related implementation: `../../lorevault-core/src/main/java/com/lorevault/api/ingestion/PipelineStageSupport.java`
