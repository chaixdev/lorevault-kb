## overview

- Purpose: Segment a chapter into scenes (chapter segmentation) and normalize into a strict schema (scene analysis)

- Role: "Scene normalization engine — Scene Analysis (schema-safe XML)."
- Input: **Triad-based context**: chapter segmentation XML, previous chapter's last scene (for cross-chapter continuity), and current chapter metadata.
- Output: Strict XML with root <scenes>, per scene:

**Enhanced Context (v0.8.3+)**: Scene analysis now receives a "triad" of information:
1. Current chapter's chapter segmentation XML
2. Previous chapter's final scene (context summary, temporal relation, timeline marker)
3. Chapter metadata (title, book context)

This enables accurate cross-chapter temporal reasoning and eliminates discontinuities at chapter boundaries, then normalizes those scenes into a strict XML schema with temporal relations (scene analysis).
- Inputs:
  - Chapter Segmentation: Raw chapter text.
  - Scene Analysis: Chapter segmentation XML, plus previous chapter last-scene context and current chapter metadata (triad). No direct access to the raw chapter text.
- Outputs:
  - Chapter Segmentation: Free-text rich hints per scene with a verbatim start anchor.
  - Scene Analysis: Strict XML per scene including an Allen-style temporal relation relative to the previous scene.

**Enhanced Implementation (v0.8.3+)**: Cross-chapter context is supported through triad-based coordination. Scene analysis can reason across chapter boundaries using previous scene context from the preceding chapter.

### Observability (v0.8.3+)

- Per-triad status records are emitted during scene analysis normalization to reflect progress across triads and retries.
- Each LLM call is logged and linked to the corresponding status record and ingestion job for traceability.
- Logs include triad identifiers and scene indices to support end-to-end diagnosis.

## chapter segmentation — segmentation + rich hints (current contract)

- Role: "Specialized literary scene segmentation engine — Chapter Segmentation."
- Input: Full chapter text.
- Output: XML with root `scenes`, containing an ordered list of `scene`.

Scene definition (as prompted)

- A scene is a semantic break with a MAJOR shift:
  - Clear time jump, complete location change, or fundamental change in story focus.
- Minor transitions do not create new scenes.

Output schema (per scene)

- `index` integer, starting at 1, sequential without gaps.
- `start_anchor` <![CDATA[...]]]
  - Must be found verbatim in the chapter text (exact case, punctuation, special characters).
  - Length: 30–100 characters.
  - Unique across the chapter.
- `what_happens` 5–7 sentences, concrete events and stakes; free text (paraphrase allowed).
- `who` key named characters/entities with brief roles if helpful; free text.
- `where` plain description of setting/location; free text.
- `when_clues` free text time hints (absolute dates, era tags, time-of-day, relative phrases like "next morning", "later that day"); paraphrase allowed.
- `break_reason` why this is a MAJOR break from the previous scene; free text.
- `temporal_relation_guess` plain-English guess relative to the previous scene (e.g., "immediately after", "earlier that day", "unknown", "days later").
  - Not enums; human phrasing.

Rules/validation (as prompted)

- Output ONLY valid XML with root `scenes`; no explanations.
- index increments by 1 starting at 1.
- start_anchor must be verbatim, length 30–100 chars, and unique.
- All other fields: free text, paraphrase allowed, prefer concrete names in who/what_happens.
- Before output: verify sequential indices, anchor length and uniqueness, and presence of all fields.

Example provided in prompt

- The example demonstrates format only; content is illustrative.

Important implications

- Chapter segmentation is the only stage that sees the raw chapter text.
- Only the start anchor is required to be verbatim from the chapter; other fields can paraphrase or summarize, which may drop lexical temporal cues.

## scene analysis — normalization into strict schema (current contract)

- Role: "Scene normalization engine — Scene Analysis (schema-safe XML)."
- Input: Chapter segmentation XML and triad context (previous chapter last scene + chapter metadata). No access to full chapter text.
- Output: Strict XML with root <scenes>, per scene:

Output schema (per scene)

