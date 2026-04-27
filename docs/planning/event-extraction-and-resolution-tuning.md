# Event Extraction and Resolution Tuning

**Status:** Planning  
**Last Updated:** April 28, 2026  
**Scope:** `scene-analysis` extraction, event coreference, and chapter event reduction (Stage 1–3 of the event pipeline)  
**Reference:** `docs/concepts/evidence-vs-interpretation-layer.md`, `docs/concepts/core-domain-model-and-graph-process-restructured.md`, `docs/concepts/temporal-relation-semantics.md`

---

## Thesis

The current pipeline is asking scene-local evidence artifacts (`displayName`, `normalizedName`, `eventType`) to do cross-scene identity and chapter-level canonicalization work they were never meant to do.

The conceptual model is correct: extraction stays evidence-first, aggregation is rebuildable interpretation. The failure is a **layer leakage** problem — not an ontology gap — where reduction accidentally treats mention text as canonical truth, and coreference defaults to high-confidence fragmentation rather than anchor-evidence merging.

---

## Goals of this work

1. Improve chapter-level event structure quality without violating the evidence-first model.
2. Reduce fragmentation caused by conservative coref and unstable reduction canonicalization.
3. Address over-extraction in recap-heavy scenes and missing temporal qualifiers in implied contexts.
4. Stay within bounded, mechanically safe slices — no big-bang rewrites.

---

## Pipeline summary

```
scene-analysis prompt
  → EventMention (displayName, normalizedName, eventType, sceneRelativeRelation, certainty, evidence)
  → [scene-linked via Scene-[:MENTIONS]->EventMention]

event-coref (Stage 2)
  → rolling 3-scene windows
  → SAME_EVENT links when sameEvent=true AND confidence >= 0.75

chapter reduction (Stage 3)
  → connected components over SAME_EVENT within chapter
  → ChapterEvent per component (canonical displayName/representativeEventType via most-frequent mention value)
```

**Code touchpoints:**

| File | Role |
|---|---|
| `lorevault-core/src/main/resources/prompts/scene-analysis.txt` | Extraction system prompt |
| `lorevault-core/src/main/resources/prompts/scene-analysis-usertemplate.st` | Extraction user template |
| `lorevault-core/src/main/resources/prompts/event-coref-system.st` | Coreference system prompt |
| `lorevault-core/src/main/resources/prompts/event-coref-usertemplate.st` | Coreference user template |
| `EventPersistenceService` | Persists EventMention from triad output |
| `EventCoreferenceService` | Builds windows, writes SAME_EVENT links |
| `EventMentionComponentLookup` | Connected-component traversal over SAME_EVENT |
| `ChapterEventResolutionService` | Builds ChapterEvent from components |

---

## Current implementation status

- Stage 1 `EventMention` persistence is shipped.
- Stage 2 chapter-scoped scene-windowed `SAME_EVENT` coreference is implemented: `EventCoreferenceService` now accepts ordered scene ids, builds rolling 3-scene windows, validates active-window pairs, writes rebuildable chapter-scoped `SAME_EVENT` links, deletes and rebuilds chapter links, uses a 0.75 confidence threshold, and escalates failures.
- Stage 3 `ChapterEvent` reduction is shipped for chapter-local aggregates and now preserves support metadata: aliases, event-type variants, and evidence snippets.
- Stage 4+ book-wide ANN candidate generation, semantic verification, `BookEvent`, and retrieval integration remain unimplemented.

## Live data examined

Chapters with full extraction and coref runs:

| Chapter | Title | Scenes | Mentions | SAME_EVENT | ChapterEvents |
|---|---|---|---|---|---|
| `dd8c31f3-cafe-4520-9977-60504b1046ee` | 002 Deathworlders Run Little Monster | 7 | 10 | 3 edges | 8 |
| `523b95ec-39f4-4a3b-943a-d213bad2f0e9` | 001 Deathworlders The Kevin Jenkins Experience | 5 | 11 | 0 edges | 11 |
| `0e885d87-e1be-4c6e-b3d2-c99d171a1e5c` | (single scene) | 1 | 1 | 0 | 1 |

---

## Confirmed findings

### Finding 1: Extraction naming is highly scene-local

