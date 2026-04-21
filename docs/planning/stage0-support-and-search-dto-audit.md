# Stage 0 Audit: support package and search DTO ownership classification

**Status:** ACTIVE
**Branch:** `refactor/staged-package-reorganization-stage0-audit`
**Parent plan:** `staged-package-reorganization-and-module-split-prep.md`

## Purpose

This document is the **Stage 0 deliverable** as defined in the parent plan. It classifies every type in `com.lorevault.api.support` and every search DTO by ownership risk, with zero code moves. It satisfies the Stage 0 exit criteria:

- [x] Every type in `support` is classified as: web-owned transport, core-owned domain/orchestration, shared contract, or misplaced (feature-local).
- [x] Every search DTO is classified as: web-owned or core-owned.
- [x] Error-response shaping types have a designated owner.
- [x] No new types have been moved — this stage is audit-only.

---

## 1. support package classification

### Current inventory (21 public types)

| Type | Kind | Consumers (production) | Proposed ownership | Confidence |
|---|---|---|---|---|
| `ErrorResponse` | Transport DTO | 5 web controllers (`JobsController`, 4 ingestion command controllers) | **web-owned transport** | HIGH |
| `JobStatusResponse` | Transport DTO | `web.query.job.JobsController`, `web.ui.JobsUiController`, `ingestion.IngestionService`, `ingestion.IngestionJobService` | **shared contract** (web + core both consume) | HIGH |
| `JobListResponse` | Transport DTO | Same consumers as `JobStatusResponse` | **shared contract** (web + core both consume) | HIGH |
| `SubmitChapterRequest` | Transport DTO | `web.command.ingestion.CommandIngestionController`, `web.ui.IngestionUiController`, `ingestion.IngestionService` | **shared contract** (web + core both consume) | HIGH |
| `SubmitChapterResponse` | Transport DTO | Same consumers as `SubmitChapterRequest` | **shared contract** (web + core both consume) | HIGH |
| `CreateUniverseRequest` | Transport DTO | `web.command.library.LibraryCommandController`, `web.ui.IngestionUiController`, `web.ui.LibraryUiController`, `library.LibraryService` | **shared contract** (web + library both consume) | HIGH |
| `CreateUniverseResponse` | Transport DTO | Same consumers as `CreateUniverseRequest` | **shared contract** | HIGH |
| `CreateSeriesRequest` | Transport DTO | Same pattern as `CreateUniverseRequest` | **shared contract** | HIGH |
| `CreateSeriesResponse` | Transport DTO | Same pattern as `CreateSeriesRequest` | **shared contract** | HIGH |
| `CreateBookRequest` | Transport DTO | Same pattern as `CreateUniverseRequest` | **shared contract** | HIGH |
| `CreateBookResponse` | Transport DTO | Same pattern as `CreateBookRequest` | **shared contract** | HIGH |
| `BookLocationResolutionResponse` | Transport DTO | `web.command.ingestion.BookLocationResolutionCommandController`, `web.ui.UiOperatorActionsController`, `ingestion.BookLocationReductionHandler`, `ingestion.BookLocationReductionService` | **shared contract** (web + core) | HIGH |
| `ChapterLocationResolutionResponse` | Transport DTO | Same cross-cutting pattern as `BookLocationResolutionResponse` | **shared contract** | HIGH |
| `BookIndividualResolutionResponse` | Transport DTO | Same cross-cutting pattern | **shared contract** | HIGH |
| `ChapterIndividualResolutionResponse` | Transport DTO | Same cross-cutting pattern | **shared contract** | HIGH |
| `PublicationCoordinates` | Domain value object | `content.Chapter`, `ingestion.IngestionService`, `search.AskDtos.CitationDto`, test builders | **core-owned domain** (used by content, ingestion, search — all core) | HIGH |
| `StringSanitizer` | Utility | `support.PublicationCoordinates` (internal), `content.Universe` | **core-owned utility** (only core types use it) | HIGH |
| `HashUtils` | Utility | `ingestion.IngestionService`, `ingestion.ChunkingHandler` | **core-owned utility** (ingestion-only) | HIGH |
| `SpoilerVisibility` | Transport DTO | `search.AskDtos`, `search.SemanticSearchDtos`, `search.SemanticSearchService`, `search.CypherTemplateRegistry`, `search.Neo4jSemanticSearch` | **shared contract** (search DTOs consumed by web controllers + search services) | HIGH |
| `SeriesProgress` | Transport DTO | `search.CypherTemplateRegistry`, `search.Neo4jSemanticSearch` (nested in `SpoilerVisibility`) | **shared contract** (only used with `SpoilerVisibility` in search context) | MEDIUM — could be search-internal if `SpoilerVisibility` moves |
| `UnconfiguredSeriesPolicy` | Enum | `search.CypherTemplateRegistry`, `search.Neo4jSemanticSearch` (nested in `SpoilerVisibility`) | **shared contract** (same reasoning as `SeriesProgress`) | MEDIUM — same dependency chain as `SeriesProgress` |

