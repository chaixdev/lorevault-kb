# LoreVault Embeddings & NLQ Roadmap

Legend: [x] done, [ ] pending, [~] in progress

## Phase 0 – Inventory & Planning (COMPLETED)
- [x] Audit existing ChunkEmbeddingService (stub only)
- [x] Identify missing embedding fields on ChunkNode
- [x] Decide embedding model: gemini-embedding-001 (initially 1536 dims → updated to 3072)
- [x] Confirm chunk text reconstruction (chapter.rawText via offsets)
- [x] Define idempotency hash = SHA256(modelId + ':' + contentHash)
- [x] Plan health check for embedding model (dummy embed of "ping")
- [x] Choose initial retrieval strategy: linear scan cosine
- [x] Define abstractions (EmbeddingService, ChunkVectorStore) (refined into EmbeddingPort + ChunkEmbeddingService)
- [x] Determine QA prompt needs and endpoint

## Phase 1 – Data Model & Config
- [x] Extend ChunkNode with embedding fields:
  - embedding (double[])
  - embeddingHash
  - embeddedAt
- [x] Add application properties for embedding:
  - spring.ai.openai.embedding.options.model=gemini-embedding-001
  - lorevault.embedding.dim=3072 (updated from 1536 after live model response)
  - lorevault.embedding.batch-size=32 (tunable)
- [~] Implement EmbeddingService (generate single embedding & batch helper) (implemented via EmbeddingPort + EmbeddingModelAdapter; rename/consolidation pending)
- [x] Update ChunkEmbeddingService to filter unembedded / stale chunks
- [x] Persist embeddings & metadata
- [x] Replace placeholder text (previously contentHash) with real chunk substring reconstruction
- [~] Add fallback logging + metrics counters (logging done, metrics pending)

## Phase 2 – Health & Observability
- [x] Extend LlmHealthCheckService to also probe embedding model (separate EmbeddingHealthCheckService implemented)
- [x] Expose embedding model health in /api/health (custom controller integration)
- [ ] Add metrics: embeddings.generated.count, embeddings.skipped.count
- [x] Add DEBUG logs for batch timings, TRACE for first N vector values (batch timing/debug implemented; vector preview present)

## Phase 3 – Retrieval Layer
- [x] Implement VectorMath util (cosineSimilarity, dot, norm)
- [ ] Implement ChunkVectorStoreLinear (Neo4j load + in-memory scoring)
- [ ] Add topK retrieval with chapter filter + threshold
- [ ] Add logging of scores & cutoffs
- [ ] (Optional) Guard: if <2 chunks, short-circuit retrieval

## Phase 4 – QA Orchestration
- [ ] Prompt template qa-answer-v1.txt (system instructions + citation format)
- [ ] QASystemService orchestrating: embed question -> retrieve -> build context -> LLM answer
- [ ] REST Controller POST /api/qa (fields: question, chapterId?, k?, threshold?)
- [ ] Response DTO: answer, citations[], usedChunkCount, retrievalLatencyMs, totalLatencyMs
- [ ] Add truncation strategy (char/token budget ~8000 chars)

## Phase 5 – Testing
- [x] Unit: VectorMath correctness
- [ ] Unit: EmbeddingService (mock client) returns expected size
- [ ] Unit: Retrieval ordering with synthetic vectors
- [ ] Integration: ingest chapter -> generate embeddings -> QA query returns cited chunks
- [ ] Edge cases: empty DB, no embeddings, long question

## Phase 6 – Hardening (Later)
- [ ] Async background embedding job after ingestion
- [ ] Cache recent question embeddings
- [ ] Optional vector index (Neo4j native or external store)
- [ ] Cross-encoder re-ranking stage
- [ ] Hallucination guard (require min avg similarity)

## Phase 7 – Cleanup & Migration
- [ ] Backfill embeddings for pre-existing chapters
- [ ] Add Neo4j constraints/indexes (Chunk.contentHash, Job.id, StatusRecord.id)
- [ ] Document operational runbook (reset, re-embed, health)

## Current Status Snapshot
Phase 1 implemented except metrics & adapter retry/backoff; real text reconstruction now active. Duplicate RestTemplate bean removed. Pending: metrics, retry/backoff robustness, retrieval layer.

## Next Immediate Actions
1. Finalize embedding metrics validation (ensure counters appear in /actuator/metrics)
2. Implement ChunkVectorStoreLinear
3. Retrieval topK endpoint scaffolding (controller + service method stub)
4. Add EmbeddingService unit test
5. Implement retrieval ordering test with synthetic vectors
