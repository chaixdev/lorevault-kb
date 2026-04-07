# LV-087-3 — Unify retry strategy and make it testable [refactor]

Context

- Three separate retry loops exist with `Thread.sleep`: `RetryableHealthChecker`, `LlmRetryStrategy` (with jitter), and a custom loop in `EmbeddingModelAdapter`.
- This duplication increases complexity and hinders deterministic testing.

Problem

- Sleep-based retries make tests slow/flaky and policies inconsistent across code paths.

Proposal

- Centralize on a single retry mechanism: either Spring Retry (`@Retryable` + backoff + jitter) or a consolidated utility component.
- Introduce an injectable `Sleeper` and `Clock` to avoid real sleeps in tests.
- Standardize retry policy (attempts, backoff, jitter) and error classification.

Scope

- Choose approach:
  - Option A: Spring Retry annotations on boundary methods (embedding, LLM connectivity), externalize policy in props.
  - Option B: Single `RetryExecutor` bean with jitter/backoff and pluggable `Sleeper`.
- Refactor `EmbeddingModelAdapter` to use the chosen mechanism (remove its internal loop).
- Align `RetryableHealthChecker` / `LlmRetryStrategy` onto the same base.
- Add focused unit tests with a fake `Sleeper` to validate backoff sequences without waiting.

Out of scope

- Changing business semantics on retry success/failure outcomes.

Technical notes

- If using Spring Retry, ensure idempotency of retried operations and configure interceptor visibility.
- If keeping custom executor, support `ScheduledExecutorService` and test doubles.

Acceptance criteria

- [ ] Only one retry implementation remains in production code.
- [ ] No direct calls to `Thread.sleep` within business code paths (outside the centralized component).
- [ ] Deterministic tests cover backoff and jitter logic without real waits.

Quality gates

- [ ] Build and tests green on JDK 21
- [ ] No new ArchUnit violations
- [ ] Coverage includes retry error paths

Links

- Classes: ../../../lorevault-api/src/main/java/com/lorevault/api/service/system/retry/RetryableHealthChecker.java, ../../../lorevault-api/src/main/java/com/lorevault/api/service/content/retry/LlmRetryStrategy.java, ../../../lorevault-api/src/main/java/com/lorevault/api/infrastructure/ai/EmbeddingModelAdapter.java
