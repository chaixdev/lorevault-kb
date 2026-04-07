# Spoiler-Aware Retrieval Pattern

**Status:** Established

LoreVault uses publication-aware coordinates and retrieval filtering to avoid returning results beyond a reader's progress.

The durable shape is:

- retrieve semantically relevant candidates
- oversample when needed
- filter by publication coordinates
- return only eligible context

Primary references:
- `docs/development/current/processes/spoiler-aware-retrieval-process.md`
- `docs/development/current/data-model/content-hierarchy-integration.md`
