# Scene Detection Context Budget And Segmentation Spec — April 2026

**Date:** April 2026  
**Status:** Proposed  
**Purpose:** Prevent chapter segmentation context overflows while keeping ingestion moving for large chapters

---

## Problem

Scene detection chapter segmentation currently sends the full chapter text to the LLM with no preflight context-window guard. This means safety depends on the configured model/provider having enough headroom for the system prompt, chapter text, and structured response.

This is risky because:

- model choice is configurable
- chapter sizes vary
- `maxTokens` controls response generation, not total context window
- there is no deterministic fallback when a chapter is too large

---

## Decision Summary

Implement a simple, opinionated context-budget guard for scene detection chapter segmentation.

Rules:

- Add `maxContextTokens` as a per-model config value
- Use a hardcoded **70% input threshold** for admission control
- Estimate input tokens conservatively before chapter segmentation
- If the chapter fits, run the existing chapter segmentation flow unchanged
- If the chapter does not fit, fall back to **deterministic naive segmentation**
- Tag likely segment-edge split scenes for later merge/review work
- Keep response token caps hardcoded in code, not configurable

Deliberately not included in this version:

- reconciliation/merge-back logic
- tokenizer-accurate counting
- dynamic threshold tuning

---

## Config

Add to each LLM model configuration:

- `maxContextTokens`

Meaning:

- `maxContextTokens` = total model context window

The output cap remains useful, but it is treated as a hardcoded safety mechanism in code, not a configuration surface.

This proposal therefore keeps the config surface minimal:

- `maxContextTokens` is configurable
- output token caps are hardcoded per flow

---

## Admission Rule

Before chapter segmentation, compute:

- `estimatedPromptTokens = estimate(systemPrompt)`
- `estimatedInputTokens = estimate(chapterText)`
- `estimatedTotalInput = estimatedPromptTokens + estimatedInputTokens`
- `usableInputBudget = floor(maxContextTokens * 0.70)`

Decision:

- if `estimatedTotalInput <= usableInputBudget`: run normal chapter segmentation
- otherwise: run segmented chapter segmentation fallback

Rationale:

- simple and conservative
- avoids pretending we know exact provider overhead
- leaves implicit headroom for output, system overhead, and estimation error

---

## Token Estimator

Use a conservative heuristic rather than a tokenizer-specific implementation.

For any text:

- `charsEstimate = ceil(charCount / 3.0)`
- `wordsEstimate = ceil(wordCount * 1.35)`
- `estimate = max(charsEstimate, wordsEstimate)`

Why this heuristic:

- safer than the current `chars / 4` logging heuristic
- simple to implement and reason about
- good enough for coarse admission control

This estimator is a **guardrail**, not a billing-accurate counter.

---

## Segmentation Strategy

When a chapter exceeds the input budget, split it into deterministic contiguous segments.

Number of segments:

- `segmentCount = ceil(estimatedTotalInput / usableInputBudget)`

Split placement should prefer boundaries near the ideal cut point in this order:

1. paragraph break (`\n\n`)
2. line break (`\n`)
3. sentence boundary (`. `, `! `, `? `)
4. whitespace
5. hard cut

Rationale:

- minimizes ugly mid-token cuts
- keeps implementation complexity low
- accepts the tradeoff that one real scene may be split into two scene nodes

This is intentionally a **naive split** design with later merge/review of tagged split-scene fragments.

---

## Chapter Segmentation Fallback Flow

For each segment:

1. run chapter segmentation independently on segment text
2. parse XML response independently
3. localize scene coordinates against the segment text
4. rebase localized offsets back to chapter-global coordinates

After all segments complete:

- concatenate localized scene candidates
- sort by global start offset
- renumber scene indexes in chapter order
- continue the rest of the pipeline as normal

No cross-segment merge logic is included in this version.

Instead, the system records where split-scene risk may have been introduced so that a later merge task can repair the graph without increasing ingestion complexity now.

---

## Split-Scene Tagging

The only scenes likely to be artifacts of naive segmentation are the boundary scenes at segment edges.

Tag these scenes:

- last scene in each non-final segment: `PotentialSplitSceneEnd`
- first scene in each non-initial segment: `PotentialSplitSceneStart`

Purpose:

- make future merge/review work easy
- keep the degraded-mode risk explicit in the graph
- avoid trying to solve merge-back now

These tags describe **potential** split-scene risk, not confirmed errors.

---

## Persistence Shape

Carry split-scene risk markers through to persisted `Scene` nodes.

Recommended representation:

- dynamic label `PotentialSplitSceneStart`
- dynamic label `PotentialSplitSceneEnd`

Why labels instead of booleans:

- easy to query later
- aligns with existing dynamic label usage on `Scene`
- keeps later merge-task Cypher simple

---

## Retry And Failure Behavior

The admission decision must happen **before** making the LLM call.

This avoids retrying the same too-large input blindly.

Behavior:

- normal-size chapter: existing retry behavior unchanged
- oversized chapter: segmented fallback is used immediately

There is no new terminal failure mode in this proposal unless a segment-level LLM call fails under the existing retry policy.

---

## Tradeoffs

Pros:

