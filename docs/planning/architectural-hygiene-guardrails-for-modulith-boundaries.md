# Architectural hygiene guardrails for modulith boundaries

**Status:** NOT STARTED

## Summary

LoreVault's `lorevault-web` / `lorevault-core` split is established, but the internal modulith still relies heavily on documented intent rather than executable enforcement.

This planning item tracks the next bounded architecture-hygiene pass: make the current boundaries harder to accidentally erode, contain known package debt, and align documentation and transport edges with the actual present-state structure.

## Problem

The repository has clear architectural rules, but several of the most important ones are still enforced mostly by convention.

Current review findings show that:

- the coarse module dependency direction is healthy, but internal package boundaries in `lorevault-core` are not actively guarded by executable architecture tests
- known bidirectional couplings (`ai ↔ ingestion`, `library ↔ content`) remain present and could deepen without guardrails
- shared entities such as `Chapter`, `Scene`, and `Chunk` already behave like a broad internal API surface across multiple features
- some `web` transport DTOs still expose core-domain vocabulary directly, which weakens the transport boundary even without reversing module dependencies
- documentation has drifted in places, making architectural intent easier to misread than to verify

Without a bounded follow-up item, new feature work can continue to accumulate architectural drift faster than the codebase documents or contains it.

## Product Context

- Contributors need a trustworthy architecture story when placing new code, shaping feature boundaries, or deciding when to use direct calls versus events.
- Operators and maintainers benefit when the codebase remains legible enough that architectural reviews do not repeatedly rediscover the same undocumented seams.
- A clearer and better-contained modulith lowers the risk that future product work accidentally deepens known debt while shipping unrelated features.

## Technical Context

Relevant current context:

- `pom.xml` defines a two-module reactor: `lorevault-core` and `lorevault-web`
- `lorevault-web/pom.xml` depends on `lorevault-core` and already includes `archunit-junit5` plus an `architecture-tests` Maven profile
- `docs/rules/lorevault-module-conventions.md` and `docs/patterns/codebase-topology.md` document the intended module direction and known intra-core coupling debt
- `docs/rules/code-organization-guidance.md` defines the current package semantics, including `content.timeline` as part of `content` rather than a top-level feature
- the architectural review identified likely follow-up areas including:
  - missing or inactive executable architecture checks
  - known bidirectional package couplings inside `core`
  - transport DTOs in `web` that still expose core-domain types directly
  - broad public visibility for internal types that makes package boundaries communicative rather than restrictive
  - documentation drift between status docs and the actual package map

Relevant files and areas include:

- `lorevault-web/pom.xml`
- `docs/rules/lorevault-module-conventions.md`
- `docs/rules/code-organization-guidance.md`
- `docs/patterns/codebase-topology.md`
- `docs/PROJECT-STATUS.md`
- `lorevault-core/src/main/java/com/lorevault/api/**`
- `lorevault-web/src/main/java/com/lorevault/api/web/**`

## Scope

- Define and implement a bounded first enforcement pass for the current architecture rather than an idealized future one.
- Make the existing `web -> core` split and current web-edge ownership rules executable and verifiable.
- Contain known intra-core architectural debt so new package cycles or new shared model sprawl do not silently enter the codebase.
- Revisit transport-boundary leakage where `web` DTOs expose core-domain shapes more directly than intended.
- Reconcile canonical docs with the actual present package/module map where architecture-facing drift exists.
- Leave enough context for later, deeper package-debt reduction without forcing that entire cleanup into this item.

## Out of Scope

- Introducing additional Maven modules as part of this item
- A repo-wide package reshuffle or broad class-moving campaign
- Eliminating all existing intra-core coupling in one pass
- Replacing the current modulith approach with a different architectural style
- A full solution design for each known coupling cluster before enforcement work begins

## Known Constraints / Prior Findings

- `lorevault-web -> lorevault-core` is the only legal cross-module dependency and is already the healthy boundary to preserve.
- `ai ↔ ingestion` and `library ↔ content` are already known technical debt and should be treated as containment problems first, not silently expanded.
- `Chapter`, `Scene`, and `Chunk` already cross multiple package boundaries; the immediate need is to prevent new shared-model spread rather than pretending the current spread does not exist.
- The repository already has architecture-test scaffolding in Maven, so a first enforcement pass can build on existing tooling instead of requiring a new framework choice.
- Documentation should reflect implemented truth; stale package-map descriptions make architectural intent less trustworthy.

## Open Questions

- Which architectural rules should be enforced first so they protect current truth without failing immediately on already-known debt?
- How much transport-boundary cleanup should be combined with the first enforcement pass versus tracked separately?
- Which internal types are the highest-value candidates for tighter visibility without causing disproportionate churn in tests or Spring wiring?
- Should the first package-containment pass freeze only top-level cycles, or also target a narrower set of cross-package imports?

## Success Criteria

- The repository has executable checks for the most important currently-true architectural boundaries.
- New top-level package cycles or equivalent boundary regressions are harder to introduce silently.
- Contributors can tell from canonical docs and verification paths what the present architecture actually is, not just what older docs said it was.
- The next phase of package-debt reduction can start from a contained baseline instead of a continuously drifting one.

## Links

- Related rules: `../rules/lorevault-module-conventions.md`
- Related rules: `../rules/code-organization-guidance.md`
- Related pattern: `../patterns/codebase-topology.md`
- Related workflow: `../rules/development-workflow.md`
- Related status snapshot: `../PROJECT-STATUS.md`
- Related module build files: `../../pom.xml`, `../../lorevault-web/pom.xml`, `../../lorevault-core/pom.xml`
