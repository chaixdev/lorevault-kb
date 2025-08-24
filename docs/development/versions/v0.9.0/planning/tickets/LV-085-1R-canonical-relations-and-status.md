# LV-085-1R — Canonical relations and edge status [refactor]

Problem

- Relations are inconsistent (13+ inverses); downstream logic is complex.
- Edges lack explicit status lifecycle for reasoning (PROPOSED/CONFIRMED/CONTESTED).

Proposal

- Introduce canonical 7 Allen relations at the domain layer (before, meets, overlaps, starts, during, finishes, equal).
- Add EdgeStatus enum with values: PROPOSED, CONFIRMED, CONTESTED.
- Persist new fields on temporal edges: relation (canonical), status, confidence, evidence_quote, evidence_offsets.
- Provide normalization util to map any inverse to canonical orientation at write/read boundaries.

Scope

- Domain: new enums (CanonicalRelation, EdgeStatus, Confidence).
- Persistence: adjust Neo4j entity/relationship mapping as needed (non-breaking property additions).
- Utilities: RelationNormalizer with tests mapping all 13 to canonical 7.
- Migration script: lightweight job to update existing edges to canonical form with default status=CONFIRMED.

Acceptance criteria

- [ ] Unit tests: mapping of all 13 Allen relations to canonical 7 (both directions) is correct.
- [ ] Edges can be created/read with status and confidence fields.
- [ ] Migration job runs idempotently and updates existing edges.

Quality gates

- [ ] Build, unit tests, ArchUnit rules pass.
