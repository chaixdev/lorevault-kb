# Planning

This directory contains outstanding work that matters, but is not yet implemented truth.

Planning items should read like lightweight tickets:

- enough **product context** to explain why the work matters
- enough **technical context** to explain what part of the system is affected
- enough **scope framing** to make the work resumable later

They should **not** be overly opinionated about the solution space.

Detailed solution design belongs in `../brainstorm/`, where proposals can be explored, challenged, and revised before implementation.

Implemented and accepted truth belongs in the top-level canonical docs such as:

- `../adr/`
- `../patterns/`
- `../rules/`
- `../concepts/`

## Naming Convention

All planning filenames **must** use an ISO datetime prefix, not a month/year suffix.

**Format:** `YYYY-MM-DDTHHMM_topic-slug.md`

**Correct:**
- `2026-04-12T1100_relation-evidence-harvesting.md`
- `2026-05-08T1530_concept-resolution-lane.md`

**Wrong (do not use):**
- `relation-evidence-harvesting-april-2026.md`
- `concept-resolution-lane-may-2026.md`

The datetime prefix makes files sort chronologically by default, avoids ambiguous month names, and includes time-of-day precision so same-day iterations are distinguishable. Generate the timestamp with `date +%Y-%m-%dT%H%M`.

## Use This Folder For

- bounded future work
- parked investigations that should be resumed later
- non-urgent but important technical problems
- product/technical work that is known to matter but not yet active
- grouped verticals or themes that may later break into smaller implementation efforts

## Do Not Use This Folder For

- current implementation truth
- accepted architectural decisions
- detailed solution proposals
- throwaway scratch notes

If the main work is exploring *how* to solve something, use `../brainstorm/` first.

If the work is already implemented and accepted, promote the relevant truth into the top-level canonical docs.

## Writing Style

Planning items should be:

- **ticket-like** in structure
- **solution-neutral** in tone
- explicit about scope and non-goals
- easy for a future human or agent to resume

Every planning item should give enough context to answer:

- What is the problem or opportunity?
- Why does it matter?
- What part of the product does it affect?
- What part of the system does it affect?
- What constraints or prior findings already exist?
- What would success roughly look like?

Every planning item should avoid:

- prematurely choosing an implementation approach
- locking in architecture before the brainstorm/implementation loop
- duplicating canonical implementation docs

## Suggested Structure For Planning Items

Use `TEMPLATE.md` as the starting point.

At minimum, each planning item should include:

- Status
- Summary
- Problem
- Product Context
- Technical Context
- Scope
- Out of Scope
- Known Constraints / Prior Findings
- Open Questions
- Success Criteria
- Links

## Relationship To Other Docs

- `../brainstorm/` — proposal and solution-space exploration
- `../adr/` — accepted architectural decisions
- `../patterns/` — stable present-state implementation mechanisms
- `../rules/` — contributor guidance and durable conventions
- `../concepts/` — durable abstractions that outlive specific implementations

## Current Planning Items

- [HTTP Request ID MDC Propagation](2026-04-23T1630_http-request-id-mdc-propagation.md)
- [Revisit domain modeling with modern Java contracts and value objects](2026-04-22T1621_modern-java-domain-modeling-follow-up.md)
- [Tighten web transport boundaries and internal type visibility](2026-04-24T1611_tighten-web-transport-boundaries-and-type-visibility.md)
- [Q&A Retrieval Quality Validation](2026-04-30T1237_qa-retrieval-quality-validation.md)
- [Concept Entity Resolution Lane](2026-04-30T1237_concept-resolution-lane.md)
- [Event extraction and resolution tuning](2026-04-27T0951_event-extraction-and-resolution-tuning.md)
- [Relation Evidence Harvesting and Catalog Discovery — Phased Solution Design](2026-05-07T1917_relation-evidence-harvesting.md)
- [Catalog Module](2026-05-13T2027_relation-catalog-module.md)
- [Pipeline Issues from Smoke Test](2026-05-27T0230_pipeline-issues-from-smoke-test.md)
- [Code Walkthrough Issues](2026-05-29T2308_code-walkthrough-issues.md)
- [Model Catalog & A/B Testing](2026-05-30T0930_model-catalog-and-ab-testing.md)
- [Incremental Book Consolidation](2026-05-30T1750_incremental-book-consolidation.md)
- [SSE Event Migration](2026-05-24T0000_sse-event-migration.md)
- [Scene Detection Handler Decomposition](2026-05-23T1600_scene-detection-handler-decomposition.md) — parked
- [Micrometer Stage Timing](2026-05-23T1700_micrometer-stage-timing.md) — parked
- [Claim-Entity Linking](2026-05-31T1509_claim-entity-linking.md)
