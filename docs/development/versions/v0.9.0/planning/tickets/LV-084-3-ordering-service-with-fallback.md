# LV-084-3 — Ordering service with edges-first, sceneIndex fallback [user story]

Context

- Clients need a deterministic order of events per chapter and across chapters; prefer graph edges when present.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/timeline-apis.md

Problem

- Without a consistent ordering policy, responses may be unstable or ambiguous.

Proposal

- Implement an ordering service that returns Events ordered by TEMPORAL edges when available; fall back to chapternumber/sceneIndex for gaps/ambiguity.

Scope

- Service method(s) to order within a chapter and up to chapter N within a book.
- Handle missing edges gracefully; ensure deterministic result.

Out of scope

- Public exposure of endpoints (0.8.6)

Technical notes

- Prefer edge-based topological order within chapter; if disconnected, sort by sceneIndex.

Acceptance criteria

- [ ] For chapters with default edges, ordered list equals sceneIndex order
- [ ] For chapters with upgraded edges, order reflects edges
- [ ] Book-level ordering concatenates chapters by publication coordinates

Quality gates

- [ ] Integration tests for ordering behavior in both modes

Post-085 validation checklist

- [ ] Determinism: Same input graph produces identical output ordering across runs (verify with shuffled input ordering; compare hashes).
- [ ] Cross-chapter edges: If 085 introduces cross-chapter precedence edges, ensure they’re honored when requesting book-up-to ordering. Where cross-chapter edges are absent, preserve chapter concatenation behavior.
- [ ] Cycle handling: If cycles can appear after 085, confirm service degrades gracefully (e.g., stable fallback using sceneIndex/UUID) and logs a clear warning with involved node IDs.
- [ ] Mixed connectivity: For partially connected scenes, verify topo order respects edges, and disconnected scenes are placed deterministically via fallback.
- [ ] Performance: Book with 500–2k scenes sorts within acceptable time budget (<250ms target on dev hardware); include micro-benchmark or timing logs if needed.
- [ ] Repository queries: Revisit read queries if 085 changes node/edge labels or relationship types (e.g., MEETS vs TEMPORAL); adjust adapter mapping if schema evolves.
- [ ] Test coverage: Add/adjust tests for new edge types or cross-chapter semantics introduced by 085; include at least one cycle case and one multi-chapter edge case.

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#084—skeleton-timeline-edges-default-meets@heuristic
- Research: ../../research/timeline-apis.md
