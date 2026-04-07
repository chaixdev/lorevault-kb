# LV-087-4 — Tighten tests: remove sleeps, console prints, and lenient stubs [chore][refactor]

Context

- Several tests have flakiness/noise signals: `Thread.sleep(10)` (timestamp separation), `System.out.println` for progress, and `lenient()` mocks.
- ReflectionTestUtils is used to set private `@Value` fields.

Problem

- Flaky timing, noisy output, and brittle reflection reduce test reliability and speed.

Proposal

- Replace time-based sleeps with deterministic time control (inject `Clock` or time abstraction) in `HealthMetricsCollector` tests.
- Remove `System.out.println` from tests; rely on assertions and logger if needed.
- Replace `lenient()` with strict, targeted stubs; fail on unused stubs.
- Migrate services with `@Value` fields to constructor-injected configuration properties to remove `ReflectionTestUtils` usage.

Scope

- `HealthMetricsCollectorTest`: remove `Thread.sleep`, inject a controllable clock or assert with `within` using fixed instants.
- `TextChunkingServiceConfigurationTest`: remove printlns; consider slimming from `@SpringBootTest` to a light context.
- `OpenAiSceneDetectionAdapterTckTest`: remove `lenient()` and stub only required calls.
- `LlmHealthCheckServiceTest`: stop using `ReflectionTestUtils`; switch service to constructor injection of config.

Out of scope

- Changing business logic or public APIs.

Technical notes

- Consider adding a simple `TimeProvider` interface used by metrics collector; provide `System` and `Test` implementations.

Acceptance criteria

- [ ] No `Thread.sleep` remains in unit tests.
- [ ] No `System.out.println` remains in unit tests.
- [ ] No `lenient()` stubs remain; tests pass with strict Mockito.
- [ ] No `ReflectionTestUtils` used in tests; config is constructor-injected.

Quality gates

- [ ] All tests pass locally and in CI on JDK 21
- [ ] Coverage unchanged or improved

Links

- Tests/examples: ../../../lorevault-api/src/test/java/com/lorevault/api/service/system/metrics/HealthMetricsCollectorTest.java, ../../../lorevault-api/src/test/java/com/lorevault/api/service/content/TextChunkingServiceConfigurationTest.java, ../../../lorevault-api/src/test/java/com/lorevault/api/infrastructure/ai/openai/OpenAiSceneDetectionAdapterTckTest.java, ../../../lorevault-api/src/test/java/com/lorevault/api/service/system/LlmHealthCheckServiceTest.java
