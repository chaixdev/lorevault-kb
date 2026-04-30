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

**Projections for partial reads.**
When only a subset of node properties is needed, use an SDN interface projection or
a custom `@QueryResult` record instead of loading the full entity graph.

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

**No circular event chains.**
Verify no handler publishes an event that transitively causes the same handler to fire.

**Event immutability.**
Events must be immutable. Prefer Java records.
Mutable events shared across threads via the event bus are a data corruption defect.

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

**Token usage observability.**
Log token usage per LLM call at DEBUG level for cost attribution and capacity planning.

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
Never log credentials, API keys, raw user-supplied content, or internal stack paths
in API error responses.

---

## Lombok Discipline

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
