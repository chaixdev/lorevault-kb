# LoreVault Refactor Session Log

> **Purpose:** Reference document for continuing refactor work across agent sessions.  
> **Last Updated:** 26 December 2025  
> **Current Branch:** `refactor/phase2-cleanup` (ready to merge to main)

---

## Context: Why This Refactor?

The LoreVault codebase became "horribly overengineered" with excessive ports-and-adapters ceremony. The original architecture applied hexagonal patterns everywhere, even where they added no value. Key problems:

1. **Ports for non-boundaries** - `JobContextPort` was just a thread-local wrapper, not a real infrastructure boundary
2. **Confused abstractions** - `SceneDetectionPort` mixed LLM client boundary with business logic (retry, job status, triad orchestration)
3. **Narrow read-only ports** - `ChapterLookupPort` and `EventOrderingPort` were single-method interfaces that should have been part of the main persistence port
4. **Synchronous orchestration** - The ingestion pipeline was a tightly-coupled `IngestionOrchestrationService` calling everything synchronously

### Guiding Principle

> "Ports & adapters truly shines at the edges of real boundaries, like when replacing a data layer is in the cards."

Ports should only exist at **true infrastructure boundaries**:
- Database (might swap Neo4j for another graph DB)
- AI/LLM providers (OpenAI, Anthropic, etc. - though Spring AI already abstracts this)
- Vector search (different embedding stores)

Business logic should be in **regular services**, not port implementations.

---

## Phase 1: Event-Driven Pipeline ✅ COMPLETE

**Branch:** Merged to `main` (commit `40039b3`)  
**Impact:** +1,244 lines (restructuring, not net growth of complexity)

### What Changed

Replaced the monolithic `IngestionOrchestrationService` with an async event-driven pipeline:

```
ChapterPersistedEvent → SceneDetectionHandler
                              ↓
                     ScenesDetectedEvent → EmbeddingHandler
                                                 ↓
                                        ChunksEmbeddedEvent → (complete)
```

### Key Implementation Details

- **Event Classes:** `ChapterPersistedEvent`, `ScenesDetectedEvent`, `ChunksEmbeddedEvent`, `IngestionFailedEvent`
- **Handler Pattern:** Each handler uses `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` + `@Transactional(propagation = REQUIRES_NEW)`
- **Idempotency:** Each handler checks for existing work before processing
- **Error Handling:** Failures emit `IngestionFailedEvent` with `retryable` flag

### Files Created/Modified

- `event/ingestion/*.java` - Event classes
- `handler/*.java` - Pipeline handlers (SceneDetectionHandler, EmbeddingHandler, etc.)
- `config/AsyncConfig.java` - Thread pool configuration
- Deleted: `IngestionOrchestrationService.java`

---

## Phase 2: Remove Port/Adapter Ceremony ✅ COMPLETE

**Branch:** `refactor/phase2-cleanup` (7 commits ahead of main)  
**Impact:** -498 net lines  
**Tests:** 283 passing

### Commits in Order

| SHA | Description | Lines |
|-----|-------------|-------|
| `45d449e` | Remove `JobContextPort` + `ThreadLocalJobContextAdapter` | -106 |
| `e16115d` | Inline `OpenAiSceneDetectionAdapter` into service | -95 |
| `319c1a0` | Remove empty `PortsAdaptersConfiguration` | cleanup |
| `9174de4` | Remove dead code (empty DTO files) | cleanup |
| `d93d3a7` | Remove orphaned `SceneDetectionPortTCK` | cleanup |
| `ab4a490` | **Eliminate `SceneDetectionPort`** → `SceneDetectionService` | -131 |
| `c2278ae` | **Fold narrow ports** into `ContentPersistencePort` | -66 |

### Major Changes Explained

#### 1. SceneDetectionPort → SceneDetectionService

**Problem:** `SceneDetectionPort` was confused - its implementation (`RetryAwareSceneDetectionService`) contained:
- Retry logic with exponential backoff
- Job status updates
- Triad orchestration (Pass 2 analysis)
- Scene persistence coordination

None of this is "LLM boundary" - it's business logic.

**Solution:**
- Deleted `SceneDetectionPort.java` interface
- Deleted `SceneDetectionException.java`
- Deleted `FakeSceneDetectionPort.java` test fake
- Renamed `RetryAwareSceneDetectionService` → `SceneDetectionService`
- Moved from `service/content/retry/` to `service/content/`
- Removed `implements SceneDetectionPort`
- Handler now injects `SceneDetectionService` directly

The actual LLM call happens through `SceneDetectionClient`, which doesn't need a port because Spring AI already provides provider abstraction.

#### 2. Fold Narrow Ports into ContentPersistencePort

**Problem:** `ChapterLookupPort` and `EventOrderingPort` were single-purpose read interfaces:
```java
// ChapterLookupPort - one method
List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber);

// EventOrderingPort - three methods, all reads
List<Scene> findChapterScenes(UUID chapterId);
List<SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId);
List<UUID> findBookChapterIdsUpTo(UUID bookId, int uptoChapterNumber);
```

**Solution:**
- Added methods to `ContentPersistencePort`
- Implemented in `Neo4jContentPersistenceAdapter`
- Updated `TriadBuilderService` and `EventOrderingService` to use `ContentPersistencePort`
- Deleted the narrow port interfaces and their adapters

