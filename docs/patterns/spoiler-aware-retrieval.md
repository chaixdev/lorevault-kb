# Spoiler-Aware Retrieval Pattern

**Status:** Established

LoreVault uses publication-aware coordinates and retrieval filtering to avoid returning results beyond a reader's progress.

## Current Shape

- retrieve semantically relevant candidates
- oversample when needed
- filter by publication coordinates
- return only eligible context

## Why This Pattern Exists

Semantic relevance alone is not sufficient in a narrative system. Retrieval must also respect what the reader is allowed to know.

## Current Direction

- spoiler visibility is expressed per request rather than through persistent user profiles
- filtering happens after vector search using publication-aware coordinates
- unconfigured series default to a conservative hide behavior
- retrieval quality is preserved by oversampling before spoiler filtering narrows the result set

## Boundaries

This pattern explains the implemented retrieval mechanism.

Broader future work around timeline-aware or cross-series spoiler semantics belongs in `../concepts/` or `../brainstorm/`, not here.

Primary references:
- `docs/development/current/processes/spoiler-aware-retrieval-process.md`
- `docs/development/current/data-model/content-hierarchy-integration.md`
- `docs/adr/006-spoiler-aware-search-design.md`
