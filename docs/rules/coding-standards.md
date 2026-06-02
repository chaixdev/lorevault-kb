# Coding Standards

**Scope:** Java 21 / Spring Boot 3.5 / Spring Data Neo4j / Spring AI stack.

---

## Java 21 Idioms

**Use records for immutable value objects.**
DTOs, event payloads, and result containers are record candidates.
Auto-generated `equals`, `hashCode`, and `toString` are correct for immutable data.

```java
// Good
public record SearchResult(List<String> ids, int totalCount) {}
```

**Use sealed interfaces or classes for closed type hierarchies.**
Pipeline result types, status variants, and typed LLM response wrappers benefit from
sealed hierarchies — the compiler verifies exhaustive handling in switch expressions.

**Use pattern-matching `instanceof`.**
```java
// Good
if (event instanceof FileProcessedEvent e) { process(e.fileId()); }

// Bad
if (event instanceof FileProcessedEvent) {
    process(((FileProcessedEvent) event).fileId());
}
```

**Use switch expressions for exhaustive dispatch over sealed types and enums.**
The compiler will catch unhandled cases at build time.

**Optional discipline.**
- `Optional.get()` without prior `isPresent()` is banned.
  Use `orElseThrow(SpecificException::new)`, `orElse(default)`, `map`, or `ifPresent`.
- Return `Optional<T>` from service/repository boundaries where absence is a valid outcome.
- Do not use `Optional<T>` as a field or parameter type — it is a return-type convention only.

**Use text blocks for multi-line Cypher queries and LLM prompt templates.**
```java
String cypher = """
    MATCH (n:Node {id: $id})
    OPTIONAL MATCH (n)-[:HAS_CHILD]->(c:Child)
    RETURN n, collect(DISTINCT c) AS children
    """;
```

**No null returns on service boundaries.**
Methods that may not find a result return `Optional<T>`.
Void methods in an unrecoverable state throw a typed exception.

---

## Spring Boot 3.5

**`SecurityFilterChain`, not `WebSecurityConfigurerAdapter`.**
`WebSecurityConfigurerAdapter` was removed in Spring Security 6.
Use `@Bean SecurityFilterChain` with lambda `HttpSecurity` configuration.

**Trailing slash matching is disabled by default in Spring 6+.**
`GET /api/status` does not match `GET /api/status/` automatically.
Do not rely on this in tests or client integrations.

**`@Configuration(proxyBeanMethods = false)` for non-cross-referencing configs.**
Avoids CGLib subclassing overhead when `@Bean` methods do not call each other.

**`@ConfigurationProperties` over scattered `@Value` injections.**
A cluster of related `@Value` fields is a signal to extract a `@ConfigurationProperties` record.
When a class uses constructor injection for its dependencies, configuration values must also
flow through the constructor — not via `@Value` on fields. The valid patterns are:

- `@ConfigurationProperties` record injected via constructor (preferred)
- `@Value` on constructor parameters (acceptable for isolated values)

`@Value` on fields alongside constructor-injected dependencies is a style inconsistency.
Using `@Value` on fields also makes the dependency invisible in the constructor signature
and harder to test without a Spring context.

```java
// Wrong — mixed styles: constructor injection + @Value fields
@RequiredArgsConstructor
public class LlmClient {
    private final ChatClient chatClient;  // constructor-injected
    @Value("${model.id}") private String modelId;  // field-injected — inconsistent
}

// Correct — ConfigurationProperties record, constructor-injected
public class LlmClient {
    private final ChatClient chatClient;
    private final LlmClientProperties props;  // constructor-injected record
    public LlmClient(ChatClient chatClient, LlmClientProperties props) { ... }
    // props.modelId() used throughout
}
```

**Prefer slice tests over `@SpringBootTest`.**
- `@WebMvcTest` for the controller layer.
- `@DataNeo4jTest` for the repository layer.
- `@SpringBootTest` only when full-context integration is genuinely needed.

**Actuator exposure.**
Verify `management.endpoints.web.exposure.include` does not expose sensitive endpoints
on the main port in non-dev profiles. Review this whenever a new profile is added.

---

## Spring Data Neo4j

**Multi-row hydration.**
Custom `@Query` methods that return a node with collection-valued relationships can
produce one row per related node; SDN re-hydration from rows may yield duplicates.
Always use `COLLECT(DISTINCT ...)` in custom queries that return nodes with relationships:

