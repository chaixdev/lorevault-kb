# ADR 010: Practical Allen Relation Usage

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault keeps the full Allen interval relation set documented, but does **not** treat the entire set as equally useful for inferred narrative temporal modeling.

In particular:

- `MEETS` and `MET_BY` are **deprecated for inferred use**
- structural adjacency must use `NEXT_IN_READING_ORDER`, not an Allen relation
- practical inferred precedence should prefer `BEFORE` / `AFTER`
- the remaining Allen relations stay documented and available for selective use where narrative evidence actually supports them

## Why

Allen algebra is theoretically precise, but LoreVault works on narrative evidence, not clock-perfect interval boundaries.

That creates a mismatch:

- in theory, `MEETS` means one interval ends exactly when another begins, with no intervening moment
- in practice, narrative text rarely justifies that level of precision
- using `MEETS` as a normal inferred relation creates fake precision and compatibility noise, especially when `MEETS` and `BEFORE` both functionally mean “this happens earlier” in product use

LoreVault also needs a clean distinction between:

- **reading-order adjacency**
- **temporal inference**

Using `MEETS` for structural adjacency blurred those two ideas.

## Options Considered

### 1. Keep the full Allen relation set as equally valid inferred output

Rejected.

This preserves theoretical completeness, but it overstates precision for narrative inference and makes `MEETS` vs `BEFORE` appear meaningfully different when they often are not in practice.

### 2. Collapse the model to a much smaller precedence-only subset

Rejected.

This would remove too much expressive power. Relations such as `OVERLAPS`, `DURING`, and `CONTAINS` still matter for flashbacks, nested scenes/events, and extra-local temporal anchors.

### 3. Keep the full set documented, but annotate practical usage and deprecate `MEETS` / `MET_BY` for inferred use

Accepted.

This keeps theoretical completeness visible while aligning the inferred model with what narrative evidence can honestly support.

## Full Allen Relation Set and LoreVault Usage

LoreVault documents the full Allen interval family below.

### Preferred for practical inferred use

| Relation | Inverse | LoreVault stance | Notes |
|---|---|---|---|
| `BEFORE` | `AFTER` | Use | Preferred coarse precedence relation for inferred narrative ordering |
| `OVERLAPS` | `OVERLAPPED_BY` | Use selectively | Useful when scenes/events are clearly concurrent or intercut |
| `DURING` | `CONTAINS` | Use | Important for scene↔event, scene↔landmark, and event↔arc anchoring |
| `STARTS` | `STARTED_BY` | Use selectively | Valid when evidence strongly supports shared start boundary |
| `FINISHES` | `FINISHED_BY` | Use selectively | Valid when evidence strongly supports shared end boundary |
| `EQUALS` | — | Use narrowly | Best reserved for strong equivalence or anchoring, not as a fallback |

### Documented but deprecated for inferred use

| Relation | Inverse | LoreVault stance | Notes |
|---|---|---|---|
| `MEETS` | `MET_BY` | Deprecated for inferred use | Too fine-grained for normal narrative inference; replace structural use with `NEXT_IN_READING_ORDER`, and use `BEFORE` / `AFTER` for practical precedence |

## Practical Interpretation Rules

### Structural adjacency is not temporal semantics

If two scenes are neighbors in reading/publication order, the graph should say:

- `(earlier)-[:NEXT_IN_READING_ORDER]->(later)`

That relationship means only that the scenes are adjacent in the source narrative structure.

It does **not** imply:

- `MEETS`
- `BEFORE`
- any other Allen relation

### Practical precedence should avoid fake precision

When the system infers only that one scene or event precedes another, it should use:

- `BEFORE`

and not escalate to:

- `MEETS`

unless LoreVault later adopts a much stricter evidence standard for exact-boundary semantics.

### Theoretical completeness remains documented

The Allen family remains part of LoreVault's conceptual vocabulary.

That matters because:

- the concept docs still discuss interval reasoning in full Allen terms
- future manual workflows, imports, or specialized modeling may still want the full family visible
- extra-local temporal resolution may need richer relations than simple precedence

Deprecating `MEETS` for inferred use is therefore a practical modeling choice, not a claim that Allen algebra is wrong.

## Implications

- `MEETS` / `MET_BY` should no longer be treated as normal inferred scene temporal outputs
- compatibility/coarsening policy should treat `MEETS`-like prior behavior as a practical precedence case rather than as a separate durable inferred truth
- any old heuristic use of `MEETS` should be reconsidered as either:
  - `NEXT_IN_READING_ORDER` for structure, or
  - `BEFORE` / `AFTER` for temporal inference
- documentation and future implementation should explicitly distinguish structural adjacency from temporal comprehension
- LoreVault retains a richer interval vocabulary for cases where the evidence actually supports it, especially around overlaps, containment, and extra-local anchor relationships
