# ADR 010: Practical Allen Relation Usage

**Status:** Accepted  
**Date:** April 2026

## Context

LoreVault infers temporal relations from narrative text, where interval boundaries are often incomplete or implicit.
The full Allen relation set remains conceptually useful, but not all relations are equally reliable for inferred output.
LoreVault also needs one consistent rule for how directed relations are interpreted, normalized, and persisted.

## Decision

1. `MEETS` / `MET_BY` are deprecated for inferred use.
2. `EQUALS` is deprecated for inferred use.
3. Structural adjacency uses `NEXT_IN_READING_ORDER`, not Allen relations.
4. Coarse inferred precedence uses `BEFORE` / `AFTER`.
5. `OVERLAPS` is compatible with `DURING` / `CONTAINS` for the same oriented pair; keep the more specific enclosure relation.
6. `DURING` vs `CONTAINS` for the same oriented pair is contradictory and remains an ambiguity/conflict case.
7. Legacy inferred `EQUALS` is treated as a coarse overlap case unless manually/specially justified.
8. Directed relation semantics are always read from source to target: `A -> B` means “A happens before/after/during/contains/overlaps B”.
9. LoreVault persists one canonical polarity per inverse pair and normalizes inverse forms by flipping endpoints.
10. Prompt-facing extraction vocabulary may include inverse/local descriptors, but durable runtime/storage semantics must be canonicalized before persistence.

## Rationale

- `MEETS` and `EQUALS` imply precision that inferred narrative evidence usually cannot support.
- `OVERLAPS` is often the correct weak-concurrency fallback when enclosure evidence is incomplete.
- Treating `OVERLAPS` vs enclosure as hard conflict overproduces ambiguity without improving correctness.
- `DURING` and `CONTAINS` assert opposite enclosure direction for one oriented pair and are true conflicts.
- A graph should store one temporal fact once, not duplicate it in both direct and inverse forms.
- Prompt ergonomics and storage ergonomics are different concerns: the model may reason in pair-local language, while the graph should preserve a single normalized truth.

## Consequences

- Inferred outputs should not emit `MEETS` / `MET_BY` / `EQUALS` as normal durable labels.
- Existing `MEETS`-like inferred behavior should be interpreted as practical precedence (`BEFORE` / `AFTER`) or structure (`NEXT_IN_READING_ORDER`) depending on context.
- Existing inferred `EQUALS` should be coarsened to overlap semantics unless explicitly curated.
- Reconciliation logic should prefer `DURING`/`CONTAINS` over `OVERLAPS` when compatible, and still raise ambiguity for `DURING` vs `CONTAINS`.
- All durable temporal semantics should be interpreted from source node to target node.
- Inverse pairs are input/output conveniences, not separate persisted truths.
- LoreVault must maintain a clear separation between:
  - prompt-facing extraction vocabulary
  - internal canonical runtime/storage vocabulary

## Canonical Operational References

This ADR records **why** these temporal choices were made. Use the following docs for current operational guidance:

- [temporal-relation-semantics.md](../concepts/temporal-relation-semantics.md) — normative contributor/operator interpretation rules
- [graph-shape-specification.md](../patterns/content/graph-shape-specification.md) — present-state graph shape and modeling/readability patterns