- `index` integer (preserve ordering and index from chapter segmentation).
- `start_anchor` <![CDATA[...]]> copy verbatim from chapter segmentation; do not alter.
- `context_summary` 3–5 sentences, concise and factual; use concrete names. Derived from chapter segmentation what_happens/who/where.
- `break_reason` derived from chapter segmentation break_reason.
- `timeline_order` enum (Allen-style relation, as currently listed):
  - One of:
    - R:temporal.before | R:temporal.after | R:temporal.meets | R:temporal.met_by |
    - R:temporal.overlaps | R:temporal.overlapped_by | R:temporal.starts | R:temporal.started_by |
    - R:temporal.during | R:temporal.contains | R:temporal.finishes | R:temporal.finished_by |
    - R:temporal.equals
  - Default when unknown: R:temporal.meets
- `timeline_order_certainty` one of:
  - Explicit | StronglyImplied | WeaklyImplied | Heuristic
- `timeline_marker` always include; extraction rules below.

Mapping rules (as prompted)

- Use chapter segmentation `temporal_relation_guess` and `when_clues` to derive timeline_order and certainty:
  - Immediate adjacency cues ("immediately after", "right after", "later the same day") → R:temporal.meets
    - Certainty:
      - Explicit when cue is lexically present.
      - StronglyImplied when logically necessary from events.
      - WeaklyImplied when plausible but not necessary.
      - Heuristic when only adjacency without evidence.
  - "earlier", "flashback", "previously" → R:temporal.before (certainty per above).
  - "same time", "meanwhile", "concurrent" → R:temporal.equals or R:temporal.overlaps
    - Prefer equals if indistinguishable; else overlaps.
  - If temporal_relation_guess is "unknown" or no clear relation → R:temporal.meets with Heuristic.
- Extract timeline_marker:
  - If chapter segmentation when_clues contains an explicit date/time or era tag, copy the single most specific phrase verbatim from the chapter text if available; else copy exact phrase as given in when_clues.
  - If only relative clues exist (e.g., "the next morning"), copy that phrase (verbatim if present in chapter; else as written in when_clues).
  - If nothing useful, output <timeline_marker/> empty.
  - Never invent dates; prefer the most specific marker.

Quality checks (as prompted)

- Indices are sequential starting at 1.
- Each start_anchor is present, 40–80 chars, and unique
  - Note: This is an inconsistency with chapter segmentation (30–100 chars). Scene analysis says "assume chapter segmentation enforced this; flag only if missing."
- All required fields present for each scene.

Important implications

- Scene analysis enumerates among 13 Allen relations (including six inverses).
- Default relation when unclear is meets with Heuristic certainty.
- Scene analysis does not require quoting evidence; it synthesizes based on chapter segmentation hints.
- Triad input allows the first scene's relation to be reasoned relative to the previous chapter's last scene when available.

## coordinate localization — enhanced anchor matching (v0.8.3+)

After scene analysis XML generation, scene coordinates are calculated by converting AI-identified text anchors into precise character positions within the chapter text. This process has been enhanced to handle LLM variability and formatting inconsistencies.

### multi-tier fallback strategy

The coordinate localization employs a robust three-tier fallback approach:

#### Tier 1: Exact String Matching

- Standard verbatim text matching with case and punctuation sensitivity
- Enhanced with whitespace normalization to handle LLM CDATA formatting artifacts
- Collapses multiple whitespace characters (spaces, tabs, newlines) to single spaces for comparison
- Maps positions back to original text to preserve formatting integrity

#### Tier 2: Progressive Word Trimming

- For anchors with ≥5 words, progressively removes words from the end
- Ensures uniqueness within scene boundaries to prevent false positives  
- Validates that trimmed matches occur within proper sequence boundaries
- Uses both exact and normalized matching at each trimming level

#### Tier 3: Fuzzy String Matching

- Employs Levenshtein distance algorithm for character-level similarity
- Configurable thresholds: ≤3 character differences, ≥85% similarity required
- Bounded search within scene boundaries to prevent cross-scene matches
- Last resort for handling minor LLM transcription variations

### bounded search constraints

**Smart Look-Ahead**: When immediate next scene anchor fails, searches through all subsequent scenes to find the next available boundary. This prevents premature extension to chapter end and maintains scene continuity.

**Position Validation**: All matches must occur:

- After the previous scene's end position (prevents overlap)
- Before the next found scene's start position (prevents out-of-order matches)
- Within realistic text boundaries (prevents buffer overflow)

**Sequence Integrity**: Failed anchor detection results in scene skipping rather than invalid boundaries, preserving the integrity of successfully located scenes.

### error resilience patterns

