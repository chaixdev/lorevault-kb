# ADR 003: Prefer Direct Services Over Internal Indirection

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault should remove internal indirection layers where they do not represent a real boundary.

## Why

- The current codebase still contains abstraction bloat
- Several ports are load-bearing only as indirection, not as meaningful seams
- Direct repository and service usage better matches the desired linear, mechanically sympathetic style

## Implication

- Repositories should return domain types directly where possible
- Services should inject repositories directly where that keeps the code honest
- Keep interfaces only at true external or infrastructural boundaries
