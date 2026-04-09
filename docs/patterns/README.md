# LoreVault Pattern Library

This directory contains present-state mechanism documentation.

Patterns explain important areas of the current system that are implemented across multiple files or layers and are not easy to reconstruct from code at a glance.

## Patterns Are For

- documenting how a significant mechanism works today
- explaining multi-file or cross-layer behavior
- preserving high-level structure without forcing readers to reverse-engineer the codebase

## Patterns Are Not For

- architectural fork-in-the-road decisions that belong in `../adr/`
- speculative future designs that belong in `../brainstorm/`
- durable conceptual models that belong in `../concepts/`
- coding conventions that belong in `../rules/`

## Index

- [Ingestion pipeline](ingestion-pipeline.md)
- [Ingestion job observability](ingestion-job-observability.md)
- [Content persistence](content-persistence.md)
- [Spoiler-aware retrieval](spoiler-aware-retrieval.md)
- [LLM structured output](llm-structured-output.md)
- [LLM call observability](llm-call-observability.md)
- [Testing strategy](testing-strategy.md)
