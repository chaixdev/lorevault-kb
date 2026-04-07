# ADR 002: Keep and Upgrade Spring AI

**Status:** Accepted  
**Date:** April 2026

## Decision

LoreVault keeps Spring AI and upgrades from `1.0.0` to `1.1.4`.

## Why

- Spring AI is already load-bearing in chat and prompt flows
- Replacing it would create infrastructure code without product value
- Version 1.1.4 materially improves structured output, token accounting, and vector-store integration

## Notes

Detailed migration analysis lives in `docs/development/research/spring-ai-keep-vs-drop-analysis.md`.
