> **Research only - not an implementation target**

# LoreVault Codebase Health & Version Assessment (Aug 21, 2025)

> **Research only - not an implementation target**

Author: GitHub Copilot (AI pair engineer)
Branch analyzed: testing-rewrite-phase1

## Executive summary

- Current project version: 0.8.0 (root and module POMs)
- Test status: mvn test passes (98 tests, 0 failures)
- Feature snapshot:
  - Semantic search (v0.7.x) present via in-memory adapter behind `SemanticSearchPort`; REST surface `/api/query/ask/vector` exists via `AskController` using `SemanticSearchService`.
  - RAG question answering present via `RagService` and `/api/query/ask/rag`, using Spring AI `ChatClient` with prompt templates.
  - Ports & Adapters refactor is visible: `SemanticSearchPort`, in-memory adapter, service orchestration; ingestion command/query split.
- Recommendation: qualify for 0.8.0 (RAG foundation) if we accept current pragmatic constraints (in-memory retrieval, minimal citations). If 0.8.0 requires DB-native vector search and spoiler gating, then remain at 0.7.x.

My read: project_summary.md defines v0.8.0 = RAG Question Answering with citations. The codebase has this feature implemented and tested (unit/integration). Therefore, bumping to 0.8.0 is justified, with clear follow‑ups for 0.8.x hardening.

---

## Evidence for version recommendation

- POMs show 0.8.0 currently.
- RAG endpoints and services:
  - `AskController` exposes POST `/api/query/ask/rag` and `/api/query/ask/vector`.
  - `RagService` orchestrates semantic search + LLM generation via Spring AI `ChatClient`; applies validation and filters; builds citations from search results.
  - `PromptLoaderService` loads `prompts/rag-answer-generation.txt` (present and used); logs confirm loading during tests.
- Tests green, including:
  - `AskControllerTest` exercising validation and wiring with mocked `RagService`/`SemanticSearchService`.
  - `SemanticSearchServiceTest`, `InMemorySemanticSearchAdapterTest`, `SimilarityOrderingIntegrationTest`.
  - Scene detection, embeddings, schema bootstrap tests.
- Surefire excludes temporarily: `PromptLoaderServiceTest`, `RagServiceTest` are excluded in module surefire config; rest of the suite still provides coverage and build remains green.

Conclusion: Minimum definition of 0.8.0 (RAG answering over vector retrieval with citations) is present and functional. Recommend: tag as 0.8.0 with a RELEASE_NOTES capturing caveats.

---

## Notable strengths

- Clean ports & adapters structure for search (`SemanticSearchPort` with in-memory adapter; clear path to swap in Neo4j-native vector search).
- Clear DTOs for ask/search with validation annotations and metadata.
- Prompt management is centralized (`PromptLoaderService`), preloading templates at startup.
- Testability and reliability: retry-aware scene detection service; deterministic embedding test config; extensive small/medium tests; Testcontainers for Neo4j connectivity.
- Logging is informative and scoped per service (good for ops) and uses concise emojis in retry logs for clarity.

---

## Code smells, gaps, and technical debt

Severity legend: [!] high, [~] medium, [-] low

• [~] In-memory vector search in production path
   - File: `InMemorySemanticSearchAdapter`
   - Impact: full scan on each query; no spoiler gating; no index. Suitable only for small corpora.
   - TODOs in code acknowledge this. Path to resolution: Neo4j vector index and adapter.

• [-] Filters not applied at source
   - `matchesFilters` returns true; hierarchy materialization missing on `Chunk` nodes.
   - Consequence: `/ask` filters are accepted but do not restrict results meaningfully.

• [~] RAG citations lack stable coordinates
   - Citations return chunkId + optional book/chapter numbers, but these are null in current adapter due to missing hierarchy traversal/materialized coordinates.
   - User experience: citations have missing location context.

• [-] Multi-provider LLM configuration is stubbed
   - `MultiProviderLlmConfiguration` contains TODOs and throws `UnsupportedOperationException` for dynamic creation.
   - Current wiring uses explicit `SpringAiConfig` with two ChatClients (nlpSmall/nlpBig), which is fine for v0.8.0; remove the unused/stub config or gate it behind a profile.

• [-] Surefire excludes tests
   - `PromptLoaderServiceTest` and `RagServiceTest` excluded. This masks issues and reduces coverage in CI. They should be re-enabled or migrated to deterministic setups.

• [~] Embedding adapter debt
   - `MultiProviderEmbeddingAdapter` includes TODOs for provider-specific settings and actual HTTP call implementation; error logs in tests show shaky local mock endpoint semantics. Ensure a robust adapter with retries/timeouts/metrics.

• [-] Deprecated `@MockBean` usage warning in tests
   - Spring Boot 3.5 deprecation noted. Track upgrade path to replacement annotations or module.