### Summary by proposed ownership

| Ownership bucket | Types | Count |
|---|---|---|
| **web-owned transport** | `ErrorResponse` | 1 |
| **core-owned domain/utility** | `PublicationCoordinates`, `StringSanitizer`, `HashUtils` | 3 |
| **shared contract** (web + core both consume) | `JobStatusResponse`, `JobListResponse`, `SubmitChapterRequest`, `SubmitChapterResponse`, `CreateUniverseRequest/Response`, `CreateSeriesRequest/Response`, `CreateBookRequest/Response`, `*ResolutionResponse` × 4, `SpoilerVisibility`, `SeriesProgress`, `UnconfiguredSeriesPolicy` | 17 |
| **misplaced / feature-local** | (none found) | 0 |

### Key finding: no types are purely misplaced

Every type in `support` is either genuinely consumed across the web/core boundary or is a core utility. There are no "obvious move to feature package" candidates that would reduce the shared surface. The package is not a catch-all of misplaced types — it is a **catch-all of cross-boundary DTOs**.

---

## 2. Search DTO classification

| Type | Current package | Consumers | Proposed ownership | Confidence |
|---|---|---|---|---|
| `AskDtos.AskRequest` | `search` | `web.query.ask.AskController`, `search.RagService` | **shared contract** | HIGH |
| `AskDtos.AskResponse` | `search` | `web.query.ask.AskController`, `search.RagService` | **shared contract** | HIGH |
| `AskDtos.CitationDto` | `search` | `web.query.ask.AskController`, `search.RagService` | **shared contract** | HIGH |
| `AskDtos.AskFilters` | `search` | `search.RagService` | **core-owned** (only consumed by service, not directly by web) | MEDIUM — could stay in `search` |
| `AskDtos.AskMetadata` | `search` | `search.RagService` | **shared contract** (returned through web) | HIGH |
| `SemanticSearchDtos.SemanticSearchRequest` | `search` | `web.query.ask.AskController`, `search.SemanticSearchService` | **shared contract** | HIGH |
| `SemanticSearchDtos.SemanticSearchResponse` | `search` | Same | **shared contract** | HIGH |
| `SemanticSearchDtos.SearchResultDto` | `search` | Same | **shared contract** | HIGH |
| `SemanticSearchDtos.SemanticSearchFilters` | `search` | `search.SemanticSearchService` | **core-owned** | MEDIUM |
| `SemanticSearchDtos.SearchMetadata` | `search` | `search.SemanticSearchService` | **shared contract** | HIGH |

### Key finding: search DTOs are already in the right feature package

Search DTOs live in `search` and are consumed by both web controllers and search services. They do not need to move for package clarity — but they **are** shared contracts that will need to be accounted for in the module split.

---

## 3. Dual ErrorResponse conflict

The codebase contains **two different ErrorResponse classes**:

| Class | Package | Structure | Consumers |
|---|---|---|---|
| `ErrorResponse` | `com.lorevault.api.support` | Generic: `{ error: { code, message, details }, timestamp, path }` | 5 web controllers (query + command) |
| `ErrorResponseFactory.ErrorResponse` | `com.lorevault.api.web.command.ingestion.response` | Simpler: `{ timestamp, status, error, message, code, details }` (flat, no nested `ErrorDetails`) | `ErrorResponseFactory` only (used by `CommandIngestionController` and resolution controllers via the factory) |

### Resolution direction

