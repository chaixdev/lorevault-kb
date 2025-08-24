## overview

- Purpose: Segment a chapter into scenes (Pass 1), then normalize those scenes into a strict XML schema with temporal relations (Pass 2).
- Inputs:
  - Pass 1: Raw chapter text.
  - Pass 2: Pass 1 XML only (no direct access to the raw chapter text).
- Outputs:
  - Pass 1: Free-text rich hints per scene with a verbatim start anchor.
  - Pass 2: Strict XML per scene including an Allen-style temporal relation relative to the previous scene.

No cross-chapter context is passed today; Pass 2 reasons only within a chapter.

## pass 1 — segmentation + rich hints (current contract)

- Role: “Specialized literary scene segmentation engine — Pass 1.”
- Input: Full chapter text.
- Output: XML with root <scenes>, containing an ordered list of <scene>.

Scene definition (as prompted)
- A scene is a semantic break with a MAJOR shift:
  - Clear time jump, complete location change, or fundamental change in story focus.
- Minor transitions do not create new scenes.

Output schema (per scene)
- <index> integer, starting at 1, sequential without gaps.
- <start_anchor> <![CDATA[...]]]>
  - Must be found verbatim in the chapter text (exact case, punctuation, special characters).
  - Length: 30–100 characters.
  - Unique across the chapter.
- <what_happens> 5–7 sentences, concrete events and stakes; free text (paraphrase allowed).
- <who> key named characters/entities with brief roles if helpful; free text.
- <where> plain description of setting/location; free text.
- <when_clues> free text time hints (absolute dates, era tags, time-of-day, relative phrases like “next morning”, “later that day”); paraphrase allowed.
- <break_reason> why this is a MAJOR break from the previous scene; free text.
- <temporal_relation_guess> plain-English guess relative to the previous scene (e.g., “immediately after”, “earlier that day”, “unknown”, “days later”).
  - Not enums; human phrasing.

Rules/validation (as prompted)
- Output ONLY valid XML with root <scenes>; no explanations.
- index increments by 1 starting at 1.
- start_anchor must be verbatim, length 30–100 chars, and unique.
- All other fields: free text, paraphrase allowed, prefer concrete names in who/what_happens.
- Before output: verify sequential indices, anchor length and uniqueness, and presence of all fields.

Example provided in prompt
- The example demonstrates format only; content is illustrative.

Important implications
- Pass 1 is the only stage that sees the raw chapter text.
- Only the start anchor is required to be verbatim from the chapter; other fields can paraphrase or summarize, which may drop lexical temporal cues.

## pass 2 — normalization into strict schema (current contract)

- Role: “Scene normalization engine — Pass 2 (schema-safe XML).”
- Input: Pass 1 XML only (no chapter text).
- Output: Strict XML with root <scenes>, per scene:

Output schema (per scene)
- <index> integer (preserve ordering and index from Pass 1).
- <start_anchor> <![CDATA[...]]> copy verbatim from Pass 1; do not alter.
- <context_summary> 3–5 sentences, concise and factual; use concrete names. Derived from Pass 1 what_happens/who/where.
- <break_reason> derived from Pass 1 break_reason.
- <timeline_order> enum (Allen-style relation, as currently listed):
  - One of:
    - R:temporal.before | R:temporal.after | R:temporal.meets | R:temporal.met_by |
    - R:temporal.overlaps | R:temporal.overlapped_by | R:temporal.starts | R:temporal.started_by |
    - R:temporal.during | R:temporal.contains | R:temporal.finishes | R:temporal.finished_by |
    - R:temporal.equals
  - Default when unknown: R:temporal.meets
- <timeline_order_certainty> one of:
  - Explicit | StronglyImplied | WeaklyImplied | Heuristic
- <timeline_marker> always include; extraction rules below.

Mapping rules (as prompted)
- Use Pass 1 <temporal_relation_guess> and <when_clues> to derive timeline_order and certainty:
  - Immediate adjacency cues (“immediately after”, “right after”, “later the same day”) → R:temporal.meets
    - Certainty:
      - Explicit when cue is lexically present.
      - StronglyImplied when logically necessary from events.
      - WeaklyImplied when plausible but not necessary.
      - Heuristic when only adjacency without evidence.
  - “earlier”, “flashback”, “previously” → R:temporal.before (certainty per above).
  - “same time”, “meanwhile”, “concurrent” → R:temporal.equals or R:temporal.overlaps
    - Prefer equals if indistinguishable; else overlaps.
  - If temporal_relation_guess is “unknown” or no clear relation → R:temporal.meets with Heuristic.
