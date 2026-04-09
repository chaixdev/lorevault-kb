# Ingestion Pipeline Pattern

**Status:** Established

LoreVault ingests narrative content through a staged pipeline with explicit stage boundaries and observable progress.

## Current Shape

1. ingest content and create a job
2. detect and normalize scenes
3. localize anchors into stable coordinates
4. build chunk hierarchy
5. generate embeddings
6. persist searchable content and temporal relationships

The durable pattern is:

- stage the work so failures and retries stay localized
- keep observable job and status records around the pipeline
- use triad-oriented context where temporal reasoning needs cross-chapter continuity
- preserve direct business-service ownership rather than reintroducing trivial wrapper layers

## Important Mechanisms

### Scene detection is a two-pass flow

- **Pass 1** segments a chapter into scenes and records verbatim anchors plus rich hints
- **Pass 2** normalizes those scenes into a stricter shape and derives temporal relations

### Triads are the temporal reasoning unit

Pass 2 uses a triad of:

- current chapter Pass 1 output
- previous chapter's final scene when available
- current chapter metadata

This allows cross-chapter temporal reasoning without collapsing the whole pipeline into one global analysis pass.

### Coordinates are derived after LLM analysis

Scene anchors are converted into exact character coordinates using fallback matching rather than assuming the LLM always returns perfect text boundaries.

### Persistence is append-oriented and observable

The pipeline emits job/status progress and links LLM call records to the work unit that produced them.

## Why This Pattern Exists

LoreVault needs the ingestion path to be:

- observable
- retryable at useful boundaries
- able to preserve temporal information
- simple enough to follow without unnecessary adapter ceremony

## Related Current-State Docs

Primary references:
- `../development/current/processes/scene-detection-specification.md`
- `../development/current/processes/triad-orchestration.md`
- `../development/current/data-model/ingestion-job-and-status.md`
- `../development/current/data-model/llm-call-records.md`
- `../archive/refactor/event-driven-ingestion-refactor-v0100.md`
