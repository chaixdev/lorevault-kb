# ADR 004: Keep the Event-Driven Ingestion Pipeline

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault keeps the event-driven ingestion pipeline as part of the system's core shape.

## Why

- The ingestion flow benefits from stage separation and asynchronous retries
- The event-driven refactor is already implemented and merged
- The problem is not the event pipeline itself, but the remaining unnecessary indirection around it

## Implication

Future simplification should remove trivial hops and ceremony without collapsing useful stage boundaries.
