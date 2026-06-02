# Model Catalog & A/B Testing

**Status:** NOT STARTED  
**Created:** 2026-05-30  
**Context:** Code walkthrough revealed LLM extraction quality issues; design evolved from simple A/B test to full model catalog redesign

## Problem Statement

The current LLM model (gpt-oss-120b on Groq, self-hosted) produces inconsistent entity extraction across scenes:

1. **Name inconsistency:** "Kevin Jenkins" and "Jenkins" extracted as separate characters with no overlapping aliases — ConsolidationEngine can't merge them
2. **Generic role extraction:** "narrator" extracted as Individual despite prompt explicitly saying "Exclude: generic roles/ideas (→ Concept)"
3. **No way to compare models:** The `nlpSmall`/`nlpBig` slot system is dead (consolidated to single model), and there's no mechanism to run the same chapter through different models for comparison

The root cause is **model quality**, not prompt quality. The prompt already says to exclude generic roles. We need a way to try different models and compare results.

## Current Architecture (Dead Config)

```
LoreVaultModelsProperties
├── embedding: ModelProperties (perplexity/pplx-embed-v1-4b via OpenRouter)
├── nlpSmall: ModelProperties (openai/gpt-oss-120b via Groq) ← DEAD, same as nlpBig
└── nlpBig:   ModelProperties (openai/gpt-oss-120b via Groq) ← only one used

ModelSlot enum: NLP_SMALL, NLP_BIG

SpringAiConfig creates 3 beans:
├── chatClient (primary) → nlpBig
├── nlpSmallChatClient → nlpSmall
└── nlpBigChatClient → nlpBig

LoreVaultPromptProperties maps task → slot name:
├── chapter-segmentation → nlp-big
├── scene-analysis → nlp-small
├── entity-extraction → nlp-big
└── rag-answer-generation → nlp-big

LlmClient.getChatClientForModel(ModelSlot) → static bean selection
```

**Problems:**
- `nlpSmall` and `nlpBig` point to the same model — the slot distinction is meaningless
- No way to switch models without redeploying
- No way to run the same chapter through different models for comparison
- All models must use the same provider (same baseUrl/apiKey)
- `ModelSlot` enum is rigid — adding a model requires code changes

## Proposed Architecture: Model Catalog

### Config Redesign

```yaml
lorevault:
  ai:
    models:
      default: deepseek-v4-flash          # string ID, not an enum
      available:
        deepseek-v4-flash:
          provider: openai-compatible
          base-url: https://openrouter.ai/api/v1
          api-key: ${OPENROUTER_API_KEY}
          model: deepseek/deepseek-chat-v4-flash
          temperature: 0.3
          top-p: 1.0
          max-context-tokens: 1000000
        gemini-2-5-flash:
          provider: openai-compatible
          base-url: https://generativelanguage.googleapis.com/v1beta/openai
          api-key: ${GOOGLE_API_KEY}
          model: gemini-2.5-flash
          temperature: 0.2
          top-p: 1.0
          max-context-tokens: 1000000
        gpt-oss-120b:                       # current model, kept for comparison
          provider: openai-compatible
          base-url: https://api.groq.com/openai/v1
          api-key: ${GROQ_API_KEY}
          model: openai/gpt-oss-120b
          temperature: 0.3
          top-p: 1.0
          max-context-tokens: 128000
```

### Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Model identification | String ID (not enum) | Extensible without code changes |
| Default model | Config property | Fallback when no override specified |
| Cross-provider support | Each model has own baseUrl+apiKey | OpenRouter, Groq, Google, Nebius all work |
| ChatClient lifecycle | Lazy creation + cache | Don't build 10 ChatClients at startup for 2 you'll use |
| Per-job override | `?model=<id>` on ingest endpoint | Enables "replay chapter with model Y" |
| Model catalog endpoint | `GET /api/query/models` | UI dropdown for model selection |

### New Components

1. **`ChatClientRegistry`** — `@Component` that lazily creates and caches `ChatClient` instances per model ID. Reads `LoreVaultModelsProperties.available`, builds `OpenAiApi` + `OpenAiChatModel` + `ChatClient` on first request, caches for reuse.

2. **`ModelCatalogProperties`** — replaces `LoreVaultModelsProperties`. Record with `String defaultModel` and `Map<String, ModelProperties> available`. Each `ModelProperties` keeps existing fields (provider, baseUrl, apiKey, model, temperature, topP, maxContextTokens).

3. **`modelOverride` propagation:**
   - `ChapterIngestionJob` — add nullable `String modelOverride`
   - `StageExecutionContext` — add nullable `String modelOverride`
   - `IngestionJobService.createIngestionJob()` — accept and persist modelOverride
   - `StageDispatcher` — propagate modelOverride from job → context
   - `LlmClient` — when modelOverride present, use `ChatClientRegistry.getChatClient(modelOverride)` instead of default

