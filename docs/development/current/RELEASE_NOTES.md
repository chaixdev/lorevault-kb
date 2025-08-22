# LoreVault Release Notes

## v0.8.1 (2025-08-21)

Release tag: v0.8.1

Highlights

- **BookTitle/ChapterTitle Mapping Fix**: Resolved critical bug where bookTitle was missing from API responses
- **Ingestion Endpoint Refactor**: Enhanced parameter consistency and validation
- **Comprehensive Test Coverage**: Added dedicated tests for Neo4j mapping logic
- **Breaking Change**: Ask API citation structure simplified for consistency

### Major Fixes

- **Fixed Neo4jMapper bookTitle bug**: Corrected mapping from `publicationCoordinates.title` to proper `bookTitle` field
- **Fixed Neo4jMapper chapterTitle bug**: Resolved incorrect field mapping that caused missing chapterTitle in responses
- **Legacy chapterTitle compatibility**: Added fallback mapping for historical Chapter nodes with lowercase `chaptertitle` property

### Ingestion Endpoint Improvements

- **Parameter standardization**: Ingestion endpoint now accepts `bookTitle` and `chapterTitle` as separate, explicit parameters
- **Enhanced validation**: Improved request validation and parameter consistency across the ingestion pipeline
- **Updated CoordinatesBuilder**: Refactored to handle the new parameter structure cleanly

### Breaking Changes

- **Ask API citation structure**: Citations now expose publication context only under `coordinates`:
  - `coordinates.universe`, `coordinates.series`, `coordinates.bookTitle`, `coordinates.chapterTitle`, `coordinates.bookNumber`, `coordinates.chapterNumber`
- **Ingestion API**: Now requires explicit `bookTitle` and `chapterTitle` parameters instead of inferring from other sources
- **Removed redundant citation fields**: `chapterId`, `bookNumber`, `chapterNumber` removed from citation items

### Test Coverage Improvements

- Added `Neo4jMapperBookTitleTest` and `Neo4jMapperChapterTitleTest` for comprehensive mapping validation
- Updated all ingestion controller tests to use new parameter structure
- All tests passing (98 tests green)

### Migration Guidance

- **API Clients**: Read publication context from `citations[i].coordinates.*` instead of flat fields
- **Ingestion Clients**: Update requests to send explicit `bookTitle` and `chapterTitle` parameters
- **Legacy Data**: No migration required - legacy chapterTitle mapping handled automatically

### Compatibility

- Not backward compatible for clients relying on removed flat citation fields
- Not backward compatible for ingestion clients using old parameter structure
- Consider pinning to v0.8.0 collections/specs if immediate migration is not feasible

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
