# LV-082-3 — Mapping and weighting tests [user story]

**Status: COMPLETE** - All required tests implemented with 30 new tests covering enum completeness, weight mapping, and field serialization.

Context

- Establish correctness of enumeration mapping and weight calculations before wiring ingestion.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/event-model.md, ../../research/tests-and-qa.md

Problem

- Without tests, certainty→weight mapping and enum values could drift, breaking temporal ordering semantics later.

Proposal

- Create unit tests to verify enum values and certainty→weight mapping.
- Add minimal repository mapping tests for TemporalEdge properties.

Scope

- Unit: CertaintyLevel→weight constants; presence and spelling of enum values; defensive default behavior.
- Mapping: TemporalEdge serialization of rationale and evidence fields.

Out of scope

- Ingestion pipeline tests
- API tests

Technical notes

- Include at least one edge case: unknown certainty defaults to Heuristic weight.

Acceptance criteria

- [x] Unit tests assert mapping constants (0.95, 0.8, 0.6, 0.5) 
- [x] Unit tests assert enum set { BEFORE, MEETS, OVERLAPS, DURING, STARTS, FINISHES, EQUALS } and certainty set
- [x] Mapping tests confirm rationale and evidence fields stored/read correctly

Quality gates

- [x] Tests pass locally and in CI; coverage thresholds met
- [x] No new ArchUnit violations

## Implementation Notes

### Files Modified

1. **TemporalRelation.java** - Added missing MEETS and EQUALS values to complete Allen's interval algebra
2. **CertaintyWeights.java** - Added null handling to default to HEURISTIC weight (0.5) for unknown certainty

### Tests Created

1. **TemporalRelationTest.java** - Verifies enum completeness and correct values
2. **CertaintyLevelTest.java** - Verifies all certainty levels exist and correct string values  
3. **CertaintyWeightsTest.java** - Enhanced with comprehensive weight mapping tests and edge cases
4. **TemporalEdgeTest.java** - Mapping tests for all TemporalEdge fields including rationale, source, evidence offsets

### Test Coverage

- **30 new tests** covering enum completeness, weight mapping, and field serialization
- **Edge cases**: null certainty defaults to HEURISTIC weight (0.5)
- **Field mapping**: All TemporalEdge properties including UUID, Long offsets, and text fields
- **Special characters**: Unicode, quotes, multiline text in rationale/source fields

All tests pass (197/197) with no regressions or ArchUnit violations.

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#082—event-shell-and-storage-readiness
- Research: ../../research/event-model.md
