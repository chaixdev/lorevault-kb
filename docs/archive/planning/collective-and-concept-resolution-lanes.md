# Collective and Concept Entity Resolution Lanes

**Status:** NOT STARTED  
**Last Updated:** April 29, 2026

## Summary

Add two new entity resolution lanes — Collective and Concept — following the established `Mention → ChapterEntity → BookEntity` ladder pattern used by Individual and Location. This broadens the graph's entity coverage to include factions, organizations, species, technologies, and other narrative-significant categories that are currently unresolved.

## Problem

Today, the entity resolution ladder handles two lanes: Individual and Location. When the scene-analysis triad extracts mentions of factions, organizations, species, or abstract concepts, those mentions are either:

- Silently dropped (not extracted at all), or
- Extracted as part of another entity type and misclassified

This means the graph has no canonical nodes for entities like "the UNSC," "the Hunters," or "the Gaoian species" — even when these are central actors in a scene. Retrieval and Q&A that involve collective actors or species/concept categories cannot be grounded in the graph.

## Product Context

- Questions about factions, organizations, or species ("which faction is X aligned with?", "what species is Y?") cannot be answered with graph-grounded context today
- Event participation and location relations (planned for a future typed-edges slice) require canonical Collective and Concept nodes as valid link targets
- The entity taxonomy in `docs/concepts/core-domain-model-and-graph-process-restructured.md` identifies six cardinal entity kinds; the resolution ladder currently covers only two
- Every additional entity type with a resolved BookEntity improves the density and navigability of the knowledge graph for retrieval

## Technical Context

The established pattern is documented in `docs/patterns/ingestion/entity-resolution-ladder.md`:

- `SceneDetectionHandler` persists scene nodes and publishes `ScenesDetectedEvent`
- Each lane listens to `ScenesDetectedEvent` and independently runs chapter resolution then book reduction
- Mention persistence is written before consolidation; chapter resolution groups mentions; book reduction produces a thin cross-chapter identity backbone

Relevant code touchpoints (Individual lane as reference):

| File | Role |
|---|---|
| `IndividualMentionPersistenceService` | Persists `IndividualMention` nodes and `Scene-[:MENTIONS]->IndividualMention` links |
| `IndividualChapterResolutionService` | Groups mentions within a chapter into `ChapterIndividual` nodes |
| `IndividualBookReductionService` | Merges `ChapterIndividual` nodes into `BookIndividual` across chapters |
| `scene-analysis.txt` / `scene-analysis-usertemplate.st` | Extraction prompt that must elicit Collective and Concept mentions |
| `IngestionCompletionCoordinator` | Fan-in gate that waits for all required branches before declaring chapter complete |

New lanes will require:

- `CollectiveMention`, `ChapterCollective`, `BookCollective` node types (and equivalents for Concept)
- Extraction prompt updates to elicit these mention types from scene analysis
- Chapter resolution and book reduction services following the same pattern
- New required branches added to the ingestion completion fan-in contract

## Scope

1. **Collective lane** — factions, organizations, teams, governments, military units
   - `CollectiveMention → ChapterCollective → BookCollective`
   - Mention fields: `displayName`, `normalizedName`, `collectiveType` (faction / organization / military / government / other), `certainty`, `evidence`
   - Resolution logic: same normalized-name grouping used for Individual and Location

2. **Concept lane** — species, technologies, artifacts-as-classes, biological categories, abstract ideas with narrative significance
   - `ConceptMention → ChapterConcept → BookConcept`
   - Mention fields: `displayName`, `normalizedName`, `conceptType` (species / technology / artifact-class / other), `certainty`, `evidence`

3. **Extraction prompt updates** — scene-analysis prompt must be updated to elicit Collective and Concept mentions in the triad output

4. **Ingestion completion** — both new lanes must be wired into the fan-in completion gate as required branches

5. **Tests** — unit and integration tests following the established pattern for Individual and Location lanes

## Out of Scope

- Typed semantic edges between Collectives, Concepts, and other entity types (this depends on the reltype catalog; see separate planning item)
- Confidence aggregation or claim-backed persistence for these entity types
- Object mentions (already extracted in a separate slice; not the focus here)
- Resolution quality tuning (LLM-backed merging, alias handling beyond normalized-name matching)
- Cross-book identity resolution

## Known Constraints / Prior Findings

- The Individual and Location lanes are the established reference implementations; Collective and Concept should follow exactly the same structural pattern
- The ingestion completion fan-in contract currently waits on five branches; adding two new lanes will require updating that gate
- The scene-analysis extraction prompt already extracts Individual and Location mentions; extending it to Collective and Concept must not degrade extraction quality for existing mention types
- Object mentions are already extracted and persisted but are not part of the resolution ladder yet; this item does not change that
- The entity taxonomy in the concepts catalog treats Species as a Concept (not a Collective) — biological taxonomy is conceptual, not an acting group

## Open Questions

- Should Collective and Concept be extracted as part of the existing scene-analysis triad output, or as an extension to the triad schema?
- How granular should `collectiveType` and `conceptType` be in v1? The risk is over-specifying types that cause LLM classification errors
- Should Collective and Concept share a single resolution handler pattern, or are there meaningful differences that warrant distinct implementations?
- What normalized-name matching strategy is correct for collectives? Aliases and abbreviations (e.g., "UNSC" vs "United Nations Space Command") are likely more common than for individuals

## Success Criteria

- `CollectiveMention`, `ChapterCollective`, `BookCollective` nodes are persisted end to end for an ingested chapter containing collective actors
- `ConceptMention`, `ChapterConcept`, `BookConcept` nodes are persisted end to end for a chapter containing species or technology references
- Both lanes are wired into the ingestion completion fan-in contract
- Architecture cycle tests continue to pass
- All existing tests continue to pass
- Retrieval queries can use `BookCollective` and `BookConcept` nodes as graph anchors

## Links

- `docs/patterns/ingestion/entity-resolution-ladder.md` — reference pattern
- `docs/concepts/core-domain-model-and-graph-process-restructured.md` — entity taxonomy
- `docs/concepts/entity-claim-model.md` — entity kind definitions
- `docs/planning/qa-retrieval-quality-validation.md` — validation work that will use these entity types
- `docs/planning/minimal-reltype-catalog.md` — dependent: typed edges between these entities require a reltype catalog
- `docs/patterns/ingestion/ingestion-pipeline.md` — ingestion completion fan-in contract
