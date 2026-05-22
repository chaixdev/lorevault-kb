# Strategic n8n Enhancement — May 2026

**Date:** May 2026
**Status:** Brainstorm — exploration and synthesis
**Purpose:** Map what n8n should own vs. what should stay in Spring Modulith, identify high-value integration points, and define architecture boundaries for strategic enhancement without replacement.

---

## 1. Why This Exists

LoreVault currently has zero n8n integration. The entire system — ingestion pipeline, entity resolution, semantic search, SSE streaming, operator UI — lives in Spring Modulith (`lorevault-core` + `lorevault-web` + `lorevault-catalog`). This works for the current scope of one developer and one deployment.

But LoreVault has operational gaps that Spring is poorly suited to fill:

- **No human-in-the-loop (HITL)** — zero pending-review states, no approve/reject endpoints, no review queue
- **No multi-channel notifications** — failed ingestion jobs are logged and dropped; there is no Slack/Telegram/email alerting
- **No retry/escalation policy** — basic `RetryTemplate` (3 attempts, 2× backoff) at the handler level, but no outbound queue for downstream systems to trigger retry
- **No dead letter queue** — failed events are logged and dropped; no persistence for replay
- **No webhook trigger surface** — chapters must be submitted through the operator UI or REST endpoints; no external event-driven triggers

n8n is purpose-built for exactly these concerns: webhook triggers, multi-channel notifications, retry/escalation, and human-in-the-loop gates with 400+ pre-built connectors. It also has deep LangChain integration and agentic AI workflow capabilities that connect to the future direction of agentic graph traversal.

This document is **not about replacing** LoreVault's core with n8n. It is about identifying where n8n can strategically enhance LoreVault, and what architectural boundaries keep the two systems clean and testable.

---

## 2. What n8n Is Good At (and What It Isn't)

### 2.1 n8n's strengths (relevant to LoreVault)

| Capability | n8n Fit | Notes |
|-----------|---------|-------|
| Webhook triggers | Excellent | 400+ connectors; start workflows from any external event |
| Human-in-the-loop gates | Excellent | Approve/reject nodes, human fallback, conditional routing |
| Multi-channel notifications | Excellent | Slack, Telegram, email, Discord, webhook — all pre-built |
| Retry/escalation policy | Excellent | Per-node retry config, error output branching, sub-workflow callbacks |
| Cron scheduling | Excellent | Built-in schedule triggers |
| AI agent orchestration | Excellent | LangChain Agent nodes, chat models (Groq, OpenAI, OpenRouter), memory, tools — purpose-built for tool-calling loops with visual step-through debugging |
| Simple RAG pipelines | Good | Vector store nodes (Qdrant, Pinecone, Supabase), document loaders, chunking |
| Document processing | Good | Google Drive, file nodes, text extraction |
| Visual workflow debugging | Excellent | Step-through execution, inline output inspection, replay with previous data |

### 2.2 n8n's weaknesses (where Spring wins)

| Concern | Why Spring Is Better |
|---------|---------------------|
| Type-safe domain events | 17 typed `ApplicationEvent` subclasses with compiler-checked payloads vs. JSON blobs |
| Neo4j graph operations | Spring Data Neo4j object-graph mapping, Cypher query generation, relationship traversal, index management |
| Transaction boundaries | `REQUIRES_NEW` on PostgreSQL catalog, Neo4j session management, multi-database consistency |
| Fan-out/fan-in coordination | `ConcurrentHashMap`-based `CompletionState` tracking, single-threaded serialization guarantee, bounded failure maps |
| Structured LLM outputs | Spring AI `@Tool` / `@Structured` output contracts with type-safe deserialization |
| Vector embedding at scale | `EmbeddingModel` with batching, dimension management, Neo4j vector index integration |
| SSE streaming | `SseEmitter` with keepalive scheduling, `CopyOnWriteArrayList` client management |
| Modulith enforcement | ArchUnit rules, `@ApplicationModule(CLOSED)`, compile-time module boundaries |
| Testability | `@SpringBootTest` with Testcontainers Neo4j/PostgreSQL; n8n workflow testing is manual (mitigated by testing Spring endpoints independently) |
| Observability | Spring Actuator, Micrometer tracing, structured logging with MDC propagation |

### 2.3 The fundamental split: ingestion vs. retrieval

The architecture separates along a natural seam that reflects genuinely different computational shapes:

| Concern | Character | Owned By |
|---------|-----------|----------|
| **Ingestion pipeline** | Deterministic, multi-stage, event-driven, write-heavy, type-safe | **Spring Modulith** |
| **Retrieval + Q&A** | Agentic, tool-calling, context-gathering, read-only, conversational | **n8n agent loop** |

