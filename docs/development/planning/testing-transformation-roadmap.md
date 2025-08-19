# LoreVault Testing Transformation Roadmap (v2)

Status: Draft
Owner: Core Engineering (Testing Guild), with support from Platform & Domain Leads
Last updated: 2025-08-19

## Why this change

We’ve grown fast with ports & adapters discipline, but our test codebase has accumulated setup complexity, redundancy, and ad hoc patterns. We now pivot to the formal strategy captured in:
- `docs/development/testing-strategy-v2-concise.md`
- `docs/development/research/Testing Patterns for Growing Codebases.md`

This roadmap operationalizes that strategy into phased, verifiable changes with clear acceptance criteria.

## Goals and Non-Goals

Goals
- Reorganize and refactor tests to be scalable, fast, and consistent with ports & adapters.
- Establish Port TCKs, centralized builders/fixtures, and slice-based integrations.
- Introduce and enforce quality gates (coverage, mutation, architecture rules).
- Make test execution predictable and fast on dev machines and CI.

Non-Goals
- E2E/UI test strategy (explicitly out of scope per strategy docs).
- Large functional rewrites of production code (testability changes only if needed).

## Target State (summary)
- Domain-first: majority of tests are unit/service with mocked or fake ports.
- Port TCKs exist for every outward-facing port; all adapters must pass them.
- Fake-first: fast in-memory fakes for iterative development and service tests.
- Integration tests limited to key workflows, use Spring test slices + Testcontainers (reused).
- Deterministic test data via centralized builders, fixed Clock/ID generators.
- LLM/GraphRAG: LLM calls mocked; prompt assembly verifiable; graph fixtures preloaded.
- Architecture boundaries enforced by ArchUnit; mutation testing via PIT; coverage via JaCoCo.
- Consistent taxonomy and structure: tags (@Tag), naming, packages.

## Success Criteria (measurable)
- Unit tests ≥ 75% of suite; integration ≤ 25% (excl. system tests).
- Line/branch coverage ≥ 85% on unit scope; PIT mutation score ≥ 80% on critical packages.
- 0 ArchUnit violations on main branch.
- All adapters pass their associated Port TCKs in CI.

## Phased Plan

### Phase 0 — Audit & Baseline (1–2 days)
Deliverables
- Inventory of all tests: category, runtime, Spring context usage, external deps.
- Baseline metrics: total runtime (local/CI), coverage, mutation score (initial quick run), flakiness list.
- Mapping of current tests → target categories (unit, integration, system) and destinations.

Acceptance
- Spreadsheet (or markdown table) with inventory + metrics committed under `docs/development/testing/`.
- Agreed shortlist of high-pain tests to refactor first.

### Phase 1 — Test Infrastructure & Tooling (1–2 days)
Actions
- Maven plugins & config:
  - surefire with JUnit 5 groups/tags; failsafe for IT naming (`*IT.java`).
  - JaCoCo report & minimum thresholds for unit scope.
  - PIT (pitest-maven-plugin) with mutation thresholds for targeted packages.
  - Enable Testcontainers reuse (`~/.testcontainers.properties` or project-level opt-in) and logging.
- Establish `src/test/java/.../testutil` package (or a dedicated `test-utils` module if multi-module grows):
  - Fixed `Clock`, deterministic `IdGenerator`, `TestDataBuilder` base, `SampleChapterLoader` utilities.
  - `IntegrationTestBase` with reusable Testcontainers (Postgres/pgvector, Neo4j if present) and Spring test slices wiring.
  - `PromptCaptor`/`ArgumentCaptor` utilities for LLM prompt verification.
- Create `tck/` package for port contracts (abstract test classes and fixtures).

Acceptance
- Build passes with new plugins; `mvn test -Dgroups=unit` and `mvn verify -Dgroups="unit,integration"` both succeed.
- Basic PIT run succeeds on a small package set.

### Phase 2 — Organization, Tags, and Conventions (2–3 days)
Actions
- Restructure test packages per `testing-strategy-v2-concise.md`:
  - `service/` (unit), `adapter/` (TCK impls), `controller/` (integration), `domain/`, `tck/`, `testutil/`.
- Apply annotations: `@Tag("unit"|"integration"|"system")`, `@DisplayName`, `@Nested`.
- Rename integration tests to `*IT.java`; ensure they don’t live under `unit` packages.

Acceptance
- No unit test starts Spring context; integration tests do (or use slices).
- All tests tagged; search shows 0 untagged JUnit classes.

### Phase 3 — Port TCKs Rollout (3–5 days)
Actions
- Identify top-priority ports (repository ports, LLM provider port, external clients).
- Write contract tests as abstract classes under `tck/` with shared fixtures and scenarios.
- Implement TCK subclasses for each adapter (in-memory fake, JPA/pgvector, Neo4j, HTTP client, etc.).
- Run TCKs in CI as part of `verify`.