4. **`ModelsController`** — `GET /api/query/models` returns list of available model IDs + display names for UI dropdown

### Deleted Components

| Component | Reason |
|---|---|
| `ModelSlot` enum | Replaced by string model IDs |
| `LoreVaultModelsProperties.nlpSmall` | Replaced by `available` map |
| `LoreVaultModelsProperties.nlpBig` | Replaced by `available` map |
| `SpringAiConfig` 3 hardcoded beans | Replaced by `ChatClientRegistry` |
| `LoreVaultPromptProperties` per-task slot mapping | Replaced by default model + per-job override |
| `LlmClient.getChatClientForModel(ModelSlot)` | Replaced by `ChatClientRegistry.getChatClient(String)` |

### API Changes

```
POST /api/command/ingest
  ?model=deepseek-v4-flash    ← optional, overrides default model for this job

GET /api/query/models          ← new endpoint
  → [{ "id": "deepseek-v4-flash", "displayName": "DeepSeek V4 Flash" }, ...]
```

### LlmCallRecord Impact

`LlmCallRecord` already tracks `provider` and `model` per call. No schema change needed — the model ID will be resolved to actual model name at call time and stored as before.

## Implementation Phases

### Phase 1: Model Catalog Config + ChatClientRegistry (Level 1 — same provider, different model)

**Scope:** Replace `ModelSlot`/`nlpSmall`/`nlpBig` with string-based model catalog. Support per-job model override within the same provider.

**Changes:**
- New `ModelCatalogProperties` record (default + available map)
- New `ChatClientRegistry` component (lazy creation, cache by model ID)
- `LlmClient` uses `ChatClientRegistry` instead of injected beans
- `ChapterIngestionJob` + `StageExecutionContext` gain `modelOverride` field
- `IngestionService`/`IngestionJobService` accept optional model param
- `CommandIngestionController` accepts `?model=` query param
- Delete `ModelSlot` enum, `SpringAiConfig` hardcoded beans, `LoreVaultPromptProperties` slot mapping
- Update `application-common.yml` to new config structure

**Not in scope:** Cross-provider switching (all models must share baseUrl/apiKey in Phase 1)

### Phase 2: Cross-Provider Switching (Level 2)

**Scope:** Support models from different providers (different baseUrl/apiKey per model).

**Changes:**
- `ChatClientRegistry` already handles this (each model entry has own baseUrl/apiKey)
- Add provider-specific configuration (e.g., Google's OAuth flow vs API key)
- Test with at least 2 providers (e.g., OpenRouter + Groq)

### Phase 3: A/B Comparison UI

**Scope:** Frontend support for model comparison.

**Changes:**
- `GET /api/query/models` endpoint
- UI dropdown for model selection on ingest
- Side-by-side comparison view for same chapter processed with different models
- LlmCallRecord queries to compare extraction quality

## Model Candidates (May 2026)

| Model | In $/1M | Out $/1M | Context | Provider | Notes |
|---|---|---|---|---|---|
| DeepSeek V4 Flash | $0.14 | $0.28 | 1M | OpenRouter | Best value; `deepseek-chat` alias routes here |
| Gemini 2.5 Flash | $0.30 | $2.50 | 1M | Google AI | Proven for fiction extraction |
| Gemini 3.1 Flash Lite | $0.125 | $0.75 | 1M | Google AI | Budget fallback |
| gpt-oss-120b | free | free | 128K | Groq (self-hosted) | Current model; quality issues |

**Recommendation:** Start with DeepSeek V4 Flash for scene-level extraction. If instruction following isn't good enough, try Gemini 2.5 Flash as fallback.

## Open Questions

1. **Google AI Pro subscription** — does it include API credits? If so, Gemini models may be effectively free up to a quota.
2. **OpenCode Go/Zen models** — only accessible through CLI, not programmatically. Can we get API access?
3. **Prompt tuning vs model switching** — should we also improve the prompt (e.g., stronger "narrator" exclusion) or rely solely on model quality?
4. **BOOK_INDIVIDUAL_CONSOLIDATION** — appeared not to run in smoke test, but this is likely expected with single-chapter test data (book-level consolidation merges across chapters; with 1 chapter it would produce 1:1 mappings). Not a real pipeline bug.

## Related

- `docs/planning/2026-05-29T2308_code-walkthrough-issues.md` — issue #3 (ConsolidationEngine restoration, now fixed)
- `lorevault-core/src/main/java/com/lorevault/api/config/ModelSlot.java` — to be deleted
- `lorevault-core/src/main/java/com/lorevault/api/config/SpringAiConfig.java` — to be replaced
- `lorevault-core/src/main/java/com/lorevault/api/config/LoreVaultModelsProperties.java` — to be replaced
- `lorevault-core/src/main/java/com/lorevault/api/ai/llm/LlmClient.java` — to be refactored