**Ingestion is a pipeline** — submit chapter → scene detection → chunking → embedding → entity resolution → completion. It's deterministic (given the same input, same result), async, and write-heavy. Spring's event-driven architecture with typed payloads and `ConcurrentHashMap`-based fan-in coordination is purpose-built for this. There are no branching decisions, no human judgment, and no tool-calling loops.

**Retrieval is an agent loop** — user asks question → agent decides what tools to call → calls tools → observes results → decides next step → synthesizes answer. It's inherently non-deterministic, conversational, and read-only. n8n's LangChain Agent nodes are purpose-built for exactly this pattern: tool-calling loops with memory, conditional branching, multi-step reasoning, and visual step-through debugging.

#### The Cypher-as-tool pattern: keeping domain logic in Spring

The boundary works because **n8n doesn't need to know Neo4j schema or domain logic.** It only needs to know what tools are available and what they do. The tools are Spring endpoints that encapsulate all domain knowledge.

The critical architectural seam is a **Cypher generation endpoint** in Spring:

```
n8n Agent                          Spring (LoreVault)
┌────────────────────┐   REST   ┌──────────────────────────────┐
│ Tool:              │─────────►│ POST /api/query/generate-    │
│ generate_cypher    │          │   cypher                      │
│                    │          │                              │
│ Sends: "find       │          │ Spring's LLM (with graph     │
│ characters who     │          │ schema knowledge) generates  │
│ appeared with      │          │ validated Cypher, executes   │
│ Gandalf in The     │          │ read-only with row limits +  │
│ Hobbit"            │◄─────────│ timeout, returns results     │
└────────────────────┘          └──────────────────────────────┘
```

Spring owns the Cypher generation — it knows entity types, relationship types, property names, and index structures. It validates the query (read-only enforcement, row limits, timeout bounds, pattern allowlisting) and executes it. n8n receives structured results without ever knowing the graph schema.

The agent's prompt needs coarse domain vocabulary ("the system has books, chapters, scenes, characters, locations, events") and tool-use instructions. Under the ingestion-vs-retrieval boundary, tuning the retrieval agent's instruction prompt is squarely within retrieval's scope — no different from how ingestion owns its own prompt engineering for scene detection and triad analysis. Each side owns its prompts; neither leaks.

This pattern keeps the two systems in their strength zones: n8n excels at agentic tool-calling and human interaction; Spring excels at graph operations and type-safe domain logic.

---

## 3. Current State: What LoreVault Has

### 3.1 The ingestion pipeline

```
ChapterIngestionEvent
  └── SceneDetectionHandler (sequential, sceneDetectionTaskExecutor)
        └── ScenesDetectedEvent ──┬── ChunkingHandler → EmbeddingHandler
                                  ├── ChapterEventResolutionHandler → ChapterEventEmbeddingHandler
                                  ├── ChapterIndividualResolutionHandler → BookIndividualReductionHandler
                                  ├── ChapterLocationResolutionHandler → BookLocationReductionHandler
                                  ├── ChapterCollectiveResolutionHandler → BookCollectiveReductionHandler
                                  └── ChapterObjectResolutionHandler → BookObjectReductionHandler
                                        │
                                        ▼ (all converge on IngestionCompletionCoordinator)
                                  IngestionCompletedEvent (terminal)
```

**Key characteristics:**

- 14 `@Async` + `@EventListener` handlers across 3 thread pools
- `IngestionCompletionCoordinator` tracks 7 parallel branches via `ConcurrentHashMap<CompletionKey, CompletionState>`
- Zero timeout on branch completion — silent hang if a handler crashes and swallows the error
- No dead letter queue — failed events are logged and dropped
- No HITL checkpoint — jobs go directly from in-progress to `COMPLETE` or `FAILED`
- SSE broadcasts all events to all connected clients via `JobStatusBroadcaster`

### 3.2 Async executors

| Executor | Core/Max | Queue | Purpose |
|----------|----------|-------|---------|
| `ingestionTaskExecutor` | 1/1 | 100 | Fan-in completion tracking (single-threaded serialization) |
| `ingestionLaneTaskExecutor` | 4/6 | 100 | Branch handlers (parallel resolution lanes) |
| `sceneDetectionTaskExecutor` | 1/3 | 10 | AI scene detection isolation |

### 3.3 Retry configuration

Three `RetryTemplate` beans: `llmRetryTemplate` (3 attempts, 2s initial, 30s max), `dbRetryTemplate` (3 attempts, 100ms initial, 1s max), default (3 attempts, 1s initial, 15s max). Retry decisions are made per-handler via `isRetryableError()` pattern matching on exception types and message strings.

### 3.4 SSE streaming

`JobStatusBroadcaster` maintains a `CopyOnWriteArrayList<SseEmitter>`. Every `IngestionEvent` subclass is broadcast to all connected clients as `status-update` SSE events. A `@Scheduled(fixedRate = 30_000)` keepalive sends `:keepalive` comments. No per-client job filtering.

### 3.5 External service calls

