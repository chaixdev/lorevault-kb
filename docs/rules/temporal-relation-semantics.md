# Temporal Relation Semantics

**Applies to:** temporal relation extraction, persistence, graph inspection, and contributor/operator interpretation.

This document defines the normative semantics rules for temporal relations in LoreVault.

Use this for statements about meaning and interpretation (`must`, `must not`, `cannot`).

For graph shape and modeling patterns, see `../patterns/graph-shape-specification.md`.

---

## Rule 1 — Read Directed Relations Source to Target

For any directed temporal relation `A -> B`, interpretation is always **A relative to B**.

Examples:

- `A -> B : BEFORE` means A happens before B.
- `A -> B : AFTER` means A happens after B.
- `A -> B : DURING` means A happens during B.
- `A -> B : CONTAINS` means A contains B.
- `A -> B : OVERLAPS` means A overlaps B.

This rule is mandatory across all temporal pairings.

---

## Rule 2 — Canonicalize Inverse Pairs Before Persistence

LoreVault stores one canonical polarity per inverse pair.

Inverse forms must be normalized by endpoint flip before durable persistence.

Canonical durable targets are:

- `R:temporal.before`
- `R:temporal.overlaps`
- `R:temporal.during`
- `R:temporal.starts`
- `R:temporal.finishes`

Inverse forms (`after`, `contains`, `overlapped_by`, `started_by`, `finished_by`) are input/output conveniences, not separate durable truths.

---

## Rule 3 — Deprecated Inferred Labels

For inferred temporal output:

- `MEETS` / `MET_BY` are deprecated.
- `EQUALS` is deprecated.

Structural adjacency must use `NEXT_IN_READING_ORDER` rather than Allen temporal labels.

---

## Rule 4 — Practical Inferred Usage Policy

| Relation | Inverse | Inferred policy |
|---|---|---|
| `BEFORE` | `AFTER` | Preferred for coarse precedence |
| `OVERLAPS` | `OVERLAPPED_BY` | Allowed as weak concurrency/default fallback |
| `DURING` | `CONTAINS` | Allowed when enclosure evidence is strong |
| `STARTS` | `STARTED_BY` | Selective use only with strong boundary evidence |
| `FINISHES` | `FINISHED_BY` | Selective use only with strong boundary evidence |
| `MEETS` | `MET_BY` | Deprecated for inferred use |
| `EQUALS` | — | Deprecated for inferred use |

Additional conflict handling:

- `OVERLAPS` is compatible with `DURING` / `CONTAINS` for the same oriented pair; keep the more specific enclosure relation.
- `DURING` vs `CONTAINS` for the same oriented pair is contradictory and remains an ambiguity/conflict case.

---

## Rule 5 — Prompt Vocabulary vs Runtime Vocabulary

LoreVault separates:

1. **Prompt-facing extraction vocabulary**
   - may include pair-local inverse descriptors (e.g., `before/after`, `during/contains`)
   - exists to make LLM reasoning clearer

2. **Runtime/storage vocabulary**
   - must be canonicalized before persistence
   - must not preserve both inverse polarities as independent durable truths

Prompt labels are extraction syntax, not final storage contract.

---

## Rule 6 — Evidence-Lane Interpretation Guardrail

`MENTIONS` edges are evidence-link semantics, not temporal-edge semantics.

For example:

- `Scene -[:MENTIONS]-> EventMention`

must be read as “this scene mentions this evidence node,” not as a timeline ordering edge.

If event mention data includes temporal qualifiers (for example `sceneRelativeRelation`), treat them as mention-level extracted qualifiers, not as inverted scene ordering.

---

## Rule 7 — Inspection and Review Checklist

When validating temporal persistence:

1. Use `TEMPORAL` edges as primary source for timeline ordering.
2. Confirm source-to-target interpretation for each directed relation.
3. Confirm inverse canonicalization has been applied (no duplicate inverse durable truth).
4. Confirm deprecated inferred labels are not used for normal durable storage.
5. Do not infer timeline ordering from `MENTIONS` edge direction.

---

## See Also

- `../adr/010-practical-allen-relation-usage.md` — architectural decision and rationale
- `../patterns/graph-shape-specification.md` — graph shape and modeling/readability patterns
- `../patterns/triad-analysis.md` — triad temporal inference mechanism
