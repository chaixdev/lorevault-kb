# Timeline APIs (initial)

> **Research only - not an implementation target**

Intent

Provide minimal APIs to read Events and their temporal relationships to support early consumers and internal testing.

Public Query APIs (v0.9.0)

- GET /api/timeline/books/{bookId}/events?uptoChapter={n}&includeEvidence={true|false} — ordered list up to chapter N (ordering policy applies)
- GET /api/timeline/chapters/{chapterId}/events — ordered list; prefers TEMPORAL edges, falls back to sceneIndex when edges are missing or ambiguous
- GET /api/timeline/events/{eventId}/neighbors — prev/next with relation + certainty

Internal/Admin APIs

- None for migration/backfill. Development remains reingestion-only.

Response Shapes

- EventSummary: { eventId, title, description, chapterNumber, sceneIndex, flags[] }
- TemporalNeighbor: { neighborId, temporalRelation, certainty, weight, rationale, offsets }

Notes

- Keep request/response shapes small and stable; prefer expanding via optional fields
- Authentication/authorization to reuse existing patterns

Ordering policy

- Prefer TEMPORAL edges for precedence; when edges are missing or ambiguous, fall back to sceneIndex for a deterministic order.
- Cross-chapter ordering follows publication coordinates; default MEETS edges link chapter k last → chapter k+1 first.
- Chapter-level spoiler gating applies to all timeline queries (return only events up to requested chapter).
