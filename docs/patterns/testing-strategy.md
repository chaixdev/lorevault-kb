# Testing Strategy

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

## Current Test Shape

LoreVault currently mixes:

- domain tests
- service tests
- persistence and integration tests
- targeted infrastructure tests
- on-demand architecture tests

The active direction is simpler than the older ports-and-adapters testing shape: test business behavior directly, then validate real wiring where it matters.

## Test Categories

- `@Tag("integration")` — real infrastructure or Spring wiring
- `@Tag("architecture")` — ArchUnit tests, excluded by default
- untagged/default tests — the normal fast loop

## Commands

See `../rules/developer-testing-workflow.md` for the authoritative command reference and default loop guidance.

## Tooling Notes

- Testcontainers reuse is enabled in test resources for faster integration runs
- JUnit 5 class-level parallelism is enabled
- Surefire uses practical fork/reuse settings for local development

## Transitional Reality

Some tests and docs still reflect older architectural assumptions.

Treat those as historical residue rather than the target model.

The target model is:

- simpler code
- direct tests for direct code
- integration tests where the system boundary is real
