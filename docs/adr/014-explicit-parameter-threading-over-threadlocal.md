# ADR 014: Explicit Parameter Threading Over ThreadLocal for Execution Context

**Status:** Accepted
**Date:** May 2026

## Context

LoreVault's ingestion pipeline needs execution identity — `stageId`, `jobId`, `chapterId`, `bookId`, and the `StageKey` — to flow from the dispatcher through handlers into services and down to repository calls. This identity is needed for:

- **Logging:** MDC fields (`stage`, `jobId`, `stageId`) on every log line
- **Graph provenance:** Tagging domain nodes with `stageId` so cleanup can target a specific stage's output
- **Observability:** Future Micrometer timer tags

The question is how this context reaches the service layer.

## Decision

`StageExecutionContext` flows as an explicit method parameter through the entire handler → service → repository chain. Every method that creates domain nodes accepts `StageExecutionContext ctx` as its first parameter and passes `ctx.stageId()` to entity constructors.

The dispatcher sets MDC from `ctx` before handler execution and clears it afterward. MDC is a logging convenience, not the primary context carrier.

## Alternatives Considered

### ThreadLocal-based context

Store `StageExecutionContext` in a `ThreadLocal` or `InheritableThreadLocal`. Services read it implicitly. Rejected because:

- **Async boundary breaks it.** `@Async` methods, `CompletableFuture` chains, and executor handoffs lose the thread-local unless explicitly propagated. The pipeline already uses two dedicated executors.
- **Hidden dependency.** A service that silently reads from ThreadLocal has an invisible contract. Refactoring, testing, or calling from a different context (e.g., a REST controller) requires ThreadLocal setup that isn't obvious from the method signature.
- **Testing is harder.** Every test must set up and tear down the ThreadLocal, or tests pass in production but fail in CI where the context is missing.
- **Spring's own guidance.** Spring Data Neo4j and Spring's transaction management use ThreadLocal for their own concerns. Adding another ThreadLocal creates competing context holders that are easy to misconfigure.

### MDC-only context

Store identity fields only in MDC (SLF4J's mapped diagnostic context). Rejected because:

- MDC is for logging, not business logic. Using MDC as the source of truth for `stageId` conflates observability with data integrity.
- MDC values are strings — no type safety, no compile-time checking.
- MDC is ThreadLocal-backed, so it has the same async boundary problems.

### Spring Security context

Use `SecurityContextHolder` to carry pipeline identity. Rejected because:

- Security context is for authentication/authorization, not pipeline execution identity.
- Would require custom `Authentication` implementations that confuse the security model.

## Implications

- Every service method that creates domain nodes must accept `StageExecutionContext ctx` as its first parameter. This is a visible contract — callers know they must provide context.
- Handler methods already receive `ctx` from `StageDispatcher.dispatch()`. The threading gap was between handlers and services; this ADR closes it by requiring services to accept `ctx` explicitly.
- `StageExecutionContext` is a Java record — immutable, trivially constructable in tests, and impossible to partially initialize.
- MDC is set by the dispatcher for logging convenience only. Services must not read from MDC for business logic.
- REST controllers that trigger ingestion stages must construct a `StageExecutionContext` from request parameters. This is explicit and testable.