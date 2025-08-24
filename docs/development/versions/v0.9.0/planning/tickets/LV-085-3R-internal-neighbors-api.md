# LV-085-3R — Internal neighbors API aligned to triad model [user story]

Problem

- Need a stable, relation-agnostic read interface that exposes prev/next neighbors with relation, confidence, evidence_quote, and status (PROPOSED/CONFIRMED/CONTESTED).

Proposal

- Add GET /api/timeline/events/{eventId}/neighbors returning previous and next neighbors with:
  - relation (canonical 7)
  - confidence (Explicit/StronglyImplied/WeaklyImplied/Heuristic)
  - evidence_quote (short, verbatim)
  - status (PROPOSED/CONFIRMED/CONTESTED)
  - filters: relationSet=precedence|all, status filter

Scope

- Controller (internal), service, and repository method(s); controller slice tests.
  - Normalize any inverse relations at read time to the canonical 7 (safety)
  - Do not perform transitive reasoning; return only local neighbors

Technical notes

- Response shape: { prev: TemporalNeighbor[], next: TemporalNeighbor[] }.
- Precedence relationSet = {before, meets}; all = canonical 7.

Acceptance criteria

- [ ] Endpoint returns neighbors for a sample event with required fields
- [ ] Controller slice tests validate response contract
- [ ] Filtering by relationSet and status works as specified

Quality gates

- [ ] Web tests pass; ArchUnit rules preserved
