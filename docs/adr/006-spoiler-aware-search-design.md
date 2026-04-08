# ADR 006: Spoiler-Aware Search Design

**Status:** Accepted  
**Date:** April 2026

## Decision

Spoiler filtering is expressed as a per-request `SpoilerVisibility` object — a list of per-series read-through coordinates — applied as a post-vector-search predicate via a Cypher `ANY()` clause over `Chapter` node properties. Publication coordinates are **not** materialized on `Chunk` nodes.

## Why

**Per-request, stateless over persisted profiles (for now):**  
No user or session concept exists yet. A stateless visibility object keeps the API deterministic and testable without dragging in auth, session state, or profile CRUD. When a real client exists, a persisted `ReadingProfile` resource can store the same DTO shape and be resolved server-side before search — no contract change required.

**Coordinates stay on `Chapter`, not `Chunk`:**  
The only reason to materialize `bookOrder_min`/`chapterOrder_min` on `Chunk` nodes is to push the spoiler filter into the Neo4j vector index call itself (same-node property filtering). At the scale of even the largest fantasy series — Wheel of Time, the Cosmere, Deathworlders — the total chunk count sits in the tens of thousands, comfortably handled by a single indexed graph hop (`Chunk → Scene → Chapter`). Denormalizing adds write complexity, a backfill concern, and a consistency surface (coordinates drift if hierarchy changes) for a join that costs microseconds. The existing oversample-then-filter approach already handles recall correctly and scales to this corpus size.

**`ANY()` over a per-series list, not a generated OR chain:**  
The number of series per universe a reader tracks simultaneously is small (typically 2–10). `ANY(p IN $seriesProgress WHERE ...)` is clean, avoids row multiplication from `UNWIND`, and handles the null/absent case without dynamic query generation.

**Oversample multiplier is configurable:**  
Vector search runs first; spoiler filtering narrows the candidate pool after. The adapter already oversamples at `topK * 3`. For readers early in a long universe this may collapse recall; the multiplier is exposed in `application.yml` so it can be tuned without code changes.

**`UnconfiguredSeriesPolicy` defaults to `HIDE`:**  
If a user provides a `SpoilerVisibility` but omits a series, the safe default is to hide that series entirely. Callers opt in to `SHOW` explicitly. This prevents accidental spoilers when new series are added to a universe the user hasn't registered progress for.

**Cross-series `spoilsSeriesIds` is deferred:**  
Annotating a chunk with the foreign series it spoils requires LLM-assisted labeling at ingestion time and adds significant ingestion and query complexity. It is the right eventual model for deeply interconnected universes (e.g., Cosmere crossover events) but is out of scope until reading-progress persistence and a client UI exist to surface it meaningfully.

## Shape

```java
record SpoilerVisibility(
    String universe,
    List<SeriesProgress> seriesProgress,
    UnconfiguredSeriesPolicy unconfiguredSeriesPolicy  // HIDE (default) or SHOW
) {}

record SeriesProgress(
    String series,
    Integer readThroughBookNumber,
    Integer readThroughChapterNumber  // null = whole book visible
) {}

enum UnconfiguredSeriesPolicy { HIDE, SHOW }
```

Field names use `bookNumber`/`chapterNumber` to match existing `Chapter` node properties (the design docs use `bookOrder`/`chapterOrder`; the code uses `bookNumber`/`chapterNumber` — these are the same concept).

## Alternatives Considered

**Materialize coordinates on `Chunk` nodes** — storing `bookNumber`, `chapterNumber`, `series`, and `universe` directly on each `Chunk` node enables vector-index-level filter pushdown (Neo4j 5.x supports same-node property predicates inside `db.index.vector.queryNodes`). Rejected: the graph join (`Chunk → Scene → Chapter`) is microseconds at tens-of-thousands-of-chunks scale; materialization adds write complexity, a consistency surface (coordinates must be updated if hierarchy changes), and a backfill concern with no measurable benefit at target corpus size.

**Persisted `ReadingProfile` server-side** — store reading progress per user/session and resolve it server-side before each search call, like a preference object. Rejected for now: no user or session concept exists yet. Explicitly deferred with an upgrade path: because `SpoilerVisibility` is a plain DTO, a `ReadingProfile` resource can store the same shape and be resolved by ID at search time with no contract change.

**Equality-only filters (current state)** — the existing `SearchFilters` with `bookNumber`/`chapterNumber` equality predicates can approximate a reading position by pinning to an exact chapter. Rejected: equality filters cannot express "up to book N" range semantics; every search call would need the exact current chapter to be fully specified, which is impractical across multiple series simultaneously.

**`UNWIND`-based multi-series predicate** — iterate over series progress entries with `UNWIND $seriesProgress AS p ... WHERE chapter.series = p.series AND ...`. Rejected: `UNWIND` multiplies rows (one row per series entry per candidate chunk), requiring downstream `DISTINCT` or aggregation that complicates the query and degrades performance as progress list length grows. `ANY()` is clean, non-multiplying, and idiomatic for this pattern.

**Cross-series `spoilsSeriesIds` annotation** — annotate each chunk with the IDs of foreign series it spoils (e.g., a Cosmere crossover event that reveals Mistborn plot points inside a Stormlight chapter). This is the correct eventual model for deeply interconnected universes. Deferred: requires LLM-assisted labeling at ingestion time, adds schema complexity, and has no client UI to surface it meaningfully yet. Revisit when reading-progress persistence exists.

## Revisit Triggers

- **Corpus grows into hundreds of thousands of chunks** → re-evaluate materializing coordinates on `Chunk` for vector index filter pushdown.
- **Cross-series spoiler reports from users** → implement `spoilsSeriesIds` annotation at ingestion time.
- **Reading progress needs persistence across sessions/devices** → add a `ReadingProfile` resource storing the same `SpoilerVisibility` shape; resolve by profile ID at search time.
- **Post-filter recall collapses consistently** → move from oversample-and-filter to a multi-pass retrieval strategy.
