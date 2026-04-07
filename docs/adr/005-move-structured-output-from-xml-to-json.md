# ADR 005: Move Structured Output From XML to JSON

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault should move structured LLM output from XML to JSON once provider support is confirmed in the chosen production path.

## Why

- The original XML choice was a workaround for older model reliability concerns
- Modern JSON-schema-based structured output makes JSON a better fit
- Spring AI 1.1.x improves the ergonomics of typed structured output

## Dependency

Confirm JSON-schema mode support in the chosen provider path before implementation.
