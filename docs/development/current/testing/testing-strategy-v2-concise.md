# Testing Strategy

**Purpose:** Describe the current testing shape of LoreVault after the architecture simplification work.  
**Status:** Active, updated during the documentation refactor.

## Core Principles

- Prefer behavior-focused tests over abstraction-driven test structure
- Keep the fast loop small and cheap
- Use integration tests for real persistence and Spring wiring
- Run architecture tests deliberately while the codebase is still being simplified
- Avoid creating indirection just to make tests fit a pattern

## Current Test Shape

LoreVault currently mixes:

- domain tests
- service tests
- persistence and integration tests
- targeted infrastructure tests
- on-demand architecture tests

The suite is no longer accurately described as a pure ports-and-adapters testing model. The codebase still contains some historical artifacts from that phase, but the active direction is simpler: test business behavior directly, then validate real wiring where it matters.

## Test Categories

- `@Tag("integration")` — real infrastructure or Spring wiring
- `@Tag("architecture")` — ArchUnit tests, excluded by default
- untagged/default tests — the normal fast loop

There is evidence of broader tag use in the test tree, but the Maven defaults mainly distinguish integration and architecture concerns.

## Commands That Match the Repo

### Fast loop

```bash
mvn test
```

Runs the default Surefire suite.

Default behavior:
- excludes file-pattern integration tests like `*IT.java` and `*IntegrationTest.java`
- excludes JUnit groups `integration,architecture`
- generates JaCoCo reports
- does **not** enforce the JaCoCo gate by default

### Integration verification

```bash
mvn verify -P integration-tests
```

Uses:
- Surefire with `groups=integration`
- Failsafe for `*IT.java`, `*IntegrationTest.java`, etc.

Use this before pushing meaningful backend changes.

### Architecture tests

```bash
mvn test -P architecture-tests
```

Runs only the architecture tests under the dedicated architecture test path.

These are not part of the default fast loop because the codebase is still in transition.

### Coverage gate

```bash
mvn verify -P coverage-gate
```

JaCoCo is always reported, but the strict gate is opt-in.

Configured thresholds:
- bundle: 85% instruction, 80% branch
- packages `service`, `domain`, `application`: 90% instruction

### Mutation testing

```bash
mvn test -P mutation-testing
```

Use this for focused quality work, not for the normal iteration loop.

## Tooling Notes

- Testcontainers reuse is enabled in `src/test/resources/testcontainers.properties`
- JUnit 5 class-level parallelism is enabled in `junit-platform.properties`
- Surefire runs with `forkCount=1C` and `reuseForks=true`

## Current Guidance

### When writing new tests

- test the business behavior first
- use integration tests for Neo4j persistence, Spring wiring, and event publication flows
- keep LLM-dependent logic mocked unless the purpose is explicit integration verification
- prefer deterministic fixtures over random data
- keep assertions aligned with user-visible or domain-visible behavior

### When refactoring

- preserve behavior first
- update architecture tests only when the structural intent is stable
- delete stale test abstractions when they no longer buy clarity

## Known Transitional Reality

Some current docs and some test packages still reflect older hexagonal/port-heavy assumptions. Treat those as historical residue, not the target model.

The target model is:

- simpler code
- direct tests for direct code
- integration tests where the system boundary is real

This strategy emphasizes behavior-first verification, a cheap default feedback loop, and deliberate use of integration coverage where the boundary is real.
