# Developer Testing Workflow

**Status:** Active

## Default Loop

- run `mvn test` frequently
- keep the fast loop cheap
- prefer behavior-preserving changes backed by tests

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

Primary sources:
- `../development/current/testing/developer-testing-workflow.md`
- `../development/current/testing/testing-strategy-v2-concise.md`