### Ports Intentionally Kept

| Port | Justification |
|------|---------------|
| `ContentPersistencePort` | True database boundary - might swap Neo4j |
| `EmbeddingPort` | Vector embedding provider abstraction |
| `SemanticSearchPort` | Vector search provider abstraction |
| `TemporalEdgePort` | Clean separation for graph edge operations |
| `PromptRepositoryPort` | Cacheable prompt template loading |

---

## Phase 3: Handler Consolidation ⏳ PENDING

### Goal
Reduce handler proliferation. The current pipeline has separate handlers for each stage, but some stages are trivially sequential and could be combined.

### Candidates for Evaluation

1. **ChapterPersistenceHandler + SceneDetectionHandler** - Always sequential, could be one handler
2. **Consider removing ContentPersistenceHandler** - It just saves the chapter and emits an event

### Approach
- Profile actual usage patterns
- Consider whether the async boundaries provide value or just add complexity
- Maintain idempotency guarantees

---

## Phase 4: Test Cleanup ⏳ PENDING

### Goal
Clean up test infrastructure after port removals.

### Tasks
- [ ] Audit `FakeContentPersistencePort` - ensure new methods have reasonable stubs
- [ ] Remove any orphaned test utilities
- [ ] Consolidate test configuration classes
- [ ] Review `TestConfig.java` for outdated mocks

---

## Quick Reference: Current Architecture

### Package Structure (Post-Phase 2)

```
com.lorevault.api/
├── application/
│   └── port/                    # Infrastructure boundaries only
│       ├── ContentPersistencePort.java    # DB operations
│       ├── EmbeddingPort.java             # Vector embeddings
│       ├── SemanticSearchPort.java        # Vector search
│       ├── TemporalEdgePort.java          # Graph edges
│       └── PromptRepositoryPort.java      # Prompt templates
├── domain/                      # Pure domain model
├── dto/                         # Data transfer objects
├── event/ingestion/             # Pipeline events
├── handler/                     # Event handlers (pipeline stages)
├── infrastructure/              # Port implementations
│   ├── ai/                      # AI clients (not ports)
│   └── persistence/neo4j/       # Neo4j adapters
├── service/                     # Business logic services
│   ├── content/
│   │   ├── SceneDetectionService.java     # AI scene detection (was port impl)
│   │   ├── SceneDetectionClient.java      # Raw LLM calls
│   │   └── ...
│   └── ...
└── web/                         # REST controllers
```

### Key Classes

| Class | Role |
|-------|------|
| `SceneDetectionService` | Orchestrates scene detection with retry, status updates, triad analysis |
| `SceneDetectionClient` | Raw LLM API calls for scene detection prompts |
| `SceneDetectionHandler` | Pipeline stage that triggers detection on `ChapterPersistedEvent` |
| `ContentPersistencePort` | Main database port (chapters, scenes, chunks, jobs, hierarchy) |
| `Neo4jContentPersistenceAdapter` | Neo4j implementation of persistence port |

---

## How to Continue

### Merge Phase 2
```bash
git checkout main
git merge refactor/phase2-cleanup
git push
```

### Start Phase 3
```bash
git checkout -b refactor/phase3-handlers
# Analyze handler boundaries
# Consider merging sequential handlers
```

### Verify Everything Works
```bash
cd lorevault-kb
mvn test -pl lorevault-api
# Should see: Tests run: 283, Failures: 0, Errors: 0
```

---

## Metrics Summary

| Metric | Before Refactor | After Phase 1 | After Phase 2 |
|--------|-----------------|---------------|---------------|
| Net Lines | baseline | +1,244 | +746 |
| Port Interfaces | ~10 | ~10 | 5 |
| Port Adapters | ~12 | ~10 | 6 |
| Test Count | ~280 | 283 | 283 |

The Phase 1 increase was structural (adding event infrastructure). Phase 2 removed ceremony (-498 lines), bringing net change to +746 from baseline. Further phases should reduce this further.

---

## Files Deleted in Phase 2

```
# Port interfaces
application/port/JobContextPort.java
application/port/SceneDetectionPort.java
application/port/SceneDetectionException.java
application/port/ChapterLookupPort.java
application/port/EventOrderingPort.java

# Adapters
infrastructure/adapter/ThreadLocalJobContextAdapter.java
infrastructure/ai/openai/OpenAiSceneDetectionAdapter.java
infrastructure/persistence/neo4j/adapter/Neo4jChapterLookupAdapter.java
infrastructure/persistence/neo4j/adapter/Neo4jEventOrderingAdapter.java
infrastructure/config/PortsAdaptersConfiguration.java

# Test files
tck/ai/SceneDetectionPortTCK.java
infrastructure/ai/openai/OpenAiSceneDetectionAdapterTckTest.java
testutil/fakes/FakeSceneDetectionPort.java

# Service moved/renamed
service/content/retry/RetryAwareSceneDetectionService.java → service/content/SceneDetectionService.java
```

---

## Contact / Questions

This refactor follows the principle: **simplify aggressively, keep ports only at real infrastructure boundaries**. When in doubt, ask: "Would I ever swap this implementation?" If no, it shouldn't be a port.
