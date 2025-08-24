# LV-085-4R — Triad builder and cache [feature]

Problem

- We lack a mechanism to assemble triads from local neighbors and reuse results.

Proposal

- Implement TriadBuilder service that:
  - For a given focal event E, queries neighbors via internal API
  - Forms triads (E, A, B) covering prev/next combinations and relation classes
  - Deduplicates unordered pairs and caches recent triads to avoid recomputation

Scope

- Service: TriadBuilder with pluggable neighbor provider
- Cache: Caffeine or simple in-memory LRU with TTL (configurable)
- Tests: unit tests with stubbed neighbors to validate triad generation and caching

Acceptance criteria

- [ ] Given neighbors for E, the service returns expected unique triads
- [ ] Cache hit avoids neighbor re-query within TTL (verified in tests)

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
