# LV-085Problem

- Evidence fields (evidenceStart, evidenceEnd, evidenceChunkId) are persisted but not intelligently merged across multiple votes.
- Counter-vote evidence is lost when edges are updated, reducing audit trail quality.
- No clear strategy for consolidating evidence when votes agree vs handling conflicting evidence when votes disagree.

Proposal

**Enhance existing `TemporalEdgePort` and persistence layer with evidence merging:**

- **Evidence Consolidation for Confirmed Edges**: When votes agree, merge evidence spans (earliest start, latest end) and combine rationale text.
- **Evidence Preservation for Contested Edges**: Store primary evidence + counter-evidence separately with clear attribution.
- **Deterministic Merge Rules**: Consistent evidence handling across re-runs (longest span wins, rationale concatenation with separator).
- **Source Attribution**: Track which triad analysis contributed each piece of evidence.

**Implementation approach:**
- Extend `TemporalEdgeWriteRepository.upsertTemporalEdge` query to handle evidence arrays/merging
- Add evidence merge logic to `TriadEdgePersistenceService.upsert()` method
- Introduce evidence consolidation strategy for different edge states (Confirmed/Contested/SingleSided)potent promotion and evidence handling [refactor]

Context

- **UPDATED POST LV-085-0**: Triad-based edge persistence is implemented with idempotent upsert via `TemporalEdgePort.upsertTemporalEdge()`.
- Current implementation handles basic properties (type, certainty, weight, source, rationale) but lacks sophisticated evidence merging.
- With triad overlap detection from LV-085-1, need robust evidence consolidation for Confirmed vs Contested edges.

Problem

- Without careful design, repeated runs might duplicate edges or overwrite evidence/counter-votes inconsistently.

Proposal

- Define idempotent upsert semantics for neighbor TEMPORAL edges keyed by (sceneA, sceneB, source="triad-pass2").
- Merge evidence fields and retain counter-vote deterministically (e.g., edge.votes) while keeping a single primary relation for parity.
- Preserve existing MEETS link if promotion thresholds aren’t met; never remove the only link.

Scope

- Enhance evidence merging within existing idempotent upsert infrastructure
- Add evidence preservation for counter-votes in contested edges
- Implement deterministic evidence consolidation rules
- Maintain backward compatibility with existing evidence fields

Out of scope

- Evidence versioning/history (simple merge strategy for now)
- Evidence validation or content analysis (store as provided by triad analysis)
- Evidence chunk relationship management (focus on coordinate merging)

Technical notes

- Extend TEMPORAL edge schema with: `counterEvidenceStart`, `counterEvidenceEnd`, `counterRationale` for contested edges
- Use existing `upsertTemporalEdge` as foundation, enhance with evidence merge logic
- Evidence merge priority: explicit coordinates > inferred ranges, longer spans > shorter spans
- Work with LV-085-1 vote states: evidence merging strategy differs for Confirmed vs Contested edges

Acceptance criteria

- [ ] **Evidence Consolidation**: When votes agree, evidence spans are merged intelligently (earliest start, latest end)
- [ ] **Counter-Evidence Preservation**: When votes disagree, both primary and counter-evidence are stored with attribution
- [ ] **Idempotent Evidence Updates**: Re-running evidence consolidation produces consistent merged results
- [ ] **Deterministic Merging**: Same input evidence always produces same consolidated output
- [ ] **Rationale Combination**: Text rationales are combined appropriately (concatenation with separators for agreements, separate storage for conflicts)

Quality gates

- [ ] **Evidence Merge Unit Tests**: Various evidence overlap scenarios with deterministic outcomes
- [ ] **Idempotency Tests**: Multiple runs of same evidence produce identical results
- [ ] **Integration Tests**: End-to-end evidence flow from triad analysis through persistence
- [ ] **Backward Compatibility**: Existing single-evidence edges continue to work unchanged

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#085—llm-temporal-upgrades-+-neighbors-api-internal
- Research: ../../research/Narrative event DAG.md
- **Dependencies**: Builds on LV-085-0 persistence infrastructure, works with LV-085-1 vote states
