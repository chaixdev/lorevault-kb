# LV-087-1 — Chunking configuration alignment [bugfix][refactor]

Context

- The service `TextChunkingService` reads `@Value("${lorevault.chunking.*}")`, while `application.yml` defines the keys under `lorevault.content.chunking.*`.
- Tests mask this mismatch via `@TestPropertySource` overrides, but defaults in runtime may be ignored.
- Related spec: ../../current/processes/text-chunking-specification.md

Problem

- Misaligned property prefix causes the default chunking behavior to diverge between test and runtime.
- Risk of unexpected single-chunk vs multi-chunk decisions and overlap sizing.

Proposal

- Introduce a dedicated `@ConfigurationProperties(prefix = "lorevault.content.chunking")` class (e.g., `ChunkingProperties`).
- Refactor `TextChunkingService` to receive the properties via constructor injection (no `@Value`).
- Update `application.yml` to remain the single source of truth (no duplicate keys).
- Update tests to bind the same prefix; avoid full `@SpringBootTest` when possible.

Scope

- Add `ChunkingProperties` with fields: decisionThreshold, targetSize, overlapPercentage, minChunkSize, maxChunkSize.
- Refactor `TextChunkingService` to use the properties class.
- Update `TextChunkingServiceConfigurationTest` and `TextChunkingServiceTest` to use the new binding.
- Adjust docs to reflect the canonical key path `lorevault.content.chunking.*`.

Out of scope

- Algorithm changes to chunking logic.
- Public API changes.

Technical notes

- Consider a temporary compatibility shim that also binds legacy `lorevault.chunking.*` if present; log a deprecation warning once.
- Keep constructor injection to eliminate `ReflectionTestUtils` and reduce heavy Spring context needs in tests.

Acceptance criteria

- [ ] `TextChunkingService` no longer uses `@Value`; it consumes `ChunkingProperties` via constructor.
- [ ] Tests use `lorevault.content.chunking.*` and pass without `System.out` prints.
- [ ] Default runtime behavior matches test expectations (decision threshold, target size, overlap, min/max).
- [ ] Docs mention the correct properties prefix.

Quality gates

- [ ] Build and tests green on JDK 21
- [ ] No new ArchUnit violations
- [ ] Coverage unchanged or improved for chunking service

Links

- Service: ../../../lorevault-api/src/main/java/com/lorevault/api/service/content/TextChunkingService.java
- Config: ../../../lorevault-api/src/main/resources/application.yml
- Spec: ../../current/processes/text-chunking-specification.md
