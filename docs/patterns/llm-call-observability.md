# LLM Call Observability Pattern

**Status:** Established

LoreVault records LLM request/response metadata as first-class ingestion artifacts so failures, retries, and provider behavior remain diagnosable.

## Current Shape

- each LLM call is recorded as an `LlmCallRecord`
- records link back to the parent ingestion job
- when the call belongs to triad-level work, it also links to the specific status record
- token, timing, provider, model, and payload metadata are captured for debugging and auditability

## Why This Pattern Exists

LLM-heavy workflows are difficult to debug if prompts, providers, retries, and outputs disappear into logs.

This pattern keeps LLM operations observable in the same graph-oriented operational model as the ingestion pipeline itself.

## Important Consequences

- triad retries produce separate records rather than overwriting old evidence
- prompt/rendered-output storage can be tuned without changing the larger pipeline pattern
- diagnosis can be correlated at job level and status-record level

Primary references:
- `../development/current/data-model/llm-call-records.md`
- `../development/current/data-model/ingestion-job-and-status.md`
- `ingestion-pipeline.md`