Every mention in the sampled data has a unique name. There are no repeated `normalizedName` values across scenes. Names like `Hunter raid on Rogers Arena`, `Alien attack on Rogers Arena`, and `Alien assault on arena` all refer to the same event cluster but share no tokens.

**Implication:** `normalizedName` cannot serve as a cross-scene identity key. It is a local cleanup field, not a merge signal.

### Finding 2: eventType vocabulary drifts freely

The same event cluster in chapter `dd8...` produced three types: `attack`, `assault`, `battle`. Chapter `523...` produced 9 unique types across 11 mentions. Near-synonyms are common (`meeting`, `conversation`, `interview`).

**Implication:** `eventType` values are narrative-local, not canonical. Using them as reduction canonicalization keys produces unstable representative types.

### Finding 3: Coref is calibrated toward fragmentation

The event-coref system prompt explicitly prefers `sameEvent=false` when uncertain. In sampled data, confident negatives cluster around 0.95. Chapter `523...` produced 0 SAME_EVENT edges from 3 event-coref calls. The 0.75 write threshold is conservative relative to the already-biased model output.

**Implication:** The issue is not just threshold — it is prompt posture. Uncertainty becomes a strong negative rather than low confidence.

### Finding 4: Successful merges rely on shared anchors, not names

The Rogers Arena attack cluster merged across 3 mentions with different names/types because evidence strings shared participants, place, and action context. Name and type similarity did not drive the merge.

**Implication:** The coref prompt should explicitly reward anchor matching (actors, place, causal chain, temporal framing) rather than relying on field overlap.

### Finding 5: Chapter `523...` (0 SAME_EVENT) is not uniformly a coref failure

The Jenkins interview pair (`Interview of Kevin Jenkins` vs `Official incident report interview`) is two distinct occurrences despite the same schema — the second is post-Hunter-attack, and inspection of the scene-analysis calls confirms this. The 0.62 rejection is correct.

The more interesting case is `Alien abduction inquiry` vs `Bar interview` in chapter `dd8...` — both are Jenkins's alien-abduction story across scenes, scored `sameEvent=false` at 0.42. This is a better candidate for missed merge due to naming drift and over-conservative posture.

### Finding 6: Over-extraction in recap-heavy scenes

Scene `948eac80-d498-4208-bf4c-e4ef0d377936` produced 5 extracted events:
- `Council special meeting`
- `Treaty amendment allowing sentience based on calculus`
- `Launch of stealth research station`
- `Civilian fleet contact attempt with Earth`
- `Vigilante fleet comet diversion`

This scene recaps prior events. The current prompt does not distinguish active occurrences from referenced/reported occurrences, so each recap becomes a new evidence artifact.

**Implication:** Evidence-first does not mean extract every paraphrase. The rule should be: one mention per distinct occurrence referenced in the scene.

### Finding 7: Missing temporal qualifiers in implied/retrospective mentions

Scene `8361394a-ce97-415d-8034-7af2ddc45244` produced 2 mentions with no `sceneRelativeRelation`:
- `Movie marathon of "Star Wars" and "The Lord of the Rings"` (`entertainment`, StronglyImplied)
- `Council emergency session on suicide bomber countermeasures` (`political`, StronglyImplied)

These are implied/retrospective references in narration. The current extraction prompt appears to omit the qualifier when the relation is not obvious.

**Implication:** `sceneRelativeRelation` should be explicitly required for all mentions, with `unknown` as an acceptable fallback rather than accidental omission.

### Finding 8: Reduction canonicalization is unstable

When every merged mention has a unique name and unique type, `most-frequent-wins` is tie-order-dependent. The Rogers Arena cluster chose `Alien assault on arena` / `battle` as canonical despite no plurality — there were 3 members with 3 unique values each.

The aggregate card preserves nuance (types, aliases, evidence), but the top-level `displayName` and `representativeEventType` are arbitrary in this case.

**Implication:** The headline canonical fields can mislead consumers into treating a tie-broken label as truth. Reduction should store aliases with support, not manufacture a single canonical from sparse mode estimation.

---

## What NOT to do

