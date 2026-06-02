# Remove Agent LSP Workarounds and Fix Annotation Processing Environment

**Status:** DONE (with narrow follow-ups listed below)

## Summary

Coding agents introduced reflection-based workarounds (`BeanWrapperImpl`, manual field reflection, avoidance of Lombok) throughout the codebase because their LSP reported false-negative errors on Lombok-generated members. The compilation itself always worked fine. This item covered:

1. diagnosing why the agent LSP could not see Lombok-generated code,
2. fixing the agent LSP environment locally,
3. removing all the workaround code that was created in response to the false negatives,
4. doing a *targeted* Lombok cleanup for the safe cases only (not a repo-wide blanket migration).

All four are now complete. The remaining items are small, intentional follow-ups.

## Problem

When coding agents worked on this codebase, their LSP (OpenCode-bundled JDT-LS) could not resolve Lombok-generated getters, setters, constructors, and builders. The agents treated these as real compile errors and applied workarounds:

- wrapping domain objects in `BeanWrapperImpl` to access properties via reflection strings
- using ad-hoc `java.lang.reflect.Field` helpers to read/write fields on Lombok-backed types
- preferring manual loggers and explicit constructors over `@Slf4j` and `@RequiredArgsConstructor`

Problems these caused:

- no compile-time type safety — property names were strings, typos became runtime failures
- no IDE navigation — cannot jump to the property, no rename refactoring
- verbose and non-idiomatic — `(UUID) eventBean.getPropertyValue("jobId")` instead of `event.getJobId()`
- misleading to future agents and humans — looked like the domain objects lacked accessors when they did have them

## Product Context

- This is a developer experience and code quality cleanup.
- No user-facing behavior change.
- Future agent sessions should now produce cleaner code because the LSP environment correctly resolves Lombok-generated members.

## Root Cause (diagnosed 2026-04-20)

**The OpenCode-bundled JDT-LS launches without the Lombok javaagent.** This was the sole cause of all false negatives.

Two JDT-LS instances exist on the dev machine:

1. **VSCode Insiders (RedHat Java extension v1.54.0)** — launches with `-javaagent:.../lombok/lombok-1.18.39-4050.jar`. Lombok works correctly here.
2. **OpenCode's built-in JDT-LS** (`~/.cache/opencode/bin/jdtls/`) — launched **without any `-javaagent` flag**. This is the LSP that coding agents used.

The JDT-LS plugins directory has APT support (`org.eclipse.jdt.apt.core`, `org.eclipse.m2e.apt.core`), and the project had an Eclipse `.factorypath` referencing `M2_REPO/org/projectlombok/lombok/1.18.36/lombok-1.18.36.jar`, but that alone is not sufficient. Lombok requires the javaagent to hook into the Eclipse compiler's AST — without it, annotations are seen but no members are generated for the LSP.

### Build configuration

Lombok annotation processing is correctly configured in the parent `pom.xml`:

- Lombok 1.18.36 pinned in `<properties>`
- `maven-compiler-plugin` has `<annotationProcessorPaths>` with Lombok
- `mvn compile` and `mvn test` succeed — the false negatives were purely in the LSP/editor layer

### Environment details

- Java: `openjdk-21.0.2` via mise (`~/.local/share/mise/installs/java/openjdk-21.0.2`)
- OpenCode JDT-LS: `~/.cache/opencode/bin/jdtls/` (version 1.58.0.202603241113)
- No `lombok.config` file at any level of the project (not required for the fix).
- Eclipse metadata (`.classpath`, `.project`, `.factorypath`, `.settings/`) is already covered by `.gitignore` and is not tracked.

## Agent LSP Fix (applied locally)

The fix happens at the OpenCode user config level, not in this repo.

1. Added a wrapper script `~/.local/bin/jdtls-lombok.sh` that launches the bundled JDT-LS with `-javaagent:$HOME/.m2/repository/org/projectlombok/lombok/1.18.36/lombok-1.18.36.jar`.
2. Added a `java` LSP entry in `~/.config/opencode/opencode.json` pointing `.java` files at that wrapper.
3. Verified: fresh `lsp_diagnostics` on known Lombok classes (`Chapter.java`, `IngestionEvent.java`, `@Slf4j` services, `@RequiredArgsConstructor` handlers) returns clean.

Because this fix lives in the user's OpenCode config and not in the repo, the repo itself is unchanged. Any new dev machine needs the same user-level wrapper and `opencode.json` entry to get correct Java LSP diagnostics.

## Workaround Code Removal (applied in repo)

