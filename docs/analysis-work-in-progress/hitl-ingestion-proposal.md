# Human-in-the-Loop (HITL) Ingestion Proposal

**Date:** August 16, 2025  
**Status:** Discussion Draft  
**Proposal Owner:** Technical Lead Team  
**Stakeholders:** KG Team, Retrieval Team, Product

## Problem Statement

Chapter ingestion represents the most critical data entry point in LoreVault. The quality of entity extraction, claim normalization, and graph projection directly impacts all downstream capabilities (search, retrieval, user experience). Currently, our pipeline is fully automated, creating risk of:

- **Mapping Errors:** Incorrect entity resolution or claim normalization that propagates through the graph
- **Quality Drift:** No feedback mechanism to improve extraction accuracy over time  
- **Trust Gap:** End users cannot validate or understand how their content became structured knowledge
- **Difficult Recovery:** Errors discovered later require complex backfill operations

## Proposed Solution: Draft-then-Apply Workflow

Implement a human-in-the-loop mechanism where intended graph projections are first made available as drafts to admin/curator users for validation before being applied to the canonical graph.

### Core Workflow
1. **Ingestion → Draft:** Chapter processing creates a draft workspace with proposed entities, claims, and relationships
2. **Human Review:** Curator reviews the draft via a visual interface, can amend mappings and validate temporal links
3. **Apply or Reject:** After validation, changes are atomically applied to the canonical graph or rejected
4. **Feedback Loop:** Review decisions improve future extraction accuracy

### Technical Approach

**Option 1: Single-DB Labeled Drafts (Recommended)**

**Labeling Strategy: `:Canonical` vs `:Provisional`**

After analysis, we recommend using `:Canonical` labels on verified data rather than `:Provisional` on draft data:

```cypher
// Draft nodes start without :Canonical label (safe by default)
CREATE (e:Entity {name: "Luke Skywalker", confidence: 0.85, workspaceId: "draft-123"})
CREATE (event:Event {title: "Battle of Hoth", workspaceId: "draft-123"})

// Draft relationships also lack :Canonical
CREATE (e)-[:PARTICIPATED_IN {certainty: 0.8, workspaceId: "draft-123"}]->(event)

// Promotion adds :Canonical and cleans draft metadata
MATCH (n {workspaceId: "draft-123"})
SET n:Canonical
REMOVE n.workspaceId, n.confidence
```

**Why `:Canonical` is Superior:**
- **Fail-safe Design:** Draft data cannot accidentally leak into production queries
- **Explicit Intent:** All production queries must explicitly filter for `:Canonical` data
- **Positive Promotion:** Adding `:Canonical` is a clear positive action vs removing `:Provisional`
- **Debugging Clarity:** Missing `:Canonical` label immediately identifies draft data

**Query Patterns:**
```cypher
-- Production queries (safe by design)
MATCH (c:Canonical:Character)-[r:Canonical]-(l:Canonical:Location)
WHERE c.name CONTAINS "Luke"
RETURN c, r, l

-- Draft review queries
MATCH (e:Entity) WHERE e.workspaceId = "draft-123" RETURN e
```

**Benefits:**
- Single database, no cross-DB synchronization
- Direct graph exploration during review
- Atomic apply via single transaction with MERGEs
- Natural audit trail preservation
- Built-in safety against data leakage

**Option 2: Change-Set First**
- Persist normalized change sets (ProposedNode/ProposedRel) in structured format
- Preview via on-the-fly virtual graph rendering
- Apply generates and executes Cypher batch

**Benefits:**
- Simpler diffing and rollback
- Platform-agnostic storage
- Explicit change modeling

**Option 3: Hybrid Approach (Maximum Safety)**
For critical production environments, combine both labeling strategies:
```cypher
// Draft nodes carry both workspace ID and explicit draft label
CREATE (e:Draft:Entity {name: "Luke", workspaceId: "draft-123"})

// Promotion removes :Draft, adds :Canonical, cleans workspace metadata  
MATCH (e:Draft:Entity {workspaceId: "draft-123"})
REMOVE e:Draft, e.workspaceId
SET e:Canonical
```

This makes draft vs canonical status completely unambiguous in both directions while providing defense-in-depth against data leakage.

