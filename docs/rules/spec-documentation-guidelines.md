# Specification Documentation Guidelines

**Status:** Active

This rule defines the quality bar for detailed, implementation-ready mechanism documentation.

In LoreVault, that usually means deeper [Pattern](../patterns/) documents rather than a separate "spec" documentation layer.

## Use Spec-Style Docs For

- detailed workflows
- state transitions
- integration boundaries
- validation and performance criteria
- implementation-ready behavior descriptions

## Purpose

Spec-style documentation should bridge the gap between high-level architecture docs and implementation.

In practice, these guidelines mostly apply to pattern docs that need more procedural or implementation-ready detail than a lightweight overview.

They are for detailed behavior and workflow description, not raw code and not broad architectural rationale.

## Do Not Put In Specs

- implementation code
- framework-specific configuration details
- architectural rationale that already belongs in ADRs or architecture viewpoints
- historical material that belongs in the archive
- guard against knowledge fragmentation

## Relationship To Pattern Docs

Use these guidelines when a pattern doc needs to explain:

- a multi-step workflow
- state transitions
- integration boundaries
- validation and failure behavior
- implementation-ready behavior across multiple files or layers

Not every pattern doc needs this level of detail.

But when a pattern doc becomes the main present-state reference for an important mechanism, this document describes the expected quality bar.

## Typical Content

Good specs often include:

- process overviews
- detailed workflows
- state transitions
- interface and validation expectations
- error handling paths
- performance constraints
- integration points

## Quality Bar

A good spec should be:

- clear enough to implement without guesswork
- testable
- explicit about edge cases and failure modes
- aligned with existing architecture and terminology
- own its knowledge area

## Why This Rule Exists

LoreVault uses several layers of documentation. Detailed mechanism docs should bridge architecture and code without collapsing into either one.
