# HTTP Request ID MDC Propagation

**Status:** NOT STARTED

## Summary

Add request-scoped unique identifiers to incoming HTTP requests and propagate them through MDC (Mapped Diagnostic Context) so that all logs for a single API call can be correlated and traced through async event chains and thread boundaries.

## Problem

Today, when debugging ingestion or query issues, it is hard to correlate logs across an entire HTTP request lifecycle because:

- Each incoming HTTP request lacks a unique identifier at the servlet entry point
- Logs from the initial request handler, downstream async listeners, and fan-out branches cannot easily be grouped together
- Thread boundary crossings (e.g., executor handoffs) lose context about the originating request
- Operator dashboards and log aggregation systems cannot group request-scoped activity without guessing

This makes it harder to answer: "Show me everything that happened in response to request X" without manually tracking job IDs or other indirect signals.

## Product Context

From an operator and debugging perspective:

- **Triage speed**: An operator debugging a stuck job should be able to filter logs by request ID and see the complete execution trace without reconstructing the context from job IDs or timestamps
- **Multi-request scenarios**: When multiple API calls are in flight simultaneously, request IDs disambiguate which logs belong to which call
- **API observability**: Future metrics, tracing, and monitoring integrations (e.g., OpenTelemetry) will need a requestId to correlate traces across thread boundaries

## Technical Context

Relevant components:

- `lorevault-web` — REST controllers accepting inbound requests
- Spring Web / Servlet layer — where requests enter the system
- MDC setup — already partially in place for async ingestion (`MDCTaskDecorator`)
- Async event listeners — need to inherit and propagate request context
- Executors — `ingestionTaskExecutor` and any future shared executors

Existing precedent:

- [ADR 009 — Structured Logging Philosophy](../adr/009-structured-logging-philosophy.md) establishes mandatory log fields including `jobId` and `correlationId`
- [Logging Philosophy](../rules/logging-philosophy.md) defines structured logging rules
- [Async Ingestion Logging Philosophy Brainstorm](../brainstorm/architecture/2026-04-17T0855_async-ingestion-logging-philosophy-brainstorm.md) explores broader correlation strategy (focused on ingestion handlers)
- `MDCTaskDecorator` — existing mechanism for async/executor MDC propagation

## Scope

This item covers:

1. **Request entry point** — Generate or accept a unique `requestId` at the servlet/controller boundary
   - Generate UUIDs or similar for requests that don't provide one
   - Accept X-Request-ID or similar header if present
   
2. **MDC population** — Store `requestId` in MDC at the servlet filter level
   
3. **MDC propagation** — Ensure `requestId` is available to downstream async handlers and event listeners
   - Verify `MDCTaskDecorator` propagates request context (not just job context)
   - If needed, enhance executor wrappers to inherit request context across thread boundaries
   
4. **Logging inclusion** — Include `requestId` in log output
   - Verify logback pattern includes the field
   - Spot-check a few logs to confirm the field appears

5. **Documentation** — Update [Logging Philosophy](../rules/logging-philosophy.md) to document `requestId` as a mandatory field alongside `jobId`

## Out of Scope

This item does **not** include:

- Changing how `jobId` and `correlationId` are handled (those are already established)
- Implementing full distributed tracing or OpenTelemetry integration
- Modifying external APIs or response payloads to include request IDs
- Generating request IDs from client input without validation
- Metrics, dashboards, or alerting based on request IDs

## Known Constraints / Prior Findings

- **Async ingestion already uses jobId/correlationId**: The brainstorm document establishes that ingestion jobs have explicit correlation IDs. Request-scoped IDs are complementary, not replacements.
  
- **MDCTaskDecorator exists**: The pattern for MDC propagation across executor boundaries is already established; this work extends it to the request scope.

- **Servlet filter approach is standard**: Spring applications typically use servlet filters (`OncePerRequestFilter`) to populate request-scoped MDC.

- **No distributed tracing yet**: This work is purely for correlation within a single process/deployment. Distributed tracing across microservices is out of scope.

## Open Questions

- Should the request ID be generated as a UUID or accept a format from the caller (e.g., `X-Request-ID` header)?
- Should request IDs be included in HTTP responses (e.g., `X-Request-ID` response header) for client-side log correlation?
- Should the `requestId` field name be customizable, or is `requestId` the canonical name?
- Do we need to validate or sanitize request IDs accepted from headers to prevent injection attacks?

## Success Criteria

- A servlet filter populates `requestId` in MDC for every incoming HTTP request
- All application logs in the request execution thread and async descendants include the `requestId` field
- Logs from async event listeners spawned during request handling show the same `requestId`
- Operator can grep logs by `requestId` and see the complete execution trace for that request
- [Logging Philosophy](../rules/logging-philosophy.md) documents `requestId` as a mandatory field

## Links

- [ADR 009 — Structured Logging Philosophy](../adr/009-structured-logging-philosophy.md)
- [Logging Philosophy](../rules/logging-philosophy.md)
- [Async Ingestion Logging Philosophy Brainstorm](../brainstorm/architecture/2026-04-17T0855_async-ingestion-logging-philosophy-brainstorm.md)
- Related component: `MDCTaskDecorator` in lorevault-core
- Related controller files: `lorevault-web/src/main/java/com/example/lorevault/web/controller/`
