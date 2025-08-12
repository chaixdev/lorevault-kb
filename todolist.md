# LoreVault Embeddings & NLQ Roadmap

Legend: [x] done, [ ] pending, [~] in progress

## Phase 0 – Inventory & Planning (COMPLETED)
- [x] Audit existing ChunkEmbeddingService (stub only)
- [x] Identify missing embedding fields on ChunkNode
- [x] Decide embedding model: gemini-embedding-001 (1536 dims)
- [x] Confirm chunk text reconstruction (chapter.rawText via offsets)
- [x] Define idempotency hash = SHA256(modelId + ':' + contentHash)
- [x] Plan health check for embedding model (dummy embed of "ping")
- [x] Choose initial retrieval strategy: linear scan cosine
- [x] Define abstractions (EmbeddingService, ChunkVectorStore)
- [x] Determine QA prompt needs and endpoint

## Phase 1 – Data Model & Config
- [~] Extend ChunkNode with embedding fields:
  - embedding (double[])
  - embeddingHash
  - embeddedAt
- [ ] Add application properties for embedding:
  - spring.ai.openai.embedding.options.model=gemini-embedding-001
  - lorevault.embedding.dim=1536
  - lorevault.embedding.batch-size=32 (tunable)
- [ ] Implement EmbeddingService (generate single embedding & batch helper)
- [ ] Update ChunkEmbeddingService to filter unembedded / stale chunks
- [ ] Persist embeddings & metadata
- [ ] Add fallback logging + metrics counters

## Phase 2 – Health & Observability
- [ ] Extend LlmHealthCheckService to also probe embedding model
- [ ] Expose embedding model health in /actuator/health (custom contributor)
- [ ] Add metrics: embeddings.generated.count, embeddings.skipped.count
- [ ] Add DEBUG logs for batch timings, TRACE for first N vector values

## Phase 3 – Retrieval Layer
- [ ] Implement VectorMath util (cosineSimilarity, dot, norm)
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
- [ ] Unit: VectorMath correctness
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
Phase 0 complete. Beginning Phase 1 (ChunkNode fields added; properties + service skeleton pending).

## Next Immediate Actions
1. Bind embedding config properties class (dim, batch size)
2. Introduce EmbeddingService skeleton
3. Update ChunkEmbeddingService to collect target chunks (generation still stub)
4. Implement batch embedding call & persistence
