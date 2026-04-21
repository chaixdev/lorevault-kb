# LoreVault Pattern Library

This directory contains present-state mechanism documentation.

Patterns explain important areas of the current system that are implemented across multiple files or layers and are not easy to reconstruct from code at a glance. any addition in this folder must take special care to guard against fragmented knowledge.

Patterns should be self-contained. They may link to other patterns, rules, or ADRs when that improves canonical understanding, but they should not depend on planning or brainstorm material.

## Patterns Are For

- documenting how a significant mechanism works today
- explaining multi-file or cross-layer behavior
- preserving high-level structure without forcing readers to reverse-engineer the codebase

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

- [Ingestion Pipeline](ingestion-pipeline.md) — async stage-based chapter processing from upload to embedding
- [Individual Resolution Ladder](individual-resolution-ladder.md) — scene-local evidence to chapter and book identity aggregation during ingestion
- [Location Resolution Ladder](location-resolution-ladder.md) — scene-local Location evidence to chapter and book location aggregation during ingestion
- [Ingestion Observability](ingestion-job-observability.md) — append-only StatusRecord chain and LLM call logging
- [Triad Analysis](triad-analysis.md) — three-scene sliding window for relationship extraction
- [Scene Detection Budgeted Segmentation](scene-detection-budgeted-segmentation.md) — chapter-segmentation context budget guard with deterministic split fallback and split-risk labels
- [Graph Shape Specification](graph-shape-specification.md) — canonical present-state graph shape and direction-reading semantics for structural, temporal, and evidence relations
- [Spoiler-Aware Retrieval](spoiler-aware-retrieval.md) — publication-coordinate filtering on vector search
- [RAG Retrieval Chain](rag-retrieval-chain.md) — retrieve-then-generate pipeline with citations
- [CQRS Command-Query Separation](cqrs-command-query-separation.md) — structural split of write and read API paths
- [LLM Structured Output](llm-structured-output.md) — typed LLM response binding and reduced ad hoc parsing
- [Testing Strategy](testing-strategy.md) — present-state testing shape and profile usage model
