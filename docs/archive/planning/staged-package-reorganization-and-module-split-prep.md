# Reorganize source packages for better browsability and semantic guidance

**Status:** ACTIVE — Stage 0, Stage 1, and Stage 2 are complete. Stage 3 is in progress: pass 1 is complete, and follow-up internal package cleanup remains.

**Execution summary:**
- Stage 0: full classification of all 21 `support` types + all search DTOs. Zero code moves.
- Stage 1: 5 type-move units executed and verified. `support` is now a more homogeneous cross-boundary DTO/contract bucket, but ownership cleanup still remains before it contains only truly shared contracts.
  - `HashUtils` → `ingestion`
  - `StringSanitizer` → `content`
  - `PublicationCoordinates` → `content`
  - `ErrorResponse` → `web` (with `ErrorResponseFactory` consolidation — inner class retired, factory now produces `web.ErrorResponse`)
  - `SpoilerVisibility`, `SeriesProgress`, `UnconfiguredSeriesPolicy` → `search`
- Verification: `mvn test` passes with 301 tests, 0 failures, 0 errors after Stage 1.
- Stage 2: module extraction is complete. The reactor now contains `lorevault-core` and `lorevault-web`; `lorevault-web` depends one-way on `lorevault-core`; transitional `../lorevault-api/src/...` back-references are gone from module POMs; and the obsolete `lorevault-api/pom.xml` module stub was removed.
- Stage 3 (pass 1): ingestion events were moved under `ingestion.events` and all affected imports/usages were updated across core + web.
- Latest known full-reactor verification on this branch remains green: `mvn clean test` passes with 305 tests, 0 failures, 0 errors.
- Branch: `refactor/staged-package-reorganization-stage0-audit`

## Summary

The top-level source package split already reflects the main feature areas of the product well, but several feature packages have become internally flat and harder to browse as they have grown.

This planning item now also tracks a combined staged direction for sequencing package cleanup with the likely later split into `web`, `core`, and an optional shared `api` module.

The current working direction is not to perform broad browsability-driven package moves first. Instead, the work should be staged so boundary-clarifying cleanup happens before module extraction, and broader package cleanup follows once module ownership is stable.

## Problem

The current Java source layout is largely coherent at the top level, but some feature packages have accumulated too many files with mixed roles in a single flat namespace.

That creates several forms of friction:

- it becomes slower to scan a package and understand what kinds of classes live there
- mixed responsibilities in one package weaken the semantic meaning of package boundaries
- new code has fewer structural cues about where it should belong
- package growth becomes accidental rather than guided by a repeatable organizational rule

The result is not that the feature split is wrong, but that some feature internals no longer communicate intent clearly enough.

At the same time, the repository is also a candidate for a later Maven module split. That introduces a second source of friction:

- some package moves would clarify eventual `web` vs `core` ownership
- some package moves would be mostly cosmetic and risk being done twice
- some shared DTO and contract types currently blur the module boundary more than the package boundary

Without an explicit staged plan, the repository risks doing readability cleanup and module-boundary cleanup in the wrong order.

## Product Context

- Better source-code browsability improves day-to-day development speed.
- Clear package semantics help new contributors and agents place new code more consistently.
- A more legible structure reduces the risk of important domain, transport, persistence, and orchestration concerns drifting together over time.
- A staged approach avoids churn from moving code for readability and then moving it again when module boundaries are enforced.

## Technical Context

The current production source layout is primarily feature-oriented at the top level under `com.lorevault.api`.

The repository is already a Maven reactor with a single runtime module, so future extraction into multiple modules is structurally possible even though the code currently lives under one production module.

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

Recent audit findings also indicate:

- `web` is already grouped more clearly than most other areas and likely maps cleanly to a future `web` module
- `support` contains both likely shared contract types and catch-all utility or policy types
- `search` mixes transport DTOs, orchestration, and infrastructure concerns in the same feature area
- `ingestion` is still the clearest large mixed-responsibility package
- `content`, `ingestion`, and `timeline` are tightly coupled enough that broad pre-split reshuffling risks encoding the wrong boundaries
- shared DTOs and service signatures appear to be the main seam for a future `web` / `core` split

