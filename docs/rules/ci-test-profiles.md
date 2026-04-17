# CI Test Profiles

**Status:** Active

## Profiles That Matter

- `mvn test` — default fast loop
- `mvn verify -P integration-tests` — broader verification including integration coverage
- `mvn test -P architecture-tests` — on-demand structural validation
- `mvn verify -P coverage-gate` — opt-in coverage enforcement
- `mvn test -P mutation-testing` — focused deep quality work

## Profile Intent

- `mvn test` is the fast default loop
- integration verification is broader confidence before meaningful backend changes land
- architecture tests are on-demand while structural work is still active
- coverage and mutation checks are deliberate quality gates, not everyday iteration commands

## Practical Expectations

- do not treat all checks as part of the default local loop
- use broader profiles deliberately based on the risk of the change
- preserve the cheap fast loop while still having stronger gates available

## Current Quality Gates

### JaCoCo coverage gate

- enforced via `mvn verify -P coverage-gate`
- bundle threshold: 85% instruction, 80% branch
- core package thresholds are stricter for important service/domain/application areas

### PIT mutation testing

- run via `mvn test -P mutation-testing`
- use for focused quality work and scheduled deeper verification

## Performance Notes

- Testcontainers reuse is enabled for faster integration runs
- JUnit 5 class-level parallelism is enabled
- Maven test forking is tuned for practical local feedback
