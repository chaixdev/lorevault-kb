# Retire StepKey — Consolidate into StageKey

**Status:** IN PROGRESS — 90% complete  
**Created:** 2026-05-31  
**Last update:** 2026-06-02

## Current State

The bulk of the migration was done in commit `3ded5fdf` (Step→Stage rename + orchestration core restructuring):

| Item | Status |
|------|--------|
| `StepKey.java` deleted | ✅ |
| `StepResult.java` → `StageResult.java` | ✅ |
| `StepDefinition.java` deleted | ✅ |
| `StepCatalog.java` deleted | ✅ |
| `StepExecutionResponse` → `StageExecutionResponse` | ✅ |
| `StepQueryController` → `StageQueryController` | ✅ |
| `StageKey.toUrlSegment()` added | ✅ |
| `StageKey.queryableValues()` added | ✅ |
| `StepEventMapper` accepts `StageKey` directly (no StepKey→StageKey bridge) | ✅ |
| All handlers use `StageResult` | ✅ |
| All consolidation controllers use `StageKey` | ✅ |

## Remaining (cosmetic renames, test updates)

All remaining references are class/file names — no semantic StepKey/StepResult types
are still referenced. Purely renames.

### Production files to rename (2)

| File | Current name | New name |
|------|-------------|----------|
| `web/command/ingestion/StepExecutionCommandController.java` | `StepExecutionCommandController` | `StageExecutionCommandController` |
| `web/command/ingestion/StepEventMapper.java` | `StepEventMapper` | `StageEventMapper` |

`StepEventMapper` already takes `StageKey` directly (Javadoc confirms the mapping was
already removed). `StepExecutionCommandController` references `StepEventMapper`
and uses `StageKey` enum values.

### Test files to update (4)

| File | Change |
|------|--------|
| `ChapterIndividualConsolidationCommandControllerWebMvcTest.java` | `StepEventMapper` → `StageEventMapper` |
| `ChapterCollectiveConsolidationCommandControllerWebMvcTest.java` | `StepEventMapper` → `StageEventMapper` |
| `ChapterObjectConsolidationCommandControllerWebMvcTest.java` | `StepEventMapper` → `StageEventMapper` |
| `CorePackageBoundaryArchitectureTest.java` | `StepExecutionCommandController` → `StageExecutionCommandController` |

### Update references (3 production + 4 test)

| File | Import/Reference from → to |
|------|--------------------------|
| All consolidation controllers (10 files) | `StepEventMapper` import and field |
| `StageExecutionCommandController.java` | Class name + `StepEventMapper` field |
| `StageQueryController.java` | (verify no Step references remain) |
| 3 WebMvcTest files | `@MockitoBean StepEventMapper` → `StageEventMapper` |
| `CorePackageBoundaryArchitectureTest.java` | Package reference |

## Kickstart

```bash
# 1. Rename production files
git mv lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StepExecutionCommandController.java \
       lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StageExecutionCommandController.java
git mv lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StepEventMapper.java \
       lorevault-web/src/main/java/com/lorevault/api/web/command/ingestion/StageEventMapper.java

# 2. Find-replace across all production sources:
#    StepEventMapper → StageEventMapper  (in imports, fields, and log prefixes)
#    StepExecutionCommandController → StageExecutionCommandController (in arch test)

# 3. Same in test files

# 4. Verify
mvn clean compile
mvn test
```
