# LoreVault Pattern Library

This directory contains present-state mechanism documentation.

Patterns explain important areas of the current system that are implemented across multiple files or layers and are not easy to reconstruct from code at a glance. Any addition in this folder must take special care to guard against fragmented knowledge.

Patterns should be self-contained. They may link to other patterns, rules, or ADRs when that improves canonical understanding, but they should not depend on planning or brainstorm material.

## Patterns Are For

- documenting how a significant mechanism works today
- explaining multi-file or cross-layer behavior
- preserving high-level structure without forcing readers to reverse-engineer the codebase
- present-state topology: module dependencies, package coupling, and structural constraints

Some pattern docs are lightweight overviews.

Others need to be more implementation-ready and procedural.

When a pattern doc needs that deeper level of workflow/state/integration detail, follow the quality bar in `../rules/spec-documentation-guidelines.md`.

## Patterns Are Not For

- architectural fork-in-the-road decisions that belong in `../adr/`
- speculative future designs that belong in exploratory docs rather than the pattern library
- durable conceptual models that belong in `../concepts/`
- coding conventions that belong in `../rules/`

If a pattern currently depends on proposal history or future-work context, extract the necessary present-state truth into the pattern itself instead of linking outward.

## Index

### Cross-Cutting (No Semantic Bucket)

- [Testing Strategy](testing-strategy.md) — present-state testing shape and profile usage model
- [Codebase Topology](codebase-topology.md) — present-state module coupling, shared model constraints, and contributor boundaries

### AI Infrastructure

- [LLM Structured Output](ai/llm-structured-output.md) — typed LLM response binding and reduced ad hoc parsing

### Ingestion

- [Ingestion Pipeline](ingestion/ingestion-pipeline.md) — async stage-based chapter processing from upload to embedding
- [Entity Resolution Ladder](ingestion/entity-resolution-ladder.md) — scene-local mention evidence to chapter and book entity aggregation during ingestion (Individual and Location lanes)
- [Ingestion Observability](ingestion/ingestion-job-observability.md) — append-only StatusRecord chain and LLM call logging
- [Triad Analysis](ingestion/triad-analysis.md) — three-scene sliding window for relationship extraction
- [Scene Detection Budgeted Segmentation](ingestion/scene-detection-budgeted-segmentation.md) — chapter-segmentation context budget guard with deterministic split fallback and split-risk labels
- [Text Chunking Specification](ingestion/text-chunking-specification.md) — narrative-aware chunk sizing and sliding-window subdivision for embedding and retrieval

### Content

- [Graph Shape Specification](content/graph-shape-specification.md) — canonical present-state graph shape and direction-reading semantics for structural, temporal, and evidence relations

### Search

- [Spoiler-Aware Retrieval](search/spoiler-aware-retrieval.md) — publication-coordinate filtering on vector search
- [RAG Retrieval Chain](search/rag-retrieval-chain.md) — retrieve-then-generate pipeline with citations

### Web

- [CQRS Command-Query Separation](web/cqrs-command-query-separation.md) — structural split of write and read API paths
