# Dependency Inversion Seam

**Status:** Established

## What it is

When two packages form a bidirectional dependency cycle, break it by defining an interface in the *dependent* package (the one that needs the data) and implementing it in the *dependency* package (the one that has the data). The dependent package codes against the interface. Spring injects the implementation. Neither package imports the other's internals.

## When to use

- Two packages have a bidirectional import dependency (ArchUnit reports a cycle)
- One package needs to query data that the other package owns
- The query is read-only — the dependent package doesn't need to mutate state in the dependency
- Extracting a shared library module is overkill (single consumer, narrow contract)

## When NOT to use

- The contract involves writes or transactional coordination — use events instead
- Multiple consumers need the same contract surface — extract a shared module
- The query is trivial (a single Spring Data repository method) — just use the repository directly if it's already in a shared package

## Structure

```
┌─ ingestion.triad ─────────────────────┐
│ TriadAnalysisArtifactLookup           │  ← interface defined here
│   findLatestJobIdByChapterId()        │     (the package that needs data)
│   findLatestTriadStageId(...)         │
│   findLatestTriadCallRecord(...)      │
│                                       │
│ TriadTemporalEdgeRequestFactory ──────│── injects the interface
└───────────────────────────────────────┘
                    ▲
                    │ implements
┌─ ingestion.infrastructure ────────────┐
│ GraphTriadAnalysisArtifactLookup      │  ← implementation lives here
│   (@Component, injected by Spring)    │     (the package that has data)
│                                       │
│   depends on:                         │
│   - ChapterIngestionJobGraphRepository│
│   - LlmCallRecordGraphRepository      │
└───────────────────────────────────────┘
```

Before: `content.timeline` imported `ingestion.infrastructure` directly → bidirectional cycle (`ingestion ↔ content.timeline`).

After: `content.timeline` depends only on the `ingestion.triad` interface. `ingestion.infrastructure` implements it. The dependency arrow reversed — the infrastructure package now depends on the interface's package, not the other way around.

## Case study: TriadAnalysisArtifactLookup

**The cycle:** `content.timeline` (SceneTemporalRelationshipPersistenceService) needed to look up triad analysis artifacts — job IDs, stage IDs, LLM call records. It imported `ingestion.infrastructure` classes directly. ArchUnit flagged the cycle between `ingestion` and `content.timeline`.

**The seam:** A 3-method interface in `ingestion.triad`:

```java
// ingestion/triad/TriadAnalysisArtifactLookup.java
public interface TriadAnalysisArtifactLookup {
    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);
    Optional<UUID> findLatestTriadStageIdByCurrentSceneId(UUID jobId, UUID currentSceneId);
    Optional<LlmCallRecord> findLatestTriadCallRecord(UUID jobId, UUID stageId);
}
```

The implementation in `ingestion.infrastructure` wires together the two repositories it needs (`ChapterIngestionJobGraphRepository`, `LlmCallRecordGraphRepository`). Spring injects it wherever the interface is requested.

**The consumer** (`TriadTemporalEdgeRequestFactory` in `ingestion.triad`) injects the interface and never imports `ingestion.infrastructure`. The cycle is gone.

**Verification:** `mvn test -P architecture-tests` confirms zero cycle violations across these packages. A grep for `import com.lorevault.api.orchestration.infrastructure` inside `content/timeline/` returns nothing.

## Rules

1. **Interface lives in the dependent package.** The package that needs the data owns the contract. This follows the Dependency Inversion Principle: "clients own the interface."

2. **Keep the interface narrow.** Three methods or fewer. If it grows past that, the contract is too broad — consider whether you need multiple focused interfaces or a different pattern.

3. **Implementation is a plain Spring `@Component`.** No `@Service`, no `@Repository`. The implementation is infrastructure glue — it delegates to real repositories but isn't one itself.

4. **Verify with ArchUnit.** After introducing a seam, add or update the ArchUnit test to assert the cycle is broken. The test is the enforcement.

5. **Don't add writes.** The seam is for lookups only. If the consumer needs to mutate state, use events or move the mutation upstream.

## Related

- `docs/rules/code-organization-guidance.md` — package dependency direction rules
- `docs/patterns/codebase-topology.md` — maps known coupling and its history
- `docs/archive/planning/contain-strong-package-cycles-and-event-boundary-gaps.md` — the 4-pass cycle repair that established this pattern
