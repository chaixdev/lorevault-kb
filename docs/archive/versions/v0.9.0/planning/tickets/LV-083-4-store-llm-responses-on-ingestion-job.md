# LV-083-4 — Persist LLM Requests/Responses on Ingestion Job [design ticket]

## Context

- We want stronger traceability for ingestion outcomes and visibility into LLM drift over time.
- Current ingestion logs some metadata but does not persist raw LLM inputs/outputs in a structured way.
- Storing LLM I/O per step will enable observability, reproducibility, and better incident analysis.

## Problem

- Lack of persistent, queryable LLM request/response data for each ingestion step impedes debugging and longitudinal drift monitoring.

## Proposal

- Persist LLM request and response payloads at appropriate granularity:
  - Either on `IngestionJob` or on `StatusRecord` (per step). Prefer `StatusRecord` to align with step-level provenance.
  - Store minimal necessary fields: provider, model, prompt template id/version, variables, full rendered prompt (optional), response body, tokens/latency, and a normalized outcome status.
- Add simple retention controls (e.g., max size, truncation, or hashing if content too large) to avoid unbounded storage.

## Scope

- Extend `StatusRecord` (or add a companion entity) to capture LLM I/O metadata and payloads.
- Update ingestion workflow to write request/response per LLM call step (e.g., pass1, pass2, RAG QA, embeddings if applicable).
- Introduce configuration flags to enable/disable payload persistence in non-dev environments.
- Provide repository/query helpers to fetch LLM histories by job and step.

## Out of scope

- Production-grade redaction or PII scrubbing (track as follow-up)
- Full-blown observability dashboard (future work)
- Vendor-neutral export pipeline

## Technical notes

- Consider size constraints: large responses may require truncation (store hash + first N chars) and a flag `truncated=true`.
- Prefer attaching to `StatusRecord` to map 1:1 with a step. For long-running steps with multiple subcalls, either:
  - Aggregate arrays of `llmCalls[]` within the status record, or
  - Create a separate `LlmCallRecord` node/document linked to the status record.
- Add telemetry fields: `provider`, `model`, `temperature`, `top_p`, `max_tokens`, `latency_ms`, `input_tokens`, `output_tokens`.
- Persist prompt template identifier/version for reproducibility.

## Acceptance criteria

- [ ] For a sample ingestion job, the system persists at least one LLM request/response pair per relevant step.
- [ ] Data includes minimal telemetry: provider, model, latency, token counts.
- [ ] A configuration property can disable persistence of payload bodies (metadata-only mode).
- [ ] Query helper can retrieve LLM history for a job and step.
- [ ] Reasonable size protection (truncate or cap) is in place and tested.

## Quality gates

- [ ] Service-level tests validate writing/reading LLM I/O metadata and payloads for a job step.
- [ ] Integration test covers persistence behavior with size capping and truncation flag.
- [ ] No regressions in existing ingestion flow tests.

## Solution alternatives

### Approach A: Embed on StatusRecord (recommended)
- Description: Add structured fields or a `llmCalls[]` collection directly to `StatusRecord`.
- Pros: Simple, aligns with step semantics, minimal graph changes.
- Cons: StatusRecord can grow large; may require size caps.

### Approach B: Separate LlmCallRecord entity
- Description: Create `LlmCallRecord` linked to `StatusRecord` (1:N).
- Pros: Fine-grained control, scalable for many calls per step, easier retention policies.
- Cons: Slightly more complexity; more queries.

### Approach C: Attach to IngestionJob
- Description: Store calls at the job level as an array or linked nodes.
- Pros: Simpler traversal to fetch all calls; less duplication.
- Cons: Loses precise step provenance; harder to reason about multi-step workflows.

## Recommendation

- Prefer Approach B if we anticipate multiple calls per step or large payloads; otherwise Approach A is acceptable for v0.9.0 with size caps.

## Open questions

- Retention policy defaults (how many days / max bytes?)
- Do we need redaction for potentially sensitive source text?
- Which environments should persist full payloads vs. metadata-only?
