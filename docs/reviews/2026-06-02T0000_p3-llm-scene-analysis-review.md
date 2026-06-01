# P3: LLM Integration & Scene Analysis — Deep Code Quality Review

**Reviewed:** June 2, 2026  
**Branch:** `feature/durable-ingestion-orchestration`  
**Scope:** ~26 source files (4,244 lines) across scene detection, triad analysis, LLM infrastructure, and prompt management  
**Methodology:** 5-track parallel oracle analysis (Logic & Correctness, Data & Persistence, Async & Events, Security & Observability, Structure & Quality)

---

## 1. Summary

This package is the AI-driven core of the ingestion pipeline — scene segmentation, coordinate localization, triad-based relationship analysis, and the LLM client infrastructure that powers them. The code is functionally correct (per UAT) and generally well-structured with robust retry logic and thoughtful error classification.

However, the review uncovered **1 CRITICAL defect** (NPE in relation-claim normalization when LLM returns partial structured output) and **6 HIGH-severity issues** spanning content leakage in logs, retry-temperature bugs, cross-chapter text corruption, thread-blocking in executor pools, and Lombok/Neo4j incompatibility on two entity classes. These must be addressed before merging to `main`.

The most pervasive theme is **content leakage**: chapter-derived text (copyrighted literary works) is logged verbatim at DEBUG/TRACE levels and embedded in exception messages stored in the graph database. While functionally harmless, this represents a data handling compliance risk.

**Verdict:** 🔁 **Request Changes** — must fix CRITICAL and HIGH items before merging

---

## 2. Findings

### 🔴 CRITICAL

#### CRIT-1 — NPE in relation-claim normalization when LLM returns claims with null sub-records

**Severity:** 🔴 CRITICAL  
**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/triad/SceneRelationshipAnalysisService.java`, lines 504–518  
**Track:** A — Logic & Correctness

**Problem:** `normalizeRelationClaims()` filters out null `TriadRelationClaimExtraction` objects (line 503), but does not guard against null `subject()`, `relationType()`, or `object()` fields *within* non-null claims. Spring AI maps missing JSON keys from the LLM response to `null` in Java records. When any sub-record is null, dereferencing `.name()`, `.entityType()`, `.alias()`, or `.description()` throws an uncaught NPE, crashing the entire triad analysis pipeline for that chapter. This is not theoretical — LLMs frequently omit optional fields under certain temperature/prompt combinations.

**Fix:** Add a filter predicate that rejects claims with any null required sub-record:
```java
.filter(claim -> claim != null 
    && claim.subject() != null 
    && claim.relationType() != null 
    && claim.object() != null)
```

>! agreed, fix accepted

---

### 🟠 HIGH

#### HIGH-1 — Fixed temperature on Spring RetryTemplate retries in structured triad calls

**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/ai/llm/LlmClient.java`, lines 325–349  
**Track:** A — Logic & Correctness

**Problem:** `executeSceneDetectionStructuredCall()` builds `OpenAiChatOptions` with a fixed `temperature` outside the retry lambda (line 329). Inside the lambda, the same immutable `options` object is reused for every retry attempt. This means Spring RetryTemplate retries use identical temperature, defeating the purpose of LLM retries where increasing temperature helps escape local minima in the model's output space. Contrast with `executeSceneDetectionCall()` (lines 248–254) which correctly recomputes `attemptTemp = temperature + (retryCount * 0.1)` inside the lambda.

The service-level retry in `SceneRelationshipAnalysisService.analyzeTriadWithSemanticRetry()` does adjust temperature across attempts, but Spring RetryTemplate retries *within* a single service attempt are wasted.

**Fix:** Move `OpenAiChatOptions` construction inside the retry lambda, computing `double attemptTemp = temperature + (retryContext.getRetryCount() * 0.1)` — matching the pattern in `executeSceneDetectionCall()`.
>! the temperature increase should be designed for consistently, service level retry is still warranted or not? can we simplify while keeping the progressive temperature increase?

---

#### HIGH-2 — Cross-chapter scene text extraction uses wrong chapter's text

