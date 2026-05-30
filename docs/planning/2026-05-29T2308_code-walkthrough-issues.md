# Code Walkthrough Issues

**Status:** Open — collecting issues during manual code walkthrough

## Summary

Issues and observations discovered during a manual walkthrough of the LoreVault codebase after the StageExecutionContext + domain node tagging implementation (Phases 1-2 + domain node tagging shipped, 463 tests green).

## Issues

### 1. `TomcatMultipartProperties` is unnecessary indirection

**Found in:** `lorevault-web/src/main/java/com/lorevault/api/config/`

**Problem:** `TomcatMultipartProperties` is a dedicated `@ConfigurationProperties` class binding under `lorevault.web.multipart.max-part-count`. It exists solely to wire a single `int` value into `TomcatMultipartConfiguration.customize()`. Spring Boot does not expose `maxPartCount` as a `spring.servlet.multipart.*` property, so the connector customization itself is legitimate — but the custom properties class is code liability. A single `@Value("${lorevault.web.multipart.max-part-count:200}")` on the customizer method would achieve the same with zero extra classes.

**Affected files:**
- `lorevault-web/.../config/TomcatMultipartConfiguration.java` — `WebServerFactoryCustomizer`
- `lorevault-web/.../config/TomcatMultipartProperties.java` — dedicated props class (delete)
- `lorevault-web/.../application.yml` line 65 — `lorevault.web.multipart.max-part-count: 200`

**Proposed fix:** Inline the `maxPartCount` value via `@Value` on `TomcatMultipartConfiguration.customize()`, delete `TomcatMultipartProperties`. Keep the `WebServerFactoryCustomizer` — it's the correct pattern for what it does.

---

### 2. `ingestion.resolution.event` package in `lorevault-web` — module boundary bleed

**Found in:** `lorevault-web/src/main/java/com/lorevault/api/ingestion/resolution/event/`

**Problem:** The package contains only 2 production classes:
- `ChapterEventAnnRerunResult` — pure DTO (Java record)
- `ChapterEventAnnRerunService` — `@Service` that re-triggers event consolidation by publishing `ChapterEventsConsolidatedEvent`

Both have **zero web-layer imports** — no `@RestController`, `HttpServletRequest`, Spring MVC, or API-layer concerns. Their imports are exclusively core domain repositories (`UniverseGraphRepository`, `BookGraphRepository`, `ChapterEventGraphRepository`), a core domain event, and standard Spring Framework annotations. They are pure domain/service logic that belongs in `lorevault-core`.

Additionally, 3 test files in this package (`BookEventConsolidationServiceTest`, `BookEventPersistenceServiceTest`, `ChapterEventEmbeddingServiceTest`) test `lorevault-core` classes from the web module's test tree — further evidence of module organization drift.

**Affected files (move to `lorevault-core` under same package path):**
- `lorevault-web/.../ingestion/resolution/event/ChapterEventAnnRerunResult.java`
- `lorevault-web/.../ingestion/resolution/event/ChapterEventAnnRerunService.java`
- `lorevault-web/.../ingestion/resolution/event/BookEventConsolidationServiceTest.java`
- `lorevault-web/.../ingestion/resolution/event/BookEventPersistenceServiceTest.java`
- `lorevault-web/.../ingestion/resolution/event/ChapterEventEmbeddingServiceTest.java`
- `lorevault-web/.../ingestion/resolution/event/ChapterEventAnnRerunServiceTest.java`

---

## Walkthrough Log

- Web module config layer — `TomcatMultipartConfiguration`, `TomcatMultipartProperties`
- Web module — `ingestion.resolution.event` package