### Phase 1 — confirmed `BeanWrapperImpl` workaround set

All string-based property access via `BeanWrapperImpl` / `getPropertyValue` / `setPropertyValue` has been replaced with direct Lombok-generated accessors across ~18 files (production + tests) and ~80 call sites. Complete. `BeanWrapperImpl` is no longer present in `lorevault-api`.

### Phase 2 — reflection-helper cleanup

Removed the ad-hoc `Field.setAccessible(true)` reflection helpers in three production services and their matching tests:

- `IngestionCompletionCoordinator`
- `TriadOrchestrationService`
- `TriadEdgePersistenceService`

Each now uses direct typed getters on events/entities. Tests switched from `ReflectionTestUtils.setField` / `sun.misc.Unsafe` hacks to typed setters or real constructors.

### Batch A — safe Lombok conversions

Converted assignment-only constructors and manual loggers to `@Slf4j` + `@RequiredArgsConstructor` in:

- AI layer: `TriadOrchestrationService`, `SceneDetectionService`, `SceneDetectionClient`, `TextChunkingService`, `LlmRetryStrategy`
- Search: `RagService`
- Web query: `AskController`
- Ingestion command controllers: `BookIndividualResolutionCommandController`, `BookLocationResolutionCommandController`, `ChapterIndividualResolutionCommandController`, `ChapterLocationResolutionCommandController`
- Ingestion event handlers: `BookLocationReductionHandler`, `BookIndividualReductionHandler`, `ChapterIndividualResolutionHandler`, `ChapterLocationResolutionHandler`

### Batch B — cautious Lombok conversion

- `IngestionCompletionCoordinator` — explicit constructor removed, now `@Slf4j` + `@RequiredArgsConstructor`. Verified by clean build and targeted test.

### EmbeddingService reflection removal

The one remaining production reflection call was `EmbeddingService` using `Method.invoke(getModelId)` on `EmbeddingModel`. Replaced with:

1. configured property `lorevault.ai.models.embedding.model` (primary)
2. `EmbeddingResponse.getMetadata().getModel()` captured from the real batch call (secondary)
3. class-name fallback for providers that don't populate metadata

`FakeEmbeddingModel` and `EmbeddingServiceTest` were updated to exercise path 2.

## Intentionally Not Converted

These were reviewed and deliberately left alone:

- **`EmbeddingHandler`** — constructor has non-trivial helper wiring (`PipelineStageSupport` construction). Not a simple assignment-only constructor, not a Lombok candidate without refactor.
- **`SceneDetectionHandler`** — same pattern as `EmbeddingHandler`.
- **Repo-wide blanket Lombok migration** — explicitly rejected. The repo intentionally mixes Lombok and explicit style; a blanket conversion would be scope creep and risk style regressions.
- **Spring event classes with explicit super(source)` constructors** — required by Spring event inheritance, not a workaround.
- **`ReflectionTestUtils` in a few tests** — ordinary Spring test plumbing, not a Lombok workaround.

## Success Criteria — Status

- [x] Zero `BeanWrapperImpl` imports in `lorevault-api`.
- [x] All removed workaround sites use direct Lombok-generated method calls.
- [x] `mvn compile` passes.
- [x] `mvn test-compile` passes.
- [x] Targeted tests for each batch pass.
- [x] Agent LSP environment correctly resolves Lombok-generated members (verified on `@Data`, `@Getter`, `@Slf4j`, `@RequiredArgsConstructor` classes).
- [x] Lombok avoidance patterns found during investigation are either converted (Batch A/B) or explicitly documented as intentional (above).

## Follow-ups

Small, bounded, not required for this ticket to be considered done:

1. Consider running the broader Maven profiles (`mvn verify -P integration-tests`, coverage gate) to confirm no surprises beyond the targeted suites we ran per batch.
2. Optional: if OpenCode ever adds native Lombok-agent support, remove the local wrapper script in favor of the upstream mechanism.
3. Optional: decide whether to add a project-root `lombok.config` for behavior knobs (e.g., `lombok.addLombokGeneratedAnnotation = true`). Not needed for the LSP fix.

## Links

- `pom.xml` — Lombok annotation processor configuration
- `lorevault-api/src/main/java/com/lorevault/api/content/Chapter.java` — example `@Data` class previously wrapped
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionEvent.java` — example `@Getter` base event class
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionCompletionCoordinator.java` — Batch B target
- `lorevault-api/src/main/java/com/lorevault/api/ai/EmbeddingService.java` — reflection removal target
- `docs/rules/development-workflow.md` — repo workflow reference
