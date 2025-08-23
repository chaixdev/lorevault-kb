# LV-087-10 — Maven enforcer and static analysis [chore]

Context

- The build targets Java 21 and has solid test profiles, but lacks enforcer and code quality plugins by default.

Problem

- Without minimum Maven/JDK checks and static analysis, regressions and drift can slip in.

Proposal

- Add `maven-enforcer-plugin` to the parent POM with rules:
  - Require Maven 3.9+, Java 21, dependency convergence (optional)
- Add one of: `spotless-maven-plugin` (preferred) or `maven-checkstyle-plugin` for code style.
- Add `spotbugs-maven-plugin` for static analysis (non-fatal locally, enforced in CI).

Scope

- Update parent `pom.xml` to include the plugins and sensible defaults.
- Add a basic Spotless config (Google Java Format or AOSP) and exclude generated sources if any.
- Wire CI to run style and spotbugs steps (warning threshold fail in CI).

Out of scope

- Mass reformat in this ticket (can be a separate PR if large).

Acceptance criteria

- [ ] Enforcer blocks incorrect Java/Maven versions locally and in CI.
- [ ] Style check and SpotBugs run as part of CI; failures block PRs.

Quality gates

- [ ] CI workflow updated to run style + spotbugs.

Links

- Parent POM: ../../../pom.xml
- CI: ../../../.github/workflows