The most likely reorganization candidates identified so far are:

- `com.lorevault.api.orchestration` as the largest mixed-responsibility package
- `com.lorevault.api.support` as a catch-all shared package containing DTOs, utilities, and policy-like types
- `com.lorevault.api.web.ui` where controllers, forms, and view data structures are all part of the same UI area
- `com.lorevault.api.web.command.ingestion` where controllers, validation, extraction, builders, and response shaping all participate in the same command area
- `com.lorevault.api.search` where retrieval orchestration and extraction concerns may deserve clearer internal separation

## Scope

- Define a desired package-organization direction for production Java code.
- Define a staged sequence that combines package cleanup with later module-boundary work.
- Preserve the current top-level feature split unless a later review shows a specific exception is necessary.
- Identify which packages should remain flat because they are already homogeneous.
- Identify which packages should gain internal subpackages because they exceed a reasonable size and mix responsibilities.
- Establish a small stable vocabulary for subpackage naming so future growth stays legible.
- Capture candidate areas for phased reorganization work later.
- Identify which cleanup steps should happen before module extraction, during module extraction, and after module extraction.

## Out of Scope

- Performing the package moves now
- Performing the Maven module split now
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
- `web.ui` and `web.command.ingestion` appear comparatively cohesive already and are weaker candidates for early package cleanup.
- The highest-value pre-module work is likely around clarifying shared DTO ownership rather than broad feature-internal reshuffling.
- Domain-free request and response models are lower-risk early move candidates than support types that directly depend on ingestion or domain enums and models.
- `ingestion`, `content`, and `timeline` appear tightly connected enough that broad internal movement before module boundaries are chosen would create move-twice risk.
- A separate `api` module is most plausible as a shared contracts jar, not automatically as a home for every interface or abstraction.

## Candidate Staged Direction

### Stage 0 — Boundary audit and ownership marking

Before moving code, identify which existing types are:

- clearly `web`-owned transport concerns
- clearly `core`-owned domain or orchestration concerns
- likely shared contracts
- still ambiguous and should wait

This stage should focus especially on:

- `support`
- search request and response DTOs
- error-response shaping
- service signatures that currently use transport-shaped DTOs directly

### Stage 1 — Narrow pre-split cleanup that clarifies boundaries

Only do package or type moves that directly reduce future module ambiguity.

The highest-value candidate areas are:

- narrowing `support` so obviously shared contracts are separated from catch-all helpers or policy-like types
- clarifying where search DTOs belong relative to search orchestration and transport-facing usage
- tightening any package naming or placement that makes `web` vs `core` ownership materially clearer

This stage should stay intentionally small. The goal is not full browsability cleanup; the goal is to remove the worst ownership ambiguity before module extraction.

### Stage 2 — Module extraction

After the narrow boundary-prep pass, use the clarified ownership lines to drive a split into:

- `web` for HTTP, UI, request handling, response shaping, and bootstrapping concerns
- `core` for business logic, orchestration, domain behavior, persistence, and infrastructure that is not itself transport-facing
- optional `api` only if a stable shared contracts surface is clearly justified

This stage should be treated as the primary architectural step. It should not wait on broad cosmetic package cleanup.

#### Agreed Stage 2 execution strategy

The current working decision for Stage 2 is:

- create `lorevault-core` as a plain jar containing non-web feature code and runtime support code
- create `lorevault-web` as the only Spring Boot application module
- keep the existing Java package root (`com.lorevault.api`) and preserve the current top-level feature split during extraction
- use a direct one-way dependency `lorevault-web` → `lorevault-core`
- do **not** create an `api` module during this stage

This keeps the split aligned with the current codebase shape while avoiding premature contract extraction.

#### Agreed package/module ownership for Stage 2

