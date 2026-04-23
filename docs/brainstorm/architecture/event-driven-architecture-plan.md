# Event-Driven Architecture Plan

**Status:** Exploratory / historical proposal  
**Primary source:** `../archive/refactor/event-driven-architecture-plan.md`

## Why Preserve This

This plan captures a richer future-facing vision for splitting ingestion into independent pipelines with distinct failure and recovery behavior.

LoreVault already uses event-driven ingestion ideas today, but this proposal goes beyond the currently implemented shape.

## Core Proposal

- separate storage, semantic analysis, and entity-profile work into independent pipelines
- publish explicit events between major stages
- allow partial progress and independent retries
- track pipeline progress with explicit event/status records

## Current Relationship To The Codebase

The current codebase already reflects some of this direction, especially around event-driven ingestion and staged processing.

But this document should not be read as a description of the full implemented system. It remains useful as a preserved future-facing design space.

For current truth, prefer:

- `../adr/004-keep-the-event-driven-ingestion-pipeline.md`
- `../patterns/ingestion/ingestion-pipeline.md`
- `../concepts/event-dag.md`