### Minimal API Surface
```http
POST /api/admin/ingest?mode=draft
  → {workspaceId: "01234567890ABCDEF", status: "pending_review"}

GET /api/admin/drafts/{workspaceId}/graph
  → {nodes: [...], relationships: [...], conflicts: [...], summary: {...}}

POST /api/admin/drafts/{workspaceId}/approve
  → {appliedChanges: {...}, canonicalIds: [...]}

POST /api/admin/drafts/{workspaceId}/reject
  → {rejected: true, reason: "..."}

GET /api/capabilities
  → {supports: {draftMode: true, reviewWorkflow: true}}
```

### Review UI Components

**Draft Graph Viewer:**
- Interactive graph visualization (Cytoscape.js recommended over Neovis.js for operational control)
- Side panel showing entity details, confidence scores, source citations
- Conflict highlighting for ambiguous mappings or contradictions
- Visual distinction between draft nodes (no `:Canonical` label) and any existing canonical data

**Entity Resolution Panel:**
- Proposed entity matches with confidence scores
- Manual override capability for incorrect mappings
- Alias and canonical name editing
- **Conflict Resolution UI:** When draft entities map to multiple canonical candidates, show similarity scores and let curators pick or merge

**Temporal Links Validator:**
- Scene-to-scene relationships with Allen interval types
- Rationale display linking to source text offsets
- Manual relationship type adjustment

**Apply Preview:**
- Cypher command preview showing exactly what will be executed
- Impact summary (nodes that will gain `:Canonical`, relationships added, conflicts resolved)
- **Atomic Transaction Guarantee:** All changes in a draft workspace promote together or fail together

**Advanced UX Patterns:**
- **Batch Review:** Group similar changes (e.g., all "Character mentions" or "Location extractions") for efficient processing
- **Progressive Disclosure:** Start with high-impact/low-confidence changes, hide obvious accepts until requested
- **Contextual Validation:** Show source text snippets alongside graph changes so curators understand extraction context

## Implementation Phases

### Phase 1: Foundation (Sprint 1)
- **Backend:** Draft node labeling, workspace creation, basic approve/reject workflow
- **API:** Core endpoints with draft mode toggle
- **Tests:** Schema validation, approve transaction idempotency, draft isolation
- **Config:** Feature flag for draft mode (enabled in dev, optional in prod)

### Phase 2: Review Interface (Sprint 2)  
- **UI:** Basic graph viewer with approve/reject buttons
- **Backend:** Graph serialization for frontend, conflict detection
- **Integration:** Connect review UI to draft APIs
- **Tests:** End-to-end review workflow

### Phase 3: Advanced Features (Future)
- **Partial Approval:** Per-node/edge approval for granular control
- **Batch Operations:** Approve multiple drafts, pattern-based auto-approval
- **Analytics:** Review decision tracking, accuracy metrics, curator performance
- **Integration:** Auto-approval rules based on confidence thresholds

## Configuration Integration

```yaml
# Development Configuration 
lorevault:
  ingestion:
    review:
      enabled: true              # Enable HITL workflow
      default-mode: draft        # draft|direct|auto
      ttl-hours: 168            # Auto-cleanup abandoned drafts (7 days)
      auto-approve:
        confidence-threshold: 0.9 # Auto-approve high-confidence extractions
        trusted-sources: []       # Sources that bypass review
    ui:
      draft-visualization:
        max-nodes: 500           # Limit graph size for performance
        layout: "cose"           # Cytoscape layout algorithm
        show-provenance: true    # Display claim citations
```

## Benefits Analysis

### Quality Improvement
- **Early Feedback:** Catch mapping errors before they propagate
- **Curator Training:** Build domain expertise in reviewers
- **Ground Truth:** Create validated datasets for ML model improvement
- **Consistency:** Establish canonical patterns for entity resolution

### Risk Reduction  
- **Data Integrity:** Human validation reduces graph corruption risk
- **Trust Building:** Transparent review process builds user confidence
- **Recovery Prevention:** Avoid costly backfill operations from mapping errors
- **Compliance:** Audit trail for sensitive content domains

### Development Benefits
- **Debugging:** Visual inspection of extraction pipeline outputs
- **Iteration:** Rapid feedback on extraction algorithm changes
- **Validation:** Test new entity types and claim patterns safely

## Challenges and Mitigations

### Performance Impact
- **Challenge:** Review bottleneck could slow ingestion throughput
- **Mitigation:** 
  - Auto-approval for high-confidence extractions
  - Batch review capabilities
  - Async processing with queue management

### Reviewer Fatigue
- **Challenge:** Human reviewers may become overwhelmed or inaccurate over time
- **Mitigation:**
  - Prioritize reviews by impact/confidence
  - Provide clear UX with minimal cognitive load
  - Rotate reviewers and track accuracy metrics