**`lorevault-web` should own:**

- `com.lorevault.api.LoreVaultApiApplication`
- `com.lorevault.api.web.*`
- Thymeleaf templates and other UI-facing resources under `src/main/resources/templates/**`
- runtime-facing resources such as `application.yml` and boot presentation assets like `banner.png`
- transport/runtime adapter classes that are currently misplaced in core packages

**`lorevault-core` should own:**

- `ai`
- `config`
- `content`
- `health`
- `ingestion`
- `library`
- `search`
- `support`
- `timeline`
- core-consumed resources such as prompts and schema/bootstrap resources

#### Boundary decisions already made for Stage 2

- `ErrorResponse` remains web-owned in `web`
- `SpoilerVisibility`, `SeriesProgress`, and `UnconfiguredSeriesPolicy` remain feature-owned in `search`
- current `support` DTOs stay in `core` for this stage even if they are transport-shaped, because existing core services still own and use them directly
- `AskDtos` and `SemanticSearchDtos` remain in `search` during Stage 2
- `lorevault-web` may depend directly on core services and core-owned feature contracts during this stage

#### Stage 2 seam-fix verification (runtime-adapter leaks)

The runtime-adapter seam leaks previously tracked for Stage 2 are now verified as resolved in the current split:

- `JobStatusBroadcaster` is web-owned at `lorevault-web/src/main/java/com/lorevault/api/web/query/job/JobStatusBroadcaster.java`
- `SystemHealthIndicator` is web-owned at `lorevault-web/src/main/java/com/lorevault/api/web/health/SystemHealthIndicator.java`

Verification note:

- `lorevault-core/src/main/java` contains no `SseEmitter`, MVC controller annotations, or Actuator `HealthIndicator` runtime adapters.

With these seam fixes in place, Stage 2 is treated as complete in the current repository state. Remaining work is now Stage 3 package-browsability cleanup inside the stable `core` / `web` split, plus any later cleanup of stale legacy metadata or local artifacts that still mention `lorevault-api`.

#### Resource ownership rule for Stage 2

- `application.yml`, templates, and boot-facing presentation/runtime resources stay in `lorevault-web`
- prompt files and schema/bootstrap resources move with `lorevault-core`
- resource movement must be verified by actual classpath loading after the split

#### Initial dependency split sketch for Stage 2

The module split should treat dependency ownership as part of the boundary, not as an afterthought.

**`lorevault-core` should keep dependencies needed for:**

- Spring-managed services and configuration
- persistence and repository support
- validation and retry
- AI clients and orchestration
- feature-local libraries used by search, ingestion, content, timeline, and library logic

The expected first-pass dependency set for `lorevault-core` is:

- `spring-boot-starter`
- `spring-boot-starter-data-neo4j`
- `spring-boot-starter-validation`
- `spring-retry`
- `spring-aspects`
- `spring-ai-openai`
- `spring-ai-client-chat`
- `opennlp-tools`
- `ahocorasick`
- `lombok`

`lorevault-core` is therefore a **plain jar**, but not a framework-free jar. It remains a Spring-managed engine module; it simply stops being the Boot runtime shell.

**`lorevault-web` should keep dependencies needed for:**

- Spring Boot application startup
- HTTP controllers and transport adapters
- Thymeleaf UI rendering
- actuator exposure and runtime shell concerns
- OpenAPI / Swagger UI

The expected first-pass dependency set for `lorevault-web` is:

