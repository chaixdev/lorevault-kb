# LV-087-11 — LLM health config via constructor injection [refactor]

Context

- `LlmHealthCheckService` uses `@Value` fields (`modelId`, `healthEnabled`), and tests set them via `ReflectionTestUtils`.

Problem

- Reflection-based field setting is brittle; property binding is implicit and harder to test.

Proposal

- Introduce `LlmHealthProperties` with fields `modelId` and `healthEnabled` and prefix (e.g., `lorevault.llm.health`).
- Inject properties via constructor into `LlmHealthCheckService`; remove `@Value` and reflection in tests.

Scope

- Add `LlmHealthProperties` with `@ConfigurationProperties` and enable binding.
- Refactor `LlmHealthCheckService` to depend on the properties object.
- Update `LlmHealthCheckServiceTest` to pass config cleanly.

Out of scope

- Changing health check business logic.

Acceptance criteria

- [ ] `LlmHealthCheckService` takes a properties object in its constructor.
- [ ] No `ReflectionTestUtils` usage remains in tests.

Quality gates

- [ ] Build/tests green on JDK 21
- [ ] No new ArchUnit violations

Links

- Service: ../../../lorevault-api/src/main/java/com/lorevault/api/service/system/LlmHealthCheckService.java
- Test: ../../../lorevault-api/src/test/java/com/lorevault/api/service/system/LlmHealthCheckServiceTest.java
