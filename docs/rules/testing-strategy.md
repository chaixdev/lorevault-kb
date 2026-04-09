# Testing Strategy Pattern

**Status:** Established

LoreVault relies on a mixed testing approach:

- unit tests for focused behavior
- integration tests for persistence and Spring wiring
- container-backed verification where required
- deliberate architecture validation outside the default loop

## Core Guidance

- prefer behavior-focused tests over abstraction-focused tests
- keep the default loop cheap with `mvn test`
- use integration verification for real persistence, Spring wiring, and meaningful end-to-end flows
- run architecture tests deliberately while the codebase continues to evolve
- preserve behavior first when refactoring

## Commands That Matter

- fast loop: `mvn test`
- integration verification: `mvn verify -P integration-tests`
- architecture tests: `mvn test -P architecture-tests`
- coverage gate: `mvn verify -P coverage-gate`
- mutation testing: `mvn test -P mutation-testing`

The durable rule is that refactors must preserve behavior and keep the full pipeline testable end to end.

Primary references:
- `../development/current/testing/testing-strategy-v2-concise.md`
- `../development/current/testing/developer-testing-workflow.md`