| Model Slot | Typical Provider | Purpose |
|-----------|-----------------|---------|
| `embedding` | OpenRouter (Perplexity) | Vector embeddings (1536 dim) |
| `nlp-small` | Groq | Scene analysis, event coreference |
| `nlp-big` | Groq | Chapter segmentation, RAG generation |

All LLM calls logged to Neo4j via `LlmCallRecord` nodes. Structured output contracts use Spring AI annotations.

### 3.6 Operator UI

Thymeleaf-based: hierarchical library selection, batch chapter upload, live job visibility, query panel, retrieval-mode selection. No review queue, no approve/reject controls, no confidence score display.

---

## 4. Strategic Integration Points

### 4.1 Tier 1: HITL Layer (Highest Value)

**What doesn't exist today:** Any mechanism for human review of ingestion results.

**What n8n would provide:**

```
Spring (LoreVault)                        n8n
┌─────────────────────┐     REST      ┌──────────────────────────┐
│ IngestionComplete   │──────────────►│ Webhook: job completed   │
│ (holds job in       │               │   ↓                      │
│  PENDING_REVIEW)    │               │ Load job details via API │
│                     │               │   ↓                      │
│ POST /api/review/   │◄──────────────│ Present in review queue  │
│   {jobId}/approve   │   HTTP POST   │   ↓                      │
│   {jobId}/reject    │               │ Human approves/rejects   │
│                     │               │   ↓                      │
│                     │               │ Notify via Slack/email   │
│                     │               │   ↓                      │
│                     │               │ On reject: trigger       │
│                     │               │ retry/correction flow    │
└─────────────────────┘               └──────────────────────────┘
```

**New Spring-side state needed:**
- `IngestionStatus.PENDING_REVIEW` — job completes pipeline but is held before terminal `COMPLETE`
- `ReviewController` with `POST /api/review/jobs/{jobId}/approve` and `POST /api/review/jobs/{jobId}/reject`
- `IngestionReadyForReviewEvent` published before terminal completion
- Review metadata: reviewer identity, timestamp, notes, rejection reason

**n8n workflow shape:**
1. Webhook trigger: `/api/webhook/ingestion-review` (called by Spring when `PENDING_REVIEW`)
2. HTTP Request node: fetch job details, entity resolution summary, LLM confidence scores
3. Switch node: route to appropriate reviewer/channel based on universe/book
4. Human-in-the-loop node: present review UI (entity merges, scene boundaries, confidence flags)
5. On approve: `POST /api/review/jobs/{jobId}/approve` with reviewer note
6. On reject: `POST /api/review/jobs/{jobId}/reject` + Slack notification + optional retry trigger

**Why n8n here:** Spring has no built-in review queue, no notification connectors, no human-task orchestration. Building this in Spring would mean writing what n8n already provides as drag-and-drop nodes.

**Deferred scope:** The initial HITL design places the review gate at the terminal boundary (post-ingestion, before `COMPLETE`). Mid-pipeline HITL — pausing execution before the 7 parallel branches fan out so an operator can correct scene detection output — is a more advanced pattern that requires pause/resume APIs at the scene boundary. This is deferred until review workflow experience makes the case for it.

### 4.2 Tier 2: Notifications and Alerting (High Value)

**What doesn't exist today:** Zero operational alerting. Failed ingestion jobs are logged and forgotten.

**n8n workflow shape:**
1. SSE listener or polling loop: watch `/api/query/jobs` for status changes
2. Filter node: detect `FAILED` status on jobs that were previously in-progress
3. Switch node: route by failure type (LLM error, Neo4j error, content error)
4. Notification nodes: Slack for dev team, email for content owners
5. Optional: auto-retry for transient errors (LLM rate limits), escalate to human for persistent errors

**Why n8n here:** n8n has 400+ notification connectors. Spring has zero. This is a pure integration concern with no domain logic.

### 4.3 Tier 3: Webhook Trigger Surface (Medium Value)

**What doesn't exist today:** Chapters must be submitted through the operator UI or REST endpoints. No external triggers.

**n8n workflow shape:**
1. Discord webhook: "new chapter dropped in #lore-dump"
2. Google Drive trigger: new file in shared folder
3. Email trigger: chapter submitted via email
4. → All route to `POST /api/command/ingest` with extracted content

**Why n8n here:** This is connector territory. n8n has Discord, Google Drive, and email nodes. Spring would require writing each integration from scratch.

### 4.4 Tier 4: Escalation and Agent-Loop Retry (Medium Value)

**What exists today:** Basic `RetryTemplate` at the handler level. No outbound escalation. No dead letter queue.

**Clarified boundary:** Spring owns retry within the ingestion pipeline — it classifies errors (LLM rate limit vs. content error vs. Neo4j connection), applies backoff via `RetryTemplate`, and decides what's retryable. n8n's role is narrower: **escalation** when Spring exhausts retries, and **its own retry** within the agentic retrieval loop.

