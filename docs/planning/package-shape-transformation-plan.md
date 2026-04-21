# Systematically transform package structure toward the target shape

**Status:** NOT STARTED

## Summary

The `web` / `core` module split is complete, and the remaining package-organization work is no longer about module extraction or replaying earlier cleanup stages.

The next planning item is to transform the codebase systematically toward the current target shape: feature-first packages by default, selective internal subpackages only where a feature has earned them, transport concerns stopped at the web boundary, and shared buckets kept intentionally small.

## Problem

The current package layout is no longer blocked on module boundaries, but it still contains mixed-responsibility hotspots and leftover ownership ambiguity.

The larger problem is not only code placement. The repository also needs a bounded plan that helps future work move the codebase toward the agreed package semantics without reintroducing a universal template, repeating completed history, or mixing archaeology with future work.

Today the main friction points are:

- some feature areas remain comparatively flat even though they mix multiple kinds of work
- `support` still risks acting as a convenience bucket rather than a minimal shared-contract area
- the codebase needs a predictable transformation path toward the new rule vocabulary without forcing every feature into the same subpackage pattern

## Product Context

- Clear package semantics help contributors place new code more consistently.
- Better local structure reduces browsing cost and cognitive overhead during feature work.
- Stronger ownership boundaries lower the risk that temporary convenience placements become long-term architectural drift.
- A focused future-work plan is more useful than carrying completed stage history inside an active planning item.

## Technical Context

LoreVault is now a multi-module repository with:

- `lorevault-web` as the runtime shell and transport/UI surface
- `lorevault-core` as the feature-oriented core module

The current durable rule is documented in `../rules/code-organization-guidance.md`.

That rule now establishes:

- top-level feature packages stay primary
- features stay flat by default
- `web.command`, `web.query`, and `web.ui` remain the canonical edge structure
- `application`, `domain`, `infrastructure`, and `events` are optional internal vocabulary, not a required template
- handlers belong in `application`
- exceptions and mappers should stay colocated by default unless a split is clearly earned
- shared space such as `support` should trend toward minimal contract ownership rather than convenience storage

The main currently known candidate areas for transformation are:

- `lorevault-core/src/main/java/com/lorevault/api/ingestion/**`
- `lorevault-core/src/main/java/com/lorevault/api/search/**`
- `lorevault-core/src/main/java/com/lorevault/api/support/**`

The main currently known areas to preserve unless new evidence appears are:

- `lorevault-web/src/main/java/com/lorevault/api/web/command/**`
- `lorevault-web/src/main/java/com/lorevault/api/web/query/**`
- `lorevault-web/src/main/java/com/lorevault/api/web/ui/**`

## Scope

- Define a bounded future-work plan for transforming the codebase toward the agreed package shape.
- Identify which feature areas should remain flat and which should be reconsidered for selective internal structure.
- Preserve the feature-first top-level organization while improving local package semantics where current browsing or ownership problems are real.
- Revisit `support` ownership so it trends toward a narrower contract area rather than a convenience bucket.
- Sequence the transformation work so it can be resumed feature by feature without replaying completed module-split history.
- Keep enough context for later brainstorm and implementation work to choose concrete moves without redoing the same framing discussion.

## Out of Scope

- Re-documenting completed Stage 1 / Stage 2 package and module history
- Another module split or new module taxonomy
- A repo-wide mandatory `application/domain/infrastructure/events` template
- A broad reshuffle of `web.command`, `web.query`, or `web.ui` where the current structure is already working well
- Immediate class-by-class move decisions for every feature area
- Turning this planning item into a detailed implementation proposal; solution-space exploration still belongs in `../brainstorm/`

## Known Constraints / Prior Findings

- The top-level feature split remains the correct default.
- The edge shape in `web.command`, `web.query`, and `web.ui` is already useful and should be preserved.
- Flat packages are acceptable when the feature area is still cohesive.
- Not every feature should gain internal buckets.
- `application`, `domain`, `infrastructure`, and `events` are available vocabulary, not mandatory shelves.
- Events may deserve a sibling `events` package when they form a shared workflow vocabulary across multiple emitters/listeners.
- Exceptions should live close to the area that gives them meaning.
- Mappers should live close to the boundary that needs them.
- Feature-local `config` should be introduced only when it is genuinely earned by meaningful local configuration or bean wiring.
- `support` should not remain a long-term ambiguity sink.
- `ingestion` and `search` are currently the strongest candidates for selective internal restructuring.
- `ai`, `content`, `library`, `timeline`, `health`, and `config` should remain flat unless active work reveals stronger mixed-responsibility seams.

## Open Questions

- Which hotspot should be transformed first: `ingestion`, `support`, or `search`?
- Which currently core-owned contracts should remain shared for now, and which should move closer to `web` or their owning feature?
- Which types in `content` and `timeline` are sufficiently concept-heavy to justify an explicit `domain` subarea later, if any?
- Are there any feature areas where a separate `events` package would be needless ceremony rather than a useful shared vocabulary?
- How much package-shape change can be safely combined with nearby feature work without creating move-twice churn?

## Success Criteria

- A future contributor or agent can quickly understand the target package shape without reading completed module-extraction history.
- The next transformation work can be chosen feature by feature from a bounded, current planning item.
- The codebase has a clear path toward more intentional ownership in `support` and more legible local structure in mixed hotspots.
- The plan preserves already-cohesive areas instead of forcing symmetry for its own sake.
- Later brainstorm or implementation work can build from this plan without re-running the same vocabulary and scope discussion.

## Links

- Related rules: `../rules/code-organization-guidance.md`
- Related rules: `../rules/service-design-principles.md`
- Related planning index: `README.md`
- Relevant source root: `../../lorevault-core/src/main/java/com/lorevault/api`
- Relevant source root: `../../lorevault-web/src/main/java/com/lorevault/api/web`
