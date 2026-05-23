# Operator Dashboard and Admin API Brainstorm — April 2026

**Date:** April 2026  
**Status:** Brainstorm — not yet accepted  
**Purpose:** Capture the current devx/operator-tooling direction before it fragments across chat history, ad hoc notes, and implementation tickets.

---

## 1. Why This Exists

LoreVault now has enough ingestion machinery that the main bottleneck is no longer just feature implementation.

We also need a faster way to:

- upload realistic test data
- watch ingestion progress live
- inspect failures and branch behavior
- selectively rerun affected downstream stages
- avoid unnecessary full resets and repeated LLM costs

This document captures the emerging operator/devx direction while it is still exploratory.

---

## 2. The Working Operator Loop

The target loop looks like this:

1. Upload a chapter
2. Watch the automatic ingestion pipeline run
3. See stage updates in real time via an SSE-backed status feed
4. Validate results in Neo4j or through a minimal Q&A/chat surface
5. Change code
6. Selectively rerun only the affected stage or branch
7. Repeat without re-ingesting the whole corpus or redoing avoidable LLM work

This is primarily a **developer/operator workflow**, not an end-user product workflow.

---

## 3. UX Direction: Timeline First

The current thinking is that the operator UI should center on a **timeline / branch graph** rather than a flat jobs table.

Why:

- the ingestion pipeline is already event-driven
- the pipeline has branch structure
- failures happen at stage boundaries
- the operator needs to understand **where** the pipeline is, not just whether a job is "running"

Each visible node in the timeline should represent a meaningful stage event, for example:

- chapter ingestion started
- scenes detected
- chunks created
- embeddings completed
- chapter individuals resolved
- book individuals reduced
- chapter locations resolved
- book locations reduced
- ingestion completed
- ingestion failed

Each node should ideally expose:

- label
- timestamp
- status (`started`, `completed`, `failed`)
- summary counts / diagnostics
- available actions

---

## 4. Primary Goals of the Operator UI

### 4.1 Make ingestion observable

The operator should quickly answer:

- what is running
- what completed
- what failed
- which branch is blocked
- which stage emitted the latest meaningful event

### 4.2 Make selective reruns practical

The operator should be able to rerun the affected layer instead of blindly re-ingesting everything.

Examples:

- rerun location resolution
- rerun individual reduction
- rerun embeddings
- reset a derived layer and replay from there

### 4.3 Reduce dev/test friction

The tool should replace a scattered workflow across:

- scripts
- logs
- ad hoc curl calls
- manual Neo4j inspection

### 4.4 Protect the evidence floor

The emerging architecture distinction is:

- preserve evidence/raw content and useful trace records
- treat selected derived/comprehension layers as disposable and replayable

That distinction should become visible in the tooling.

---

## 5. Current Backend Shape That Supports This Direction

The repo already has several important ingredients:

- command/query split in the REST layer
- event-driven ingestion pipeline
- persisted ingestion jobs and append-only status history
- LLM call recording
- existing manual individual-resolution command endpoints
- cleanup/recompute seams for several derived graph layers
- SSE job-stream work on `feature/sse-job-stream`
- an existing minimal `/ui` dashboard surface that can be extended instead of replaced

This means the operator dashboard is not greenfield. It is more like a consolidation of capabilities that already exist in partial form.

---

## 6. Important UX/Backend Principle: Named Actions, Not Raw Internals

The operator UI should expose **named actions**, not backend implementation details.

Good operator actions:

- `Reset stage state`
- `Retrigger stage`
- `Replay downstream branch`
- `Open graph`
- `Inspect job timeline`

Bad operator actions:

- `Emit ChunksCreatedEvent`
- `Run arbitrary Cypher`

Internally, a named action may publish an event or execute parameterized Cypher.

But the UI should remain:

- safe
- explicit
- auditable
- understandable without knowing pipeline internals

---

## 7. API Direction Under Discussion

One key architectural question is how to namespace the current API surface while LoreVault is still in an R&D/operator-heavy stage.

### Current reality

The existing API surface is largely:

- ingestion-oriented
- observability-oriented
- diagnostic/operator-facing
- not yet a committed end-user API contract

### Current direction under consideration

Move the existing REST surface under an **admin namespace** early, for example:

- `/api/admin/query/...`
- `/api/admin/command/...`

Rationale:

- preserves explicit CQRS in URLs
- makes the current surface truthfully admin/operator-facing
- prevents the current R&D endpoints from hardening into the accidental public API
- leaves future end-user API space open until product requirements are clearer

### Important note

This is still exploratory.

What seems increasingly likely is:

- current endpoints are better treated as **admin/internal R&D surface**
- future user-facing APIs should be designed later against actual product needs rather than inherited accidentally from ingestion/admin workflows

---

## 8. Early Backend Capability Wishlist for the Dashboard

The timeline-first dashboard implies several backend capabilities.

### Query-side needs

- job timeline history endpoint
- filtered SSE stream per job or scope
- richer normalized event payloads for timeline rendering
- operator summary views for chapters/books/jobs

### Command-side needs

- stage-specific retrigger endpoints
- stage-specific reset endpoints
- location-resolution manual endpoints to match the existing individual-resolution endpoints
- curated graph repair/reset actions
- audit logging for operator actions

### Deeper future need

If LoreVault wants to replay more of the pipeline without repeated LLM cost, it may later need a path to reuse stored `LlmCallRecord` outputs rather than invoking the model again.

That does **not** need to block the first dashboard, but it is part of the longer arc.

---

## 9. Phased Shape

### Phase 1 — Observable operator console

- extend the existing `/ui` dashboard
- add SSE-backed job feed
- add timeline/history view for jobs
- keep actions minimal

### Phase 2 — Actionable stage controls

- add stage reset/retrigger actions
- add location rerun paths
- add better diagnostics and audit trail

### Phase 3 — Smarter replay and evidence-preserving iteration

- finer-grained derived-layer rebuilds
- replay-from-stage orchestration
- possible reuse of recorded LLM outputs where appropriate

---

## 10. Open Questions

### API namespace

- Should the current API surface move wholesale under `/api/admin/query` and `/api/admin/command`?
- Should any current endpoints remain outside that surface?

### UI surface

- Should the operator UI continue to live under `/ui/*`, or should it later move under a clearer admin-facing URL space?

### Replay model

- Which stages should support reset + retrigger immediately?
- Which stages are safe to replay without fresh LLM calls?
- Which stages need stronger idempotency guarantees first?

### Authorization

- When the admin/operator namespace becomes real, what security model should protect it?
- How should OpenAPI/docs distinguish admin/internal endpoints from future public endpoints?

---

## 11. Current Working Direction

The current direction is:

1. Build the operator dashboard first
2. Make the pipeline observable in a timeline/branch-oriented way
3. Add selective reset/retrigger flows for derived stages
4. Treat the current REST API surface as admin/operator-facing rather than prematurely as the public user API
5. Leave end-user API design intentionally open until the real product surface is better understood

That is not yet a final architectural decision, but it is the clearest working direction today.