- Do not introduce a large canonical event ontology at extraction time — this conflicts with the evidence-first model.
- Do not collapse `EventMention` evidence into `ChapterEvent` canonical truth — they are different layers.
- Do not make `normalizedName` an identity key across scenes.
- Do not blindly lower the coref threshold without widening candidate generation and fixing prompt calibration — connected components can over-merge transitively.
- Do not conflate stage 3 canonicalization improvements with a claim-first rewrite — that is a much larger slice.
- Do not infer timeline order from `MENTIONS` edges or treat `sceneRelativeRelation` as canonical event order (per `temporal-relation-semantics.md`).

---

## Evidence vs. interpretation boundary for events

Per `docs/concepts/evidence-vs-interpretation-layer.md`:

**Evidence layer (durable, auditable):**
- `EventMention` — including `displayName`, raw `eventType`, `certainty`, `sceneRelativeRelation`, `evidence`
- `Scene -[:MENTIONS]-> EventMention` relationship
- Supporting provenance / raw evidence text

**Interpretation layer (rebuildable from evidence):**
- `SAME_EVENT` links — cross-mention identity judgments
- `ChapterEvent` — aggregated component
- Representative label / type on `ChapterEvent`
- Alias consolidation
- Any future catalog/taxonomy mapping

This means `ChapterEvent` can and should be rebuilt from scratch when coref or reduction logic changes. Consumers should trace back to `EventMention` evidence for provenance.

---

## Prioritized recommendations

### Tier 1 — Immediate prompt/instruction changes

These address the highest-impact issues without touching architecture or pipeline shape.

#### T1-A: Redefine extraction around distinct occurrences
**Target:** `scene-analysis.txt`  
Instruct the model to extract one `EventMention` per distinct event occurrence, not one per paraphrase or mention. Recap clusters should collapse into one mention with richer evidence. Add explicit guidance: if the scene refers back to a prior event or reports it secondhand, treat that as a reference to the same occurrence (unless clearly a new development).

#### T1-B: Require `sceneRelativeRelation` on all mentions
**Target:** `scene-analysis.txt` / extracted schema  
Make the field explicitly required with `unknown` as an acceptable fallback. Add guidance for implied and retrospective mentions: prefer `R:temporal.before` for past-reference narration, `R:temporal.overlaps` for concurrent background, `R:temporal.during` for embedded sub-events.

#### T1-C: Demote `normalizedName` from identity signal
**Target:** `scene-analysis.txt`  
Clarify that `normalizedName` is a local lowercase cleaned-up label, not a cross-scene canonical event ID. The prompt should not try to produce a globally stable name.

#### T1-D: Constrain `eventType` to broad reusable categories
**Target:** `scene-analysis.txt`  
Provide a soft preferred vocabulary with examples. Avoid fine stylistic synonyms (`attack`, `assault`, `raid`). Goal: consistent coarse categories that survive across scenes, not a rigid controlled vocabulary. Treat this as discipline guidance, not an ontology.

Example preferred set: `battle`, `meeting`, `interview`, `ceremony`, `journey`, `discovery`, `announcement`, `negotiation`, `conflict`, `lecture`, `investigation`.

#### T1-E: Retune coref around shared anchors
**Target:** `event-coref-system.st`  
Replace the "prefer sameEvent=false when uncertain" instruction with: reduce confidence when uncertain, but do not treat uncertainty as evidence of difference. Add a ranked signal list: shared participants + place + action = strong evidence; similar name/type alone = weak evidence. Add a worked example with the Rogers Arena attack pattern showing how anchor overlap justifies merging across name drift.

#### T1-F: Fix overconfident negative posture
**Target:** `event-coref-system.st`  
Remove or invert the "prefer false when uncertain" bias. Replace with: if anchor features are weak on both sides, emit `sameEvent=false` at low confidence (0.45–0.55), not 0.90+. High-confidence negatives should require positive evidence of difference, not just name/type mismatch.

---

### Tier 2 — Medium-term pipeline/model changes

These require code changes but stay within the current pipeline shape.

#### T2-A: Expand coref candidate generation beyond 3-scene windows
**Target:** `EventCoreferenceService`  
After rolling-window coref completes, add a chapter-level second pass: compare mentions that share extracted participants, place, or action-anchor keywords. Only proposal generation needs to expand; adjudication can still use the LLM. This prevents recall cliffs where non-adjacent mentions are never even proposed.