```cypher
MATCH (n:Parent {id: $id})
OPTIONAL MATCH (n)-[:HAS_CHILD]->(c:Child)
RETURN n, collect(DISTINCT c) AS children
```

**Parameterised Cypher is mandatory.**
Never compose user input, LLM output, or any dynamic string into a Cypher query.
Always use `@Param` bindings or `Neo4jClient` parameter maps.

**`Neo4jClient` and auto-commit.**
`Neo4jClient` queries called outside a `@Transactional` method run in auto-commit mode.
Wrap in a `@Transactional` service method when transactional semantics are required.

**Relationship direction must match semantic intent.**
The `@Relationship(direction = ...)` in the Java model must match the intended graph
edge direction. Document the intended direction alongside the relationship annotation.

**Index coverage.**
Every property used in a `WHERE` clause on a high-cardinality node must have a Neo4j index.
New `@Query` methods filtering on un-indexed properties must include a corresponding
index declaration.

**`MERGE` key discipline.**
A `MERGE` must use the exact properties that constitute the uniqueness constraint.
Merging on a partial key creates duplicate nodes when other properties differ.

**Atomic idempotency guards.**
An idempotency guard that checks then creates — `if (exists) return; else save()` —
is a TOCTOU defect. The check and the create must be atomic. Acceptable patterns:

- `MERGE` with the full uniqueness key (preferred — single atomic operation)
- Catch a unique-constraint violation from the write (guardrail, not primary strategy)
- A persisted claim record under a unique constraint (multi-node-safe serialization)

```java
// Wrong — check-then-create race between threads
if (sceneRepo.findByChapterId(chapterId).isEmpty()) {
    sceneRepo.saveAll(scenes); // another thread may have inserted between check and save
}

// Correct — MERGE is atomic
MERGE (s:Scene {chapterId: $chapterId, sceneIndex: $sceneIndex})
ON CREATE SET s.id = $id, s.text = $text, ...
```

**Path repetition cardinality bounds.**
Never use unbounded path repetition patterns (e.g., `(m)-[:SAME_EVENT*0..]-(related)`).
All path expressions must have an explicit upper bound. Unbounded traversal can exhaust
Neo4j memory and produce non-deterministic results even on modest datasets. Default to
a bound that matches the realistic maximum path length for the domain (e.g., `*0..10`).

**Projections for partial reads.**
When only a subset of node properties is needed, use an SDN interface projection or
a custom `@QueryResult` record instead of loading the full entity graph.

**Projection return types on `Neo4jRepository`.**
When a `@Query` method on a `Neo4jRepository<Entity, ID>` returns custom columns that
don't match the entity's fields, use a Java record as the return type — not a projection
interface. Spring Data Neo4j's `DirectFieldAccessFallbackBeanWrapper` will attempt to map
result columns onto the domain entity when the return type is an interface, causing
`NotReadablePropertyException` at runtime. Records bypass this path because SDN constructs
them via canonical constructor, matching parameters to Cypher column names. Projection
interfaces are safe only on repositories extending the base `Repository<Entity, ID>` (not
`Neo4jRepository`), which doesn't trigger entity-aware mapping.

**Stage provenance on domain nodes.**

Every `@Node` entity created during pipeline execution must carry a `stageId` property for provenance, cleanup, and replay:

```java
// Record entity — stageId as record component, placed after the scope ID
public record ChapterIndividual(
        @Id UUID id,
        UUID chapterId,
        @Property("stageId") UUID stageId,  // after scope ID, before business fields
        String displayName,
        String normalizedName,
        // ...
) {}

// @Data entity — stageId as field with setter, NOT in @PersistenceCreator
@Data
@Node("Scene")
public class Scene {
    @Property("stageId")
    private UUID stageId;  // set via scene.setStageId(ctx.stageId())
    // ...
}
```

Placement convention: `stageId` goes after the scope ID (`chapterId` for chapter entities, `bookId` for book entities, `sceneId` for mention entities) and before business fields.

For records, `stageId` is a record component that must be passed at every construction site. For `@Data` classes with `@PersistenceCreator`, use a field + setter to avoid adding a 16th parameter to the persistence constructor.

Services that create domain nodes must accept `StageExecutionContext ctx` as their first parameter and pass `ctx.stageId()` to entity constructors:

