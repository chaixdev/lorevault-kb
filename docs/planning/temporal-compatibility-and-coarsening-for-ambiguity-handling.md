# Temporal ambiguity handling needs compatibility and coarsening rules

**Status:** PARKED

## Summary

The current temporal-link persistence path treats any differing `TEMPORAL` relation labels as a hard conflict and records an `AMBIGUOUS_RELATION`.

This is likely too strict for pragmatic V1 usage, especially for relation pairs that agree on precedence but differ in specificity, such as `MEETS` versus `BEFORE`.

## Problem

The current ambiguity decision is based on raw string inequality between the existing persisted temporal relation and the incoming inferred relation.

That means the system cannot currently distinguish between:

- truly incompatible temporal claims
- equivalent claims expressed in inverse orientation
- compatible claims where one relation is more specific than another

This creates ambiguity records in cases that may be better understood as refinement, coarsening, or compatible precedence rather than real disagreement.

## Product Context

- Operators reviewing the graph can see ambiguity edges that may overstate disagreement.
- Excess ambiguity noise makes UAT harder to interpret and can reduce trust in the temporal model.
- If pragmatic compatibility cases are common, the current behavior may create more reconciliation burden than useful signal.

## Technical Context

Relevant implementation areas:

- `lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalEdgeWriteRepository.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalRelation.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/CanonicalRelation.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/RelationNormalizer.java`
- `lorevault-api/src/main/java/com/lorevault/api/timeline/TriadRelationInverter.java`

Current observed behavior:

1. `TriadEdgePersistenceService` reads the existing persisted `temporalRelation`
2. if the incoming relation string is different, it writes `AMBIGUOUS_RELATION`
3. no compatibility, subsumption, or coarsening check exists before that decision

There is already normalization infrastructure for inverse/orientation handling, but there is not yet a semantic model for compatibility between distinct forward relations.

## Scope

- Preserve the current findings about overly strict ambiguity classification.
- Capture the need for compatibility/coarsening semantics in temporal-link persistence.
- Keep the work framed as a bounded semantic policy problem rather than a full temporal-model redesign.

## Out of Scope

- Implementing the policy now
- Redesigning the entire temporal graph model
- Reworking all read-side consumers in the same item unless needed by the chosen policy
- Preserving every possible Allen-interval nuance in V1 if the product only needs pragmatic conflict reduction

## Known Constraints / Prior Findings

### Confirmed implementation behavior

- The current ambiguity trigger is localized in `TriadEdgePersistenceService.upsertWithAmbiguityHandling(...)`.
- The effective condition is: existing type present and not equal to incoming type.
- That means all non-identical relation labels are currently treated as conflicts.

### Existing semantic infrastructure

- `TemporalRelation` contains the broader relation set.
- `CanonicalRelation` models canonical forward relations.
- `RelationNormalizer` normalizes inverse relations into canonical orientation.
- `TriadRelationInverter` maps triad labels for inverted labeling use.

### Important limitation of existing normalization

- Current normalization handles direction/orientation.
- It does **not** model compatibility or specificity between distinct canonical relations.
- For example, `MEETS` vs `BEFORE` is not currently treated as compatible.

### Why this matters

- Some relation pairs are plausibly compatible by subsumption or specificity.
- `MEETS` can be seen as a stricter form of `BEFORE` in a pragmatic precedence-oriented interpretation.
- If such pairs are always marked ambiguous, the graph may accumulate avoidable ambiguity noise.

### Current consumer expectations

- Downstream consumers often care that a precedence edge exists, not always which exact Allen subtype it carries.
- That suggests there may be room for a bounded write-path policy improvement without a full consumer redesign.

## Open Questions

- Which relation pairs should count as compatible rather than conflicting?
- Should compatibility be evaluated only after canonical normalization?
- For compatible-but-different pairs, should the system keep the more specific relation, keep the coarser relation, or preserve the existing value while storing refinement evidence elsewhere?
- If ambiguity is no longer written for compatible pairs, how should evidence of refinement or disagreement history be retained?
- Should this remain a local write-path rule, or become a broader temporal semantics policy shared across other parts of the system?

## Success Criteria

- The system can distinguish true temporal conflicts from compatible or refinement-style differences.
- Pragmatic compatible cases no longer generate avoidable `AMBIGUOUS_RELATION` noise.
- The chosen policy is explicit enough to test and reason about.
- The change remains bounded and does not accidentally force a broad redesign unless later requirements justify it.

## Links

- Related planning items:
  - `./cross-chapter-temporal-linking-materialization-gap.md`
  - `./scene-temporal-linking-gaps.md`
- Related implementation files:
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/TriadEdgePersistenceService.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalEdgeWriteRepository.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/RelationNormalizer.java`
  - `../../lorevault-api/src/main/java/com/lorevault/api/timeline/TemporalRelation.java`
  - `../../lorevault-api/src/test/java/com/lorevault/api/timeline/RelationNormalizerTest.java`
  - `../../lorevault-api/src/test/java/com/lorevault/api/timeline/TriadRelationInverterTest.java`
