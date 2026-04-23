---
name: lorevault-coding-style
description: >
  LoreVault coding standards reference for Java 21 / Spring Boot 3.5 /
  Spring Data Neo4j / Spring AI. Load when implementing features or providing
  analysis criteria for a code review. The canonical rules live in
  [Coding Standards](../../../docs/rules/coding-standards.md) — this skill tells you how to apply them
  by concern track and what to cross-reference.
---

# LoreVault Coding Style

## Canonical Source

**Read [Coding Standards](../../../docs/rules/coding-standards.md) in full before applying any rules.**

That file is the authoritative source for LoreVault's coding standards.
Do not use this skill as a substitute for reading it — this skill tells you
how to navigate and apply those rules efficiently.

Cross-reference rules files for areas already covered elsewhere:

| Topic | Canonical file |
|-------|---------------|
| Exception taxonomy, retryability | [Exception Semantics](../../../docs/rules/exception-semantics.md) |
| Package naming, dependency direction | [Code Organization Guidance](../../../docs/rules/code-organization-guidance.md) |
| Service segmentation smells, port discipline | [Service Design Principles](../../../docs/rules/service-design-principles.md) |
| Log tiers, mandatory fields, log safety | [Logging Philosophy](../../../docs/rules/logging-philosophy.md) |
| Allen relation semantics, canonical polarity | [Temporal Relation Semantics](../../../docs/concepts/temporal-relation-semantics.md) |
| Maven profiles, test types, coverage gates | [Developer Testing Workflow](../../../docs/rules/developer-testing-workflow.md) |
| Ingestion executor, jobId, fan-in, AFTER_COMMIT scoping | [Ingestion Pipeline](../../../docs/patterns/ingestion/ingestion-pipeline.md) (Contributor Constraints section) |
| Module deps, coupling risks, shared models, schema init | [LoreVault Module Conventions](../../../docs/rules/lorevault-module-conventions.md) |

---

## Applying the Rules by Concern Track

When conducting a review, [Coding Standards](../../../docs/rules/coding-standards.md) maps to five concern
tracks. Apply the relevant sections to each track — do not try to apply all sections
in a single pass.

### Track A — Logic & Correctness
Apply:
- **Java 21 Idioms** — Optional discipline, null returns, record vs class, sealed types
- **Spring Boot 3.5** — trailing slash, `SecurityFilterChain`, slice test preference
- Cross-reference: [Exception Semantics](../../../docs/rules/exception-semantics.md) — exception taxonomy, retryability, swallow defects

### Track B — Data & Persistence
Apply:
- **Spring Data Neo4j** — multi-row hydration, parameterised Cypher, `Neo4jClient` auto-commit,
  relationship direction, index coverage, `MERGE` key discipline, projections
- **@Transactional Discipline** — `@Async` + `@Transactional` incompatibility, `readOnly`,
  proxy bypasses (private method, self-invocation), scope, `AFTER_COMMIT` handlers
- **Temporal Relation Semantics** — edge direction, canonical polarity, `MENTIONS` vs `TEMPORAL`
- Cross-reference: [Temporal Relation Semantics](../../../docs/concepts/temporal-relation-semantics.md)

### Track C — Async & Events
Apply:
- **Async & Executors** — named executor qualifier, MDC propagation, self-deadlock,
  `CompletableFuture` error handling, rejection policy
- **Event-Driven Pipeline** — delivery semantics, `@TransactionalEventListener`, idempotency,
  correlation IDs, fan-in correctness, circular chains, event immutability
- LoreVault-specific: [Ingestion Pipeline](../../../docs/patterns/ingestion/ingestion-pipeline.md) (Contributor Constraints section) —
  executor binding, jobId/correlationId fields, AFTER_COMMIT scoping, fan-in branch count

### Track D — Security & Observability
Apply:
- **Security** — Cypher injection, prompt injection, secrets in source, REST surface,
  deserialization, log safety
- Cross-reference: [Logging Philosophy](../../../docs/rules/logging-philosophy.md) for mandatory log fields,
  log levels, and what must never appear in logs

### Track E — Structure & Quality
Apply:
- **Package Boundary Discipline** — package-private types, prefer events over direct calls
- **Spring AI / LLM Integration** — null guards, structured output validation,
  timeouts, `maxTokens`, prompt injection, prompt externalization, token observability
- **Over-Abstraction** — single-impl interfaces, speculative generality, premature extraction
- **Composition vs Inheritance** — composition preference, Liskov, `default` method misuse
- **Lombok Discipline** — `@Data` on entities, `@ToString` exclusions, `@Builder.Default`,
  `@Singular`, `@RequiredArgsConstructor`, `@SneakyThrows`
- LoreVault-specific: [LoreVault Module Conventions](../../../docs/rules/lorevault-module-conventions.md) — module dependency
  direction, known coupling risks, shared model freeze, GraphSchemaInitializer
- Cross-reference: [Code Organization Guidance](../../../docs/rules/code-organization-guidance.md),
  [Service Design Principles](../../../docs/rules/service-design-principles.md)

---

## When Injecting Rules into a Sub-task

When an orchestrating agent prepares a parallel track sub-task prompt, extract only
the sections relevant to that track from [Coding Standards](../../../docs/rules/coding-standards.md).
Do not inject the full file into every sub-task prompt — that wastes tokens and
dilutes focus.

Use the track table above to identify which sections to extract.
Each sub-task should receive: its section(s) + the relevant cross-reference file paths.
