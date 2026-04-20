# Remove Agent LSP Workarounds and Fix Annotation Processing Environment

**Status:** NOT STARTED

## Summary

Coding agents introduced reflection-based workarounds (`BeanWrapperImpl`) and possibly avoided Lombok annotations throughout the codebase because their LSP reported false-negative errors for Lombok-generated methods. The compilation itself works fine. These workarounds are noise that hurts readability and type safety. This item covers diagnosing the local dev environment to make the LSP correctly process Lombok, then reverting every workaround instance.

## Problem

When coding agents worked on this codebase, their LSP (likely Eclipse JDT-LS via an editor integration) could not resolve Lombok-generated getters, setters, constructors, and builders. The agents treated these as real compile errors and applied workarounds — primarily wrapping domain objects in `BeanWrapperImpl` to access properties via reflection strings instead of calling the Lombok-generated methods directly.

This causes several problems:

- **No compile-time type safety** — property names are strings, typos become runtime failures
- **No IDE navigation** — cannot click through to the property, no refactoring support
- **Verbose and non-idiomatic** — `(UUID) eventBean.getPropertyValue("jobId")` instead of `event.getJobId()`
- **Misleading to future agents and humans** — looks like the domain objects lack accessors when they actually have them via Lombok

## Product Context

- This is entirely a developer experience and code quality issue
- No user-facing behavior change expected
- Removing the workarounds makes the codebase significantly easier to maintain and extend
- Future agent sessions will produce cleaner code if the LSP environment is fixed

## Technical Context

### Known workaround pattern: `BeanWrapperImpl`

16 files use `BeanWrapperImpl` to access properties on objects that already have Lombok-generated accessors. The pattern appears in both production code and tests.

**Production files (6):**
- `SceneDetectionService.java` — wraps `Chapter` (has `@Data`)
- `BookLocationReductionHandler.java` — wraps ingestion events (have `@Getter`)
- `BookIndividualReductionHandler.java` — wraps ingestion events
- `ChapterLocationResolutionHandler.java` — wraps ingestion events
- `ChapterIndividualResolutionHandler.java` — wraps ingestion events
- `EmbeddingHandler.java` — wraps ingestion events and `Chapter`
- `SceneDetectionHandler.java` — wraps `Chapter` and events

**Test files (9):**
- `SceneDetectionServiceTest.java`
- `TextChunkingServiceTest.java`
- `TextChunkingServiceConfigurationTest.java`
- `IndividualResolutionIT.java`
- `BookLocationReductionHandlerTest.java`
- `SceneDetectionHandlerTest.java`
- `BookIndividualReductionHandlerTest.java`
- `ChapterLocationResolutionHandlerTest.java`
- `ChunkingHandlerTest.java`

All target objects (`Chapter`, `Chunk`, `IngestionEvent` subclasses, `Book`) use Lombok annotations (`@Data`, `@Getter`) and have proper accessors at compile time.

### Possible additional pattern: Lombok avoidance

There may be classes that manually define getters/setters/constructors/builders where Lombok annotations would be appropriate, because the agent avoided adding `@Data`/`@Getter`/`@Setter`/`@Builder` to new or modified classes. This needs investigation during the session.

### Build configuration

Lombok annotation processing is already correctly configured in the parent `pom.xml`:
- Lombok 1.18.36 pinned in `<properties>`
- `maven-compiler-plugin` has `<annotationProcessorPaths>` with Lombok
- `mvn compile` and `mvn test` succeed — the false negatives are purely in the LSP/editor layer

## Scope

1. **Diagnose the agent LSP environment** — determine why the LSP does not process Lombok annotations and fix it so future agent sessions get correct diagnostics
2. **Replace all `BeanWrapperImpl` workarounds** — revert to direct Lombok-generated method calls in all 16 identified files
3. **Scan for other Lombok avoidance patterns** — find classes that manually define boilerplate Lombok could generate, and assess whether converting them is appropriate
4. **Verify** — `mvn test` and `mvn compile` must pass after all changes; LSP diagnostics should report no false negatives on the changed files

## Out of Scope

- Changing the Lombok version or replacing Lombok with Java records (separate architectural decision)
- Refactoring domain model classes beyond removing workaround patterns
- Fixing unrelated pre-existing test failures or lint issues
- Build toolchain upgrades

## Known Constraints / Prior Findings

- The compilation works correctly — `mvn compile` passes, `mvn test` passes. The problem is exclusively in the LSP layer
- The parent POM already has the correct `annotationProcessorPaths` configuration for maven-compiler-plugin
- The `BeanWrapperImpl` workaround is concentrated in the ingestion pipeline handlers and their tests, plus `SceneDetectionService`
- All wrapped target classes use Lombok annotations — this is a pure revert, not a design change
- Approximately 80 individual `getPropertyValue`/`setPropertyValue` call sites across the 16 files

