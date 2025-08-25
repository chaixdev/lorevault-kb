# LV-085-0 — Triad-based Pass 2 refactor (no external behavior change) [refactor]

Context

- We are pivoting Pass 2 from a "single-scene mapping" to a triad-only temporal resolver (Prev–Curr–Next) per the Narrative Event DAG spec.
- For 0.9.0, the external contract and stored outputs must remain exactly the same as today: strict Pass 2 XML and neighbor edges as currently persisted (no new fields, no API changes).
- This is a hard pivot: the triad-based Pass 2 fully replaces the legacy Pass 2 with no feature flags, migrations, or backfills.

Problem

- The current Pass 2 relies on per-scene hints and can be brittle. We want to ground temporal relations locally with triads and enable later overlap confirmation, without changing public behavior yet.

Proposal

- Replace legacy Pass 2 with triad-based Pass 2 and an adapter that emits the current strict Pass 2 output shape.
- Introduce a triad builder/orchestrator (per chapter; cross-chapter disabled initially), keep indices implicit (Prev=1, Curr=2, Next=3) to simplify parsing.
- Use the updated Pass 2 prompt (triad-based) that returns role-based relations (previous_to_current, current_to_next), a single current timeline marker, and optional scene entities (ignored in 085-0).
- Aggregate Curr↔Prev and Curr↔Next internally, but for parity return only the single relation needed by the legacy Pass 2 schema (Curr relative to Prev: chronology + certainty + chronology_marker) and preserve all other legacy fields unchanged.
- Keep default R:temporal.meets @ Heuristic when unknown. Do NOT change downstream edge persistence.

Scope

- Triad builder: construct (Prev?, Curr, Next?) for each scene within a chapter (cross-chapter retrieval can be added later). Use role labels (prev/curr/next) rather than numeric scene indices in LLM output.
- Prompts: keep using existing prompt path key for Pass 2 (PromptLoaderService property). Replace the content with the triad instructions and 5 canonical relations (R:temporal.before|meets|overlaps|contains|equals). No property or API changes.
- Parser: implement a tolerant parser for the new triad XML (root `<scene_analysis>`), reading:
  - `<timeline_marker>` (belongs to Curr/scene 2)
  - `<relationships>/<previous_to_current>` and `<relationships>/<current_to_next>` (each with `<temporal_type>`, `<certainty>`, `<evidence>`)
  - `<current_scene_entities>` (optional) — parsed or ignored in 085-0
- Adapter: map triad output to the legacy Pass 2 per-scene XML expected by existing code (or directly to the in-memory DTOs that legacy parser would produce), setting:
  - chronology (Curr vs Prev) via inversion mapping
  - chronology_certainty from previous_to_current.certainty
  - chronology_marker from timeline_marker
  - start_anchor, index, break_reason, context_summary from Pass 1
- Persistence: unchanged. Continue writing neighbor edges exactly as today; DefaultTemporalEdgeService, cycle guard, repositories remain untouched.

Out of scope

- Persisting contested/confirmed states, evidence quotes, counter-votes, or entities.
- Event Linker and Landmark attachments.
- Any public API or storage schema changes.

Implementation details

1) Pass 1 persistence tweaks (scene raw text)
- After Pass 1 segmentation, persist Scene.text (raw slice) and offsets (start/end) on Scene/SceneNode.
- Decompose current single-shot write into: create scenes → write text and offsets per scene.
- Requirements: idempotent writes (safe re-run), offsets 0-based end-exclusive, ensure anchors remain unique.

2) Triad builder (role-based)
- Input: ordered scenes for a chapter.
- For each i, emit triad {prev=i−1?, curr=i, next=i+1?} with role labels; do not rely on user-provided scene indices in the model output.
- Boundary handling: first scene (no prev), last scene (no next). Cross-chapter neighbors deferred.
- Retrieve slices from Scene.text/offsets; clamp if missing or out-of-range.

