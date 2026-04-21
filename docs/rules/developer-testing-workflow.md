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