**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/triad/SceneRelationshipAnalysisService.java`, lines 771–782 (called from lines 757–758)  
**Track:** A — Logic & Correctness

**Problem:** `buildUserVars()` calls `extractSceneText(chapter, triad.previous())` and `extractSceneText(chapter, triad.next())`, passing the *current* `chapter` object. However, `TriadBuilderService.buildTriad()` resolves prev/next via `NEXT_IN_READING_ORDER` graph edges, which can cross chapter boundaries. When `triad.previous()` belongs to a different chapter, `extractSceneText()` reads from the current chapter's `rawText` using the previous scene's *character offsets* — which are valid only for the previous scene's own chapter text. This either returns garbage text (if offsets happen to land in-bounds for the wrong chapter) or silently returns an empty string (line 777: bounds check fails). The LLM receives corrupted or missing context for cross-chapter boundary triads, silently degrading relationship analysis quality.

**Fix:** Before extracting text from a scene, verify the scene belongs to the current chapter. If not, fall back to the scene's own `text` field (populated during persistence) or skip the text for that triad position, logging a debug message that cross-chapter text extraction was suppressed.
>! if accurate, this is in fact 'crit' grade bug. but i'm skeptical. i need this bug existence validated before proceeding to fix. 

---

#### HIGH-3 — Thread.sleep() in retry loop blocks single-threaded executor

**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/scene/SceneDetectionService.java`, line 140  
**Track:** C — Async & Events

**Problem:** `detectScenesWithRetry()` uses `Thread.sleep(delay + jitter)` inside a synchronous `for`-loop for exponential backoff between semantic retry attempts. This method is called from `SceneDetectionHandler.execute()`, which runs on the `sceneDetectionTaskExecutor` — a single-threaded executor (`corePoolSize=1, maxPoolSize=1`). While `Thread.sleep` is active (up to ~1.4s cumulative across attempts), the only thread in the pool is blocked and unable to process any other work. The delays compound with LLM call latency (30–120s per attempt). Although current product constraints (no concurrent uploads) mask the problem, this is a resource-wasting anti-pattern that blocks the executor for no benefit — the delay can be scheduled asynchronously.

**Fix:** Replace the manual retry loop with a `ScheduledExecutorService.schedule()` to reschedule attempts, or delegate entirely to Spring Retry's `RetryTemplate` with a `FixedBackOffPolicy` configured in `LlmClient`. Either way, remove `Thread.sleep()` from the executor path.
>! agreed, this needs a more robust, "adult in the room" handling. 

---

#### HIGH-4 — Chapter-derived content leaked in application logs at multiple levels

**Severity:** 🟠 HIGH  
**Files:**  
- `SceneProcessingService.java`, line 201 — raw LLM XML response (containing scene anchors/quotes from chapter) logged at DEBUG  
- `SceneProcessingService.java`, line 183 — cleaned XML response logged at TRACE  
- `LlmClient.java`, line 280 — full raw LLM response logged at TRACE with explicit `System.lineSeparator()` formatting  
**Track:** D — Security & Observability (3 findings merged — same root cause)

**Problem:** Multiple `log.debug()` and `log.trace()` statements serialize LLM responses containing verbatim chapter text fragments (start_anchor, context_summary). If DEBUG or TRACE logging is enabled in production (common during incident response), copyrighted literary content is dumped into log files that may be less protected than the primary data store. These logs may be aggregated to centralized logging systems (Datadog, Splunk, ELK) with broader access controls.

