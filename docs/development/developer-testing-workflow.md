# Development and Testing Workflow

This guide explains how to develop and test in LoreVault going forward: philosophy, test layers, and which Maven commands to run at each stage.

## Philosophy
- Hexagonal (ports & adapters): domain at the center; application/services depend on domain and ports; adapters implement ports.
- Tests drive boundaries:
  - Unit tests are the fast default loop.
  - Integration tests validate wiring with containers.
  - Architecture tests codify boundaries (ArchUnit) and are runnable on demand until post-refactor.
- Quality gates are pragmatic:
  - JaCoCo coverage reports always generate; strict thresholds are opt-in when you want to enforce them.
  - Mutation testing (PIT) is opt-in for scheduled or focused quality work.

## Test categories and tags
- Unit: `@Tag("unit")` (default, fast)
- Integration: `@Tag("integration")` (containers, heavier)
- Architecture: `@Tag("architecture")` (ArchUnit rules; excluded from default runs for now)

## Profiles and when to use them
- Day-to-day (fast loop):
  - Command: `mvn test`
  - What runs: unit tests only; JaCoCo report generated; ArchUnit excluded by path.
- Pre-commit (broader check):
  - Command: `mvn verify -P integration-tests`
  - What runs: unit + integration tests (via Failsafe in verify), with reports.
- Enforce coverage (PRs, protected branches, or local enforcement):
  - Command: `mvn verify -P coverage-gate`
  - What runs: unit tests + JaCoCo with strict thresholds enforced.
- Architecture validation (on demand, pre/post refactor):
  - Command: `mvn test -P architecture-tests`
  - What runs: only `@Tag("architecture")` tests (ArchUnit), re-enabling their path.
- Mutation testing (scheduled/nightly or focused refactors):
  - Command: `mvn test -P mutation-testing`
  - What runs: PIT against service/domain/application packages.

## Typical workflows
- Feature TDD/iteration
  - Edit code + tests
  - Run fast loop: `mvn test`
- Before pushing a branch
  - Broader check: `mvn verify -P integration-tests`
  - Optional coverage enforcement: `mvn verify -P coverage-gate`
- Architecture guardrail review (manual until post-refactor)
  - `mvn test -P architecture-tests`
- Deep quality audits
  - `mvn test -P mutation-testing`

## Running and debugging tests
- Single class: `mvn -Dtest=ClassNameTest test`
- Single method: `mvn -Dtest=ClassNameTest#methodName test`
- Trim stack traces for readability: add `-DtrimStackTrace=true`

## Reports
- JaCoCo coverage: `lorevault-api/target/site/jacoco/index.html`
- PIT mutation: `lorevault-api/target/pit-reports/index.html`
- Surefire (unit) results: `lorevault-api/target/surefire-reports/`
- Failsafe (IT) results: `lorevault-api/target/failsafe-reports/`

## Performance and stability
- Parallel JUnit 5:
  - Configured via `src/test/resources/junit-platform.properties` (classes concurrent; methods same_thread).
- Testcontainers reuse:
  - Enabled in `src/test/resources/testcontainers.properties` for faster ITs.
- Maven forking: `forkCount=1C`, `reuseForks=true` in Surefire.

## Architecture rules (status)
- ArchUnit rules live in `src/test/java/com/lorevault/api/architecture/*`.
- Currently excluded from default runs via a path-based exclude for stability during refactors.
- Use `-P architecture-tests` to run them on-demand; plan to re-enable after addressing violations.

## Running the app for manual testing
- Spring Boot (dev): `mvn -pl lorevault-api spring-boot:run -Dspring-boot.run.profiles=dev`
- Environment variables: `.env` is loaded by the provided VS Code task; ensure values are set locally when running outside the task.

## Quick reference
- Fast loop: `mvn test`
- Integration: `mvn verify -P integration-tests`
- Coverage gate: `mvn verify -P coverage-gate`
- Architecture: `mvn test -P architecture-tests`
- Mutation: `mvn test -P mutation-testing`