- dependency on `lorevault-core`
- `spring-boot-starter-web`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-actuator`
- `springdoc-openapi-starter-webmvc-ui`
- `lombok`

#### Dependency decisions already made for Stage 2

- `spring-boot-starter-web` must not remain in `lorevault-core`
- `spring-boot-starter-thymeleaf` must not remain in `lorevault-core`
- `springdoc-openapi-starter-webmvc-ui` must not remain in `lorevault-core`
- `spring-boot-starter-actuator` is treated as web-owned for this split because actuator exposure is part of the runtime shell
- `spring-boot-starter` is acceptable in `lorevault-core` for the first-pass split because it minimizes churn while keeping core Spring-managed

This dependency split is intentionally pragmatic. It prefers a clean runtime boundary over premature narrowing of every Spring dependency to the smallest possible artifact set.

#### Test placement rule for Stage 2

- MVC slice tests and UI/controller tests belong in `lorevault-web`
- focused core service and repository tests belong in `lorevault-core`
- full-context `@SpringBootTest` coverage should remain in `lorevault-web` initially because the Boot application lives there

#### Safe execution order for Stage 2

1. Add `lorevault-core` and `lorevault-web` modules to the reactor and establish one-way dependency from web to core.
2. Move non-web source and core-owned resources into `lorevault-core` without renaming packages.
3. Move Boot application, web source, and web-owned resources into `lorevault-web`.
4. Fix known boundary leaks (`JobStatusBroadcaster`, `SystemHealthIndicator`) and any compile-time dependency violations exposed by the split.
5. Re-home tests by runtime boundary only after the production split compiles cleanly.

#### Stage 2 risk notes

- The biggest extraction risk is trying to redesign service signatures while moving modules. Existing core services already use current feature DTOs directly, and changing those signatures during Stage 2 would add avoidable risk.
- The other major risk is silent resource breakage: prompts, schema/bootstrap files, and runtime config must be verified after the move rather than assumed to load correctly from the new module layout.
- A separate `api` module remains deferred until the repository has at least two genuine consumers of the same shared contract surface.

#### Relocation strategy after the initial module split

The agreed follow-up strategy is **not** one-file-at-a-time relocation. The preferred approach is a small number of coherent relocation passes that remove the temporary shared-source setup without mixing that work with broader architectural redesign.

The current reactor split proves that the `web` / `core` boundary works. The next goal is to make the filesystem layout match that boundary.

#### Transitional rule for dependency strictness during relocation

During physical source relocation, dependency cleanup should stay **secondary** to source movement.

- temporary overlap is acceptable when it reduces migration churn
- `lorevault-web` may remain permissive while files and tests are moving
- `lorevault-core` may keep broad **core-safe** Spring and infrastructure dependencies while relocation is in progress

However, the following remain hard red lines for `lorevault-core` even during relocation:

- no `spring-boot-starter-web`
- no Thymeleaf dependency
- no springdoc/OpenAPI web starter
- no MVC/SSE/transport runtime APIs as a module dependency strategy

This keeps the boundary meaningful while still allowing a pragmatic migration.

#### Preferred relocation sequence

**Phase 2A — relocate web production source first**

Move into `lorevault-web/src/main/java`:

- `LoreVaultApiApplication`
- `web/**`
- runtime adapters already classified as web-owned, including `JobStatusBroadcaster` and `SystemHealthIndicator`

Then update `lorevault-web/pom.xml` to stop reading production sources from `../lorevault-api/src/main/java`.

**Why first:** `web` is already the clearest seam and is smaller than the core feature set, so it is the safest first physical move.

**Phase 2B — relocate web resources**

Move into `lorevault-web/src/main/resources`:

- `application.yml`
- `templates/**`
- `banner.png`

Then remove the temporary back-reference from `lorevault-web/pom.xml` to the old resource location.

**Phase 2C — relocate core production source**

Move into `lorevault-core/src/main/java`:

- `ai`
- `config`
- `content`
- `health` minus web-owned runtime adapters
- `ingestion` minus web-owned runtime adapters
- `library`
- `search`
- `support`
- `timeline`

Then update `lorevault-core/pom.xml` to stop reading production sources from `../lorevault-api/src/main/java`.

**Phase 2D — relocate core resources**

Move into `lorevault-core/src/main/resources`:

- `prompts/**`
- `db/**`

Then remove the temporary back-reference from `lorevault-core/pom.xml` to the old resource location.

**Phase 2E — relocate tests by module ownership**

Only after production source and resources are stable:

- move MVC slice tests and UI/controller tests into `lorevault-web/src/test/java`
- move focused service, repository, and core integration tests into `lorevault-core/src/test/java`
- keep full `@SpringBootTest` coverage in `lorevault-web` first, then reconsider later only if a narrower runtime test shape becomes worthwhile

**Phase 2F — remove transitional scaffolding**

After the physical relocation is complete and verified:

- remove `sourceDirectory` back-references into `../lorevault-api/src/...`
- remove compiler include/exclude slicing used to carve the old tree into modules
- remove any now-obsolete transitional `lorevault-api` source structure

**Completion note (verified):**

- `lorevault-core/pom.xml` and `lorevault-web/pom.xml` no longer use `../lorevault-api/src/...` source/resource back-references.
- No temporary compiler include/exclude slicing remains for module carving.
- The obsolete transitional module stub `lorevault-api/pom.xml` has been removed.

#### What this strategy is explicitly avoiding

- no one-file-at-a-time relocation across unrelated packages
- no giant all-at-once filesystem rewrite across code, tests, and resources together
- no service-signature redesign during source relocation
- no tightening of every dependency at the same time files are still moving

#### Verification rule for relocation phases

Each relocation pass should be followed by verification at the module/reactor level:

- module compile still passes
- full reactor tests still pass
- Boot startup still works from `lorevault-web`
- user-facing runtime/UAT checks confirm that `lorevault-web` loads and wires `lorevault-core` correctly

Only after that should the repository move into a stricter dependency-pruning pass.

### Stage 3 — Post-split browsability cleanup inside stable modules

Once module ownership is settled, revisit larger browsability problems that are still worth solving inside the new module boundaries.

The strongest current candidates for that later cleanup now span multiple feature areas, not only `ingestion`:

- `ingestion`, especially if it still mixes events, handlers, repositories, services, and status models in one namespace
- `search`, especially if orchestration, infrastructure, and DTO concerns still sit too close together
- `support`, especially where transport-shaped DTOs still blur true feature ownership or remain in a shared bucket by convenience rather than necessity
- selective mixed web adapter zones only where ownership is still unclear after the split

At that point, cleanup becomes less likely to be undone by later architectural moves.

#### Stage 3 progress notes (current)

- ✅ Pass 1 complete: all ingestion pipeline event classes now live in `com.lorevault.api.orchestration.signals`.
- ✅ Core pipeline listeners/coordinators/support classes now import events from `ingestion.events`.
- ✅ Web broadcaster and related tests were updated to consume `ingestion.events` types.
- ✅ Stage 2 module extraction and transitional scaffolding cleanup are complete: the parent reactor only includes `lorevault-core` and `lorevault-web`, module POMs read their own `src/main/**` trees directly, and the obsolete `lorevault-api/pom.xml` stub is gone.
- ✅ Latest known full reactor verification on this branch remains green (`mvn clean test`, 305/0/0).
- ⏳ Stage 3 pass 2 should target handler/service/repository/status-model separation inside `lorevault-core/src/main/java/com/lorevault/api/ingestion/**`, using the agreed vocabulary (`application`, `infrastructure`, events isolated from handlers) while preserving runtime behavior.
- ⏳ A parallel Stage 3 planning concern is ownership cleanup inside `lorevault-core/src/main/java/com/lorevault/api/support/**`: the package is now comparatively homogeneous, but many request/response DTOs are still feature-shaped contracts rather than clearly minimal shared contracts.
- ⏳ A later Stage 3 pass should reassess `lorevault-core/src/main/java/com/lorevault/api/search/**`, where orchestration, DTOs, policies, and infrastructure concerns still appear comparatively flat even though `entityextraction` already exists as a focused subarea.
- ⏳ Selective web cleanup should remain in scope only for mixed adapter zones, not as a broad `web` reshuffle. `lorevault-web/src/main/java/com/lorevault/api/web/ui/**` and `.../web/command/ingestion/**` are already meaningfully structured and should be preserved unless new growth creates sharper seams.
- ⏳ `lorevault-core/src/main/java/com/lorevault/api/ai/**`, `.../content/**`, and `.../timeline/**` remain valid future browsability candidates, but they are lower-priority than `support`, `ingestion`, and `search` unless active work exposes clearer ownership or navigation pain.
- ⏳ The transitional web test tree under `lorevault-web/src/test/java/com/lorevault/api/api/**` should be normalized if Stage 3 continues, so test package layout matches the post-split module structure more clearly.
- ⏳ Non-blocking cleanup still exists outside the reactor boundary: stale `lorevault-api` references remain in IDE metadata and local artifacts (for example `.idea/**`, `logs/lorevault-api.log`, and the legacy `lorevault-api/` directory contents). Those are cleanup follow-ups, not architectural blockers.

#### Remaining staged work from the current repo state

1. Re-audit `support` DTO ownership so the package trends toward a minimal shared-contract area rather than a long-term catch-all DTO bucket.
2. Perform Stage 3 pass 2 inside `ingestion` by separating mixed responsibilities into stable subpackages without changing runtime semantics.
3. Re-evaluate `search` for an equivalent internal split once the intended ownership of DTOs, orchestration, and infrastructure concerns is clearer.
4. Keep selective web cleanup in scope only where feature ownership is still blurred; preserve the existing `web.command`, `web.query`, and `web.ui` structure where it is already working well.
5. Normalize the post-split test tree so `lorevault-web` tests no longer look like a transitional mirror of the old module layout.
6. Revisit `ai`, `content`, and `timeline` only if future growth or active feature work reveals clearer browsability seams worth codifying.
7. Optionally clean stale legacy `lorevault-api` metadata/artifacts once they are no longer useful for local archaeology.

## Candidate Work Sequence

1. Audit and classify shared contract and support types by ownership risk.
2. Perform only the smallest package or type moves that clarify future module boundaries.
3. Split modules around the now-clearer `web` / `core` seam.
4. Revisit larger internal package cleanup within the resulting modules, starting with the highest-friction mixed feature areas and preserving already-cohesive web substructure.

## Open Questions

- Which packages should remain intentionally flat even after review?
- Should feature-specific request and response models move out of `support` and live closer to their owning feature or web area?
- How much repository naming standardization is desirable before package moves begin?
- Should search extraction concerns remain under `search` or become a more explicitly named subarea?
- Should the eventual work be done feature by feature, or should a repo-wide package convention be codified first in a brainstorm or rules doc?
- Which existing support types are truly shareable contracts versus feature-owned DTOs that should move closer to their owning area?
- Should the eventual `api` module exist at all, or should shared contract extraction stop at a narrower set of DTOs?
- How much of `ingestion` should be reorganized before module extraction versus only after `core` exists as a stable home?

## Code Organization Guidance

The durable code-organization rules for package vocabulary, dependency direction, type ownership, DTO placement, repository naming, stage exit criteria, and anti-patterns now live in:

- `../rules/code-organization-guidance.md`

Use that rules doc as the source of truth when placing new code and when deciding what to move or split during staged package cleanup.

## Success Criteria

- There is a clear, bounded proposal for how internal package structure should evolve while preserving the top-level feature split.
- There is a clear staged plan for sequencing package cleanup relative to later module extraction.
- Future reorganization work can identify high-value target packages and a sensible sequencing strategy.
- Contributors can apply a small consistent set of package semantics when adding new code.
- Shared packages such as `support` have a narrower and more intentional meaning.
- Large mixed-responsibility packages have an agreed direction for being split into more legible subareas.
- The repository avoids broad pre-split cleanup that would likely be repeated during module extraction.

## Links

- Related rules: `../rules/code-organization-guidance.md`
- Related rules: `../rules/development-workflow.md`
- Related planning index: `README.md`
- Relevant source root: `../../lorevault-api/src/main/java/com/lorevault/api`