**Data-flow trace (representative):**
1. **Source:** `chapterText` (author's copyrighted narrative) → sent to LLM
2. **Flow:** LLM returns XML containing scene anchors and summaries derived from chapter text → `xmlResponse`
3. **Sink:** `log.debug("Raw response was: {}", xmlResponse)` at `SceneProcessingService.java:201`
4. **Impact:** Copyrighted literary content in log files with different access controls than the primary data store

**Fix:** Replace all three log statements:
- `SceneProcessingService.java:201` — log `xmlResponse.length()` and a SHA-256 prefix for deduplication: `log.debug("Raw response: {} chars, sha256={}", xmlResponse.length(), sha256Prefix(xmlResponse))`
- `SceneProcessingService.java:183` — remove entirely; replace with `log.trace("Cleaned XML: {} chars", cleanXml.length())`
- `LlmClient.java:280` — remove entirely; the preview at line 281 already provides the first 400 chars at DEBUG — reduce that to TRACE level

>! logging can truncate verbatim fragments, but provenance and auditing nodes **can not** omit detailed request/responses.

---

#### HIGH-5 — Chapter text fragments embedded in exception messages and IngestionFailure records

**Severity:** 🟠 HIGH  
**File:** `lorevault-core/src/main/java/com/lorevault/api/orchestration/scene/SceneProcessingService.java`, lines 314–336  
**Track:** D — Security & Observability

**Problem:** `sceneAnchorMismatch()` and `sceneLocalizationFailure()` embed the LLM-output `startAnchor` (up to 160 characters of verbatim chapter text) into `IngestionFailure` messages like `"Failed to localize scene %d because start anchor '%s' was not found"`. These messages flow into:
- **Logs** at ERROR level via `SceneDetectionHandler.execute()` line 188: `log.error("...{}", ... e.getMessage())`
- **Graph database** via `IngestionFailure` → `SceneDetectionException` → `IngestionStatus` persistence

This represents the original author's copyrighted work embedded in operational data stores and logs with different access controls than the primary content store.

**Fix:** Three changes:
1. In `anchorPreview()`, reduce max length from 160 to 40 chars (sufficient for diagnostic matching)
2. In `SceneDetectionHandler.execute()` line 188, use `ExceptionSanitizer.sanitizeMessage(e)` instead of `e.getMessage()` (consistent with `StepResult` construction)
3. In `IngestionFailure` records, store a `SHA-256(startAnchor)` as a detail field instead of the raw anchor text, so the failure is deduplicable without exposing content

>! rejected. this is critical app info we need. 

---

#### HIGH-6 — `@Data` on Neo4j entities with `@Relationship` collections causes infinite recursion risk

**Severity:** 🟠 HIGH  
**Files:**  
- `lorevault-core/src/main/java/com/lorevault/api/graph/event/scene/Scene.java`, line 33  
- `lorevault-core/src/main/java/com/lorevault/api/ai/telemetry/LlmCallRecord.java`, line 20  
**Tracks:** B — Data & Persistence + E — Structure & Quality (cross-track, 2 findings merged)

**Problem:** Both `Scene` and `LlmCallRecord` use Lombok `@Data`, which generates `equals()`, `hashCode()`, and `toString()` that traverse all fields — including `@Relationship` collections (`chunks`, `chapter`, `request`, `response`, `job`, `stage`). When these relationships are lazily loaded or form bidirectional references, calling `toString()`, `equals()`, or `hashCode()` triggers infinite recursion or `LazyLoadingException`. This is a well-known Spring Data Neo4j + Lombok incompatibility. Additionally, `@Data` generates public setters for identity fields (`id`, `chapterId`, `sceneIndex`), making them mutable post-persistence.

**Fix (both files):** Replace `@Data` with `@Getter @Setter` and exclude relationship fields from `@EqualsAndHashCode` and `@ToString`:
```java
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Scene {
    @EqualsAndHashCode.Include @ToString.Include private UUID id;
    @EqualsAndHashCode.Include @ToString.Include private UUID chapterId;
    // ... other identity fields
    // @Relationship fields are NOT included — no recursion
}
```
At minimum, add `@ToString.Exclude` and `@EqualsAndHashCode.Exclude` on all `@Relationship` fields.
>! agreed. all use of @Data on SDN entities shuold be replaced with appropriate annotations.

---

### 🟡 MEDIUM

#### MED-1 — `isKnownRetryableMessage` doesn't recognize empty LLM responses as retryable

**Severity:** 🟡 MEDIUM  
**File:** `SceneDetectionService.java`, lines 247–257  
**Track:** A — Logic & Correctness

**Problem:** `isKnownRetryableMessage` checks for scene-coordinate and segmentation-specific substrings, but does not include `"empty response"` — the message used when `LlmClient` returns an empty response (line 273). This causes the service-level 4-attempt retry loop to break immediately on transient LLM empty responses, relying solely on the handler-level retry (`SceneDetectionHandler.isRetryableError` line 258, which *does* check for `"empty response"`). The inconsistency means the service's retry loop is useless for this failure mode.

**Fix:** Add `|| lowerMessage.contains("empty response")` to `isKnownRetryableMessage`. Alternatively, extract retryable-message logic into a shared utility used by both `SceneDetectionService` and `SceneDetectionHandler`.
>! 
---

#### MED-2 — Default `execute` method passes null `stageId`, causing provenance data loss

**Severity:** 🟡 MEDIUM  
**File:** `SceneDetectionOperation.java`, lines 29–31  
**Track:** A — Logic & Correctness

**Problem:** The default method `execute(UUID jobId, UUID chapterId)` constructs `StageExecutionContext` with `null` for both `callingStageId` and `stageId`. This null propagates to `SceneProcessingService.persistDetectedScenes()` (line 155: `scene.setStageId(ctx.stageId())` sets null on every scene) and to `TriadTemporalEdgeRequestFactory.buildRequests()`. In `GraphTriadAnalysisArtifactLookup.findLatestTriadCallRecord()`, a null `stageId` causes early return of `Optional.empty()`, so temporal edges lose their `llmCallId` provenance — breaking traceability to which LLM call produced each relationship.

**Fix:** Either require callers to provide a `stageId`, or deprecate the convenience default method. No caller currently invokes the 2-param overload, so removing it is the simplest fix.

---

#### MED-3 — Scene-Chapter duplicate detection is non-atomic (TOCTOU race)

**Severity:** 🟡 MEDIUM  
**File:** `SceneProcessingService.java`, lines 93–96, 156  
**Track:** B — Data & Persistence

**Problem:** `persistDetectedScenes()` checks `sceneRepo.findByChapterId(chapterId).isEmpty()` (line 93) to skip persistence, then executes `sceneRepo.saveAll(toSave)` (line 156). Between the check and the save, a concurrent execution for the same chapter could also pass the emptiness check and create duplicate Scene nodes. The `linkSceneToChapter` MERGE (line 159) is idempotent for the relationship but does not prevent duplicate nodes.

**Fix:** Add a unique constraint on `(:Scene {chapterId, sceneIndex})` in Neo4j, or use a `MERGE`-based persistence that matches on `{chapterId, sceneIndex}`. Alternatively, synchronize on `chapterId` if concurrent ingestion of the same chapter is an expected scenario.

---

#### MED-4 — `llmCallRecordId` provenance stored inconsistently across temporal edge types

**Severity:** 🟡 MEDIUM  
**File:** `SceneTemporalRelationshipPersistenceService.java` (adjacent to P3 scope)  
**Track:** B — Data & Persistence

**Problem:** When persisting `TEMPORAL` edges, `llmCallRecordId` is embedded in a free-text `rationale` string property. When persisting `AMBIGUOUS_RELATION` edges, the same provenance ID is stored in a dedicated `r.llmCallRecordId` property. This means querying "find all edges derived from LLM call X" requires different query patterns for different edge types — string parsing for TEMPORAL edges, simple property match for AMBIGUOUS_RELATION edges.

**Fix:** Add `llmCallRecordId` as a dedicated property on the `TEMPORAL` edge, mirroring the `AMBIGUOUS_RELATION` pattern.

---

#### MED-5 — `LlmRetryStrategy` is dead code with Thread.sleep() anti-pattern

**Severity:** 🟡 MEDIUM  
**File:** `LlmRetryStrategy.java`  
**Tracks:** C — Async & Events + E — Structure & Quality (cross-track)

**Problem:** `LlmRetryStrategy` is declared as a field in `SceneDetectionService` (line 31) but `llmRetryStrategy.` is never invoked anywhere. The actual retry logic lives in two other places: `SceneDetectionService.detectScenesWithRetry()` (hand-rolled loop) and `LlmClient` (Spring `RetryTemplate`). The class also contains `Thread.sleep()` in `waitWithJitter()` — a blocking anti-pattern if this code were ever activated. Additionally, a `static final Random` field uses `java.util.Random` instead of `ThreadLocalRandom`, creating potential contention if shared across threads.

**Fix:** Delete `LlmRetryStrategy.java` and remove the import and unused field from `SceneDetectionService.java`. The retry capability already exists in `LlmClient` via Spring Retry.

---

#### MED-6 — System prompt logged verbatim at TRACE exposes proprietary prompt engineering

**Severity:** 🟡 MEDIUM  
**File:** `LlmClient.java`, line 241  
**Track:** D — Security & Observability

**Problem:** `log.trace("[LLM] System prompt ({} chars): {}", systemPrompt.length(), systemPrompt)` logs the full system prompt text. System prompts are proprietary prompt engineering — the system prompt IS the intellectual property of the prompt design. If logs are sent to centralized logging services with broader access, competitors could reproduce exact prompt templates.

**Fix:** Log only `systemPrompt.length()` and a SHA-256 hash for version identification. Never log full prompt text.

---

#### MED-7 — Token counts are heuristic estimates, not actual API-reported usage

**Severity:** 🟡 MEDIUM  
**File:** `LlmClient.java`, lines 425–434  
**Track:** D — Security & Observability

**Problem:** `estimateTokens()` uses `max(ceil(chars/3), ceil(words*1.35))` — a crude heuristic. Spring AI's `ChatResponse` object, accessible via `.call().chatResponse()`, carries actual token usage metadata (`promptTokens`, `generationTokens`) from the provider's API response. This metadata is discarded. `LlmCallRecord` stores `tokensEstimated = Boolean.TRUE` as a mitigation flag, but the accuracy gap can be 30%+, degrading cost tracking, capacity planning, and abuse detection.

**Fix:** Capture `ChatResponse` via `.call().chatResponse()` and extract `chatResponse.getMetadata().getUsage().getPromptTokens()` and `getGenerationTokens()`. Pass these to `persistLlmCallSafely()` and set `tokensEstimated = false` when actual counts are available.

---

#### MED-8 — `llmCallId` generated post-call — no audit trail during LLM invocation

**Severity:** 🟡 MEDIUM  
**File:** `LlmCallLoggingService.java`, line 67  
**Track:** D — Security & Observability

**Problem:** `LlmCallRecord.id` (the audit trail `llmCallId`) is generated inside `logCall()` — called *after* the LLM response is received. This means: (1) log messages emitted during the call cannot reference this ID, (2) if `persistLlmCallSafely()` fails (e.g., Neo4j write timeout), the call has occurred but no `llmCallId` was ever persisted, and (3) pre-call budget checks (`evaluateSegmentationBudget()`) cannot be correlated with post-call records.

**Fix:** Generate `llmCallId` in `LlmClient` *before* making the LLM call. Pass it through to `persistLlmCallSafely()` and into all log statements. Store it in MDC so all log messages during the call carry it automatically.

---

#### MED-9 — Chapter text passed to LLM without structural data/instruction delimiters (prompt injection risk)

**Severity:** 🟡 MEDIUM  
**File:** `lorevault-core/src/main/resources/prompts/chapter-segmentation.txt` (entire prompt)  
**Track:** D — Security & Observability

**Problem:** `LlmClient.detectChapterSegmentation()` passes raw `chapterText` as the entire user message without XML/CDATA wrappers separating "data to analyze" from "instructions." An adversarial chapter text containing text like `"END OF TEXT. IGNORE ALL PREVIOUS INSTRUCTIONS..."` could confuse the model about the data/instruction boundary. The system message provides partial protection, but modern LLMs are known to be susceptible to indirect prompt injection through user message content. The `scene-analysis-usertemplate.st` path correctly uses `<text><![CDATA[...]]></text>` wrappers — chapter segmentation should follow the same pattern.

**Fix:** Create a user template (`chapter-segmentation-usertemplate.st`) that wraps chapter text in structural XML:
```xml
<chapter_text><![CDATA[{chapter_text}]]></chapter_text>
```
Render via `PromptTemplate` as is done for scene analysis, rather than passing raw text as the user message.

---

#### MED-10 — Single-implementation interfaces without clear justification

**Severity:** 🟡 MEDIUM  
**Files:** `LlmCallLogger.java`, `TriadAnalysisArtifactLookup.java`, `SceneDetectionOperation.java` (3 files)  
**Track:** E — Structure & Quality

**Problem:** Three interfaces each have exactly one implementation with no strategy-pattern justification, mock variant, or documented planned second implementation:
- `LlmCallLogger` → `LlmCallLoggingService`
- `TriadAnalysisArtifactLookup` → `GraphTriadAnalysisArtifactLookup`
- `SceneDetectionOperation` → `SceneDetectionHandler`

Additionally, `SceneDetectionOperation.execute(UUID, UUID)` contains a dead default method that constructs a `StageExecutionContext` with three `null` values — no caller invokes it.

**Fix:** Delete the interfaces and depend directly on the concrete classes, or add a comment documenting the abstraction's purpose. For `SceneDetectionOperation`, remove the dead default method.

---

#### MED-11 — Mixed injection style in `LlmClient`

**Severity:** 🟡 MEDIUM  
**File:** `LlmClient.java`, line 62  
**Track:** E — Structure & Quality

**Problem:** `LlmClient` uses constructor injection for 8 of its 10 dependencies but uses field injection for `@Value` properties `nlpSmallModelId` and `nlpBigModelId`. Mixing injection styles in the same class reduces consistency and makes the class harder to test (field injection requires a Spring context). The `@Qualifier("llmRetryTemplate")` annotation also lives on a field (line 41) rather than the constructor parameter (line 51).

**Fix:** Move `@Value` properties to constructor parameters, or use `@ConfigurationProperties` for cohesive property groups. Move `@Qualifier` from the field declaration to the constructor parameter.

---

### 🟢 LOW

#### LOW-1 — `toMap` merge function silently discards duplicate scene indices

**Severity:** 🟢 LOW  
**File:** `SceneDetectionHandler.java`, line 140  
**Track:** A — Logic & Correctness

**Problem:** The `Collectors.toMap` merge function `(left, right) -> left` silently discards all but the first Scene for a given `sceneIndex`. If duplicate indices exist, the discarded scene's `eventId` is lost from the `sceneIndexToId` map, and temporal edges involving it are silently dropped. No warning is logged.

**Fix:** Log a warning when a duplicate scene index is encountered.

---

#### LOW-2 — Logged temperature is always initial value, not retry-attempt temperature

**Severity:** 🟢 LOW  
**File:** `LlmClient.java`, lines 296–300  
**Track:** A — Logic & Correctness

**Problem:** `executeSceneDetectionCall()` passes the initial `temperature` parameter to `persistLlmCallSafely()`, but the actual LLM call may have succeeded at a higher temperature from the retry lambda. The logged `LlmCallRecord` records an inaccurate temperature, misleading telemetry.

**Fix:** Capture the actual `attemptTemp` from the successful retry attempt using an atomic reference or mutable holder, then pass it to `persistLlmCallSafely()`.

---

#### LOW-3 — `Scene.chapter` field always null when loaded from the graph

**Severity:** 🟢 LOW  
**File:** `Scene.java`, line 47  
**Track:** B — Data & Persistence

**Problem:** The field `private Chapter chapter` has no `@Relationship` annotation. The actual graph relationship `(c:Chapter)-[:HAS_SCENE]->(s:Scene)` is incoming to Scene. Without `@Relationship(direction = INCOMING)`, Spring Data Neo4j looks for an outgoing relationship FROM Scene TO Chapter — which does not exist. The field always receives `null` during materialization. Current code uses `scene.getChapterId()` instead, so no functional defect exists, but future code expecting `scene.getChapter()` to work would fail silently.

**Fix:** Either annotate with `@Relationship(type = "HAS_SCENE", direction = Relationship.Direction.INCOMING)` or remove the field entirely since `chapterId` suffices.

---

#### LOW-4 — `SceneHasChunk` relationship properties inaccessible via domain model

**Severity:** 🟢 LOW  
**File:** `Scene.java`, lines 110–111  
**Track:** B — Data & Persistence

**Problem:** `Scene` maps chunks as `@Relationship(type = "HAS_CHUNK") private List<Chunk> chunks` — directly to Chunk entities, bypassing the `SceneHasChunk` `@RelationshipProperties` class that carries `chunkIndex`. The `chunkIndex` stored in Neo4j is invisible when traversing `Scene.getChunks()` through the domain model.

**Fix:** Change `Scene.chunks` to `List<SceneHasChunk>` and access Chunk via `.getChunk()`, or remove the unused `SceneHasChunk` class.

---

#### LOW-5 — Unused method overloads in `LlmClient`

**Severity:** 🟢 LOW  
**File:** `LlmClient.java`, line 233  
**Track:** E — Structure & Quality

**Problem:** Two method overloads are never called: `executeSceneDetectionCall(UUID, StageKey, String, String, ChatClient, String)` (4-param without temperature) and `detectChapterSegmentation(UUID, String)` (2-param without temperature). Both merely delegate with default values.

**Fix:** Delete both unused overloads.

---

#### LOW-6 — `serializeStructuredResponse` silently swallows serialization failures

**Severity:** 🟢 LOW  
**File:** `LlmClient.java`, line 436  
**Track:** E — Structure & Quality

**Problem:** When JSON serialization of the structured LLM response fails, the fallback `String.valueOf(response)` produces a non-JSON `ClassName@hashCode` string, which is persisted in the database. No warning is logged.

**Fix:** Upgrade from `log.debug` to `log.warn` when the fallback is triggered.

---

## 3. Priority Action Table

| ID | Severity | File | Description | Must Fix Before Merge? |
|----|----------|------|-------------|------------------------|
| CRIT-1 | 🔴 CRITICAL | `SceneRelationshipAnalysisService.java:504` | NPE in relation-claim normalization from null LLM sub-records | **Yes** |
| HIGH-1 | 🟠 HIGH | `LlmClient.java:325` | Fixed temperature on Spring RetryTemplate retries in structured calls | **Yes** |
| HIGH-2 | 🟠 HIGH | `SceneRelationshipAnalysisService.java:771` | Cross-chapter scene text extraction uses wrong chapter's text | **Yes** |
| HIGH-3 | 🟠 HIGH | `SceneDetectionService.java:140` | Thread.sleep() blocks single-threaded executor during retry | **Yes** |
| HIGH-4 | 🟠 HIGH | `SceneProcessingService.java:183,201` + `LlmClient.java:280` | Chapter-derived content in logs at multiple levels | **Yes** |
| HIGH-5 | 🟠 HIGH | `SceneProcessingService.java:314–336` | Chapter text fragments in exception messages and IngestionFailure records | **Yes** |
| HIGH-6 | 🟠 HIGH | `Scene.java:33` + `LlmCallRecord.java:20` | @Data on Neo4j entities with @Relationship collections | **Yes** |
| MED-1 | 🟡 MEDIUM | `SceneDetectionService.java:247` | Empty LLM responses not recognized as retryable in service-level loop | Recommended |
| MED-2 | 🟡 MEDIUM | `SceneDetectionOperation.java:29` | Default execute method passes null stageId (provenance loss) | Recommended |
| MED-3 | 🟡 MEDIUM | `SceneProcessingService.java:93` | Non-atomic duplicate detection (TOCTOU) | Recommended |
| MED-4 | 🟡 MEDIUM | `SceneTemporalRelationshipPersistenceService.java` | llmCallRecordId stored inconsistently across edge types | Recommended |
| MED-5 | 🟡 MEDIUM | `LlmRetryStrategy.java` | Dead code with Thread.sleep() anti-pattern — delete | Recommended |
| MED-6 | 🟡 MEDIUM | `LlmClient.java:241` | System prompt logged verbatim at TRACE | Recommended |
| MED-7 | 🟡 MEDIUM | `LlmClient.java:425` | Token counts are heuristic estimates, not API-reported | Recommended |
| MED-8 | 🟡 MEDIUM | `LlmCallLoggingService.java:67` | llmCallId generated post-call — incomplete audit trail | Recommended |
| MED-9 | 🟡 MEDIUM | `chapter-segmentation.txt` | Chapter text without structural delimiters (prompt injection) | Recommended |
| MED-10 | 🟡 MEDIUM | 3 interfaces | Single-implementation interfaces without justification | Recommended |
| MED-11 | 🟡 MEDIUM | `LlmClient.java:62` | Mixed @Value field + constructor injection | Recommended |
| LOW-1 | 🟢 LOW | `SceneDetectionHandler.java:140` | Duplicate scene indices silently discarded in merge | No |
| LOW-2 | 🟢 LOW | `LlmClient.java:296` | Logged temperature always initial value, not retry-attempt | No |
| LOW-3 | 🟢 LOW | `Scene.java:47` | chapter field always null when loaded from graph | No |
| LOW-4 | 🟢 LOW | `Scene.java:110` | SceneHasChunk properties inaccessible via domain model | No |
| LOW-5 | 🟢 LOW | `LlmClient.java:233` | Unused method overloads | No |
| LOW-6 | 🟢 LOW | `LlmClient.java:436` | serializeStructuredResponse silently swallows failures | No |

---

## 4. Test Gaps

The following scenarios lack test coverage against the findings identified above:

- ⚠️ **CRIT-1 / HIGH-2:** No test for LLM returning `TriadRelationClaimExtraction` with null `subject()`, `relationType()`, or `object()`. Tests should verify graceful degradation when partial structured output is received.
- ⚠️ **HIGH-1:** No test verifying that Spring RetryTemplate retries within `executeSceneDetectionStructuredCall` use different temperatures per attempt.
- ⚠️ **HIGH-2:** No test for cross-chapter triad boundaries — scenes from different chapters appearing as prev/next in `TriadBuilderService.buildTriad()`. Test should verify text extraction degrades gracefully rather than returning garbage.
- **HIGH-3:** No test verifying the `sceneDetectionTaskExecutor` thread is not blocked during retry delays.
- **HIGH-4 / HIGH-5:** No test or lint rule enforcing that chapter-derived content is not logged. Consider a custom Logback filter or build-time check.
- **HIGH-6:** No test verifying that `Scene.toString()` and `LlmCallRecord.toString()` do not trigger lazy-loading exceptions with hydrated relationship collections.
- **MED-3:** No concurrent test verifying that duplicate scene ingestion of the same chapter does not create duplicate nodes.
- **MED-7:** No test verifying actual API token counts are captured and preferred over heuristic estimates.

---

## 5. Positive Notes

The retry architecture is thoughtfully layered — service-level semantic retry with temperature progression (`SceneDetectionService.detectScenesWithRetry`) sits above infrastructure-level retry (`LlmClient`'s Spring RetryTemplate), and the handler (`SceneDetectionHandler.isRetryableError`) provides a third classification layer. The retryable-error code sets (`SCENE_DETECTION_RETRY_EXHAUSTED`, `TRIAD_RELATION_MISSING`, etc.) are comprehensive and well-organized.

Entity extraction from triad analysis is impressively null-safe — every `normalize*()` method in `SceneRelationshipAnalysisService` guards against null `parsed`, null `currentSceneEntities()`, and null sub-lists, with clean `List.of()` fallbacks. The `ExceptionSanitizer` pattern (CR/LF stripping, length truncation) is a good foundation that should be applied more consistently. The `scene-analysis-usertemplate.st` with CDATA wrappers is the correct pattern for prompt injection defense and should be replicated for chapter segmentation.
