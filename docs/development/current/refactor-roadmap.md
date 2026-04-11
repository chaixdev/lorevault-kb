# Refactor Roadmap

Date: April 2026
Status: Structural roadmap completed; retained as continuity document for post-modulith feature direction
Relationship: Promoted from refactor follow-up analysis; historical companion remains `../../archive/refactor/PRAGMATIC_MODULITH_PLAN.md`

## Context

This roadmap was promoted into the canonical current docs after the April 2026 codebase and documentation audit. It is the main refactor continuity document for future work.

## Untangling Historical "Phase" Numbering

Three separate plans reused the word "phase" with their own numbering. This section maps them to avoid confusion.

**Plan A — Service Consolidation (Aug–Sep 2025).** Tickets LVREF001-019, 5 phases. Goal: reduce 15+ micro-services to 4-6. All done, all merged.

| Phase | What | When | Status |
|---|---|---|---|
| A1 | Eliminate utility services (LVREF001-003) | Aug 25 | Done |
| A2 | Consolidate ingestion services (LVREF004-007) | Aug 26 | Done |
| A3 | Consolidate content processing (LVREF008-012) | Aug 26 – Sep 8 | Done |
| A4 | Consolidate system services (LVREF013-014) | Sep 8 | Done |
| A5 | Final cleanup & testing (LVREF015-019) | Sep 8 | Done |

**Plan B — Event-Driven + Port Cleanup (Dec 26-27, 2025).** Documented in `REFACTOR-SESSION-LOG.md`. 4 phases in 1 day. All done, all merged.

| Phase | What | When | Status |
|---|---|---|---|
| B1 | Event-driven ingestion pipeline | Dec 26 | Done |
| B2 | Remove 5 port/adapter pairs | Dec 26 | Done |
| B3 | Handler consolidation (5→3) | Dec 26 | Done |
| B4 | Test cleanup | Dec 26 | Done |

**Plan C — Pragmatic Modulith (Dec 27-28, 2025).** Documented in `PRAGMATIC_MODULITH_PLAN.md`. Goal: SDN-annotate entities, kill God Port, eliminate mappers.

| Phase | What | When | Status |
|---|---|---|---|
| C1 | Ingestion entities → @Node | Dec 28 | Done |
| C2 | Content entities → @Node | Apr 2026 | Done |
| C3 | Port cleanup, Spring AI modernization, package flatten | Apr 2026 | Done |

The branch `refactor/phase5-pragmatic-modulith` is named "phase 5" because it followed Plan B's 4 phases sequentially. Its commit message says "Phase 1 of Pragmatic Modulith plan" because within Plan C it IS phase 1. Both refer to the same work: C1.

**Going forward**, all phase references use the **M-prefix** (Modulith): M1, M2, M3, etc. Plans A and B are complete history, and M1-M4 are now complete as well.

## Current Audit Findings

Original April audit baseline before M2-M4 completion:
- 153 production Java files, 78 test files, ~60 packages
- 263 passing tests
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.0, Neo4j 5.26
- Core pipeline functional: Ingestion → Scene Detection → Chunking → Embedding → Semantic Search/RAG

Those findings motivated the completed structural work:
- `ContentPersistencePort` deleted in M3
- `Neo4jMapper` deleted in M2
- `Neo4jContentPersistenceAdapter` deleted in M3
- Package layout flattened in M4 to 10 top-level feature packages
- Scene processing remains a large service, but the structural blockers called out here are no longer present

Verdict from that audit was correct: the codebase was salvageable through consolidation, and the consolidation work has since landed.

## Current Post-M4 State

Current implemented state:
- 138 production Java files, 57 test Java files, 10 top-level feature packages
- 263 passing tests
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.1.4, Neo4j 5.26
- Core pipeline functional: Ingestion → Scene Detection → Chunking → Embedding → Semantic Search/RAG
- Scene detection now includes context-budget checks and deterministic segmented fallback
- Spoiler-aware search is shipped with per-series visibility windows
- Individual mentions are persisted as groundwork for later entity resolution

## Architectural Reframe

