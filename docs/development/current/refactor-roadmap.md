# Refactor Roadmap

Date: April 2026
Status: Active roadmap
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
| C1 | Ingestion entities → @Node | Dec 28 | Done, merged locally on `main` |
| C2 | Content entities → @Node | — | Not started |
| C3 | Search & AI port cleanup | — | Not started |

The branch `refactor/phase5-pragmatic-modulith` is named "phase 5" because it followed Plan B's 4 phases sequentially. Its commit message says "Phase 1 of Pragmatic Modulith plan" because within Plan C it IS phase 1. Both refer to the same work: C1.

**Going forward**, all phase references use the **M-prefix** (Modulith): M1, M2, M3, etc. Plans A and B are complete history.

## Current Audit Findings

Current state of the codebase:
- 153 production Java files, 78 test files, ~60 packages
- 263 passing tests
- Stack: Java 21, Spring Boot 3.5.4, Spring AI 1.0, Neo4j 5.26
- Core pipeline functional: Ingestion → Scene Detection → Chunking → Embedding → Semantic Search/RAG

Remaining structural issues:
- ContentPersistencePort: 40+ methods, remains a God Port
- Neo4jMapper: 372 LOC, redundant domain/node mapping for content entities
- Neo4jContentPersistenceAdapter: 590 LOC monolith
- SceneProcessingService: 694 LOC god class
- Package bloat: ~60 packages where ~12 are sufficient

Verdict: Domain remains viable, codebase is salvageable through consolidation.

## Architectural Reframe

The original plan retained 4 port abstractions (EmbeddingPort, SemanticSearchPort, LlmPort, PromptRepositoryPort). The updated direction eliminates all ports based on these rationales:
- Spring AI provides the provider abstraction layer. A port on top of Spring AI is redundant indirection.
- SemanticSearchPort: LoreVault uses domain-specific Cypher with dual relationship patterns (HAS_SCENE→HAS_CHUNK). A generic port cannot express these requirements; the implementation is the interface.
- PromptRepositoryPort: Replace with a single Spring bean using @Value or @ConfigurationProperties.
- Twikey-informed values: Code is liability, minimize dependencies, prioritize linear logic over indirection, treat the database as truth.

## Target Shape

A high-IQ reasoning model (Oracle) synthesized the audit findings and Twikey philosophy into a consolidated vision.

Target structure: 12 feature-oriented packages:
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
- Target: ~55 total files, 3-hop maximum call depth.

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

## M1 Baseline (Merged Locally)

The branch `origin/refactor/phase5-pragmatic-modulith` has been merged locally into `main` at:

- `cfb7404` — `refactor(ingestion): complete Phase 1 of Pragmatic Modulith plan`

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

This establishes M1 as the current local planning baseline. Remote push and rollout sequencing are tracked separately.

## Comparison — Original Plan vs Current Proposal

| Dimension | Original Plan (Dec 2025) | Oracle Proposal (Apr 2026) |
|---|---|---|
| Kill God Port | Yes | Yes |
| SDN-annotate entities | Yes | Yes |
| Direct repo injection | Yes | Yes |
| Delete mappers | Yes | Yes |
| Retained ports | 4 (Embedding, Search, LLM, Prompt) | 0 — kill all |
| Spring AI | Keep | Keep + Upgrade (1.1.4) |
| Package layout | Layered (app/domain/infra) | Feature-oriented (12 flat packages) |
| File count target | Not specified | ~55 files |
| Max call depth | Not specified | 3 hops |

## Open Decisions

1. **M2 start boundary?** Define the first M2 slice now that M1 is merged locally (content entities, remaining mapper reduction, and service cleanup).
2. **Package layout implementation?** Transition from layered to 12 flat feature packages. Determine if a hybrid approach (flat features within a single root) is preferable.
3. **Migration sequence?** Determine if the Spring AI upgrade should precede M2 (content entities) migration.
4. **Provider stabilization?** XML→JSON migration depends on final provider choice. Confirm Gemma 4 availability before committing to structured output changes.

---
## Related Documents

- `../../archive/refactor/PRAGMATIC_MODULITH_PLAN.md` — original plan (historical)
- `../../archive/refactor/REFACTOR-SESSION-LOG.md` — phases 1-4 execution log (historical)
- `../../archive/refactor/ARCHITECTURAL_BLOAT_ANALYSIS.md` — original bloat diagnosis (historical but still useful)
- `../research/spring-ai-keep-vs-drop-analysis.md` — Spring AI analysis
