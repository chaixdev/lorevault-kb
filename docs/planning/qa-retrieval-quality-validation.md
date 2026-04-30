# Q&A Retrieval Quality Validation

**Status:** NOT STARTED  
**Last Updated:** April 30, 2026

## Summary

Validate the current Q&A and retrieval quality against the lore question space that LoreVault aims to serve. Identify where the existing pipeline fails, what graph data is missing, and what signals should drive prioritization of future extraction and retrieval work.

## Problem

The current retrieval pipeline supports chunk-level semantic search, entity-aware RAG grounded in resolved entity context, and hybrid retrieval modes. However, it has not been systematically tested against the broad lore question space — identity continuity, scene reconstruction, social/structural relations, temporal development, causal explanations, and comparative questions.

Without a validation pass, it is unclear which question types work adequately today, which fail due to missing graph data, and which fail due to retrieval or assembly logic gaps. Prioritization of the catalog, typed edges, and new entity type lanes should be grounded in this evidence.

## Product Context

- Users asking lore questions expect grounded, spoiler-safe answers that go beyond "find a chunk containing this term"
- Relational and temporal questions (how is X related to Y, what changed after event Z) are likely to fail or produce poor answers today because the graph has no typed inter-entity edges
- Without validation, engineering investment may go to the wrong layer — more extraction may not help if retrieval assembly is the bottleneck
- Validation evidence also establishes a baseline to measure future improvements against

## Technical Context

Relevant components and surfaces:

- Query routing: `lorevault-web/src/main/java/com/lorevault/api/web/query/` — distinguishes entity lookup from narrative Q&A
- UI query surface: `lorevault-web/src/main/java/com/lorevault/api/web/ui/UiQueryController.java` and `lorevault-web/src/main/resources/templates/ui/query.html`
- Operator dashboard: `lorevault-web/src/main/resources/templates/ui/dashboard.html` currently embeds a small Q&A panel as an operator side tool
- RAG path: `lorevault-core/src/main/java/com/lorevault/api/search/rag/` — assembles context from chunks and entity nodes
- Graph-aware retrieval: uses resolved entity nodes as graph anchors; current regular anchors include Individual, Location, Object, and Collective
- Hybrid retrieval: reciprocal-rank-fusion composition of semantic + graph paths
- Current graph data should be refreshed before running validation. Prior sampled data covered 18 chapters with BookEvents, BookIndividuals, BookLocations, chunks, and scenes; Object and Collective anchors now exist and should be included in the next validation snapshot.

The question space taxonomy is documented in:

- `docs/brainstorm/query/2026-04-15_robust-qa-strategy-report.md` — full target question space and strategic analysis
- `docs/brainstorm/query/2026-04-12_graph-aware-qa-design-april-2026.md`
- `docs/brainstorm/query/2026-04-12_multi-entity-retrieval-external-research.md`

## Scope

0. **Create a Q&A validation workspace UI** as the enabling slice before running the validation pass:
   - Move the current dashboard Q&A panel onto a dedicated `/ui/query` page
   - Keep the dashboard focused on ingestion/upload/job telemetry and let the live event console reclaim the right rail
   - Use a wider Q&A layout with persistent question/answer history instead of replacing the last response
   - Preserve existing modes: vector, RAG baseline, graph-aware, hybrid RRF
   - Reserve a diagnostics column for retrieval traces, citations/chunks, graph context, and future visualization work

1. **Design a validation question set** covering the key question categories:
   - Identity and continuity (who is X, what is known about X by chapter N)
   - Scene and event reconstruction (what happened when X met Y)
   - Social and structural relations (how is X related to Y, which factions align with Z)
   - Temporal development (what changed before/after event Z)
   - Causal and explanatory (why did this happen)
   - Comparative (how does X in ch.3 compare to X in ch.9)

2. **Run questions against the live system** (18 ingested chapters) using the ask endpoint with each available retrieval mode (baseline, graph-aware, hybrid)

3. **Classify failures by cause:**
   - Missing graph data (entity type not extracted, typed edge not present)
   - Missing retrieval path (existing data not surfaced by current logic)
   - Assembly gap (data present, answer not assembled correctly)
   - Spoiler leak or incorrect gating

4. **Produce a structured findings report** mapping failure categories to candidate engineering investments

## Out of Scope

- Fixing identified gaps (this is a diagnostic, not an implementation item)
- Full diagnostics visualization or graph rendering in the first UI slice; reserve the layout space first, then fill it from validation needs
- Automated evaluation harness or regression testing framework
- Prompt engineering for the Q&A assembly step
- Multi-book or cross-series retrieval scenarios

## Known Constraints / Prior Findings

- The robust Q&A strategy brainstorm (`2026-04-15`) already categorized the full target question space and identified the evidence/interpretation/runtime layering as the right architecture
- Graph-aware retrieval is available but has not been stress-tested against relational and temporal question types
- The current chat window is an afterthought inside the operator dashboard; moving it to a dedicated validation page should happen before the manual validation pass so mode comparison and diagnostics are practical
- The dedicated Q&A page can be implemented incrementally with the existing Thymeleaf/HTMX stack; no frontend framework migration is required
- No typed inter-entity edges exist today (no `participated_in`, `member_of`, `located_in` between entity nodes) — relational questions are likely to fail
- Object and Collective entities are now resolved. Concept entities are still not resolved, so species/category questions will lack Concept graph anchors until the Concept lane lands.

## Open Questions

- Which retrieval mode (baseline, graph-aware, hybrid) performs best by question category today?
- What minimum diagnostics should the first Q&A page show immediately, versus leaving as placeholders for later?
- Should the Q&A workspace keep all answers in one history stream, or group responses by question and retrieval mode for easier comparison?
- Are answer failures primarily caused by missing data or missing retrieval logic?
- Which question categories are already adequately served by chunk-level semantic search alone?
- Does spoiler gating behave correctly for questions that span chapter boundaries?
- What threshold of answer quality is "good enough" for a given question category at this stage?

## Success Criteria

- A structured set of at least 20 questions spanning all major question categories has been run against the live system
- The dashboard no longer embeds the Q&A panel; a dedicated `/ui/query` workspace exists for validation work
- The Q&A workspace preserves prior answers during a session and supports mode comparison without losing context
- The Q&A workspace includes reserved diagnostics areas for retrieved chunks/citations, graph context, and future retrieval trace visualization
- Each question result is classified by failure mode (missing data / missing retrieval / assembly gap / spoiler issue / pass)
- A findings report exists that maps failure categories to specific missing graph capabilities
- The findings report is usable as prioritization input for the catalog, typed edges, and new entity lane planning items

## Links

- `docs/brainstorm/query/2026-04-15_robust-qa-strategy-report.md`
- `docs/brainstorm/query/2026-04-12_graph-aware-qa-design-april-2026.md`
- `docs/brainstorm/query/2026-04-12_multi-entity-retrieval-external-research.md`
- `docs/concepts/entity-claim-model.md`
- `docs/concepts/core-domain-model-and-graph-process-restructured.md`
- `docs/patterns/ingestion/entity-resolution-ladder.md`