**n8n escalation workflow:**
1. Spring handler exhausts retries → job transitions to `FAILED`
2. n8n detects `FAILED` state (polling `/api/query/jobs`)
3. Switch node: route by failure type
4. Notification nodes: Slack for dev team, email for content owners
5. Optional: trigger manual retry via `POST /api/jobs/{jobId}/retry`

**n8n agent-loop retry:** n8n's built-in per-node retry configuration handles transient failures within the retrieval agent's tool-calling loop — LLM timeout, generate-cypher endpoint temporarily unavailable, etc. This is n8n operating its own domain (agentic retrieval) with its own mechanisms.

**Why this split:** Spring's error classification is domain-aware (it knows the difference between a content error and a rate limit) and shouldn't leak through the API. n8n's escalation role is pure notification routing — no domain knowledge required. Similarly, n8n retrying its own agent tool calls is standard workflow behavior, not domain logic leakage.

### 4.5 Tier 5: Agentic Retrieval (Recommended Path: n8n Agent + Cypher-as-Tool)

LoreVault's knowledge graph is a natural target for agentic traversal: "find all characters who appeared in scenes with Character X in Book Y and trace their relationships." The retrieval side of LoreVault is inherently agentic — the system can't know in advance what tools a question needs, so it must reason, call tools, observe results, and decide next steps.

**Recommended architecture:** n8n hosts the agent loop. Spring provides tools as REST endpoints. The Cypher-as-tool pattern (see §2.3) keeps all domain logic in Spring.

```
n8n Agent (LangChain)                     Spring (LoreVault tools)
┌─────────────────────────┐    REST    ┌───────────────────────────────┐
│ User: "What happened    │            │                               │
│  with the ring in       │            │                               │
│  Mordor?"               │            │                               │
│   ↓                     │            │                               │
│ Agent reasons: "I       │            │                               │
│  need to find scenes    │───────────►│ POST /api/query/generate-     │
│  in Mordor, then        │            │   cypher                      │
│  characters there"      │            │ {instruction: "find scenes    │
│   ↓                     │            │  set in Mordor"}              │
│ Agent receives results  │◄───────────│ Spring generates + executes   │
│   ↓                     │            │ validated Cypher, returns     │
│ Agent: "now get         │───────────►│ POST /api/query/generate-     │
│  characters in those    │            │   cypher                      │
│  scenes"                │            │ {instruction: "find           │
│   ↓                     │◄───────────│  characters in scenes [ids]"} │
│ Agent: "now synthesize  │───────────►│ POST /api/query/ask/rag       │
│  an answer from these   │            │ {context: [scene texts,       │
│  scene texts"           │◄───────────│  character names]}            │
│   ↓                     │            │                               │
│ Agent returns answer    │            │                               │
│ to user                 │            │                               │
└─────────────────────────┘            └───────────────────────────────┘
```

**Why n8n for the agent loop:**
- LangChain Agent nodes with visual step-through debugging — see exactly why the agent picked each tool call, inspect intermediate results, replay with previous data. For a one-developer project, this is a force multiplier.
- Ships faster than building an equivalent agent loop in Java (1-2 weeks vs. 3-4 weeks for langchain4j + custom debugging tooling)
- Memory, conditional branching, and multi-step reasoning are built-in — no infrastructure work

**Why Spring for the tools:**
- Cypher generation knows the full graph schema (entity types, relationship types, property names, indexes)
- Query validation: read-only enforcement, row limits, timeout bounds, pattern allowlisting
- Domain logic stays in Spring — n8n never touches Neo4j directly
- Spring endpoints are independently testable with `@SpringBootTest` + Testcontainers

**Long-term idea — Cypher Template Catalog:** As the agent generates Cypher queries, observe and cluster them (pgvector embedding — same pattern as the existing Relation Catalog module). Promote high-frequency, validated patterns to typed tool endpoints. This mirrors the Relation Catalog architecture: closed Spring Modulith module, PostgreSQL + Flyway + pgvector, `definitionKey`-style stable identifiers.

### 4.6 Tier 5b: langchain4j in Spring (Fallback — Consider Only If Needed)

If the n8n agent loop runs into practical limits (n8n workflow complexity outgrows visual debugging, testing confidence demands automated agent-loop tests, or direct Neo4j access becomes a performance bottleneck), langchain4j in Spring is the fallback.

In this model, the agent loop runs in the same JVM as Spring with direct Neo4j access. n8n is called only when the agent needs human judgment mid-traversal (the 4.1 HITL pattern). The Cypher-as-tool endpoint already covers the testing gap — you can validate Cypher generation independently in Spring's test suite regardless of where the agent loop runs.

**Decision trigger:** Migrate from n8n agent to langchain4j if (a) n8n workflow complexity makes step-through debugging slower than Java IDE debugging, (b) the agent loop needs automated CI testing that n8n can't provide, or (c) REST hop latency for tool calls becomes a user-facing issue. Until then, n8n agent + Cypher-as-tool is the faster path to working retrieval.