**Whitespace Normalization**: Handles common LLM artifacts:

- Extra indentation from CDATA sections (`\n    \n` → `\n\n`)
- Mixed tab/space inconsistencies
- Leading/trailing whitespace variations

**LLM Drift Tolerance**: Accommodates common AI variations:

- Minor word additions/omissions (handled by word trimming)
- Character-level typos (handled by fuzzy matching)
- Punctuation variations (handled by normalization)

**Cascade Prevention**: When multiple consecutive anchors fail, uses smart look-ahead to find the next valid boundary rather than causing complete pipeline failure.

### performance characteristics

**Algorithmic Complexity**: O(n) for exact matching, O(n*m) for fuzzy matching where n=text length, m=anchor length
**Fallback Frequency**: Tier 1 (exact) handles ~85% of cases, Tier 2 (trimming) ~12%, Tier 3 (fuzzy) ~3%
**Boundary Validation**: Constant-time position checks with pre-computed scene boundaries

## end-to-end data flow (current)

- Chapter Segmentation:
  - Segments chapter, emits scene nodes with free-text hints and a verbatim start_anchor.
- Scene Analysis (Triad-Based):
  - Consumes chapter segmentation XML PLUS previous chapter context (triad approach)
  - Enables cross-chapter temporal reasoning for first scene relationships
  - Outputs strict XML with context_summary, break_reason, timeline_order (13-value enum), timeline_order_certainty (4-value enum), and timeline_marker (extracted)
  - Emits per-triad status records and logs LLM calls linked to those statuses
- Coordinate Localization:
  - Converts AI anchors to precise character coordinates using multi-tier fallback
  - Handles LLM drift through whitespace normalization, word trimming, and fuzzy matching  
  - Maintains scene boundaries and prevents overlaps through bounded search
- Downstream Persistence:
  - Scenes mapped to graph/domain nodes with calculated character coordinates
  - Single `:TEMPORAL` edge type created between scenes using timeline_order
  - Consolidated relationship model (no separate edge types per Allen relation)
  - Cross-chapter scene relationships supported through triad context

## determinism and defaults (current)

- Determinism:
  - Not guaranteed by the prompts. Scene analysis only sees chapter segmentation summaries; minor phrasing shifts may change mapping.
- Defaults:
  - timeline_order defaults to R:temporal.meets when unknown, with Heuristic certainty.
  - Scene analysis preserves the ordering and indices from chapter segmentation.

## error handling and invalid outputs (current)

- Prompts instruct to output ONLY valid XML; there is no explicit runtime recovery in the prompts.
- Quality checks mention indices, anchor presence/length, and required fields; behavior on failure is not specified (LLM is expected to comply).
- No strict schema validation layer is described in the prompts themselves.

## edge cases / special cases (current behavior)

- First scene of a chapter (index=1):
  - No explicit prior-scene context is available within the chapter; timeline_order often becomes meets (Heuristic) by default when clues are insufficient.
- Cross-chapter relations:
  - Handled using triad context; scene analysis receives previous chapter's last-scene summary and can compute a non-default relationship for the first scene when evidence exists.
- Concurrency:
  - Mapped to equals or overlaps; equals preferred if indistinguishable.
- Inverses:
  - All 13 relations are valid outputs; the prompts do not normalize inverses to canonical labels.

## known inconsistencies and assumptions (current)

- Anchor length mismatch between stages:
  - Chapter Segmentation: 30–100 chars.
  - Scene Analysis: 40–80 chars (but says assume chapter segmentation enforced).
- Evidence provenance:
  - Scene analysis is asked to derive from chapter segmentation hints; there is no requirement to include verbatim quotes as evidence.
- No explicit guidance for how to store or use the six inverse relations downstream (left to persistence/ordering code).

## observability and artifacts (current)

- Prompts are loaded from classpath resources (PromptLoaderService).
- Chapter segmentation and scene analysis outputs are XML and can be logged or archived per test/service logs.
  - Per-triad status emission and LLM call logging provide traceability across retries and triads.

## current success criteria

- Chapter Segmentation:
  - Produces valid `scenes` XML, sequential indices, unique verbatim anchors of required length, and filled fields.
- Scene Analysis:
  - Produces valid `scenes` XML with strict fields, a recognized timeline_order from the 13 listed, a certainty label, and a timeline_marker per extraction rules.