### Technical Complexity
- **Challenge:** Draft isolation and atomic apply operations add complexity
- **Mitigation:** 
  - Start with simple `:Canonical` labeling approach (fail-safe by design)
  - Comprehensive test coverage for edge cases
  - Clear rollback procedures and dependency ordering
  - **TTL Cleanup:** Automated removal of old draft data to prevent accumulation
  
```cypher
-- Automated cleanup of abandoned drafts
MATCH (n) 
WHERE n.workspaceId IS NOT NULL 
  AND n.createdAt < datetime() - duration({days: 7})
DETACH DELETE n
```

### Data Safety
- **Challenge:** Accidental mixing of draft and canonical data in queries
- **Mitigation:**
  - `:Canonical` labeling makes production queries explicit and safe by default
  - All retrieval services must include `:Canonical` filters
  - Integration tests that fail if any production query returns unlabeled data
  - Clear documentation and code review guidelines for query patterns

### User Experience
- **Challenge:** Additional step may frustrate users expecting immediate results
- **Mitigation:**
  - Clear communication about review timeline
  - Partial results available during review
  - Optional bypass for trusted users/content

## Timeline Impact Assessment

### Development Overhead
- **Foundation:** +2 sprints for core draft/apply workflow and basic UI
- **Ongoing:** ~5-10% additional effort in ingestion features for draft path maintenance
- **Testing:** Additional integration tests for review workflow scenarios

### Delivery Impact
- **v0.5.0:** Add draft mode toggle and basic approval workflow  
- **v0.5.1:** Enhanced review UI with graph visualization
- **Net Timeline:** +2 sprints on critical path, but can be developed in parallel with other features

### Operational Considerations
- **Reviewer Training:** Time investment in curator onboarding
- **Process Definition:** Establish review guidelines and SLAs
- **Monitoring:** Track review queue depth and processing times

## Success Metrics

### Quality Metrics
- **Accuracy:** Reduction in post-deployment entity mapping corrections
- **Completeness:** Percentage of claims successfully normalized after review
- **Consistency:** Inter-reviewer agreement scores on validation decisions

### Process Metrics  
- **Throughput:** Average time from ingestion to canonical graph application
- **Queue Health:** Review queue depth and age distribution
- **Reviewer Efficiency:** Reviews completed per hour, decision reversal rates

### User Satisfaction
- **Trust:** User confidence scores in extracted knowledge
- **Transparency:** User understanding of how content becomes structured data
- **Utility:** Downstream search/retrieval quality improvements

## Decision Points

### Immediate Decisions Needed
1. **Scope for MVP:** Should we implement basic draft/apply (Phase 1) or include UI (Phase 2)?
2. **Technical Approach:** Single-DB labeled drafts vs change-set approach?
3. **Feature Flag Strategy:** Default enabled in dev, opt-in for prod, or always enabled?

### Future Decisions
1. **Auto-Approval Rules:** What confidence thresholds and patterns should bypass review?
2. **Reviewer Workflow:** Single reviewer vs consensus, escalation procedures?
3. **Integration Points:** How does this interact with the proposed Retrieval team timeline?

## Recommendation

**Proceed with Implementation** - The benefits significantly outweigh the costs for LoreVault's use case. The proposal aligns with our graph-first priority and enables higher data quality from the beginning rather than fixing problems later.

**Suggested Approach:**
- Start with Phase 1 (foundation) in v0.5.0 
- Implement basic review UI in v0.5.1
- Use `:Canonical` labeling strategy for fail-safe draft isolation
- Feature flag enabled by default in dev, configurable for prod
- Consider hybrid approach (`:Draft` + `:Canonical`) for production environments requiring maximum safety

**Timeline Integration:**
- Adds 2 sprints to v0.5.x but can be developed alongside temporal relations work
- Provides valuable validation capability for Claims→Facts workflow in v0.6.x
- Creates foundation for advanced reviewer workflow in v2.0.0

## Next Steps

1. **Technical Spike:** Prototype draft labeling approach with simple approve/reject (1 week)
2. **UX Review:** Design review interface mockups with stakeholders (1 week) 
3. **Integration Planning:** Define handoff points with KG and Retrieval teams (1 week)
4. **Go/No-Go Decision:** Based on spike results and stakeholder feedback

---

**Status:** Awaiting stakeholder review and technical spike results  
**Next Review:** After prototype completion  
**Decision Deadline:** Before v0.5.0 sprint planning
