# Tests and QA — v0.9.0

Test Themes

- Model validation for Event and TemporalEdge
- Certainty mapping correctness and defaulting
- Ingestion: scenes produce Events and edges with evidence persisted
- Migration: backfill creates Events and meets@Heuristic edges
- API: chapter and book-level (up to chapter N) events ordered, neighbors returned with metadata
- NLQ smoke: "what happened so far?" up to chapter N returns coherent summary aligned with Event order

Fixtures

- Small synthetic book with 2 chapters, 3–4 scenes each, with flashback and overlap cases
- Edge cases: single-scene chapter; equals relation; ambiguous/no-evidence case

Metrics & Perf

- Ingestion latency deltas for timeline steps
- Size overhead of evidence storage (target: <10% of content storage size)

Non-Goals

- End-user UI validation (deferred)
