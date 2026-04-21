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

- [Book-location reduction can fail under chained uploads](book-location-reduction-race-under-chained-uploads.md)
- [Audit generic caught exceptions that should become meaningful business failures](audit-generic-caught-exceptions-for-business-failure-semantics.md)
- [Cross-chapter temporal linking is analyzed but not materialized](cross-chapter-temporal-linking-materialization-gap.md)
- [Systematically transform package structure toward the target shape](package-shape-transformation-plan.md)
- [Stuck ingestion status sometimes remains in an intermediate state](stuck-ingestion-status.md)
