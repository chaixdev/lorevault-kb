# Pipeline-Oriented Package Restructure

**Status:** COMPLETE (2026-04-28)

## Summary

Reorganize `lorevault-core` packages from a layered `domain/application/infrastructure` split into a DDD pipeline-oriented structure where each package represents a bounded context or pipeline stage. The goal is for the package tree itself to communicate system structure.

## Completion Notes

All bounded contexts reorganized in sequential phases, each verified with `mvn test` (382 tests, 0 failures) and `mvn test -P architecture-tests` before proceeding:

| Phase | What changed | Notes |
|---|---|---|
| `ingestion` | `domain/application/pipeline` dissolved into `submission/`, `job/`, `scene/`, `content/`, `resolution/{individual,location,event}/`, `triad/`, `completion/` | First phase; established the pattern |
| `content` | `entities/domain` + `entities/infrastructure` dissolved into `scene/`, `chunk/`, `chapter/`, `mention/`, `association/`; `timeline/` preserved | ArchUnit: excluded `content` from subslice cycle checker — bidirectional graph relationships are intentional |
| `ai` | `application/domain/infrastructure` dissolved into `embedding/`, `chunking/`, `llm/`; `infrastructure/` kept for shared prompt infra | |
| `library` | Dissolved into `book/`, `series/`, `universe/`, `service/`; `StringSanitizer` stays at `library/` root | |
| `search` | Dissolved into `rag/`, `semantic/`, `extraction/`, `model/`; shared domain types (CoreSearchRecords, exceptions, policy enums) moved to `search/model/` to break rag↔semantic cycle | `search/model/` addition was a one-pass cycle fix after initial split |

## Problem

The current layered packaging (`application/`, `domain/`, `infrastructure/`) within each bounded context creates flat, hard-to-navigate packages — particularly in `ingestion` and `content`. `ingestion/application/` alone spans scene detection, entity resolution, triad analysis, event embedding, and pipeline coordination. `content/entities/domain` is a blob of 13 entity types with no internal structure. A reader cannot tell from the package tree what the system actually does.

## Product Context

- No user-facing impact; this is a developer experience and maintainability change.
- Reduces onboarding friction and cognitive load when navigating the codebase.

## Technical Context

- Affects `lorevault-core` entirely; `lorevault-web` package structure is unaffected.
- Two ArchUnit tests enforce top-level package rules and cycle constraints:
  - `ModulithBoundaryArchitectureTest` — whitelists top-level packages, bans core-to-web deps, enforces controller placement.
  - `CorePackageBoundaryArchitectureTest` — enforces cycle-freedom across top-level slices.
- Both tests will require updates to allowed-package lists and slice expressions, but the structural rules themselves (no cycles, no core-to-web deps) remain correct and should be preserved.

## Proposed Structure

```
com.lorevault.api/
  config/               # unchanged

  library/
    book/
    series/
    universe/
    query/
    infrastructure/

  content/
    scene/
    chunk/
    chapter/
    mention/
    association/
    timeline/

  ingestion/
    submission/
    job/
    scene/
    content/
    resolution/
      individual/
      location/
      event/
    triad/
    completion/
    events/             # flat shared messaging contract
    infrastructure/

  search/
    query/
    extraction/
    infrastructure/

  ai/                   # shared kernel
    embedding/
    chunking/
    llm/
    infrastructure/

  health/               # unchanged
```

Key redistributions from current structure:
- `ingestion/domain/` dissolves: `IngestionJob` → `job/`, `BookReductionClaim` → `resolution/`, LLM models → `infrastructure/`
- `ingestion/application/pipeline/` stages → their owning pipeline packages
- `content/entities/domain` + `content/entities/infrastructure` → split by entity type across `scene/`, `chunk/`, `chapter/`, `mention/`, `association/`
- `content/timeline/` stays as-is but moves up one level
- `ai/application/domain/infrastructure` → `ai/embedding/`, `ai/llm/`, `ai/chunking/`

## Scope

- Rename/move all packages and classes in `lorevault-core/src/main/java`
- Update both ArchUnit tests to reflect new package names
- Update corresponding test packages in `lorevault-core/src/test/java`
- Verify `mvn test` and `mvn verify -P architecture-tests` pass after each bounded context is moved

## Out of Scope

- `lorevault-web` internal package structure (separate item exists: `tighten-web-transport-boundaries-and-type-visibility.md`)
- Behavioral changes of any kind
- Changes to Spring configuration or bean wiring beyond package rename imports

## Known Constraints / Prior Findings

- ArchUnit whitelist in `ModulithBoundaryArchitectureTest` currently allows: `ai`, `config`, `content`, `health`, `ingestion`, `library`, `search`, `web`, `architecture`. Top-level names are unchanged in the proposed structure, so the whitelist itself does not change — only sub-package rules inside each context.
- Cycle-freedom enforcement in `CorePackageBoundaryArchitectureTest` operates at top-level slices; internal sub-package cycles within a bounded context are not currently checked. Worth adding finer-grained rules as part of this work.
- IntelliJ refactor-rename is available via IDE MCP tools and should be used for all moves to keep references correct.

## Open Questions

- Should each bounded context expose a narrow public API surface (e.g. a `package-info.java` or a façade) and treat sub-packages as package-private? This would be a meaningful architectural tightening but is a larger scope.
- Should `association/` in `content/` be folded into the individual entity packages (e.g. `chapter/` owns `ChapterIndividual`) or kept as a seam?

## Success Criteria

- Package tree communicates system structure without needing to read class names
- `mvn test -P architecture-tests` passes
- `mvn verify -P integration-tests` passes
- No behavioral changes (all existing tests pass)

## Links

- `docs/patterns/ingestion/ingestion-pipeline.md`
- `docs/patterns/ingestion/entity-resolution-ladder.md`
- `docs/patterns/ingestion/triad-analysis.md`
- `lorevault-web/src/test/java/com/lorevault/api/architecture/ModulithBoundaryArchitectureTest.java`
- `lorevault-web/src/test/java/com/lorevault/api/architecture/CorePackageBoundaryArchitectureTest.java`