3) Pass 2 LLM orchestration
- Build prompt with Prev/Curr/Next slices in fixed order (1,2,3), include lightweight metadata as needed.
- Call model with retry/backoff; cap concurrent calls; log model name, latency, tokens (if available).
- Store call metadata in logs; do not persist full responses in 085-0.

4) Triad XML parser (new)
- Accept root `<scene_analysis>`; be whitespace/namespace-tolerant.
- Fields:
  - timeline_marker: string (may be empty)
  - relationships.previous_to_current: temporal_type (R:temporal.*), certainty, evidence
  - relationships.current_to_next: same fields (optional)
  - current_scene_entities: optional (ignore in 085-0)
- Evidence length may exceed 120 chars; parser should trim safely. Do not fail run on minor malformation; log and fall back.

5) Adapter to legacy Pass 2 per-scene output
- For each Curr scene, produce legacy fields:
  - chronology (Curr vs Prev) via inversion of prev→curr relation:
    - R:temporal.before (prev→curr) → R:temporal.after (curr vs prev)
    - R:temporal.meets → R:temporal.met_by
    - R:temporal.overlaps → R:temporal.overlapped_by
    - R:temporal.contains → R:temporal.during
    - R:temporal.equals → R:temporal.equals
  - chronology_certainty = previous_to_current.certainty
  - chronology_marker = timeline_marker
  - context_summary, start_anchor, break_reason, index copied from Pass 1
- Defaults:
  - If previous_to_current missing (first scene) → chronology=R:temporal.meets, certainty=Heuristic
  - If parser fails or temporal_type unknown → same default
- Emission path options (choose one for 085-0):
  - A) Build a legacy `<scenes>...` XML and feed the existing SceneDetectionXmlParser
  - B) Populate the in-memory DTO (SceneDetectionResult) directly, bypassing legacy XML parsing
  - Prefer A for maximum parity with existing tests; both are acceptable.

6) Edge writing (unchanged)
- Keep using existing services/repos to create neighbor edges; no new properties or states.
- Cycle guard behavior remains identical; a failed confirmed edge should not alter existing defaults.

7) Observability and failure modes
- Log: triad calls attempted/succeeded/failed, parse success count, adapter fallbacks, defaulted-first-scene counter.
- On LLM or parse failure: emit safe default for the affected Curr (meets@Heuristic), continue processing.
- Add tiny health metric in logs (not a public API): agreement rate (if both directions ever available later), certainty distribution.

Acceptance criteria

- [ ] On curated fixtures, triad-backed Pass 2 produces outputs byte-for-byte identical to the legacy Pass 2 (allowing only directionally equivalent inverses mapped to the canonical label used today).
- [ ] Re-runs are idempotent and deterministic (no drift across runs with same inputs).
- [ ] Build/tests pass with no downstream behavior changes; no additional fields are introduced in outputs or storage.
- [ ] Basic observability: log triad parse rate and any adapter fallbacks.

Quality gates

- [ ] Unit tests — triad XML parser: happy path, boundary scenes (missing prev/next), malformed/extra whitespace, unknown relation → default.
- [ ] Unit tests — inversion/adapter mapping: 5→legacy 13 mapping (after/met_by/overlapped_by/during/equals), first-scene default.
- [ ] Golden-file tests — given Pass 1 + triad outputs, the adapter-emitted legacy `<scenes>` matches the previous (legacy) Pass 2 outputs.
- [ ] Integration — existing temporal edge, cross-chapter defaults, and cycle guard tests remain green unchanged.
- [ ] Prompt loader — still uses same Pass 2 property path; caching behavior unaffected.

Links

- Research: ../../research/Narrative event DAG.md
- Current Pass 2 spec: ../../../current/processes/scene-detection-specification.md
- Revised Pass 2 prompt (triad): lorevault-api/src/main/resources/prompts/scene-detection-pass2.txt