```java
// Required — ctx threaded through
public void persistExtractedIndividuals(StageExecutionContext ctx, ...) {
    individualMentionRepository.save(new IndividualMention(
            UUID.randomUUID(), SOURCE, displayName, ...,
            ctx.stageId(),  // stageId after scope IDs
            sceneId, chapterId, ...));
}

// Wrong — no ctx, no stageId
public void persistExtractedIndividuals(...) {
    individualMentionRepository.save(new IndividualMention(
            UUID.randomUUID(), SOURCE, displayName, ...,
            sceneId, chapterId, ...));  // missing stageId
}
```

Stage-scoped cleanup uses `deleteDataByStageId(stageId)`:
```cypher
MATCH (n {stageId: $stageId}) DETACH DELETE n
```

This removes all nodes and their relationships created by a specific stage execution, enabling safe replay.

See ADR-014 (explicit parameter threading) and ADR-015 (stage node provenance).

---

## @Transactional Discipline

**`@Async` and `@Transactional` are incompatible on the same method boundary.**
An `@Async` method runs in a new thread with no inherited transaction. Adding
`@Transactional` gives it its own new transaction — not the caller's. Correct when
intentional; a defect when the intent is to participate in the caller's transaction.

**`readOnly = true` on all query-only paths.**
```java
@Transactional(readOnly = true)
public Optional<NodeEntity> findById(String id) { ... }
```
SDN skips dirty checking in read-only transactions — a correctness hint and a
performance gain.

**Private method proxy bypass.**
`@Transactional` on a private method is silently ignored by Spring AOP.
Apply only to `public` (or `protected` with class-based proxying) methods.

**Self-invocation proxy bypass.**
`this.txMethod()` within the same bean bypasses the transactional proxy.
Refactor to a separate `@Service` bean, or inject self via `ApplicationContext`.

**Narrow transaction scope.**
Do not hold a transaction open across LLM calls, HTTP outbound calls, or file I/O.
Holding the Neo4j connection slot while a slow external operation runs is expensive.

```java
// Wrong — Neo4j connection held open during every HTTP call to the embedding API
@Transactional
public int generateEmbeddingsForChapter(UUID chapterId) {
    List<Chunk> chunks = chunkRepo.findByChapter(chapterId); // read inside tx
    List<double[]> vectors = embeddingModel.call(chunks);    // external I/O inside tx
    chunkRepo.saveAll(chunks);                               // write inside tx
}

// Correct — only the write needs a transaction
public int generateEmbeddingsForChapter(UUID chapterId) {
    List<Chunk> chunks = loadChunks(chapterId);         // outside tx
    List<double[]> vectors = embeddingModel.call(...);  // outside tx
    persistEmbeddings(chunks, vectors);                 // @Transactional write
}
```

**`REQUIRES_NEW` for writes that must commit independently.**
Use `propagation = REQUIRES_NEW` when a status update or audit write must commit and
be immediately visible to other threads regardless of whether the caller's transaction
eventually rolls back.

```java
// Job status updates use REQUIRES_NEW so the committed status is visible
// to other threads even if the outer pipeline transaction later fails.
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void updateJobStatus(UUID jobId, IngestionStatus status, ...) { ... }
```

Do not use `REQUIRES_NEW` speculatively. Overuse creates nested transactions that
accumulate connection slots and can deadlock.

**Repository writes outside a `@Transactional` method run in auto-commit.**
Any `repository.save()` or `repository.saveAll()` call made from a method with no
`@Transactional` context executes as a separate auto-commit operation. If a loop is
interrupted mid-way the partial writes are permanent and idempotency guards that check
"is the work already done?" will treat the partial result as complete on the next
attempt. Always delegate multi-step write sequences to a `@Transactional` service
method.

**`@Transactional` on inner methods called via self-invocation is dead code.**
When a `@Transactional` method delegates to other methods on the same bean using
`this.`, the inner annotations are bypassed. Declare the transaction boundary at the
outermost method only. If an inner method genuinely needs independent transaction
semantics, move it to a separate `@Service` bean.

