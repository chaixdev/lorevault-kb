# Scene Detection Budgeted Segmentation Pattern

**Status:** Established

## Purpose

This pattern explains how LoreVault protects Scene Detection Chapter Segmentation from LLM context-window overflow while still ingesting large chapters.

The mechanism adds:

- a conservative chapter-segmentation budget preflight,
- deterministic chapter segmentation when budget is exceeded,
- and explicit split-risk labeling on boundary scenes.

## Problem

Chapter segmentation previously sent full chapter text without a preflight context budget check. That made successful ingestion depend on chapter length and current model context limits, with no deterministic fallback path for oversized input.

## Mechanism Overview

### 1) Budget check preflight

Before chapter segmentation calls the LLM, `SceneDetectionClient` computes a `SegmentationBudgetCheck`:

- `estimatedPromptTokens = estimate(systemPrompt)`
- `estimatedInputTokens = estimate(chapterText)`
- `estimatedTotalInput = estimatedPromptTokens + estimatedInputTokens`
- `usableInputBudget = floor(maxContextTokens * 0.70)`

If `estimatedTotalInput <= usableInputBudget`, segmentation proceeds with one full-chapter segment.
If not, deterministic multi-segment fallback is activated.

### 2) Conservative token estimator

Estimator (guardrail, not billing-accurate):

- `charsEstimate = ceil(charCount / 3.0)`
- `wordsEstimate = ceil(wordCount * 1.35)`
- `estimate = max(charsEstimate, wordsEstimate)`

### 3) Uniform segment-processing loop

`SceneDetectionService` now always processes a segment list uniformly:

- budget fits: segment list size is `1` (full chapter)
- budget exceeded: segment list size is `N > 1`

Per segment:

1. run chapter segmentation
2. parse scene XML
3. localize scene coordinates against segment text
4. rebase offsets to chapter-global coordinates

After all segments:

- concatenate
- sort by global start offset
- renumber scene indexes in chapter order

This ensures one orchestration path regardless of split/no-split mode.

### 4) Deterministic split boundary preference

When splitting is required, cut points prioritize:

1. paragraph breaks (`\n\n`)
2. line breaks (`\n`)
3. sentence boundaries (`.`, `!`, `?` followed by whitespace)
4. whitespace
5. hard cut

### 5) Split-risk scene labeling

The pipeline marks likely segmentation artifacts at segment boundaries:

- first scene in each non-initial segment: `PotentialSplitSceneStart`
- last scene in each non-final segment: `PotentialSplitSceneEnd`

These are persisted as dynamic labels on `Scene` nodes for later reconciliation workflows.

## Configuration Surface

Model config now uses context-window intent explicitly:

- `maxContextTokens` (per model)

`maxTokens` was removed from the operator-facing `lorevault.ai.models.*` surface for this flow.
Chapter-segmentation call output caps remain hardcoded in code (`maxTokens=6000`) for controlled behavior.

## Key Code References

- `lorevault-api/src/main/java/com/lorevault/api/config/LoreVaultModelsProperties.java`
- `lorevault-api/src/main/resources/application.yml`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionClient.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneDetectionService.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneWithCoordinates.java`
- `lorevault-api/src/main/java/com/lorevault/api/ai/SceneProcessingService.java`
- `lorevault-api/src/main/java/com/lorevault/api/content/Scene.java`

## Validation Evidence

Validated during implementation with:

- `mvn -pl lorevault-api clean compile -DskipTests`
- `mvn -pl lorevault-api -Dtest=SceneDetectionServiceTest,SceneDetectionHandlerTest,TriadOrchestrationServiceTest test`
- `mvn -pl lorevault-api test`

Latest full-module result at implementation time: `188` tests, `0` failures, `0` errors.

## Tradeoffs

Pros:

- prevents full-chapter context overflows,
- preserves ingestion throughput for large chapters,
- keeps fallback deterministic and observable.

Costs:

- true scenes can split at segment boundaries,
- boundary summaries may be weaker,
- triad analysis may see fragmented boundaries.

These costs are made explicit through split-risk labels.

## Boundaries / Deferred Work

This pattern does **not** include:

- split-fragment merge/reconciliation,
- tokenizer-accurate token counting,
- dynamic threshold tuning from observed usage.

Those remain future improvements after baseline safety and throughput.

## Related Documentation

- `ingestion-pipeline.md`
- `triad-analysis.md`