Acceptance
- Each port has at least one TCK; each adapter has a passing subclass.
- Duplicate adapter tests reduced by ≥50% (measured by lines or count).

### Phase 4 — Service/Domain Unitization (3–5 days)
Actions
- Convert service tests using `@SpringBootTest` to pure JUnit 5 + Mockito (or fakes) where feasible.
- Replace mocks with fakes where beneficial (in-memory repositories, test doubles of ports).
- Introduce property-based tests (jqwik) for critical invariants in value objects/domain.

Acceptance
- Measurable drop in test runtime for service layer by ≥40%.
- Property-based tests exist for at least 3 critical domain invariants.

### Phase 5 — Integration Tests Rationalization (3–4 days)
Actions
- Move DB-focused tests to slices (`@DataJpaTest`) with Testcontainers.
- Consolidate container config via `IntegrationTestBase` and enable reuse.
- Create small scenario fixtures for end-to-end controller flows (happy path + 1–2 edge cases).
- Remove redundant integration tests covered by TCKs + service tests.

Acceptance
- Controller and repository integrations run fast and deterministically (≤ N seconds/class; define N from baseline –30%).
- Flaky tests list reduced to zero or quarantined.

### Phase 6 — Architecture, Mutation, and Coverage Gates (2–3 days)
Actions
- Add ArchUnit rules enforcing hexagonal boundaries (domain independent of infra; port visibility, package rules, naming conventions).
- Turn on JaCoCo thresholds for unit scope; PIT thresholds for key packages.
- Wire CI to run: unit on every push; integration and PIT on PR; system on nightly or protected branches.

Acceptance
- CI enforces: coverage ≥85% (unit scope), PIT ≥80% (targeted), 0 ArchUnit violations.

### Phase 7 — LLM/GraphRAG Test Enhancements (2–3 days)
Actions
- Standardize LLM port mocks/fakes with deterministic responses and prompt captors.
- Provide preloaded graph fixtures for retrieval logic tests; verify retrieved context quality (entities/relations) instead of LLM output.
- Add `@Timeout` to LLM-involved flows and test retry/circuit-breaker behavior.

Acceptance
- LLM-related tests deterministic, fast, and focused on retrieval/context metrics; timeouts and resilience covered.

### Phase 8 — Cleanup, and Guardrails (1–2 days)
Actions
- Remove deprecated helpers and duplicate tests.
- Add PR checklist and pre-commit hooks for tags, naming, and package placement.

Acceptance
- Repo free of legacy test scaffolding; reviewers use the PR checklist; green CI with all gates enabled.

## Risks and Mitigations
- Container startup time: enable reuse, minimize started services, share base classes.
- Flakiness from time/IDs: enforce fixed `Clock` and deterministic IDs; ban `new Date()` in tests.
- Over-mocking: prefer fakes; keep interactions assertions minimal and business-focused.
- Test ordering/dependencies: isolate data; reset state per test; avoid shared mutable globals.
- Mutation test slowness: target critical packages first; run full mutation tests on nightly.

## Reference Conventions (quick)
- Tags: `@Tag("unit")`, `@Tag("integration")`, `@Tag("system")`, optional `@Tag("performance")`.
- Naming: `ServiceNameTest`, `ComponentNameIT`, `PortNameTCK`, `AdapterNameTCKTest`.
- Packages under `src/test/java`:
  - `service/`, `controller/`, `adapter/`, `tck/`, `domain/`, `testutil/`.
- Determinism: `Clock.fixed(...)`, `UUID.nameUUIDFromBytes(...)` or seeded generators.
- LLM: mock/fake provider port; verify prompt assembly with captors; test retrieval context quality.

## Acceptance Gate (final)
The transformation is complete when:
- Structure and tags match conventions; search finds 0 violations.
- Gates enforced in CI and passing: coverage, mutation, ArchUnit.
- Ports covered by TCKs; adapters pass TCKs.
- Runtime and flake KPIs improved vs baseline as defined above.

---

Appendix A — Suggested Maven Additions (illustrative)

Note: examples only; adapt to `pom.xml` structure and modules.

- Surefire & Failsafe (JUnit 5, tags/groups)
- JaCoCo (coverage report + threshold on unit scope)
- PIT (targeted packages, thresholds)
- Testcontainers reuse property in CI (env/properties)

Appendix B — PR Checklist (tests)
- [ ] Correct `@Tag` used (unit/integration/system)
- [ ] Uses `@DisplayName` and `@Nested` for groups
- [ ] Deterministic builders; fixed `Clock`/IDs; no randoms
- [ ] Service tests don’t load Spring
- [ ] New/changed adapters covered by Port TCKs
- [ ] LLM interactions mocked; prompt assertions when relevant
- [ ] Integration uses slices or base container class
- [ ] No ArchUnit violations; coverage/mutation unaffected or improved
