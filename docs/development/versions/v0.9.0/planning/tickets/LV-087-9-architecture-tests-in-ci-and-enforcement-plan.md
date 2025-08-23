# LV-087-9 — Architecture tests in CI and enforcement plan [chore]

Context

- ArchUnit tests exist and currently use `@Tag("architecture")`; Surefire excludes them by default. A dedicated profile is present.

Problem

- Architecture drift can occur unnoticed if not run in CI.

Proposal

- Wire `-P architecture-tests` into CI (separate job or step) to surface violations.
- Create a tracking plan to address violations and then re-tag tests to run by default.

Scope

- Update CI workflow to run the profile; mark job as required.
- Create an issue list of current violations (if any) and prioritize fixes.

Out of scope

- Fixing violations here (covered by follow-up tickets if needed).

Acceptance criteria

- [ ] Architecture tests run on PRs and fail the build on violations.
- [ ] A short doc lists current violations and owners.

Links

- Tests: ../../../lorevault-api/src/test/java/com/lorevault/api/architecture/PortsAndAdaptersArchitectureTest.java
- POM: ../../../lorevault-api/pom.xml (profile: architecture-tests)
