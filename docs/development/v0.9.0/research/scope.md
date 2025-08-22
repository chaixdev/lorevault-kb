# v0.9.0 Scope — Timeline & Scene Events

Goal

Make Scenes first-class Events (Entity subtype) with a skeleton timeline built during ingestion, enabling temporal queries and future timeline features.

In-Scope (v0.9.0)

- Event entity model defined (schema, enums, relationships)
- Scene→Event promotion with back-compat references from existing Scene records
- Temporal relations captured between scenes, including cross-chapter boundaries (temporalRelation enum, default MEETS; upgrade via LLM when confident)
- Certainty model (Explicit | StronglyImplied | WeaklyImplied | Heuristic) mapped to numeric weight
- Provenance retained for temporal links (text span/rationale, offsets, source chunk)
- Ingestion changes to emit and persist Events + temporal edges
- Minimal read APIs to fetch Events for a chapter/book and their temporal neighbors; book-level timeline up to a chapter (spoiler gate)
- Reingestion strategy: allow clean DB wipe and reprocessing during 0.9.0 development (no backfill/rollback tooling)

Out-of-Scope (v0.9.0)

- Full cross-book timeline inference (only neighbor links across boundaries via summaries)
- Global reordering algorithms beyond pairwise links
- UI/visualization
- Spoiler-aware filtering (planned for v0.10.0)
- Entity extraction expansion (post 0.9.0)

Success Criteria

- Can create Events for each scene during ingestion and link them using at least MEETS/BEFORE/AFTER/OVERLAPS/DURING/STARTS/FINISHES/EQUALS
- Each link has a certainty enum, a calibrated weight, and a rationale snippet with offsets
- Smoke queries return ordered Events per chapter and across adjacent chapter boundaries; cross-chapter links created with default MEETS and upgraded by LLM when evidence
- NLQ "what happened so far?" up to chapter N returns a coherent summary aligned with Event order (spoiler gating at chapter level)
- Unit/integration tests cover model, ingestion, and API read paths