- protects against full-chapter context overflows
- keeps ingestion moving for large chapters
- simple implementation
- makes likely split artifacts visible for later cleanup

Cons:

- one true scene can become two nodes
- boundary summaries may be weaker near segment cuts
- scene graph can become noisier for large chapters
- triad analysis may operate on fragmented scene structure

This is considered acceptable because scene boundaries are already heuristic and LLM-derived.

---

## Deferred Work

Explicitly deferred:

- boundary-fragment merge/reconciliation
- tokenizer-backed estimation
- calibration against actual usage metadata
- dedicated background merge task for `PotentialSplitSceneStart` / `PotentialSplitSceneEnd`

These are all reasonable future improvements, but not required for the first safe version.

---

## Implementation Outline

Expected code changes:

- `LoreVaultModelsProperties` — add `maxContextTokens`
- `SceneDetectionClient` — add conservative input estimator and chapter segmentation budget helper
- `SceneDetectionService` — add segmented fallback orchestration and offset rebasing
- `SceneWithCoordinates` — carry split-scene risk markers
- `SceneProcessingService` — persist split-scene risk labels onto `Scene`
- `Scene` — define/allow split-scene risk labels alongside `Event`

As part of this simplification, the existing configurable `maxTokens` model parameter should be removed from the operator-facing configuration surface for scene-detection flows and replaced by hardcoded output caps in code.

---

## Bottom Line

This proposal chooses the smallest practical safety mechanism that still preserves ingestion throughput:

- conservative budget check
- deterministic naive segmentation fallback
- explicit tagging of likely split-scene artifacts

It does not attempt to make segmentation perfect. It makes the failure mode explicit and recoverable.

---

## Implementation Notes & Deviations (Live)

This section is append-only during implementation and validation.

### 2026-04-10 — Initial implementation pass (feature branch)

Implemented:

- Added per-model `maxContextTokens` support in `LoreVaultModelsProperties.ModelProperties` with default `128000`.
- Added chapter segmentation admission preflight in `SceneDetectionClient`:
  - `SegmentationBudgetCheck` record
  - `evaluateSegmentationBudget(...)`
  - hardcoded `70%` usable input budget ratio
- Upgraded token estimator heuristic from `chars/4` to:
  - `charsEstimate = ceil(chars/3.0)`
  - `wordsEstimate = ceil(words*1.35)`
  - `estimate = max(charsEstimate, wordsEstimate)`
- Added deterministic segmented fallback orchestration in `SceneDetectionService`:
  - segment count from `ceil(estimatedTotalInput / usableInputBudget)`
  - boundary preference ordering: paragraph → line → sentence → whitespace → hard cut
  - per-segment chapter segmentation detection, coordinate localization, global offset rebasing
  - global sort + scene index renumbering
- Added split-risk flags to `SceneWithCoordinates`:
  - `potentialSplitSceneStart`
  - `potentialSplitSceneEnd`
- Persisted split-risk as dynamic labels in `SceneProcessingService` via `Scene` labels:
  - `PotentialSplitSceneStart`
  - `PotentialSplitSceneEnd`
- Added constants in `Scene` for split-risk labels.
- Added/updated tests:
  - `SceneDetectionServiceTest` (new)
  - `SystemHealthServiceTest` adjusted for new `ModelProperties` constructor arg

Validation snapshot:

- `mvn clean compile -DskipTests` ✅
- `mvn -Dtest=SceneDetectionServiceTest,SceneDetectionHandlerTest,TriadOrchestrationServiceTest test` ✅
- `mvn test` ✅ (`188` tests, `0` failures, `0` errors)

Deviations / clarifications from original prose:

1. **Split-risk carriage in memory uses booleans, persistence uses labels.**
   - The spec focuses on label persistence; implementation carries temporary booleans in `SceneWithCoordinates` and maps them to dynamic labels at persistence boundaries.
2. **No changes were made to triad merge/reconciliation behavior.**
   - Triad pass remains unchanged except it now receives scenes that may originate from segmented fallback.
3. **Output token caps remain hardcoded in scene detection call options (`maxTokens=6000`).**
   - Configurable scene-detection output cap removal/de-surfacing remains aligned with existing implementation strategy (hardcoded in code path).

### 2026-04-10 — Review-driven design adjustments

Based on review feedback, implementation was refined as follows:

- Terminology cleanup:
  - Renamed chapter segmentation preflight concept from `Admission` to `BudgetCheck` (`SegmentationBudgetCheck`, `evaluateSegmentationBudget(...)`).
- Uniform segmented processing flow:
  - Refactored orchestration so detection always processes a segment list iteratively.
  - If budget check passes, segment list size is `1` (full chapter) and processing path stays identical.
  - If budget check fails, list size is `N > 1` using the deterministic split strategy.
  - This removes branch-specific orchestration differences and keeps downstream handling uniform.
- Operator config surface simplification:
  - Removed `maxTokens` from `lorevault.ai.models.*` config model and `application.yml` model entries.
  - Kept scene-detection output cap hardcoded in code (`6000`) per spec intent.
- Scene label handling simplification:
  - Removed `normalizeDynamicLabels(...)` from `Scene` after review concern.
  - Label assignment now happens explicitly at persistence mapping boundaries.
