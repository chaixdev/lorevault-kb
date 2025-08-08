# Concurrency Viewpoint

**Stakeholders:** Performance engineers, system architects, technical leads  
**Concerns:** Concurrency patterns, parallel processing strategy, resource coordination

## Overview

LoreVault uses asynchronous, queue-based processing to decouple HTTP request handling from long-running AI tasks. Background workers perform chapter processing and update job status records.

## Core Concurrency Strategy

### Asynchronous Processing Model

- Immediate request acknowledgment with job tracking
- Background processing isolated from request thread
- Client-driven status polling
- Resource protection through bounded processing queues

### Queue-Based Asynchronous Processing

- FIFO job ordering with bounded capacity
- Worker pool consumes jobs and updates status
- Durable state persisted in `ingestion_jobs` and `status_records`

### Current Implementation Notes

- No explicit optimistic/pessimistic locking is implemented in entities yet.
- Status transitions are persisted atomically within transactional methods.
- External AI calls are retried with exponential backoff; failures transition jobs to FAILED.
- Database access uses connection pooling; operations are performed within Spring-managed transactions.

## Processing Pipeline Concurrency

- Chapters are processed independently; multiple jobs may run in parallel subject to worker count.
- AI calls are executed sequentially within a job; parallelization across jobs is supported.
- Chunk generation and scene detection run within the same job context; writes are batched where practical.

## Resource Management

- External LLM access coordinated via Spring AI `ChatClient` with retry settings.
- Database connections managed by the application pool; writes are transactional.

## Future Enhancements

- Introduce optimistic locking on core entities to prevent concurrent update anomalies.
- Add explicit job queue persistence and back-pressure controls.
- Circuit breaker and rate limiting for external AI providers.
- Configurable worker pool sizes with graceful shutdown and draining.
