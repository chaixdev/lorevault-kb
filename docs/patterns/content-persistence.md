# Content Persistence Pattern

**Status:** Transitional

LoreVault is moving toward direct persistence through SDN-annotated domain entities and repository-driven access.

Target characteristics:

- domain entities annotated directly for persistence
- repositories return domain types directly
- no large mapper layer between domain and persistence nodes
- no God Port for content persistence

Primary references:
- `../development/current/data-model/neo4j-content-data-model.md`
- `../development/current/refactor-roadmap.md`
- `../archive/refactor/ARCHITECTURAL_BLOAT_ANALYSIS.md`