```java
// Wrong — inner annotations are bypassed; only createAllDefaults() opens a tx
@Transactional
public void createAllDefaults(UUID bookId) {
    createInChapterDefaults(bookId);  // this.createInChapterDefaults() — proxy skipped
    createCrossChapterDefault(bookId);
}

@Transactional   // never fires when called from createAllDefaults()
public int createInChapterDefaults(UUID bookId) { ... }

// Correct — annotate only the outer boundary; remove redundant inner annotations
@Transactional
public void createAllDefaults(UUID bookId) {
    createInChapterDefaults(bookId);
    createCrossChapterDefault(bookId);
}

public int createInChapterDefaults(UUID bookId) { ... }  // participates in outer tx
```

**In-process locks do not substitute for distributed coordination.**
`ConcurrentHashMap<UUID, ReentrantLock>` or similar JVM-local guards protect against
concurrent access within a single JVM only. Any reduction or merge operation that uses
in-process locking to serialize writes for the same aggregate key is silently unsafe in
a multi-node deployment.

Acceptability rules:
- JVM-local keyed locks are only acceptable when the service is explicitly fixed to a
  single instance and that constraint is documented in both code and ops/deployment docs.
- Multi-node-safe serialization is required when the write is destructive, non-commutative,
  or triggered from async/event/scheduled paths that can overlap for the same aggregate key.
- A Neo4j unique-constraint violation + retry loop is a guardrail against duplicate node
  creation, not a serialization strategy for multi-step aggregate rebuilds. Do not substitute
  one for the other.
- The correct multi-node approach for delete-and-rebuild aggregates is a persisted
  work-claim model: write a dirty marker / claim record to Neo4j under a unique constraint,
  have the worker atomically claim it, run the rebuild, then clear or re-queue if a new
  trigger arrived during the run.

**Default to declarative transaction management. Mix sparingly.**
`@Transactional` annotations on public methods of separate beans are the primary
transaction management mechanism. `TransactionTemplate` is acceptable only when
transaction settings must be chosen at runtime or you need a callback-scoped transaction
outside of a proxied service method. If a `TransactionTemplate` is used, a code comment
must explain why declarative annotations are insufficient.

When the real goal is an isolated commit (equivalent to `REQUIRES_NEW`), the correct
approach is a public method annotated `@Transactional(propagation = REQUIRES_NEW)` on a
separate bean — not a hand-built template in the calling class.

```java
// Wrong — TransactionTemplate in the same class for what is just a REQUIRES_NEW read
@PostConstruct
private void init() {
    txTemplate = new TransactionTemplate(txManager);
    txTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
}

// Correct — separate bean, declarative annotation, no setup noise
// IngestionDeduplicationService.java
@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
public Optional<IngestionJob> findActiveJobForChapter(UUID chapterId) { ... }
```

**Do not mix `Neo4jClient` and SDN repositories in the same `@Transactional` method.**
`Neo4jClient` queries bypass the SDN entity manager's identity map. When a raw
`Neo4jClient` DELETE is followed by SDN `saveAll()` in the same transaction, previously
loaded entities held in the identity map are stale — they reference graph state that
no longer exists. Use one paradigm consistently per transaction boundary: either SDN
repository methods exclusively, or `Neo4jClient` exclusively. If the operation requires
raw Cypher that SDN cannot express, extract it to a separate service method that runs
in its own transaction.

**`@TransactionalEventListener(AFTER_COMMIT)` + new transaction.**
This fires outside any active transaction. If the handler needs to write to Neo4j,
add `@Transactional(propagation = REQUIRES_NEW)`.

---

## Async & Executors

**Always qualify `@Async` with a named executor.**
`@Async` without a qualifier falls back to Spring's implicit default executor.
All domain async work must declare an explicit executor bean:

```java
// Good
@Async("domainTaskExecutor")
@EventListener
public void onWorkItemCreated(WorkItemCreatedEvent event) { ... }

// Bad — silently uses wrong or default executor
@Async
@EventListener
public void onWorkItemCreated(WorkItemCreatedEvent event) { ... }
```

**MDC propagation.**
`@Async` dispatches to a new thread; MDC context (request IDs, correlation IDs) is lost
unless the executor is configured with an `MDCTaskDecorator`. Verify on every new executor.

**Self-deadlock in bounded pools.**
A `CompletableFuture` running inside a bounded pool must never submit work back to the
same pool and then block on it. This exhausts all threads and deadlocks.

**`CompletableFuture` error handling.**
Always attach `.exceptionally()` or `.handle()` to async chains.
An unhandled exception is silently dropped and the stage never completes — callers
waiting on `allOf()` will hang indefinitely.