### 4.7 Tier 6: Cron-Based Maintenance (Low Value, Easy Win)

- Scheduled Neo4j index rebuild
- Periodic embedding model version check
- Catalog definition staleness check
- Health check aggregation across all services

All of these are natural cron-triggered n8n workflows with no domain logic.

---

## 5. Architecture Boundaries

### 5.1 The boundary: ingestion pipeline vs. retrieval agent

```
┌──────────────────────────────────┐       ┌──────────────────────────────────┐
│        Spring Modulith            │       │              n8n                 │
│                                   │       │                                  │
│  INGESTION PIPELINE               │       │  RETRIEVAL + INTERACTION         │
│  ✓ Scene detection + triad       │       │  ✓ Agentic tool-calling loop     │
│  ✓ Entity resolution lanes       │  REST │  ✓ HITL review gates             │
│  ✓ Chunking + embedding          │◄─────►│  ✓ Multi-channel notifications   │
│  ✓ Temporal reasoning            │       │  ✓ Webhook triggers              │
│  ✓ Fan-out/fan-in completion     │       │  ✓ Retry/escalation policy       │
│  ✓ SSE job streaming             │       │  ✓ Cron scheduling               │
│  ✓ Relation Catalog              │       │  ✓ Human-task orchestration      │
│                                   │       │                                  │
│  RETRIEVAL TOOLS (for n8n)       │       │  ✗ No direct Neo4j access        │
│  ✓ Cypher generation endpoint    │       │  ✗ No domain logic               │
│  ✓ Entity listing endpoint       │       │  ✗ No entity resolution          │
│  ✓ Relation traversal endpoint   │       │  ✗ No ingestion pipeline stages  │
│  ✓ RAG generation endpoint       │       │                                  │
│  ✓ Semantic search endpoint      │       │                                  │
└──────────────────────────────────┘       └──────────────────────────────────┘
```

**Rules:**
1. n8n communicates with Spring exclusively via REST APIs — no direct Neo4j or PostgreSQL connections
2. Spring owns all domain state, Neo4j operations, and LLM calls involving graph schema knowledge
3. Spring defines the API contract — n8n consumes it
4. n8n owns the agent decision loop, HITL gates, and multi-channel interaction surface
5. The ingestion pipeline is independent of n8n — Spring remains deployable and testable without n8n
6. The retrieval tools are additive — they wrap existing Spring capabilities (search, Cypher, RAG) behind agent-friendly endpoints

### 5.2 Security model

**Separate Neo4j principals:**

| Principal | Permissions | Used By |
|-----------|------------|---------|
| `ingestion-writer` | Read + Write on all nodes/relationships | Spring ingestion handlers |
| `retrieval-reader` | Read-only on all nodes/relationships | Spring retrieval endpoints |
| `n8n-reader` | Read-only on all nodes/relationships | n8n (via Spring API, not direct connection) |

Even if n8n consumed a Neo4j principal directly, it should use the same read-only `retrieval-reader` principal as Spring's retrieval endpoints. But the preferred path is **n8n never touches Neo4j directly** — it goes through Spring's REST API, which gives defense-in-depth: Neo4j RBAC + Spring validation + catalog observability. Direct n8n-to-Neo4j Cypher execution is explicitly avoided by the Cypher-as-tool pattern (§2.3); all Cypher is generated and executed within Spring.

### 5.3 API surface needed

**New Spring endpoints for n8n integration:**

| Endpoint | Method | Purpose | Priority |
|----------|--------|---------|----------|
| `/api/review/jobs/{jobId}/approve` | POST | Human approves ingestion result | Tier 1 |
| `/api/review/jobs/{jobId}/reject` | POST | Human rejects with reason | Tier 1 |
| `/api/review/jobs/pending` | GET | List jobs awaiting review | Tier 1 |
| `/api/query/generate-cypher` | POST | Generate + execute validated Cypher from free-text instruction | Tier 5 |
| `/api/query/entities/{type}` | GET | Typed entity listing (for agentic retrieval) | Tier 5 |
| `/api/query/relations` | GET | Graph neighbors from entity ID (for agentic retrieval) | Tier 5 |
| `/api/query/tools` | GET | Tool metadata (names, descriptions, parameter schemas) for n8n agent prompt generation | Tier 5 |
| `/api/webhook/ingestion-event` | POST | n8n receives ingestion lifecycle events | Tier 3 |
| `/api/jobs/{jobId}/retry` | POST | Trigger retry of failed pipeline stage | Tier 4 |

**Existing endpoints n8n will consume:**

| Endpoint | Purpose |
|----------|---------|
| `/api/command/ingest` | Chapter submission |
| `/api/query/jobs/{jobId}` | Job status polling |
| `/api/query/jobs` | Job listing |
| `/api/query/ask/rag` | RAG Q&A |
| `/api/query/jobs/stream` | SSE job events |

