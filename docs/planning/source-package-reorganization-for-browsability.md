# Reorganize source packages for better browsability and semantic guidance

**Status:** PARKED

## Summary

The top-level source package split already reflects the main feature areas of the product well, but several feature packages have become internally flat and harder to browse as they have grown.

This planning item tracks the opportunity to reorganize internal package structure so package boundaries better communicate responsibility, improve discoverability, and guide future code into more legible homes.

## Problem

The current Java source layout is largely coherent at the top level, but some feature packages have accumulated too many files with mixed roles in a single flat namespace.

That creates several forms of friction:

- it becomes slower to scan a package and understand what kinds of classes live there
- mixed responsibilities in one package weaken the semantic meaning of package boundaries
- new code has fewer structural cues about where it should belong
- package growth becomes accidental rather than guided by a repeatable organizational rule

The result is not that the feature split is wrong, but that some feature internals no longer communicate intent clearly enough.

## Product Context

- Better source-code browsability improves day-to-day development speed.
- Clear package semantics help new contributors and agents place new code more consistently.
- A more legible structure reduces the risk of important domain, transport, persistence, and orchestration concerns drifting together over time.

## Technical Context

The current production source layout is primarily feature-oriented at the top level under `com.lorevault.api`.

Stable top-level areas currently include:

- `ingestion`
- `content`
- `search`
- `ai`
- `timeline`
- `library`
- `web`
- `config`
- `health`
- `support`

Existing naming already mixes feature-first structure with role-oriented naming such as `Service`, `Controller`, `Handler`, `Event`, and repository suffixes.

The strongest current internal structure already visible in the codebase includes:

- `web.command`, `web.query`, and `web.ui` separation
- feature-local classes with role suffixes such as `*Service`, `*Handler`, `*Controller`
- some focused subareas such as `search.entityextraction`

The most likely reorganization candidates identified so far are:

- `com.lorevault.api.ingestion` as the largest mixed-responsibility package
- `com.lorevault.api.support` as a catch-all shared package containing DTOs, utilities, and policy-like types
- `com.lorevault.api.web.ui` where controllers, forms, and view data structures are all part of the same UI area
- `com.lorevault.api.web.command.ingestion` where controllers, validation, extraction, builders, and response shaping all participate in the same command area
- `com.lorevault.api.search` where retrieval orchestration and extraction concerns may deserve clearer internal separation

## Scope

- Define a desired package-organization direction for production Java code.
- Preserve the current top-level feature split unless a later review shows a specific exception is necessary.
- Identify which packages should remain flat because they are already homogeneous.
- Identify which packages should gain internal subpackages because they exceed a reasonable size and mix responsibilities.
- Establish a small stable vocabulary for subpackage naming so future growth stays legible.
- Capture candidate areas for phased reorganization work later.

## Out of Scope

- Performing the package moves now
- Renaming classes as part of broader domain modeling changes
- Large behavioral refactors that are only indirectly related to package layout
- Replacing the current top-level feature split with a layer-first architecture
- Documenting final canonical package rules before the structure is actually accepted and implemented

## Known Constraints / Prior Findings

- The top-level feature split appears broadly correct and should be preserved by default.
- Package growth beyond roughly 7-10 files deserves review when the contents are not highly uniform.
- Larger flat packages are acceptable when they contain one narrow kind of thing, such as DTOs, mappers, or similarly homogeneous types.
- The repository already uses a mixed but understandable style: feature-first organization with role-based naming inside features.
- `ingestion` appears to be the clearest current example of a package that mixes events, handlers, services, repositories, status models, and logging-related types.
- `support` currently behaves as a shared catch-all and likely needs sharper boundaries so only truly shared concerns remain there.
- `web` already contains a useful semantic split that should likely be strengthened rather than replaced.
- Repository naming currently shows some drift across `*Repository`, `*GraphRepository`, `*ReadRepository`, and `*WriteRepository` styles.
- DTO placement also appears mixed between feature-local usage and cross-cutting shared placement.

## Open Questions

- Which subpackage vocabulary should become the default across features: `model`, `service`, `repository`, `dto`, `event`, `handler`, `client`, `config`, and similar?
- Which packages should remain intentionally flat even after review?
- Should feature-specific request and response models move out of `support` and live closer to their owning feature or web area?
- How much repository naming standardization is desirable before package moves begin?
- Should search extraction concerns remain under `search` or become a more explicitly named subarea?
- Should the eventual work be done feature by feature, or should a repo-wide package convention be codified first in a brainstorm or rules doc?

## Success Criteria

- There is a clear, bounded proposal for how internal package structure should evolve while preserving the top-level feature split.
- Future reorganization work can identify high-value target packages and a sensible sequencing strategy.
- Contributors can apply a small consistent set of package semantics when adding new code.
- Shared packages such as `support` have a narrower and more intentional meaning.
- Large mixed-responsibility packages have an agreed direction for being split into more legible subareas.

## Links

- Related rules: `../rules/development-workflow.md`
- Related planning index: `README.md`
- Relevant source root: `../../lorevault-api/src/main/java/com/lorevault/api`