**Rejection policy.**
`AbortPolicy` (the default) throws `RejectedExecutionException` back to the submitter.
Ensure this exception is caught and converts to a job failure, not an unhandled crash.

**Spring's `ApplicationEventPublisher` is synchronous.**
`ApplicationEventPublisher.publishEvent()` dispatches to `@EventListener` methods on
the calling thread. Publishing from an HTTP controller thread blocks the response until
every synchronous listener has completed — including Neo4j writes, fan-out evaluations,
and SSE broadcasts. When publishing from an HTTP thread, offload via executor:

```java
CompletableFuture.runAsync(() -> eventPublisher.publishEvent(event), taskExecutor)
    .exceptionally(ex -> { log.error("Event publish failed", ex); return null; });
```

---

## Event-Driven Pipeline

**Delivery semantics: best-effort, JVM-internal.**
`@EventListener + @Async` provides no durability. A JVM crash after publish but before
handler execution loses that work. Document this constraint for every new pipeline stage.

**`@TransactionalEventListener(AFTER_COMMIT)` for cross-aggregate coordination.**
Use this for the first handler in any chain that must not fire if the publishing
transaction rolls back. Subsequent downstream handlers use plain `@EventListener + @Async`.

**Idempotency.**
Every event handler must guard against duplicate execution — check whether the work is
already done before doing it. Required for any handler that may be invoked twice in
retry scenarios.

**Correlation identifier on all events.**
Every event class must carry a correlation identifier. Without it, tracing work across
all async log lines from all handlers is impossible.

**Fan-in correctness.**
Any fan-in coordinator must:
1. Know the exact number of expected branches upfront.
2. Use atomic counter operations — no race condition on the count.
3. Fire the completion event exactly once.
4. Handle branch failure: the coordinator must still reach a terminal state if a branch fails.

**Fan-out loop resilience.**
When a coordinator iterates over downstream stages and invokes an external dependency
(Neo4j, Spring event publisher, etc.) per iteration, a failure on one iteration must
not prevent the remaining iterations from being attempted. Each iteration's failure
domain is independent; one child's failure is not an excuse to abandon its siblings.

```java
// Good — individual failure cannot stall siblings
for (StageKey child : dag.childrenOf(completedStage)) {
    try {
        boolean triggered = stageRepo.tryTrigger(jobId, child);
        if (triggered) { publishEvent(...); }
    } catch (Exception e) {
        log.error("Failed to evaluate barrier for child={}: {}", child, e.getMessage(), e);
    }
}

// Bad — a single Neo4j error abandons all remaining children
for (StageKey child : dag.childrenOf(completedStage)) {
    boolean triggered = stageRepo.tryTrigger(jobId, child); // throws → loop terminates
    if (triggered) { publishEvent(...); }
}
```

**No circular event chains.**
Verify no handler publishes an event that transitively causes the same handler to fire.

**Event immutability.**
Events must be immutable. Prefer Java records.
Mutable events shared across threads via the event bus are a data corruption defect.

---

## Type Information Must Survive Interfaces

**Do not degrade typed information to strings across method boundaries.**

When crossing a method boundary — especially into an infrastructure adapter — do not convert enums or domain types to strings and reconstruct them on the other side. String-based lookup tables (`Map<String, Enum>`) are fragile: every new enum value requires a manual table update, and string reconstruction has silent fallback bugs.

```java
// Wrong — StageKey (enum) → String → lookup table → StageKey (fragile reconstruction)
LlmClient.call("chapter-segmentation", ...);       // enum → string
LlmCallLoggingService:                              // string → enum via LLM_STEP_TO_STAGE map
    StageKey key = LLM_STEP_TO_STAGE.get(step);     // misses "event-coref", "event-merge"

// Correct — pass the type directly
LlmClient.call(StageKey.CHAPTER_SEGMENTATION, ...);
LlmCallLoggingService:
    stageRepo.findByJobIdAndStep(jobId, stage);     // no lookup table, no fallback
```

The lookup table (`LLM_STEP_TO_STAGE`) missed two values. The fallback "find any RUNNING stage" query linked LLM call records to the wrong stage when multiple stages ran concurrently. Neither bug is possible if the enum is passed directly.

