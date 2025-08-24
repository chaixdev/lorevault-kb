# LV-085-9R — Triad visualization endpoint [feature]

Problem

- Developers need visibility into triads, evidence, and statuses for debugging.

Proposal

- Add an internal endpoint to visualize triads for an event:
  - GET /internal/triads/{eventId}
  - Returns triads with relations, evidence quotes, scores, and statuses for involved edges

Scope

- Controller: internal-only route
- Service: compose data from TriadBuilder, Evidence, and Scoring services
- Tests: controller slice ensuring response contract

Acceptance criteria

- [ ] Endpoint returns triads with required fields
- [ ] Response is stable and paginates if needed

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
