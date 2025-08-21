# LoreVault Release Notes

## v0.8.1 (Unreleased)

Breaking change: Ask API citation structure simplified

- Citations in Ask responses now expose publication context only under `coordinates`:
  - coordinates.universe, coordinates.series, coordinates.bookTitle, coordinates.chapterTitle, coordinates.bookNumber, coordinates.chapterNumber

## Unreleased

### Fixes

- Neo4j chapterTitle compatibility: some historical Chapter nodes stored the property as `chaptertitle` (lowercase 't'). The persistence model now maps this legacy property and falls back to it when the canonical `chapterTitle` is absent. No API schema changes; responses will correctly include `coordinates.chapterTitle` and `chapterTitle` where applicable.
- Removed redundant flat fields from citation items: `chapterId`, `bookNumber`, `chapterNumber`.

Migration guidance

- Clients should read publication context from `citations[i].coordinates.*`.
- Remove references to `citations[i].chapterId`, `citations[i].bookNumber`, `citations[i].chapterNumber`.
- No other fields changed; `chunkId`, `snippet`, and `score` remain.

Compatibility

- This change is not backward compatible for clients relying on the removed flat fields. Consider pinning to v0.8.0 collections/specs or updating clients accordingly.

## v0.8.0 (2025-08-21)

Release tag: v0.8.0

Highlights

- Testing rewrite (phase 1): stabilized unit tests, pragmatic defaults, opt-in heavy checks
- RAG/Query: AskController validation and in-memory semantic search adapter improvements
- Health checks: Embedding/LLM health services with retry metrics
- Versioning tooling: versions-maven-plugin integration, SCM metadata, RELEASING.md

Changes

- Build: Java 21, Spring Boot 3.5.4, surefire 3.1.2
- Tests: 98 tests green by default; expanded profiles for integration/architecture
- Prompt management: Preload scene-detection and RAG prompts via PromptLoaderService
- Neo4j: Testcontainers-based tests, repositories wired; internal ID deprecation warning remains

Breaking/Behavioral notes

- Some previously flaky tests are excluded by default; see module surefire config and RELEASING.md
- Validation constraints: AskRequest (topK<=10, threshold<=1.0, question not blank)

Known issues / caveats

- In-memory vector search; filters not enforced at source (consider Neo4j vector adapter)
- Scene detection XML parsing is strict—malformed responses are rejected; retry pipeline assists
- Some docs still reference older versions in historical context; functional docs are current

Upgrade guide

- Update to tag v0.8.0
- Ensure Java 21 toolchain
- If running integration tests: configure Docker and optional Testcontainers reuse (~/.testcontainers.properties)

Thanks to everyone involved in the testing rewrite and API hardening.
