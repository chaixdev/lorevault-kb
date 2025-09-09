# LLM Call Records (Concise)

Purpose: Capture per-step LLM request/response metadata during ingestion for observability and traceability.

- Node label: `LlmCallRecord`
- Stored by: `LlmCallLoggingService` via `ContentPersistencePort`
- Config: `lorevault.ai.llm-logging` (see LoreVaultLlmLoggingProperties)

Key properties

- `jobId`, `statusRecordId` (linkage for indexing)
- `step` (e.g., `scene-detection-pass1` | `scene-detection-pass2`)
- Provider/model: `provider`, `model`, `temperature`, `topP`, `maxTokens`
- Telemetry: `latencyMs`, `inputTokens`, `outputTokens`, `tokensEstimated`
- Prompt: `promptTemplateId`, `storeRenderedPrompt`, `renderedPrompt`
- Payloads: `inputPreview`, `responseBody`, `responseHash`, `truncated`
- `createdAt`

Relationships

- `(LlmCallRecord)-[:OF_JOB]->(IngestionJob)`
- `(LlmCallRecord)-[:OF_STATUS]->(StatusRecord)` (optional, triad-level when applicable)

Note on verification

- Tests verify relationships with small Cypher checks rather than relying on SDN hydration. See `LlmCallRecordGraphRepository.hasOfJobRelation(...)`.

Constraints and linkage (v0.8.3+)

- Each LLM call should link to exactly one ingestion job and, when emitted during triad processing, to the specific triad status record.
- `statusRecordId` is stored redundantly on the call node for indexing/traceability in addition to the relationship.
- Multiple LLM calls may occur per triad due to retries; each call gets its own record.

Retention and size

- Full response bodies persisted by default in dev; truncation controlled by `maxBodyChars`.
- `responseHash` is SHA-256 of the original body when truncation occurs.