---

## 6. What Should NOT Move to n8n

### 6.1 The ingestion pipeline core

The fan-out/fan-in topology with 7 parallel branches, type-safe event payloads, `ConcurrentHashMap`-based completion tracking, and single-threaded serialization on the coordinator is domain infrastructure. Rebuilding it in n8n's visual editor would:

- Lose type safety (JSON blobs instead of `ScenesDetectedEvent`, `EmbeddingsCompletedEvent`, etc.)
- Lose the single-threaded serialization guarantee on the coordinator
- Lose the bounded failure map (10K entry cap with oldest-first pruning)
- Add network latency between every pipeline stage
- Make the pipeline untestable (no `@SpringBootTest` with Testcontainers)

### 6.2 Entity resolution and reduction

The `IndividualMention → ChapterIndividual → BookIndividual` ladder (and Location, Object, Collective, Event variants) operates directly on Neo4j with Cypher merge queries, trie-based named entity matching, and ANN candidate generation. This is pure domain logic with no workflow-orchestration character.

### 6.3 Scene detection and triad analysis

Two-pass LLM orchestration with context-budget checks, deterministic segmented fallback, and windowed triad entity extraction uses Spring AI structured outputs and prompt contracts. The orchestration is tightly coupled to the domain — it's not separable workflow glue.

### 6.4 Semantic search and hybrid retrieval

Vector search + graph traversal with reciprocal rank fusion, spoiler-aware Cypher filtering, and entity-grounded RAG context assembly. These stay in Spring as tools the n8n agent calls — n8n never runs Cypher directly. n8n's built-in RAG templates (Qdrant, Pinecone) are designed for document retrieval, not knowledge graph traversal, and offer no value over Spring's existing graph-native search. The generate-cypher endpoint (§2.3) is the correct bridge: Spring owns search implementation, n8n owns the agent decision of which search to call.

### 6.5 SSE job streaming

n8n doesn't do server-sent events. This is pure Spring infrastructure (`SseEmitter`, `CopyOnWriteArrayList`, `@Scheduled` keepalive). If n8n needs job status, it polls `/api/query/jobs` or consumes a webhook.

### 6.6 Relation Catalog

Closed Spring Modulith module with PostgreSQL + Flyway + pgvector. Not workflow territory.

---

## 7. Implementation Sequencing

The sequencing balances two learning goals: (a) ship working retrieval + HITL fast via n8n to build pattern intuition, and (b) build AWS platform skills on infrastructure n8n can't teach. The key insight: n8n teaches **patterns** (retry, escalation, HITL, agentic tool-calling) in hours to days. AWS teaches **platform skills** (IAM, VPC, SQS semantics, DynamoDB conditional writes, Step Functions ASL) — skills n8n has zero overlap with. The sequence front-loads AWS prerequisites (n8n can't accelerate them), then uses n8n for rapid pattern learning, then returns to AWS with acquired pattern intuition so you're learning AWS verbs, not inventing patterns at the same time.

### The deliberate split: what goes where and why

| Concern | Goes to | Time to demo | CV-marketable skill |
|---------|---------|-------------|---------------------|
| Agentic retrieval MVP | n8n | 1-2 weeks | LangChain agent design, tool-calling loop architecture, AI product demo |
| HITL review gate | n8n | 3-5 days | Human-in-the-loop workflow design, approval state machines |
| Notification routing | n8n | 2-3 days | Multi-channel alerting patterns, escalation design |
| ECS Fargate deployment | AWS | 1-2 weeks | Container orchestration, IAM, VPC, ALB — prerequisites for any cloud role |
| Secrets Manager | AWS | 3-5 days | IAM least-privilege, managed secrets rotation |
| CloudWatch logging | AWS | 3-5 days | Structured JSON logging, Logs Insights — foundational observability |
| SQS pipeline stages | AWS | 2-3 weeks | Queue semantics, visibility timeouts, at-least-once delivery, DLQ patterns |
| DynamoDB job state | AWS | 1-2 weeks | Conditional writes, TTL, GSI design — pure AWS data modeling |
| Step Functions orchestration | AWS | 2-3 weeks | ASL state machines, error handling, parallel branches, input/output processing |

n8n gives you a working AI product demo and operational patterns in under 2 weeks. AWS gives you cloud platform skills that transfer to any AWS role. The sequence uses n8n's speed for pattern learning, then returns to AWS for platform depth once you know *what* you're building.

### Phase 1: AWS Foundation (Weeks 1-3)
**Goal:** Get LoreVault running on AWS infrastructure. These are prerequisites for any cloud-native deployment, and n8n accelerates none of them — there's no n8n node for ECS task definitions or IAM policies. Do them first, get them on the CV.

