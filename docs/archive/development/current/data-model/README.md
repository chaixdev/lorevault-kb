# Data Model

This directory contains the current canonical data-model docs for LoreVault.

## Start Here

- **[Neo4j Content Data Model](neo4j-content-data-model.md)** — graph schema, node structure, relationships, and constraints
- **[Content Hierarchy Integration](content-hierarchy-integration.md)** — publication-coordinate materialization and hierarchy coordination
- **[Ingestion Job and Status](ingestion-job-and-status.md)** — ingestion-job and status-record lifecycle model
- **[LLM Call Records](llm-call-records.md)** — observability and traceability model for LLM calls

## Schemas

- **[Claims Schema](schemas/claims.schema.json)** — structured claims schema placeholder for future extraction work
- **[Claims Examples](schemas/claims.examples.json)** — example claim payloads

## Current Model Shape

LoreVault centers its content graph on a publication hierarchy:

- **Universe** → **Series** → **Book** → **Chapter** → **Scene** → **Chunk**

This hierarchy supports:

- spoiler-aware retrieval through publication coordinates
- chunk-level embeddings for semantic search
- traceable ingestion and LLM-call observability

## Boundaries

- Keep data structures, schemas, and persistence rules here
- Put workflow/process behavior under `../processes/`
- Put architecture-wide viewpoints under `../../../architecture/`
- Put API-facing contracts under `../../../api/specifications/`

## Maintenance Guidance

- Update `neo4j-content-data-model.md` when entity shape or constraints change
- Update `content-hierarchy-integration.md` when coordinate materialization or hierarchy rules change
- Add formal schemas under `schemas/` with clear names and current examples
- Keep this directory aligned with implemented behavior, not speculative future designs

## References

- [Neo4j Graph Database](https://neo4j.com/)
- [Spring Data Neo4j](https://spring.io/projects/spring-data-neo4j)
