# ADR 001: Neo4j for Graph and Vectors

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault keeps Neo4j as the single store for both graph relationships and vector search for the current scale of the project.

## Why

- The current corpus size does not justify a second storage system
- Neo4j's vector capabilities are sufficient at current scale
- Splitting vectors to PostgreSQL/pgvector would add operational and consistency complexity

## Revisit Trigger

Re-evaluate when the corpus grows into the hundreds of thousands of embeddings or Neo4j vector tuning becomes a real bottleneck.