- ECS Fargate deployment for `lorevault-web` (container build, task definition, service, ALB)
- Secrets Manager for API keys (Groq, OpenRouter) — replaces `.env` file
- CloudWatch structured logging — JSON log format, Logs Insights queries
- IAM roles with least-privilege for ECS tasks
- Health check endpoint wired to ALB target group

**CV skills:** Container orchestration, IAM, secrets management, CloudWatch — foundational for any AWS role.

### Phase 2: n8n Sprint — Patterns and Demos (Weeks 4-5)
**Goal:** Ship working retrieval + HITL fast. n8n's visual editor and pre-built nodes compress what would be weeks of Java infrastructure into days of workflow configuration. Build pattern intuition before tackling AWS-specific mechanisms.

**Week 4 — HITL + Notifications:**
- Set up n8n instance (Docker, same VPC/network as Spring)
- Spring: Add `PENDING_REVIEW` job status, `ReviewController`, `IngestionReadyForReviewEvent`
- n8n: Review queue workflow with human-in-the-loop node, Slack notification on completion
- n8n: Job failure detection → Slack alert (polling `/api/query/jobs`)

**Week 5 — Agentic Retrieval MVP:**
- Spring: `POST /api/query/generate-cypher` endpoint — LLM generates validated Cypher from free-text instruction, executes read-only with bounds
- Spring: `GET /api/query/entities/{type}?book={id}` — typed entity listing
- n8n: LangChain Agent with tools pointing to Spring endpoints
- n8n: Agent prompt with coarse domain vocabulary (books, chapters, scenes, characters, locations, events)
- End-to-end test: ask a real lore question, watch the agent decide which tools to call

**CV skills:** Agentic retrieval demo (working AI product), HITL pattern design, cross-platform trace ID propagation — the observability gap becomes a learning goal (see §8.2). The Cypher-as-tool architecture demonstrates API contract design between heterogeneous systems.

### Phase 3: AWS Native Pipeline — Platform Depth (Weeks 6-8)
**Goal:** Now that you understand retry, escalation, fan-out, and HITL patterns from n8n, rebuild the pipeline infrastructure on AWS. This is platform learning, not pattern invention — you already know *what* retry and fan-out look like; you're learning the AWS-specific *verbs* to express them.

- SQS queues for pipeline stage events (replacing Spring `ApplicationEvent` pub/sub across process boundaries)
- DynamoDB for job state (replacing in-memory `ConcurrentHashMap<CompletionKey, CompletionState>`)
- Step Functions for the ingestion state machine (replacing `IngestionCompletionCoordinator` fan-in)
- SNS for system-to-system notifications (replacing n8n Slack workflows — optional; see §8.2 item 8 for the human-vs-system notification split)

**CV skills:** SQS, DynamoDB, Step Functions — the highest-value CV items on the AWS learning path. SQS teaches distributed messaging semantics (visibility timeouts, DLQ, at-least-once delivery) that transfer to any message queue. DynamoDB teaches NoSQL data modeling (conditional writes, TTL, GSI design) that transfers to any key-value store. Step Functions teaches state machine orchestration that transfers to any workflow engine.

### Phase 4: Decision Gate (Week 8+)
**Decision: Does n8n stay or get migrated?**

| If n8n retrieval is working well | If n8n has become a burden |
|---|---|
| Keep n8n for agentic retrieval + HITL | Migrate retrieval agent to langchain4j in Spring |
| Keep n8n for Slack/email notifications | Migrate notifications to SNS + SES |
| Migrate pipeline infrastructure to AWS (already done in Phase 3) | Migrate HITL gates to Step Functions |
| Two runtimes serve different concerns — this is fine | Consolidate to Spring + AWS only |

The Cypher-as-tool endpoint and entity/relation endpoints work regardless of where the agent loop runs — the Spring tool surface is runtime-agnostic. This means the n8n agent is replaceable without rewriting the tools.

**Key principle:** The Spring tool endpoints (generate-cypher, entities, relations, RAG) are the stable API contract. The agent runtime (n8n or langchain4j) is an implementation detail. Build the tools once; the agent loop can migrate later without touching domain logic.

---

## 8. Risks and Open Questions