**Rule:** If the caller has a typed value, pass the typed value. If the interface is generic, widen the signature. Do not create string-based correspondence tables that must be manually maintained.

---

## Package Boundary Discipline

**Package-private types.**
Types internal to a domain package should be package-private, not public.
Public visibility is an implicit API contract that any package can take a dependency on.

**Prefer events over direct method calls for cross-package coordination.**
If package A triggers work in package B, the clean pattern is: A publishes a domain
event, B listens. Direct method calls from A into B create tight coupling.

---

## Spring AI / LLM Integration

**Null-guard every LLM response access.**
```java
// Bad — NPE when LLM returns empty response
String content = response.getResult().getOutput().getContent();

// Good
String content = Optional.ofNullable(response)
    .map(ChatResponse::getResult)
    .map(Generation::getOutput)
    .map(AssistantMessage::getContent)
    .orElseThrow(() -> new LlmResponseException("LLM returned null content"));
```

**Structured output validation.**
When using `BeanOutputConverter` or parsing LLM JSON output manually, always handle
`JsonProcessingException`. LLMs produce non-conformant JSON under load, on prompt edge
cases, or during rate limiting.

**Timeouts on all LLM calls.**
Unbounded LLM calls exhaust the thread pool during outages or rate limiting.
Configure a timeout at the `ChatClient` level.

**`maxTokens` cap on all calls.**
Unlimited response tokens cause unexpected memory and latency spikes.
Set an upper bound appropriate to the expected response size for each call.

**Prompt injection risk.**
User-supplied content flowing into LLM prompts must be structurally separated from
system instructions using delimiters:

```
Analyze the following content:
<input>
{user_content}
</input>
```

**Prompt externalization.**
Do not hardcode prompts as Java string literals. Externalize to classpath template files
or `application.yml` properties to allow tuning without recompile.

**Retry parameter variation.**
When retrying a structured-output LLM call after a parse or validation failure, vary the
temperature across attempts. A fixed temperature re-rolls the same likely-failure
distribution — wasted retries. Progressive temperature (e.g., `+0.1` per retry) or a
temperature sweep is required for all structured-output call paths that have retry logic.

```java
// Good — temperature increases on each retry
retryTemplate.execute(ctx -> {
    double attemptTemp = baseTemp + (ctx.getRetryCount() * 0.1);
    var options = OpenAiChatOptions.builder().temperature(attemptTemp).build();
    return chatClient.prompt().options(options).call().entity(MyType.class);
});

// Bad — same temperature on every retry
var options = OpenAiChatOptions.builder().temperature(0.1).build();
retryTemplate.execute(ctx -> {
    return chatClient.prompt().options(options).call().entity(MyType.class);
});
```

**Token usage observability.**
Log token usage per LLM call at DEBUG level for cost attribution and capacity planning.
Prefer actual API-reported token counts (`ChatResponse.getMetadata().getUsage()`) over
heuristic estimates (`chars/3`). Heuristic estimates can be off by 30%+.

---

## Over-Abstraction

**Single-implementation interfaces are a defect without justification.**
One interface + one production class + no test fake = indirection overhead with no
benefit. Delete the interface; use the concrete class directly.
Add an interface only when:
- Multiple real implementations exist or are imminent.
- The boundary is an external system and testability requires a mock or stub.
- It is a true external or cross-module boundary.
- It defines a small, explicit ownership seam that would otherwise force the wrong package dependency direction.

**Speculative generality.**
Do not add extension points, abstract base classes, generic type parameters, or callback
hooks for hypothetical future use cases. Add abstractions when the second concrete use
case appears — not before.

**Premature extraction.**
A helper method or utility class extracted from a single callsite is premature.
Wait until the same logic is needed in a second callsite before extracting.

**Excessive duplication.**
Three or more near-identical blocks of code that differ only by mechanically derivable
values — enum constants, type names, URL paths, log format strings — are a defect.
Extract the shared logic into a parameterised method, generic base class, or
configuration-driven component. Copy-paste duplication compounds every future change:
a bug fix or behavioural change must be replicated N times, and one copy inevitably
drifts.

**Return types that no external caller consumes.**
A public method's return type must serve at least one external (non-`this`) caller.
If the value is only used by code inside the same class — another method on `this`, or
an internal delegate — the return type is an implementation detail leaked through the
public boundary.

