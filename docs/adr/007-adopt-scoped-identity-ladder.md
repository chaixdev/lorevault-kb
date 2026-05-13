# ADR 007: Adopt Scoped Identity Ladder

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault models extracted identity information as a scoped ladder instead of flattening directly into one canonical `Individual` node.

The accepted shape is:

- `Scene -[:CONTAINS]-> IndividualMention`
- `IndividualMention -[:REFERS_TO]-> ChapterIndividual`
- `ChapterIndividual -[:REFERS_TO]-> BookIndividual`

`IndividualMention` remains the evidence-bearing layer. `ChapterIndividual` is the first real consolidation boundary. `BookIndividual` is kept intentionally thin and exists to connect chapter-local identities across a book.

## Why

- A mention is scene-local evidence, not canonical truth
- Chapter scope is the first useful consolidation boundary with low enough ambiguity to be operationally valuable
- Book scope is useful for continuity, but should not become a broad cross-chapter fact bag
- The scope-explicit ladder preserves provenance while still enabling cleaner traversal and future retrieval value
- The shape aligns with LoreVault's spoiler-aware direction better than a flat canonical entity model

## Alternatives Considered

**Single canonical `Individual` from the start** — flatten all extracted evidence directly into one book-wide or global entity type. Rejected: it collapses evidence and interpretation too early, loses semantic clarity, and creates more room for premature over-merging.

**Mention-only model** — keep `IndividualMention` nodes without any higher identity layers. Rejected: it preserves provenance but leaves too much duplicate structure in the graph and limits retrieval value.

**Chapter-only resolution with no book layer** — consolidate only to `ChapterIndividual` and stop there. Rejected: chapter-local identity is useful, but LoreVault also needs a thin continuity layer across chapters in the same book.

## Implications

- Future identity work should preserve the evidence-first layering model
- `BookIndividual` should remain structurally useful but semantically conservative
- Better candidate generation and scoring can be added later without replacing the ladder itself
- Related entity types can reuse the same scoped pattern if they prove valuable (`<Type>Mention -> Chapter<Type> -> Book<Type>`)
