# Ingestion Job Observability Pattern

**Status:** Established

LoreVault models ingestion progress as append-only job and status records rather than as a single mutable status field.

## Current Shape

- ingestion work is grouped under an `IngestionJob`
- progress is recorded as append-only `StatusRecord` nodes
- triad-oriented processing emits `TRIAD_*` statuses for fine-grained visibility
- LLM call records attach to jobs and, when appropriate, to individual status records

## Why This Pattern Exists

The ingestion pipeline has multiple observable steps, retries, and partial failures. An append-only status chain gives better traceability than repeatedly overwriting a single status.

## Practical Consequences

- retries are visible as additional records, not hidden state transitions
- job history can be queried directly for audit and debugging
- triad-level failures remain localized and diagnosable

Primary references:
- `../development/current/data-model/ingestion-job-and-status.md`
- `../development/current/data-model/llm-call-records.md`
- `ingestion-pipeline.md`
