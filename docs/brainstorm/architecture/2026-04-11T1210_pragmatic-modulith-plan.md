# Pragmatic Modulith Plan

**Status:** Exploratory / historical proposal  
**Primary source:** `../archive/refactor/PRAGMATIC_MODULITH_PLAN.md`

## Why Preserve This

This plan captures a useful architectural transition idea: keep core graph entities close to persistence while retaining abstractions for volatile external infrastructure.

Parts of that direction were later implemented, but this document is preserved here as a proposal lineage rather than current source of truth.

## Core Proposal

- annotate core graph entities directly for persistence
- remove duplicated persistence model layers
- inject repositories directly for stable internal persistence paths
- retain abstraction only for genuinely swappable external systems such as LLMs, embeddings, or search backends

## Current Relationship To The Codebase

Several parts of this proposal influenced the current codebase, but the authoritative current-state explanation now belongs in:

- `../adr/003-prefer-direct-services-over-ports-and-mappers.md`
- `../rules/service-design-principles.md`

This file remains a preserved brainstorm/proposal artifact.
