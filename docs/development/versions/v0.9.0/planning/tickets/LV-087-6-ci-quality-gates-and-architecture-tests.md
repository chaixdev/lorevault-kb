# LV-087-6 — CI quality gates and architecture tests [chore]

Context

- The project targets Java 21; local build failed due to lower JDK. CI wiring is minimal (labels/docs workflows present) and does not enforce build/tests.
- ArchUnit tests exist but are excluded by default; a profile `architecture-tests` is available.
- Coverage gate is opt-in via `-P coverage-gate`.

Problem

- Lack of CI enforcement risks regressions: broken builds, architecture drift, and coverage erosion.

Proposal

- Add a GitHub Actions workflow running on JDK 21:
  - Build and unit tests
  - Coverage gate (`-P coverage-gate`)
  - Architecture tests (`-P architecture-tests`)
- Optionally schedule mutation testing profile nightly or behind a PR label.

Scope

- Create `.github/workflows/ci.yml`:
  - matrix: os: ubuntu-latest; java: 21
  - steps: checkout, setup-java, cache maven, build/test phases described above
- Document developer JDK requirement in README.

Out of scope

- Raising coverage thresholds in this ticket.

Technical notes

- Use `actions/setup-java@v4` with temurin 21.
- Cache `.m2/repository` using standard key.

Acceptance criteria

- [ ] CI workflow present and green on main and PRs (build + unit + coverage gate).
- [ ] Architecture tests run in a separate job or step; failures block PRs.

Quality gates

- [ ] CI required checks updated in repository settings.

Links

- Profiles: ../../../lorevault-api/pom.xml (profiles: coverage-gate, architecture-tests)
- Existing workflows: ../../../.github/workflows
