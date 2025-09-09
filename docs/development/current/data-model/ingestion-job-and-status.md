# Ingestion Job and Status Data Model (v0.8.3+)

This document defines the nodes and relationships for ingestion jobs, their status records, and how they connect to LLM call records, including per-triad status chaining for scene normalization.

## Nodes

### IngestionJob

- id: UUID (unique)
- createdAt: DateTime
- currentStatus: String (QUEUED|PROCESSING|COMPLETED|FAILED)
- metadata: Map (optional)

### StatusRecord

- id: UUID (unique)
- jobId: UUID (redundant for indexing)
- status: String (QUEUED|PROCESSING|TRIAD_STARTED|TRIAD_LLM_CALLED|TRIAD_PARSED|TRIAD_PERSISTED|COMPLETED|FAILED)
- triadKey: String (optional; identifies the triad, e.g., chapterOrder:prevSceneIndex)
- message: String (optional)
- createdAt: DateTime

## Relationships

- (Chapter)-[:HAS_JOB]->(IngestionJob)
- (IngestionJob)-[:HAS_STATUS]->(StatusRecord) [append-only]
- (LlmCallRecord)-[:OF_JOB]->(IngestionJob)
- (LlmCallRecord)-[:OF_STATUS]->(StatusRecord) [when applicable; triad-level]

## Status Chain Semantics

- Append-only: Status records form an immutable audit trail
- Per-triad statuses are emitted during Pass 2 normalization to reflect progress at triad granularity
- Legacy step names removed; standardized TRIAD_* steps avoid duplication and ambiguity

## Constraints

- A StatusRecord must reference a valid IngestionJob
- TRIAD_* statuses SHOULD include a `triadKey` for correlation
- LLM calls emitted during triad processing SHOULD reference the corresponding triad status via OF_STATUS

## Querying Patterns (Examples)

- Latest job status:
  - MATCH (j:IngestionJob {id:$jobId})-[:HAS_STATUS]->(s:StatusRecord) RETURN s ORDER BY s.createdAt DESC LIMIT 1
- LLM calls for a specific triad:
  - MATCH (j:IngestionJob {id:$jobId})<-[:OF_JOB]-(c:LlmCallRecord)-[:OF_STATUS]->(s:StatusRecord {triadKey:$triadKey}) RETURN c ORDER BY c.createdAt ASC

## Observability

- Logs should include jobId, triadKey, and status transitions
- LlmCallRecord stores summarized prompt/model and token/timing telemetry for diagnosis
- Retry attempts are visible as repeated TRIAD_* chains per triad with distinct timestamps
