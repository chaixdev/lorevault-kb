# Agentic TDD Workflow — Brainstorm

**Date:** 2026-04-24  
**Status:** exploratory

---

## Problem

The standard agentic development loop has a semantic drift problem.

Agents write tests for the code they just wrote. Syntax errors and type errors are already caught by compilers and LSPs — those are solved problems. The new failure mode is semantic: an agent misunderstands a requirement, implements accordingly, then writes tests that validate the misunderstanding. Everything is green. Nothing is right.

Tests as documentation also degrades. Tests written to match implementation rather than to specify intent are noise, not signal.

---

## Core Insight

The test suite is a **specification**, not a verification artifact.

When the same agent writes both tests and source, it is self-grading. The separation of concerns that makes TDD valuable — spec first, implementation second — collapses.

---

## Proposed Paradigm: Two-Agent Split with Scoped Write Permissions

| Agent | Read scope | Write scope |
|---|---|---|
| Spec agent | entire project | `src/test/java/**` only |
| Impl agent | entire project | `src/main/java/**`, `src/main/resources/**` only |
| Neither | — | ArchUnit tests, `docs/rules/`, frozen invariants |

The filesystem constraint is the enforcement mechanism. The impl agent cannot rationalize a failure by rewriting the goalpost. The spec agent cannot patch implementation details to make its tests easier to satisfy.

---

## The Loop

```
explore → propose → [spec session] → [impl agent] → verify → promote
```

### Spec session (new step between proposal and implementation)

- Human and spec agent iterate collaboratively on test design
- Human stays in the semantic layer: what does "correct" mean here?
- Spec agent translates intent into executable tests
- The proposal doc is *context* for the spec session, not the spec itself
- Output: a committed, failing test suite that represents accepted intent
- The proposal is not implementation-ready until this step completes

### Implementation

- Orchestrator delegates to impl agent with the failing test suite as the target
- Impl agent reads the full codebase for context but writes only to source
- Completion gate: `mvn verify` green (not just compilation)
- If the impl agent cannot satisfy a test without architectural conflict, it escalates — it does not find a clever workaround

### Escalation path

The impl agent must be able to say:
> "Satisfying this test requires X, which conflicts with Y. Halting."

This surfaces real disagreements rather than burying them in green-but-wrong implementations. The escalation goes back to the human, not into a retry loop.

---

## Why This Is Stronger for Established Projects

This paradigm assumes:

- General architecture is stable — signatures and module boundaries don't churn
- Tests are written at the right abstraction level (interfaces, public contracts, observable outcomes)
- The docs layer is curated enough to inform the spec agent about domain intent

Under these conditions:

- The signature-refactoring breakage concern largely dissolves (tests at the right level don't couple to impl internals)
- Test infrastructure (@SpringBootTest config, Testcontainers) is already stable
- The spec agent can write meaningful tests from docs + domain context without needing to peek at impl details

---

## Test Abstraction Level

Overly granular unit tests are a pre-existing problem, independent of agentic coding. They were already:

- tightly coupled to implementation
- expensive to maintain through refactors
- low signal as documentation

The spec agent should be disciplined about testing **interfaces and abstractions, not internals**. Verify outcomes. If a spec agent produces tests that break on impl-internal renames, that is a spec quality problem — not a workflow problem.

---

## Human Role

The human drives the spec session. This is not optional.

The spec agent infers intent from docs and context, but the human is the authority on what "correct" means. The spec session is effectively a requirements review. Two parties pressure-test the spec before it becomes frozen tests — the impl agent then gets a higher-signal target.

If the spec agent were fully autonomous (inferring intent without human input), you get a subtler form of self-grading: one step removed, but still present.

---

## Fit with Existing Workflow

The existing workflow core loop is:

1. identify a problem, opportunity, or missing capability
2. discuss cause, constraints, and solution space
3. write an exploratory solution design proposal document
4. implement and iterate with verification and UAT
5. append implementation notes and deviations to proposal document
6. promote accepted truth into canonical top-level docs
7. keep not-yet-done work in planning documentation

The agentic TDD loop inserts between steps 3 and 4:

> **3a.** human + spec agent iterate on test design; spec agent writes tests; tests committed as accepted intent  
> **3b.** orchestrator delegates step 4 to impl agent with test suite as target

UAT in step 4 becomes partly redundant if the spec session was rigorous — passing tests at the right abstraction level *are* UAT.

---

## Open Questions

- How does the spec agent handle domain concepts it doesn't fully understand from docs alone? Does the human explain inline, or is there a structured way to feed additional context?
- What is the right granularity of the spec session? One session per proposal? Per feature slice?
- How does the orchestrator decide when to escalate impl agent failures to the human vs. retrying?
- Should the spec agent have a read-only view of existing tests to maintain consistency, or is a fresh read of docs + interfaces sufficient?
- Does this workflow need a formal "spec review" artifact, or is the committed test diff sufficient as a record of accepted intent?