### Root Cause (diagnosed 2026-04-20)

**The OpenCode-bundled JDT-LS launches without the Lombok javaagent.** This is the sole cause of all false negatives.

Two JDT-LS instances run on this machine:

1. **VSCode Insiders (RedHat Java extension v1.54.0)** — launches with `-javaagent:.../lombok/lombok-1.18.39-4050.jar`. Lombok works correctly here.
2. **OpenCode's built-in JDT-LS** (`~/.cache/opencode/bin/jdtls/`) — launches **without any `-javaagent` flag**. This is the LSP that coding agents use.

The OpenCode JDT-LS command line (observed from `ps`):
```
java -jar .../org.eclipse.equinox.launcher_1.7.100.v20251111-0406.jar \
  -configuration .../config_mac \
  -data /tmp/opencode-jdtls-data... \
  -Declipse.application=org.eclipse.jdt.ls.core.id1 \
  -Dosgi.bundles.defaultStartLevel=4 \
  -Declipse.product=org.eclipse.jdt.ls.core.product \
  -Dlog.level=ALL \
  --add-modules=ALL-SYSTEM \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED
```

Notice: **no `-javaagent:lombok.jar`** anywhere.

The JDT-LS plugins directory has APT support (`org.eclipse.jdt.apt.core`, `org.eclipse.m2e.apt.core`) and the project has a `.factorypath` referencing `M2_REPO/org/projectlombok/lombok/1.18.36/lombok-1.18.36.jar`, but this alone is insufficient. Lombok requires the javaagent to hook into the Eclipse compiler's AST — without it, the annotations are seen but no methods are generated.

### Environment details

- Java: `openjdk-21.0.2` via mise (`~/.local/share/mise/installs/java/openjdk-21.0.2`)
- OpenCode JDT-LS: `~/.cache/opencode/bin/jdtls/` (version 1.58.0.202603241113)
- Project Eclipse metadata: `lorevault-api/.classpath`, `lorevault-api/.project`, `lorevault-api/.factorypath`
- No `lombok.config` file exists at any level of the project
- No `.settings/` directory exists (no `org.eclipse.jdt.apt.core.prefs`)
- OpenCode config (`~/.config/opencode/opencode.json`) has no Java LSP override in its `lsp` section — only `marksman` for Markdown

### Fix approach

The fix needs to happen at the OpenCode level — either:

1. **OpenCode config override**: If OpenCode supports passing JVM args to its built-in JDT-LS via the `lsp` section in `opencode.json`, add `-javaagent:/path/to/lombok.jar` there
2. **Replace with custom JDT-LS launch**: Configure `opencode.json` `lsp` section with a custom Java LSP command that includes the javaagent flag
3. **Upstream fix**: If OpenCode auto-detects Lombok in the project dependencies and should add the javaagent automatically, this is an OpenCode bug

Option 2 is the most reliable and project-local approach.

## Open Questions

- Does OpenCode support JVM arg customization for its built-in JDT-LS? Check OpenCode docs or source.
- Can we override the Java LSP entirely via the `lsp` section in `opencode.json` with a custom command that includes `-javaagent:lombok.jar`?
- Should we add a `lombok.config` at the project root? (Not strictly needed for the javaagent fix, but good practice for configuring Lombok behavior.)
- Are there files where the agent added `@Data` but then also wrote manual accessors (belt-and-suspenders pattern)?
- Should the Eclipse metadata files (`.classpath`, `.project`, `.factorypath`) be gitignored? They were generated by JDT-LS and may cause confusion.

## Success Criteria

- Zero `BeanWrapperImpl` imports remaining in the codebase (unless used for a legitimate non-workaround reason)
- All property access uses direct Lombok-generated method calls
- `mvn test` passes
- `mvn compile` passes
- Agent LSP environment correctly resolves Lombok-generated members (verified by running LSP diagnostics on a `@Data` class and seeing no false errors)
- Any Lombok avoidance patterns found during investigation are either fixed or explicitly documented as intentional

## Links

- `pom.xml` — Lombok annotation processor configuration (lines 29–31, 73–79)
- `lorevault-api/src/main/java/com/lorevault/api/content/Chapter.java` — example `@Data` class being wrapped
- `lorevault-api/src/main/java/com/lorevault/api/ingestion/IngestionEvent.java` — example `@Getter` base event class
- `docs/rules/development-workflow.md` — repo workflow reference