### 8.1 Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| n8n becomes permanent (AWS migration never happens) | **High** | Explicit Phase 4 decision gate at Week 8 with concrete criteria. n8n is a prototyping layer by default; production status requires explicit commitment. |
| n8n workflow testing gap | Medium | Spring endpoints tested with `@SpringBootTest` + Testcontainers. n8n agent loop debugged via visual step-through. If CI testing of the agent loop becomes required, migrate to langchain4j (Phase 4 fallback). |
| API contract drift between Spring and n8n | Medium | Versioned API endpoints; Spring owns the contract. The Cypher-as-tool endpoint is the critical seam — it must be stable. |
| n8n performance under load | Low | Polling model (not SSE push to n8n); n8n handles 220 executions/sec on single instance. Retrieval is read-only and low-throughput. |
| Operational complexity of two runtimes | Medium | Docker Compose for local dev; both self-hosted. In AWS: ECS Fargate for Spring, EC2 or ECS for n8n — two services, same VPC. |
| n8n workflow versioning | Low | n8n supports Git-based source control and environments. Workflows stored as JSON in the repo. |
| Agent vocabulary drift from Spring schema changes | Low | Under ingestion-vs-retrieval, the agent's domain vocabulary is retrieval's responsibility — not leakage. Mitigation: the `/api/query/tools` endpoint (see §8.2 item 7) keeps tool descriptions in sync with Spring's schema automatically. |
| Observability chasm (no distributed tracing across Spring + n8n) | Medium | **Explicit design goal** (not an emergent gap): X-Request-ID headers on all cross-boundary calls, structured logging with trace context on both sides, correlation ID propagation. Teaches cross-service tracing — a real-world skill. See §8.2. |

### 8.2 Open questions

1. **n8n deployment model:** Self-hosted Docker alongside LoreVault, or n8n Cloud? Self-hosted keeps latency low and data local; Cloud removes ops burden but adds network dependency. Start self-hosted; evaluate Cloud for production if ops burden grows.

2. **Authentication between n8n and Spring:** API key? Shared JWT? mTLS? Start with API key (simplest); upgrade if needed. The generate-cypher endpoint must be authenticated — it's a powerful Cypher execution surface.

3. **Event fan-out to n8n:** Should Spring push events to n8n via webhook (tight coupling), or should n8n poll Spring (loose coupling)? Polling is simpler and keeps Spring unaware of n8n, but adds latency. Start with polling; add webhook push only if latency matters for HITL notifications.

4. **n8n review UI vs. custom review UI:** n8n's built-in human-in-the-loop nodes provide a review interface. Is that sufficient, or does LoreVault need a custom review UI in the operator dashboard? Start with n8n's built-in; build custom only if needed.

5. **Cypher Template Catalog scope:** Does this become a fourth Maven module (`lorevault-cypher-catalog`) following the Relation Catalog pattern? Or is it a separate service? The Relation Catalog architecture (closed module, PostgreSQL, Flyway, pgvector, `definitionKey` identifiers) maps directly — same pattern, different domain.

6. **Observability design:** The doc frames cross-platform tracing as a learning opportunity. To make this real, the implementation must include: (a) `X-Request-ID` header propagation on all Spring → n8n webhooks and n8n → Spring API calls, (b) structured JSON logging with `trace_id` / `span_id` fields on both sides, (c) a concrete observability test: "when a job is stuck in `PENDING_REVIEW`, trace the failure across both runtimes using correlation IDs." This correlates directly to the existing MDC context propagation planning.

7. **Agent prompt engineering ownership:** The n8n agent's system prompt needs coarse domain vocabulary and tool descriptions. Who owns this? Recommendation: Spring provides a `/api/query/tools` endpoint returning tool metadata (names, descriptions, parameter schemas) — the agent prompt is generated from this, keeping prompt ownership in Spring. n8n only configures the LangChain Agent node to point at this endpoint.

8. **n8n vs. AWS notification migration:** If n8n handles Slack/email in Phase 2 and Phase 3 builds SNS, do we migrate? Recommendation: keep n8n for human-facing notifications (Slack, email) where its connector library has real value. Migrate system-to-system notifications (pipeline stage failures → retry logic) to SNS/SQS. Human channels are n8n's strongest fit; system channels should be AWS-native.

---

## 9. Related Documents

- [Orchestration / Domain Separation brainstorm](../architecture/2026-05-11T2027_orchestration-domain-separation.md) — the pipeline orchestration vs. domain logic separation that this n8n split extends
- [AWS Cloud-Native Learning Path](../aws-cloud-native/2026-05-11T2027_aws-cloud-native-learning-path.md) — the AWS deployment strategy this document complements and sequences against
- [Operator Dashboard and Admin API brainstorm](../devx/2026-04-16T0855_operator-dashboard-and-admin-api-brainstorm.md) — operator UI context relevant to HITL review integration
- [Async ingestion logging philosophy brainstorm](../architecture/2026-04-17T0855_async-ingestion-logging-philosophy-brainstorm.md) — MDC context propagation that correlates to the cross-platform tracing design goal (§8.2 item 6)
- [ADR-003](../../adr/003-prefer-direct-services.md) — prefer direct services over internal indirection (applies to n8n API surface design)
- [ADR-004](../../adr/004-event-driven-ingestion.md) — event-driven pipeline architecture (the boundary n8n stays outside of)
- [ADR-012](../../adr/012-dual-database-transaction-boundary.md) — dual-database transaction boundary (relevant to n8n's lack of transactional awareness)
- [Project Status](../../PROJECT-STATUS.md) — current architecture snapshot