- Extract timeline_marker:
  - If Pass 1 when_clues contains an explicit date/time or era tag, copy the single most specific phrase verbatim from the chapter text if available; else copy exact phrase as given in when_clues.
  - If only relative clues exist (e.g., “the next morning”), copy that phrase (verbatim if present in chapter; else as written in when_clues).
  - If nothing useful, output <timeline_marker/> empty.
  - Never invent dates; prefer the most specific marker.

Quality checks (as prompted)
- Indices are sequential starting at 1.
- Each start_anchor is present, 40–80 chars, and unique
  - Note: This is an inconsistency with Pass 1 (30–100 chars). Pass 2 says “assume Pass 1 enforced this; flag only if missing.”
- All required fields present for each scene.

Important implications
- Pass 2 enumerates among 13 Allen relations (including six inverses).
- Default relation when unclear is meets with Heuristic certainty.
- Pass 2 does not require quoting evidence; it synthesizes based on Pass 1 hints.
- No explicit cross-chapter input; the first scene’s relation is still determined relative to a “previous scene” within chapter context (which effectively doesn’t exist), so the default-meets path often applies for index=1.

## end-to-end data flow (current)

- Pass 1:
  - Segments chapter, emits <scenes> … <scene> nodes with free-text hints and a verbatim start_anchor.
- Pass 2:
  - Consumes Pass 1 XML and outputs strict XML with:
    - context_summary
    - break_reason (copied)
    - timeline_order (13-value enum)
    - timeline_order_certainty (4-value enum)
    - timeline_marker (extracted)
- Downstream persistence (typical, as currently conceptualized):
  - Scenes are mapped to graph/domain nodes (e.g., Scene/Event).
  - A temporal edge is created between adjacent scenes using timeline_order as provided by Pass 2 (direction and label as asserted).
  - There is no enforced normalization to a canonical subset at this stage in the prompts (any normalization would be downstream code, not defined by the prompts).

## determinism and defaults (current)

- Determinism:
  - Not guaranteed by the prompts. Pass 2 only sees Pass 1 summaries; minor phrasing shifts may change mapping.
- Defaults:
  - timeline_order defaults to R:temporal.meets when unknown, with Heuristic certainty.
  - Pass 2 preserves the ordering and indices from Pass 1.

## error handling and invalid outputs (current)

- Prompts instruct to output ONLY valid XML; there is no explicit runtime recovery in the prompts.
- Quality checks mention indices, anchor presence/length, and required fields; behavior on failure is not specified (LLM is expected to comply).
- No strict schema validation layer is described in the prompts themselves.

## edge cases / special cases (current behavior)

- First scene of a chapter (index=1):
  - No explicit prior-scene context is available within the chapter; timeline_order often becomes meets (Heuristic) by default when clues are insufficient.
- Cross-chapter relations:
  - Not handled in-pass today; Pass 2 does not receive the last scene from the previous chapter.
- Concurrency:
  - Mapped to equals or overlaps; equals preferred if indistinguishable.
- Inverses:
  - All 13 relations are valid outputs; the prompts do not normalize inverses to canonical labels.

## known inconsistencies and assumptions (current)

- Anchor length mismatch between passes:
  - Pass 1: 30–100 chars.
  - Pass 2: 40–80 chars (but says assume Pass 1 enforced).
- Evidence provenance:
  - Pass 2 is asked to derive from Pass 1 hints; there is no requirement to include verbatim quotes as evidence.
- No explicit guidance for how to store or use the six inverse relations downstream (left to persistence/ordering code).

## observability and artifacts (current)

- Prompts are loaded from classpath resources (PromptLoaderService).
- Pass 1 and Pass 2 outputs are XML and can be logged or archived per test/service logs.
- No explicit telemetry fields are included in the prompt outputs.

## current success criteria

- Pass 1:
  - Produces valid <scenes> XML, sequential indices, unique verbatim anchors of required length, and filled fields.
- Pass 2:
  - Produces valid <scenes> XML with strict fields, a recognized timeline_order from the 13 listed, a certainty label, and a timeline_marker per extraction rules.