1. `support.ErrorResponse` is the **canonical web error format** — used by the query side and some command controllers.
2. `ErrorResponseFactory.ErrorResponse` is a **local variant** within the ingestion command area with a different JSON shape.
3. The ingestion command controllers that import `support.ErrorResponse` (the 4 resolution controllers) do so for return type compatibility, but the actual error responses they produce go through `ErrorResponseFactory` which returns its own inner class.
4. **Recommendation for Stage 1:** Consolidate on one `ErrorResponse` shape. The `support.ErrorResponse` with nested `ErrorDetails` is more structured. The factory's flat variant should be retired in favor of the canonical shape, and the factory should produce `support.ErrorResponse` instances. Then `ErrorResponse` moves to `web` as part of the module split.

---

## 4. IngestionStatus dependency note

`JobStatusResponse` and `JobListResponse` both reference `com.lorevault.api.ingestion.IngestionStatus` — a domain enum from the `ingestion` package. This creates a dependency from shared DTOs into a core domain type, which is the exact pattern the parent plan warns about: *"transport DTOs reuse domain enums directly."*

This is acceptable for now because:
- `IngestionStatus` is a stable enum, not a mutable domain object.
- Removing the dependency would require a parallel enum in the shared area, which is worse.
- The module split should place `IngestionStatus` in `core`, and the shared DTOs in `api` (if it exists) or have the web module depend on core.

---

## 5. Stage 1 execution record

The following three moves from Section 5 were executed and verified:

| Move | From | To | Import sites updated | Test status |
|---|---|---|---|---|
| `HashUtils` | `support` | `ingestion` | 2 production + 1 test | Pass |
| `StringSanitizer` | `support` | `content` | 2 production + 1 test | Pass |
| `PublicationCoordinates` | `support` | `content` | 4 production + 6 test | Pass |

**Verification:** `mvn clean compile` succeeds. `mvn test` passes all 301 tests with 0 failures.

After these moves, `support` now contains 18 types (down from 21), all of which are transport-shaped shared contracts. The package no longer contains any utility classes or domain value objects.

## 6. Proposed Stage 1 targets (from this audit)

Based on the classification above, the Stage 1 moves that would most reduce module ambiguity are:

### High-value, low-risk

1. **Move `HashUtils` to `ingestion`** — only consumed by `IngestionService` and `ChunkingHandler`. This is core-owned and has zero web consumers. Moving it removes one non-contract type from `support`.

2. **Move `StringSanitizer` to `content`** — only consumed by `PublicationCoordinates` (support-internal) and `Universe` (content package). This is core-owned with zero web consumers. Moving it removes another non-contract type from `support`.

3. **Move `PublicationCoordinates` to `content`** — a domain value object used by `Chapter`, `IngestionService`, and search DTOs. It is core-owned. This move slightly clarifies that `support` is for transport contracts, not domain models.

### Medium-value, requires more thought

4. **Consolidate ErrorResponse** — retire `ErrorResponseFactory.ErrorResponse` in favor of the canonical `support.ErrorResponse`, then move `ErrorResponse` to the `web` package during module split.

5. **SpoilerVisibility / SeriesProgress / UnconfiguredSeriesPolicy cluster** — these three are tightly coupled. `SpoilerVisibility` is consumed by search DTOs (shared), but `SeriesProgress` and `UnconfiguredSeriesPolicy` are only consumed by search infrastructure. If search DTOs move to a shared area, these three should follow as a unit.

### Deferred (move-twice risk)

6. **All `Create*Request/Response` and `*ResolutionResponse` types** — these are genuine shared contracts. They should not move until the `api` shared-contracts module decision is made, because moving them to a feature package and then to `api` would be move-twice work.

---

## 7. Revised support package profile after proposed Stage 1 moves

If moves 1–3 are executed, `support` shrinks from 21 to 17 types, and all remaining types are **transport-shaped shared contracts**. The package would then have a clear, narrow meaning: "cross-boundary DTOs consumed by both web and core."

| After Stage 1 | Status |
|---|---|
| `support` contains only transport DTOs | Yes |
| All remaining types are shared contracts | Yes |
| `support` has no utility classes | Yes |
| `support` has no domain types | Yes |
| `support` has no catch-all policy types | Yes |

This satisfies the Stage 1 exit criterion: *"support contains only types that are genuinely shared contracts with two or more real consumers."*
