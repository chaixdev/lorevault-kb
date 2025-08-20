# LoreVault Testing Rewrite Roadmap (v2)

Status: Draft — rewrite-first
Owner: Core Engineering (Testing Guild), with support from Platform & Domain Leads
Last updated: 2025-08-19

## Why this change

We’ve grown fast with ports & adapters discipline, but our test codebase has accumulated setup complexity, redundancy, and ad hoc patterns. Rather than iteratively patching existing tests, we will perform a clean rewrite of the test suite using the new conventions and scaffolding. This rewrite will be parity-driven (same behaviors covered), faster, and more maintainable.

We align to the formal strategy captured in:
- `docs/development/testing-strategy-v2-concise.md`
- `docs/development/research/Testing Patterns for Growing Codebases.md`

This roadmap operationalizes a rewrite-first approach into phased, verifiable changes with clear acceptance criteria.

## Goals and Non-Goals

Goals
- Replace existing tests with a clean test suite built from first principles and shared scaffolding.
- Establish Port TCKs, centralized builders/fixtures, and slice-based integrations.
- Introduce and enforce quality gates (coverage, mutation, architecture rules).
- Make test execution predictable and fast on dev machines and CI.

Non-Goals
- E2E/UI test strategy (explicitly out of scope per strategy docs).
- Broad rewrites of production code (testability changes only if needed). Main code refactors are follow-ups after the new test gates are in place.

## Target State (summary)
- Domain-first: majority of tests are unit/service with mocked or fake ports.
- Port TCKs exist for every outward-facing port; all adapters must pass them.
- Fake-first: fast in-memory fakes for iterative development and service tests.
- Integration tests limited to key workflows, use Spring test slices + Testcontainers (reused).
- Deterministic test data via centralized builders, fixed Clock/ID generators.
- LLM/GraphRAG: LLM calls mocked; prompt assembly verifiable; graph fixtures preloaded.
- Architecture boundaries enforced by ArchUnit; mutation testing via PIT; coverage via JaCoCo.
- Consistent taxonomy and structure: tags (@Tag), naming, packages.

## Approach (rewrite-first)
- Freeze legacy tests (no further edits except quarantine). Keep them runnable for comparison if needed.
- Build new scaffolding under `src/test/java/.../testutil` and slices: deterministic Clock/IDs, builders, fakes, and base container class with reuse.
- Recreate tests by capability, guided by a parity inventory. Prefer unit/service tests; add adapter TCKs; add minimal, high-value integrations.
- For each legacy area, mark parity achieved when equivalent coverage and assertions exist in the new suite; then delete or permanently quarantine the legacy tests.

Tracking docs:
- Planning inventory: `docs/development/planning/testing-rewrite-phase1/test-rewrite-inventory.md`
- Rewrite plan: `docs/development/planning/testing-rewrite-phase1/test-rewrite-plan.md`

## Success Criteria (measurable)
- Unit tests ≥ 75% of suite; integration ≤ 25% (excl. system tests).
- Line/branch coverage ≥ 85% on unit scope; PIT mutation score ≥ 80% on critical packages.
- 0 ArchUnit violations on main branch.
- All adapters pass their associated Port TCKs in CI.
 - Legacy tests removed or quarantined with documented parity; CI runs only the new suite by default.

## Risks and Mitigations
- Container startup time: enable reuse, minimize started services, share base classes.
- Flakiness from time/IDs: enforce fixed `Clock` and deterministic IDs; ban `new Date()` in tests.
- Over-mocking: prefer fakes; keep interactions assertions minimal and business-focused.
- Test ordering/dependencies: isolate data; reset state per test; avoid shared mutable globals.
- Mutation test slowness: target critical packages first; run full mutation tests on nightly.
 - Parity drift: maintain an explicit inventory mapping old tests to new coverage; block removal without mapping.

## Reference Conventions (quick)
- Tags: `@Tag("unit")`, `@Tag("integration")`, `@Tag("system")`, optional `@Tag("performance")`.
- Naming: `ServiceNameTest`, `ComponentNameIT`, `PortNameTCK`, `AdapterNameTCKTest`.
- Packages under `src/test/java`:
  - `service/`, `controller/`, `adapter/`, `tck/`, `domain/`, `testutil/`.
- Determinism: `Clock.fixed(...)`, `UUID.nameUUIDFromBytes(...)` or seeded generators.
- LLM: mock/fake provider port; verify prompt assembly with captors; test retrieval context quality.

## Acceptance Gate (final)
The rewrite is complete when:
- Structure and tags match conventions; search finds 0 violations.
- Gates enforced in CI and passing: coverage, mutation, ArchUnit.
- Ports covered by TCKs; adapters pass TCKs.
- Legacy tests removed or quarantined with a one-to-one parity mapping.
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