#### T2-B: Improve reduction to store aliases/support rather than single canonical
**Target:** `ChapterEventResolutionService`, `ChapterEvent`  
**Status:** Implemented for chapter-local `ChapterEvent` aggregates.  
`ChapterEvent` should store:
- A representative `displayName` (deterministic from most-supported or first-seen, not a frequency tie-break on a small set)
- A `supportedAliases` list (all distinct member display names)
- A `representativeEventType` plus a `supportedEventTypes` list
- The existing `aggregateCard` for evidence snippets

Remove the illusion that the canonical fields are "true" — they are serving conveniences.

Implementation note: the shipped reducer keeps `displayName`, `normalizedName`, and `representativeEventType` as representative conveniences, while `supportedAliases`, `supportedEventTypes`, and `evidenceSnippets` preserve the supporting variation. Current consumers should treat the support lists as provenance/inspection metadata unless and until a later retrieval or book-event reducer explicitly promotes them into query behavior.

#### T2-C: Keep SAME_EVENT as interpretation, not durable evidence
**Target:** Conceptual — already in place mechanically, but make it explicit in the codebase and coref cleanup logic  
`SAME_EVENT` is a derived judgment. When extraction or coref changes, it should be fully rebuildable. Ensure the deletion + rebuild pattern is treated as a first-class operation, not a workaround.

---

### Tier 3 — Defer (conflicts with current goals or maturity)

- Full canonical event ontology
- Book-wide event identity resolution
- Claim-first graph rewrite
- Custom/fine-tuned models
- Transitive event resolution beyond chapter scope

---

## Conceptual guardrails while tuning

1. Raw extraction should remain auditable. Even if aggregates improve, `EventMention` evidence should trace back to scene text.
2. `ChapterEvent` is interpretation. Rebuild it freely; never let it become the source of truth that `EventMention` is derived from.
3. Favor consensus over flat canonicalization. When evidence genuinely varies, preserve the variation in aliases/types.
4. Temporal qualifiers belong on mentions (`sceneRelativeRelation`), not manufactured at the aggregate level. Per `temporal-relation-semantics.md`, `MENTIONS` edges are evidence links, not timeline ordering.
5. Ingestion mechanics must remain reliable. All changes should be prompt + projection changes, not pipeline surgery.

---

## High-signal examples for testing prompt changes

| Label | Chapter | Scene / Mentions | Expected outcome |
|---|---|---|---|
| Good merge | `dd8...` | Rogers Arena attack cluster: `Hunter raid on Rogers Arena`, `Alien attack on Rogers Arena`, `Alien assault on arena` | Three distinct names merged into one component |
| Correct separation | `dd8...` | Scene `196e3809...`: `Alien assault on arena` vs `Jenkins planetary classification lecture` | Two separate components |
| Correct same-schema negative | `523...` | `Interview of Kevin Jenkins` vs `Official incident report interview` | Two separate components (distinct occurrences) |
| Ambiguous near-miss | `dd8...` | `Alien abduction inquiry` vs `Bar interview` | Potentially one component after tuning |
| Over-extraction | `523...` | Scene `948eac80-d498-4208-bf4c-e4ef0d377936`, 5 extracted events | Should likely produce 1–2 distinct occurrences (recap scene) |
| Missing qualifier | `523...` | Scene `8361394a-ce97-415d-8034-7af2ddc45244` | `sceneRelativeRelation` should be populated on both mentions |

---

## Recommended execution order

1. **First:** T1-A, T1-E, T1-F — extraction scope + coref posture (highest leverage, no code change)
2. **Then:** T1-B, T1-C, T1-D — temporal qualifiers + naming discipline
3. **Then:** T2-B — reduction alias/support storage (small code change, removes the worst design misalignment)
4. **Later:** T2-A — candidate generation expansion (larger change, justified only after prompt tuning stabilizes)
5. **Defer:** T3 items entirely until chapter-level aggregation is mechanically stable

---

## See also

- `docs/concepts/evidence-vs-interpretation-layer.md`
- `docs/concepts/core-domain-model-and-graph-process-restructured.md`
- `docs/concepts/temporal-relation-semantics.md`
- `docs/concepts/Narrative event DAG.md`
- `docs/patterns/ingestion/ingestion-pipeline.md`
