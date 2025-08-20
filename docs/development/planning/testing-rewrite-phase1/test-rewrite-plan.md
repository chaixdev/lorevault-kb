# Test Rewrite Plan (Phase 1)

Status: Draft
Owner: Testing Guild
Last updated: 2025-08-20

Objective: Replace legacy tests with a clean, scalable suite following the strategy docs. Maintain behavior parity via explicit mapping.

## Scope (Phase 1)
- Module: lorevault-api
- Focus areas: Domain + Service tests, Adapter TCK scaffolding, container base, deterministic testutil

## Deliverables
- Test scaffolding:
  - testutil: `FixedClock`, `DeterministicIdGenerator`, builders for key entities, `GraphFixtureLoader`
  - Fakes: `InMemorySemanticSearchPort`, `FakeEmbeddingProvider`, `FakeChatClient`
  - Base container test: `BaseNeo4jContainerIT` (reused, singleton), Testcontainers reuse
- Maven config updates: surefire/failsafe tags, JaCoCo min thresholds, PIT targets
- Tags and taxonomy applied consistently

## Parity mapping (initial)
- Domain content: Chapter/Book/Series/Universe → unit tests with builders and invariants
- Scene detection: parsing/xml/localization/retry → service tests with fakes; slice tests where needed
- Search ranking/order → service tests; one integration confirms ordering end-to-end
- Embeddings: chunk embedding + vector math → service/unit with deterministic embeds
- Neo4j persistence adapter → adapter TCK + minimal IT for schema/constraints
- Web AskController → slice test with mocked services
- RAG service → service tests with `FakeChatClient`; prompt assembly assertions

## Milestones
1) Scaffolding landed; unit/service tests for domain + core services
2) Adapter TCKs and fakes; migrate adapter tests
3) Minimal integrations and slices; remove legacy overlap
4) Enforce gates; delete or quarantine remaining legacy tests

## Risks
- Hidden coupling in legacy tests → Address via better seams in services and ports
- Performance regs in CI → enable container reuse, parallelize unit scope, focus failsafe for ITs

## Exit criteria (Phase 1)
- Parity for above areas confirmed; legacy tests for those areas removed
- CI green with new gates and tags
