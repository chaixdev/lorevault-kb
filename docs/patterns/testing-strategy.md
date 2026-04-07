# Testing Strategy Pattern

**Status:** Established, documentation cleanup in progress

LoreVault relies on a mixed testing approach:

- unit tests for focused behavior
- integration tests for persistence and Spring wiring
- container-backed verification where required

The durable rule is that refactors must preserve behavior and keep the full pipeline testable end to end.

Primary references:
- `../development/current/testing/`
- `../development/current/refactor-roadmap.md`
