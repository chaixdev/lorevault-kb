# Developer Testing Workflow

**Status:** Active

## Default Loop

- run `mvn test` frequently
- keep the fast loop cheap
- prefer behavior-preserving changes backed by tests

## Philosophy

- keep the default loop fast
- use broader verification deliberately before meaningful structural changes land
- prefer tests that reflect real behavior over tests that preserve stale abstractions
- treat architecture tests as a deliberate guardrail, not part of the default loop while the system is still evolving

### When to test what

| Layer | Scope | Tooling | Purpose |
|---|---|---|---|
| **Business logic** | Public class methods, domain services, complex helpers | JUnit + AssertJ | TDD for logic with known I/O but unclear implementation. Not every method needs a test — test when a method has more than one caller, or when the logic is algorithmically non-trivial (even if short, e.g. regex validation). |
| **DB–app contract** | Repository layer, triggers, constraints, schema-enforced invariants | Testcontainers + raw SQL or repository calls | Assert that the database behaves as the application expects. DB triggers that recalculate fields, unique constraints that encode business rules, default values — these are business logic expressed in the schema, not infrastructure. |
| **Integration** | Module-to-module roundtrips, service wiring, serialization boundaries | Testcontainers, WireMock | Verify modules fit together. Loose coupling reduces visibility of compatibility issues — these tests catch that. Do **not** use this layer to verify business logic. |

### Test lifecycles and feedback loops

Different layers run at different cadencies. The point is to keep the inner loop fast.

| Layer | When it runs | Rationale |
|---|---|---|
| **Business logic + DB–app contract** | Every compile (IDE save / `mvn test`) | Inner loop. Must stay fast. Testcontainers is accepted here because it's explicitly scoped to app–db contract verification — the cost is bounded and the feedback is immediate. |
| **Integration** | Before submitting a PR (developer discipline), and enforced in CI pipeline before merge | Slower but still pre-merge. Loose coupling hides compatibility issues — these catch what the inner loop can't see. |

**Why Testcontainers in the unit test suite is acceptable:** the scope is strictly limited. An app–db contract test asserts repository and schema behaviour — not service wiring, not external dependencies. The startup cost of a containerized database is small enough that it doesn't break the inner-loop feedback contract. If it ever did (rare), the test belongs in the integration layer instead.

### How to write them

**Tests are executable documentation.** Name them after business scenarios, not implementation details:

```
✓ personReceivesRRnUpdate_eventRecalculatesRightsWindow     // good
✗ testHandleEvent_returnsOk                                  // bad
```

**Test through behaviour, not internals.** Assert outcomes via the public API (class method, service interface, repository contract). Avoid testing private methods directly.

**Spike during exploration.** When the domain is fuzzy and you don't know the I/O shape yet, write throwaway code. Invest in tests once the contract stabilizes.

### Mock, fake, or stub?

The question is "what am I trying to verify?"

| Strategy | When to use | Example |
|----------|-------------|---------|
| **Mock** | Need to verify a dependency was called correctly (interaction verification) | `@Mock LlmClient` — asserting the right prompt was sent |
| **Fake** | Need the dependency to actually work, but the real one is too expensive | `FakeNeo4jSemanticSearch` — in-memory vector math, no Neo4j |
| **Stub** | Need the dependency to exist but don't care what it does | `TestConfig` `@Bean` mocks for `ChatClient` — Spring context needs them, tests don't exercise them |

Rule of thumb: mock for interaction assertions, fake for working substitutes, stub for context plumbing.

### Regression tests

**Always update, never delete** — as long as the *contract* the test documents still exists. Update expected values when behaviour changes. Delete the test only when the contract itself disappears (e.g. a feature or integration is removed). Git preserves archaeology.

### Integration tests are not business logic tests

If a test is verifying a business rule (e.g. a rights window calculation, a state transition, a validation), it belongs in the **business logic** layer — even if that test spins up Testcontainers to exercise a repository. The distinction is *what you're asserting*, not *what tooling you use*.

### When not to test

- **Boilerplate getters/setters** — no behaviour to verify.
- **Simple CRUD delegation** — the integration test covers the roundtrip.
- **Exploratory spikes** — test only after the contract emerges.
- **Annotation metadata and record accessors** — testing that `@Retention(RUNTIME)` is present or that a record has a `jobId()` accessor is testing the compiler. Add a test only when you add runtime behaviour (validation, guards).

### LoreVault-specific notes

- **Test data:** chapter uploads for ingestion tests live in `lorevault-web/src/test/resources/sample-chapters/`. Use `SampleChapterLoader` to load them in tests.
- **Deterministic IDs:** use `TestIds` for fixed UUIDs — never `UUID.randomUUID()` in tests.
- **E2E tests:** not currently applicable to LoreVault. The layer exists in the guidance for completeness; adopt when team size and flow criticality justify it.

## Broader Checks

- `mvn verify -P integration-tests` before meaningful backend changes land
- `mvn test -P architecture-tests` when structural intent is being checked
- `mvn verify -P coverage-gate` when enforcing coverage thresholds
- `mvn test -P mutation-testing` for focused quality work, not everyday iteration

## Practical Rules

- use integration tests for real persistence and Spring wiring
- keep LLM-heavy logic mocked unless the test is explicitly about integration behavior
- prefer deterministic fixtures
- do not preserve stale abstractions just to satisfy old test structure

## Running Individual Tests

- single class: `mvn -Dtest=ClassNameTest test`
- single method: `mvn -Dtest=ClassNameTest#methodName test`
- add `-DtrimStackTrace=true` when you want shorter failure output

## IDE Index Hygiene

When files are modified by agents (or any external process), the IntelliJ IDE index goes stale. This causes false-positive diagnostics from `ide_diagnostics` — missing Lombok-generated members (`log` from `@Slf4j`, getters from `@Data`), unresolved record accessors, etc.

**Rule:** After agent-driven file changes, call `ide_sync_files` for the changed paths before trusting `ide_diagnostics`. For large refactors, sync the entire project.

**Maven compile is the source of truth.** If `mvn compile` passes but the IDE reports errors, the IDE index is stale — sync and re-check.

### Java LSP disabled

The built-in Java LSP (Eclipse JDT-LS via `jdtls-lombok.sh`) is disabled in `~/.config/opencode/opencode.json`. It produced false-positive diagnostics for Lombok-annotated classes (`@Slf4j`, `@Data`, `@Builder`) and records because its transient workspace lacks the full Maven project model. We rely on IntelliJ's `ide_diagnostics` (which has the real project model) and `mvn compile` instead.

## Reports

- JaCoCo: `lorevault-web/target/site/jacoco/index.html`
- PIT: `lorevault-web/target/pit-reports/index.html`
- Surefire reports: `lorevault-web/target/surefire-reports/`
- Failsafe reports: `lorevault-web/target/failsafe-reports/`

## Running The App For Manual Testing

Preferred local run command:

```bash
./scripts/dev-api.sh run
```

Background workflow:

```bash
./scripts/dev-api.sh start
./scripts/dev-api.sh logs
```

If you are not using the helper script, make sure the environment variables referenced by `lorevault-web/src/main/resources/application.yml` are available.
