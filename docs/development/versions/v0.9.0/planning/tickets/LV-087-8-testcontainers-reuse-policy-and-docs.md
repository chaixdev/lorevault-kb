# LV-087-8 — Testcontainers reuse policy and docs [chore]

Context

- `src/test/resources/testcontainers.properties` enables reuse in-repo: `testcontainers.reuse.enable=true`.
- Best practice is to keep reuse as a developer machine opt-in (`~/.testcontainers.properties`).

Problem

- Enabling reuse in-repo can have security/resource implications in CI and on shared machines.

Proposal

- Remove the in-repo `testcontainers.properties` and document how to enable reuse locally.
- Ensure CI does not reuse containers.

Scope

- Delete `lorevault-api/src/test/resources/testcontainers.properties`.
- Update `docs/development/testing/` to include a note on enabling reuse locally.

Out of scope

- Test refactors; functional test behavior should remain unchanged.

Acceptance criteria

- [ ] No `testcontainers.properties` in the repo.
- [ ] Docs include local developer instructions for container reuse.

Quality gates

- [ ] CI integration tests still pass without reuse.

Links

- File: ../../../lorevault-api/src/test/resources/testcontainers.properties
- Docs: ../../../docs/development/testing