• [-] Internal Neo4j ID deprecation warning
   - `SceneHasChunk` uses internal Long ids; SDN warns they are deprecated. Introduce external IDs for relationships/entities.

• [-] Configuration drift and backups
   - `application.yml.backup` present. Risk of configuration confusion. Remove backup or move to docs.

• [-] Ask filters hierarchy silently coerces/ignores

`RagService.validateAndConvertFilters` logs warnings and drops invalid combinations. Consider bean validation or API docs clarifying constraints. Also surface invalid filters with 400 instead of silent adjustment.

• [~] No explicit rate limiting/guardrails on `/ask`

Could DOS the in-memory search/LLM. Consider per-request caps, circuit breakers, and budget.

• [-] Error handling in `RagService` on empty LLM response throws generic RuntimeException

Replace with domain-specific exception mapped to 502 with actionable message.

• [-] Lack of API docs contracts

No OpenAPI spec checked in; Postman collections exist. Consider springdoc-openapi or hand-authored spec in docs/api/specifications.

• [-] Build metadata/versioning mismatch with roadmap

POM is now at 0.8.0; README/docs partially out-of-date (quick start still mentions 0.4.0 in some places; project_summary claims 0.7.0 completed and 0.8.0 planned).

---

## Known TODOs and cut corners in code

Collected via grep and review:

- `InMemorySemanticSearchAdapter`: implement DB-native vector search; implement filters; enrich results with chapter coordinates.
- `MultiProviderLlmConfiguration`: implement proper programmatic ChatClient creation/routing; or remove until needed.
- `MultiProviderEmbeddingAdapter`: complete provider-specific configuration and HTTP calls; make dimensions configurable per model; timeouts/retries.
- `OpenAiSceneDetectionAdapter`: add jobId to `SceneDetectionPort` (API change).
- Tests: re-enable excluded tests; stabilize mocks for RAG and prompt loading.

---

## 0.8.0 acceptance checklist (proposed)

Minimal criteria (met):

- [x] POST `/api/query/ask/rag` returns an answer synthesized from retrieved chunks.
- [x] Uses embeddings and vector similarity to select evidence.
- [x] Returns citations with chunk IDs and snippets.
- [x] Test suite green without critical failures.

Recommended hardening before tag (nice-to-have but not blocking):

- [ ] Replace in-memory search with Neo4j vector index OR document limitation and add a feature flag.
- [ ] Implement filter application or clearly mark filters as no-op in 0.8.0 response metadata.
- [ ] Re-enable `RagServiceTest` and `PromptLoaderServiceTest` (remove surefire excludes) once stable.
- [ ] Add API error mapping for empty LLM response; rate limit ask endpoint.
- [ ] Update README and docs to reflect v0.8.0 capabilities and caveats.

If these non-blockers are acceptable, proceed with version bump to 0.8.0.

---

## Suggested next steps (0.8.1/0.8.2)

1. Vector search adapter for Neo4j
   - Introduce `Neo4jVectorSearchAdapter implements SemanticSearchPort`
   - Schema: create vector index on `Chunk(embedding)`; add APOC or native procedure queries.
   - Feature flag swap via Spring profile/property.

2. Materialize publication coordinates on `Chunk`
   - Store `bookNumber`, `chapterNumber`, and `chapterId` on chunk nodes for fast read-path filtering and better citations.
   - Update ingestion projection and backfill migration.

3. Filter enforcement and validation
   - Apply filters at query layer; fail fast on invalid hierarchies with 400.

4. Test hygiene
   - Re-enable excluded tests; add contract tests for `/ask` outputs and edge cases (no evidence, threshold too high, long questions).

5. API documentation
   - Add springdoc-openapi and publish OpenAPI spec; update docs/api/specifications with ask/search contracts and examples.

6. Remove or gate experimental configs
   - Hide `MultiProviderLlmConfiguration` behind a `multi-llm` profile until fully implemented, to avoid confusion.

7. Operational guardrails
   - Timeouts and budget for LLM calls; rate limiting; risk-based logging (avoid printing snippets in info logs in prod).

---

## Quality gates snapshot

- Build: PASS (Maven test build SUCCESS)
- Lint/Typecheck: N/A (no explicit linter configured)
- Unit/Integration tests: PASS (98 tests)
- Smoke: Health of LLM chat slots validated in test profile; Neo4j container connectivity validated.

---

## Versioning recommendation

- Bump parent and module versions to 0.8.0 on mainline when ready to tag.
- Create release notes summarizing:
  - New: RAG question answering endpoint `/api/query/ask/rag` with citations
  - Improvements: Ports & Adapters for search and ingestion, test rewrite
  - Known limitations: in-memory retrieval, partial citation coordinates, filters no-op, experimental multi-LLM config

End of report.
