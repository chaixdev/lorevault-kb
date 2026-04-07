# LV-085-1 — LLM temporal upgrades for edges [user story]

Status: NEEDS UPDATE

Context

- **UPDATED POST LV-085-0**: Triad-based Pass 2 is implemented and provides dual votes per adjacency (prev→curr, curr→next) with certainty levels and evidence.
- Current implementation creates TEMPORAL edges with properties (type, certainty, weight, source, rationale) but doesn't detect vote agreement/disagreement.
- Need to enhance existing triad processing to identify when dual votes agree (Confirmed) vs disagree (Contested) and preserve counter-votes for audit.

Problem

- Dual triad votes exist but agreement/disagreement detection is missing, losing valuable consensus information.
- No distinction between single-sided votes and confirmed agreements in the current edge state.
- Counter-vote information is lost when votes disagree, reducing audit trail quality.

Proposal

**Enhance existing `TriadEdgePersistenceService` with overlap analysis:**
- Detect vote agreement: When prev→curr and curr→prev are inverse-compatible (e.g., "meets" vs "met_by"), mark as Confirmed.
- Handle disagreement: When votes conflict, create Contested edge with primary vote + counter-vote preservation.
- Single-sided votes: When only one direction has a vote, mark as SingleSided.
- Upgrade certainty: Confirmed agreements can upgrade certainty level (e.g., WeaklyImplied + WeaklyImplied → StronglyImplied).

**Implementation approach:**
- Add vote comparison logic to existing `applyTriadAnalyses` method
- Extend TEMPORAL edge properties with: `state` (Confirmed/Contested/SingleSided), `counterVoteType`, `counterVoteCertainty`
- Use existing inverse relation detection from `TriadRelationInverter`

Scope

- Enhance existing triad edge persistence with overlap detection
- Add state management to TEMPORAL edge properties  
- Preserve counter-vote information for contested edges
- Maintain existing idempotent upsert behavior

Out of scope

- New relationship types (work within existing `:TEMPORAL` model)
- Public API changes (internal enhancement to existing triad processing)
- Evidence text extraction (rationale field already persisted, full evidence in LV-085-2)

Technical notes

- Use existing `RelationNormalizer.getCanonicalRelation()` for agreement detection
- Integrate with existing `TemporalEdgePort.upsertTemporalEdge()` - extend signature if needed for counter-vote fields
- Confirmed agreements can upgrade certainty: two WeaklyImplied votes → StronglyImplied confirmed edge

Acceptance criteria

- [ ] **Vote Agreement Detection**: When prev→curr and curr→next are inverse-compatible, create Confirmed edge with upgraded certainty
- [ ] **Vote Disagreement Handling**: When votes conflict, create Contested edge preserving both primary vote and counter-vote
- [ ] **Single-Sided Recognition**: When only one direction provides a vote, mark as SingleSided with appropriate certainty
- [ ] **Idempotency Maintained**: Re-running triad analysis produces consistent results without duplicates
- [ ] **Existing Behavior Preserved**: Non-overlapping scenes continue to work as before (single vote per edge)

Quality gates

- [ ] **Enhanced Unit Tests**: Vote agreement/disagreement scenarios with realistic triad data
- [ ] **Integration Tests**: End-to-end triad processing with overlap detection
- [ ] **Regression Tests**: Existing single-vote scenarios continue to work unchanged

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/Narrative event DAG.md
- **Dependencies**: Builds on LV-085-0 triad infrastructure
