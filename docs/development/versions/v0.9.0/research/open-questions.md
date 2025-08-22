# Open Questions — v0.9.0

Priorities

- Which business use-cases depend first on timeline? (e.g., "what was known by X?", "order scenes for recap")
- Is cross-chapter linking required on day one, or can we defer to 0.9.x?
- Any constraints from Neo4j model (labels/props) or current repositories?

Modeling

- Do Events need additional fields now (e.g., viewpoint character, location) or keep lean?
- Should equals relations trigger immediate merges or be stored as parallel nodes initially?
- Certainty weight calibration targets? Acceptable defaults above?

APIs

- Are public read APIs sufficient, or do we need chapter-range/Book-level endpoints now?
- Any spoiler-awareness required in 0.9.0 responses, or strictly 0.10.0?

Operations

- Migration volume and expected runtime for current dataset?