The original plan retained 4 port abstractions (EmbeddingPort, SemanticSearchPort, LlmPort, PromptRepositoryPort). The updated direction eliminates all ports based on these rationales:
- Spring AI provides the provider abstraction layer. A port on top of Spring AI is redundant indirection.
- SemanticSearchPort: LoreVault uses domain-specific Cypher with dual relationship patterns (HAS_SCENE→HAS_CHUNK). A generic port cannot express these requirements; the implementation is the interface.
- PromptRepositoryPort: Replace with a single Spring bean using @Value or @ConfigurationProperties.
- Twikey-informed values: Code is liability, minimize dependencies, prioritize linear logic over indirection, treat the database as truth.

## Target Shape

A high-IQ reasoning model (Oracle) synthesized the audit findings and Twikey philosophy into a consolidated vision.

Target structure discussed during the audit was 12 feature-oriented packages. The implemented result converged to 10 top-level feature packages:
```
com.lorevault.api/
├── config/       # Spring @Configuration beans
├── content/      # Universe, Series, Book, Chapter, Scene, Chunk — all @Node
├── library/      # Library management service + repository
├── ingestion/    # IngestionJob, handlers, services
├── timeline/     # Temporal edges, event ordering
├── ai/           # SceneDetectionService, LLM clients, embedding
├── search/       # Semantic search, RAG
├── health/       # Health checks, diagnostics
├── web/          # REST controllers
├── web.command/  # CQRS command controllers
├── web.query/    # CQRS query controllers
└── support/      # Cross-cutting utilities
```

Structural moves:
- Apply @Node annotations directly to domain entities (SDN-annotated model).
- Repositories return domain types directly.
- Services inject repositories directly.
- Delete ContentPersistencePort, all adapters, and all mappers.
- The exact file-count target was not pursued literally; the meaningful outcome was removal of redundant layers and consolidation into feature packages.

## Storage Decision

Evaluation of splitting vector duties to pgvector concluded: **Stay on Neo4j for both graph and vectors.**
At current scale (tens of thousands of embeddings), Neo4j HNSW index is sufficient. Adding PostgreSQL introduces operational complexity, a second connection pool, and consistency concerns without measurable benefit.

Revisit trigger: Corpus exceeds hundreds of thousands of embeddings or Neo4j vector search becomes difficult to tune.

## Spring AI Decision — Keep and Upgrade

Initial recommendation to drop Spring AI was overturned by research in `docs/development/research/spring-ai-keep-vs-drop-analysis.md`.
Spring AI 1.1.4 (from 1.0.0) introduces 850+ improvements.

| Feature | What it replaces in LoreVault |
|---|---|
| `Neo4jVectorStore` + `TokenCountBatchingStrategy` | `EmbeddingModelAdapter` (148 LOC raw RestTemplate) |
| `Neo4jChatMemoryRepository` | New capability (not currently implemented) |
| `RetrievalAugmentationAdvisor` | Parts of `RagService` (309 LOC) — deferred |
| `entity(MyRecord.class)` structured output | `TriadXmlParser` (96 LOC) + XML infra |
| Micrometer `gen_ai.client.token.usage` | `estimateTokens()` heuristic (4 chars/token) |
| `CallAdvisor`/`StreamAdvisor` chain | Manual retry/logging wiring |

Migration effort: 2-4 hours for BOM bump.

## Structured Output Direction

The original choice of XML to avoid JSON encoding issues with narrative dialogue is now obsolete.
- Native JSON Schema mode (grammar-constrained decoding) guarantees valid JSON.
- OpenRouter supports JSON Schema mode via `response_format`.
- Spring AI 1.1 generates JSON Schema automatically via `.entity(Record.class)`.

Migration: Define Java records, use `.entity()`, and delete `TriadXmlParser` and XML-specific prompt instructions. Prerequisite: Verify provider support for JSON Schema mode.

## Provider Direction

- Current: Groq (chat), Gemini (embeddings via OpenAI-compatible API).
- Future: OpenRouter or Nebius, targeting Gemma 4 models.
- Integration: All via OpenAI-compatible API; Spring AI handles transparency.

## Immediate Next Work

The previously listed first item has now shipped:

1. **Scene detection context budget + deterministic segmentation fallback** — complete in `21e2369`
   - Added `maxContextTokens` handling and conservative admission checks
   - Added deterministic segmentation fallback for oversized chapters
   - Tagged segment-boundary risk for later reconciliation
   - See `../../brainstorm/scene-detection-context-budget-and-segmentation-spec-april-2026.md`

Current priority order:

1. **Individual resolution on top of persisted mentions**
   - Build `IndividualMention -> ChapterIndividual -> BookIndividual` incrementally
   - Start with chapter-local exact linking for real retrieval value

2. **Broader entity extraction (Locations, Collectives, later claims)**
   - Extend the extraction foundation beyond individuals
   - Preserve the current simple architecture: extract first, map later

3. **Timeline modeling and production hardening**
   - Revisit scene-as-event enrichment once entity-linked retrieval is more valuable
   - Continue operational hardening where needed

## M1-M4 Completion Baseline

Key milestones now landed on `main`:

- `cfb7404` — `refactor(ingestion): complete Phase 1 of Pragmatic Modulith plan`
- `1b41679` — docs updated to reflect M2/M3 completion
- `dc7e3b6` — `refactor(m4): flatten packages from 44 layered to 12 feature packages`
- `76d25df` — spoiler-aware search shipped
- `21e2369` — scene budget checks and deterministic segmented processing shipped
- `05e2752` — individual mention metadata persistence improved

The two accompanying docs commits that describe guardrails/rules are:

| SHA | Description |
|---|---|
| `d541d21` | docs: phase 5 pragmatic modulith guardrails |
| `c75cdae` | docs: clarify UUIDv7 + ordering policy |
| `cfb7404` | refactor(ingestion): complete M1 (ingestion entities → @Node) |

Implementation commit `cfb7404` (26 files, -813/+419 lines):
- Migrated IngestionJob, StatusRecord, LlmCallRecord to SDN @Node entities.
- Deleted node-specific mirror classes (IngestionJobNode, etc.).
- Removed ingestion methods from ContentPersistencePort (22 lines) and Adapter (116 lines).
- Reduced Neo4jMapper complexity (ingestion mapping removed).
- Repositories return domain types; services use repositories directly.
- All tests updated and passing.

These commits establish the structural program as complete and show the transition into feature work on ingestion quality, retrieval correctness, and entity groundwork.

## Comparison — Original Plan vs Current Proposal

| Dimension | Original Plan (Dec 2025) | Oracle Proposal (Apr 2026) |
|---|---|---|
| Kill God Port | Yes | Yes |
| SDN-annotate entities | Yes | Yes |
| Direct repo injection | Yes | Yes |
| Delete mappers | Yes | Yes |
| Retained ports | 4 (Embedding, Search, LLM, Prompt) | 0 — kill all |
| Spring AI | Keep | Keep + Upgrade (1.1.4) |
| Package layout | Layered (app/domain/infra) | Feature-oriented layout; implemented as 10 top-level packages |
| File count target | Not specified | ~55 files |
| Max call depth | Not specified | 3 hops |

## Open Decisions

Resolved:

1. **M2 start boundary** — Content entities → @Node first (same pattern as M1). Port/adapter elimination follows in M3. Spring AI upgrade deferred to M4.
2. **Package layout** — Flatten to a small feature-oriented package set in M4, after all ports/adapters are gone. Implemented result: 10 top-level packages.
3. **Migration sequence** — M2 (content @Node) → M3 (kill all ports) → M4 (Spring AI upgrade + package flatten).
4. **Provider stabilization** — XML→JSON structured output in M4.3; prerequisite is confirming provider JSON Schema support before coding.

Remaining open items from the structural program are largely historical. The roadmap-level open question is no longer how to finish M2-M4, but which feature tranche should follow them.

---
## Related Documents

- `m2-m4-implementation-plan.md` — full slice-by-slice implementation plan for M2 through M4
- `../../archive/refactor/PRAGMATIC_MODULITH_PLAN.md` — original plan (historical)
- `../../archive/refactor/REFACTOR-SESSION-LOG.md` — phases 1-4 execution log (historical)
- `../../archive/refactor/ARCHITECTURAL_BLOAT_ANALYSIS.md` — original bloat diagnosis (historical but still useful)
- `../research/spring-ai-keep-vs-drop-analysis.md` — Spring AI analysis
