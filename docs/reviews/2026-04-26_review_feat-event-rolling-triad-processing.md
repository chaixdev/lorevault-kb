## Section 1 — Summary

This branch correctly moves chapter-event identity away from lexical buckets and adds the missing Stage 2/Stage 3 event branch, but the implementation is not merge-ready yet. The most serious defects are in the co-reference execution path: Stage 2 holds a Neo4j transaction open across remote LLM calls, and it can silently degrade through repeated LLM failures while still letting Stage 3 and the completion coordinator report success. There are also material graph-correctness issues in SAME_EVENT scoping and persistence that can corrupt component membership or mis-link mentions to the wrong `ChapterEvent`. 🔁 **Request Changes**

## Section 2 — Findings

### [CRIT-1] — External LLM I/O is inside the chapter write transaction
**Severity:** 🔴 CRITICAL
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java`, line 55
**Problem:** `runCorefPass(...)` is `@Transactional`, but it deletes and rebuilds SAME_EVENT links while also making remote LLM calls inside the loop. That keeps the Neo4j transaction open across network latency and retries, expands rollback scope to the whole chapter pass, and risks lock/timeout failures under load.
**Fix:** Split the pass into two phases: collect validated co-reference link intents outside any write transaction, then execute delete + link writes in a short transactional writer method.

### [CRIT-2] — Stage 2 can fail internally and still report a successful pipeline branch
**Severity:** 🔴 CRITICAL
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java`, line 82
**Problem:** per-window exceptions from `llmClient.runEventCoref(...)` are caught and skipped. If the provider is down or every window fails, Stage 2 still returns a normal `CorefPassResult`, Stage 3 still runs, and `ChapterEventResolutionHandler` still publishes `ChapterEventsResolvedEvent`. That converts a real stage failure into silent fragmentation and false-success completion.
**Fix:** Track window failures and throw a typed stage exception when all windows fail or a failure threshold is crossed, so `PipelineStageSupport` emits `IngestionFailedEvent` and terminates the branch.

### [HIGH-1] — SAME_EVENT chapter scoping is not enforced end-to-end
**Severity:** 🟠 HIGH
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java`, line 105
**Problem:** Stage 2 accepts any parseable UUID returned by the model and writes SAME_EVENT links without proving both IDs belong to the current window/chapter. Stage 3 then traverses SAME_EVENT components with `OPTIONAL MATCH (root)-[:SAME_EVENT*0..]-(peer ...)`, which constrains only the endpoints, not every intermediate node in the path. A hallucinated or stale cross-chapter edge can therefore collapse unrelated chapter components into one identity cluster.
**Fix:** Allowlist pair IDs against the current window (or at minimum the current chapter) before writing, reject self-links, and constrain component traversal so every node on the path has `chapterId = $chapterId`.

### [HIGH-2] — Mention-to-ChapterEvent linking relies on non-guaranteed iteration order
**Severity:** 🟠 HIGH
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/resolution/ChapterEventResolutionService.java`, line 109
**Problem:** the code assumes `saveAll(chapterEvents)` returns entities in the same order as input and that this order still matches `componentMap.entrySet()`. Neither is a safe contract here, especially because `componentMap` is created via default `Collectors.groupingBy(...)`. If ordering shifts, mentions are linked to the wrong `ChapterEvent`.
**Fix:** keep an explicit `componentId -> ChapterEvent` mapping and link mentions by component id, not by positional index.

### [HIGH-3] — SAME_EVENT MERGE key includes mutable properties and can create duplicate edges
**Severity:** 🟠 HIGH
**File:** `lorevault-core/src/main/java/com/lorevault/api/content/entities/EventMentionGraphRepository.java`, line 51
**Problem:** `MERGE (a)-[:SAME_EVENT {confidence: $confidence, passId: $passId, model: $model}]->(b)` uses non-identity properties as part of the merge key. The same mention pair can appear in multiple overlapping windows with different confidence values, which creates parallel SAME_EVENT edges for one logical pair and distorts connected-component traversal.
**Fix:** `MERGE` only on endpoints and relationship type, then `SET` metadata on the matched relationship with an explicit update policy.

