# LV-087-12 — Random and time abstractions for deterministic tests [refactor]

Context

- `LlmRetryStrategy` uses a static `Random`; time-sensitive tests rely on real time or sleeps.

Problem

- Non-deterministic random and real time introduce flakiness.

Proposal

- Introduce small abstractions:
  - `RandomProvider` (default: `ThreadLocalRandom`); allow injection in tests.
  - `TimeProvider`/`Clock` to control timestamps in metrics and retry.
- Use them in retry/metrics code paths.

Scope

- Update `LlmRetryStrategy` to use `RandomProvider`.
- Update `HealthMetricsCollector` (if feasible) to accept a `Clock`.
- Adjust relevant tests to inject deterministic providers.

Out of scope

- Behavior changes in production (providers should default to current behavior).

Acceptance criteria

- [ ] Tests do not rely on `Thread.sleep` for time separation.
- [ ] Retry jitter is testable deterministically.

Quality gates

- [ ] Build/tests green on JDK 21

Links

- LLM retry: ../../../lorevault-api/src/main/java/com/lorevault/api/service/content/retry/LlmRetryStrategy.java
- Metrics: ../../../lorevault-api/src/main/java/com/lorevault/api/service/system/metrics/HealthMetricsCollector.java
