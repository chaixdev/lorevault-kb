# LV-087-7 — Embedding failure signaling vs empty vectors [user story][refactor]

Context

- `EmbeddingModelAdapter` returns empty vectors on failure as a fallback; downstream services may treat these as valid, masking outages.

Problem

- Empty vectors are indistinguishable from intentional zeros and can degrade search quality silently.

Proposal

- Change contract to signal failure explicitly (e.g., null vectors, Result/Either type, or domain exception) and short-circuit persistence.
- Alternatively, annotate vectors with an error flag and skip indexing.
- Add metrics and warnings for failure rates.

Scope

- Update `EmbeddingPort` semantics to represent failures explicitly (backward-compatible plan may be needed).
- Update `ChunkEmbeddingService` to handle failure signals by skipping persistence and recording a status.
- Add tests covering partial batch failures.

Out of scope

- Switching embedding provider.

Technical notes

- If API change is too broad for v0.9.0, add an opt-in feature flag `lorevault.embedding.failOnError=true` to stop returning empty vectors.

Acceptance criteria

- [ ] Failure to retrieve embeddings does not produce zero-length vectors that are treated as valid.
- [ ] Metrics or logs clearly show failure counts and contexts.
- [ ] Tests cover batch with mixed success/failure.

Quality gates

- [ ] Build and tests green on JDK 21
- [ ] No new ArchUnit violations

Links

- Adapter: ../../../lorevault-api/src/main/java/com/lorevault/api/infrastructure/ai/EmbeddingModelAdapter.java
- Port/Service: ../../../lorevault-api/src/main/java/com/lorevault/api/application/port/EmbeddingPort.java, ../../../lorevault-api/src/main/java/com/lorevault/api/service/content/ChunkEmbeddingService.java
