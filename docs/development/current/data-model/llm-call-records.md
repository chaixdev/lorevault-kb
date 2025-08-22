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
- `(LlmCallRecord)-[:OF_STATUS]->(StatusRecord)` (optional)

Note on verification

- Tests verify relationships with small Cypher checks rather than relying on SDN hydration. See `LlmCallRecordGraphRepository.hasOfJobRelation(...)`.

Retention and size

- Full response bodies persisted by default in dev; truncation controlled by `maxBodyChars`.
- `responseHash` is SHA-256 of the original body when truncation occurs.