```java
// Wrong — createAllForJob returns Map<StageKey, UUID> but bootstrapJob only
// null-checks one entry; the map is consumed internally by rewireEdges (called
// inside createAllForJob). Zero external callers use the map.
public Map<StageKey, UUID> createAllForJob(UUID jobId, UUID chapterId) { ... }

// Correct — return void; rewireEdges is called internally, bootstrapJob queries
// stageRepo independently.
public void createAllForJob(UUID jobId, UUID chapterId) { ... }
```

This is the method-level companion to the record-design rule "all fields must be
consumed." A return type is part of the public API. If no external code reads it,
the API is advertising implementation structure that doesn't belong on the surface.

---

## Composition vs Inheritance

**Prefer composition.** Inheritance is appropriate only for:
- Extending framework types (`ApplicationEvent`, `AbstractMessageConverter`).
- Exception type hierarchies.

Do not use inheritance to share utility logic between unrelated services.
Inject a shared collaborator or use a static utility method instead.

**Liskov Substitution.**
If a subclass overrides a method to throw `UnsupportedOperationException`, the hierarchy
is wrong. Redesign using composition or a separate abstraction.

**Interface `default` methods are not traits.**
Do not use `default` methods to share implementation logic across implementing classes.
This creates hidden coupling between the interface and all implementers.

---

## Security

**Cypher injection.**
All graph query parameters must use parameterised Cypher.
Never compose user-supplied values or dynamic strings into a Cypher query string:

```java
// Bad
String cypher = "MATCH (n:Node {name: '" + userInput + "'}) RETURN n";

// Good
String cypher = "MATCH (n:Node {name: $name}) RETURN n";
Map<String, Object> params = Map.of("name", userInput);
```

**Prompt injection.**
Clearly separate system instructions from user-provided input using structural markers.
Consider sanitising control characters from user text before injection.

**Secrets in source.**
All secrets come from environment variables.
Never hard-code credentials or API keys in source or config files.

**REST surface.**
Every new endpoint must be matched against the `SecurityFilterChain` configuration.
Verify it is not accidentally open due to an overly broad permit-all matcher.

**Deserialization.**
Do not use Java object deserialization (`ObjectInputStream`) for untrusted data.
Use JSON with a strict `ObjectMapper` configuration.

**Log safety.**
Never log credentials, API keys, raw user-supplied content, or internal stack paths.

**Error response hygiene.**
Never include exception messages, stack traces, or internal identifiers in HTTP response
bodies sent to API clients. Exception messages from downstream services may contain
Neo4j query fragments, database identifiers, internal file paths, or AI provider metadata.
Return sanitized, client-safe messages in all error responses. Log the full exception
server-side at ERROR level.

---

## Lombok Discipline

**Always use `@Slf4j` for logger fields.**
Never use `LoggerFactory.getLogger(MyClass.class)` manually. `@Slf4j` generates the
same field (named `log`) with less noise and eliminates a common source of copy-paste
errors when adding logging to new or refactored classes. The only acceptable
`LoggerFactory.getLogger()` call is in abstract or framework code where Lombok
annotation processing cannot reach.

**Never `@Data` on Neo4j entity classes.**
`@Data` generates `equals` and `hashCode` using all fields including mutable relationship
collections. This breaks `HashSet`/`HashMap` correctness. Use explicit annotations:

```java
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"children", "tags"})
@Getter
@Setter
@Node
public class NodeEntity { ... }
```

**`@ToString` exclusions on entities.**
Always exclude relationship collection fields. Logging an entity that eagerly includes its
full relationship collection triggers graph traversal and produces enormous log lines.

**`@Builder.Default` for fields with non-null defaults.**
Without `@Builder.Default`, a field with an initializer (e.g., `List<T> items = new ArrayList<>()`)
is set to `null` when constructed via the builder.

**`@Singular` produces immutable collections.**
Callers that mutate a `@Singular` collection get `UnsupportedOperationException`.
Document this or use a regular collection field.

**`@RequiredArgsConstructor` for dependency injection.**
Never use field injection (`@Autowired` on fields). Constructor injection via
`@RequiredArgsConstructor` makes dependencies explicit and testable.

**`@SneakyThrows` is a last resort.**
It bypasses checked exception handling. Acceptable only in framework integration
boilerplate where checked exceptions are genuinely impossible. In domain code,
convert to a typed domain exception instead.