### [MED-1] — Failed jobs leave orphaned completion state in the fan-in coordinator
**Severity:** 🟡 MEDIUM
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/IngestionCompletionCoordinator.java`, line 148
**Problem:** `completionStates` is removed only on the all-branches-success path. When a required branch fails and never publishes its success event, the `(jobId, chapterId)` state is retained indefinitely. Over time, failed jobs accumulate in coordinator memory.
**Fix:** listen for `IngestionFailedEvent` or another terminal failure signal and remove the matching coordinator state immediately.

### [MED-2] — Event coreference prompt assembly allows structural prompt injection
**Severity:** 🟡 MEDIUM
**File:** `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java`, line 135
**Problem:** **Source:** persisted mention fields such as `displayName`, `normalizedName`, and `evidence`, which originate from chapter-derived content. **Flow:** those values are concatenated directly into XML-like `<mention>` markup in `renderUserInput(...)`. **Sink:** the LLM user prompt passed to `runEventCoref(...)`. **Impact:** crafted content containing tags or instruction-like text can break the intended structure and influence SAME_EVENT judgments, corrupting downstream chapter-event aggregation. **Fix:** escape XML-special characters or switch the payload to strict JSON serialization before prompt rendering.
**Fix:** escape XML-special characters in all mention fields, or render the window as JSON and instruct the model to treat it as inert data.

### [MED-3] — New Stage 2/3 hot path has no EventMention.id schema guarantee
**Severity:** 🟡 MEDIUM
**File:** `lorevault-core/src/main/java/com/lorevault/api/config/Neo4jSchemaInitializer.java`, line 86
**Problem:** Stage 2 and Stage 3 repeatedly match `EventMention` by `id`, but schema initialization adds no `EventMention.id` unique constraint or dedicated index. That weakens lookup performance and leaves the graph without an explicit uniqueness guard on the identifier the new pipeline depends on.
**Fix:** add `CREATE CONSTRAINT ... FOR (m:EventMention) REQUIRE m.id IS UNIQUE` in `Neo4jSchemaInitializer`.

## Section 3 — Priority Action Table

| ID | Severity | File | Description | Must Fix Before Merge? |
|----|----------|------|-------------|------------------------|
| CRIT-1 | 🔴 CRITICAL | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java` | Stage 2 holds a DB transaction open across remote LLM calls | Yes |
| CRIT-2 | 🔴 CRITICAL | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java` | Window failures are swallowed and branch success can be reported falsely | Yes |
| HIGH-1 | 🟠 HIGH | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java` | SAME_EVENT write/read path does not enforce chapter scope | Yes |
| HIGH-2 | 🟠 HIGH | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/resolution/ChapterEventResolutionService.java` | Mention linking depends on unstable iteration order | Yes |
| HIGH-3 | 🟠 HIGH | `lorevault-core/src/main/java/com/lorevault/api/content/entities/EventMentionGraphRepository.java` | SAME_EVENT `MERGE` key can create duplicate logical edges | Yes |
| MED-1 | 🟡 MEDIUM | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/pipeline/IngestionCompletionCoordinator.java` | Failed branches leave orphaned coordinator state | Recommended |
| MED-2 | 🟡 MEDIUM | `lorevault-core/src/main/java/com/lorevault/api/ingestion/application/coref/EventCoreferenceService.java` | Prompt assembly does not escape untrusted mention data | Recommended |
| MED-3 | 🟡 MEDIUM | `lorevault-core/src/main/java/com/lorevault/api/config/Neo4jSchemaInitializer.java` | No uniqueness/index guarantee for `EventMention.id` | Recommended |

## Section 4 — Test Gaps

- ⚠️ No test proves Stage 2 inference is executed outside the write transaction boundary or that stale SAME_EVENT links are not deleted until a valid write phase begins.
- ⚠️ No test covers the case where all event-coref windows fail and the handler must terminate the branch instead of publishing `ChapterEventsResolvedEvent`.
- No test rejects model-returned UUIDs that are not in the current window/chapter, or guards Stage 3 against cross-chapter SAME_EVENT contamination.
- ⚠️ No test exercises reordered `saveAll(...)` results / nondeterministic component ordering to prove mentions still link to the correct `ChapterEvent`.
- No test verifies that repeated judgments for the same mention pair produce one SAME_EVENT edge rather than parallel relationships with different metadata.
- No test covers coordinator cleanup on `IngestionFailedEvent` for a job that never receives all four success-branch events.
- No test feeds mention data containing XML delimiters such as `</mention>` into `renderUserInput(...)` to verify prompt structure is preserved.
- No schema/integration test asserts that `EventMention.id` has a unique constraint or indexed lookup path.

## Section 5 — Positive Notes

The branch does align the storage model with the ticket intent by removing lexical uniqueness as the source of `ChapterEvent` identity and externalizing the new event-coref prompts instead of hardcoding them. The handler also uses `PipelineStageSupport` and the correct named async executor, which is the right orchestration shape for this pipeline stage.
