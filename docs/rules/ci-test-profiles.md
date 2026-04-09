# CI Test Profiles

**Status:** Active

## Profiles That Matter

- `mvn test` — default fast loop
- `mvn verify -P integration-tests` — broader verification including integration coverage
- `mvn test -P architecture-tests` — on-demand structural validation
- `mvn verify -P coverage-gate` — opt-in coverage enforcement
- `mvn test -P mutation-testing` — focused deep quality work

## Practical Expectations

- do not treat all checks as part of the default local loop
- use broader profiles deliberately based on the risk of the change
- preserve the cheap fast loop while still having stronger gates available

Primary source:
- `../development/current/testing/ci-test-profiles.md`
