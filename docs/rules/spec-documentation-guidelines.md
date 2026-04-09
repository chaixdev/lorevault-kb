# Specification Documentation Guidelines

**Status:** Active

## Use Spec-Style Docs For

- detailed workflows
- state transitions
- integration boundaries
- validation and performance criteria
- implementation-ready behavior descriptions

## Do Not Put In Specs

- implementation code
- framework-specific configuration details
- architectural rationale that already belongs in ADRs or architecture viewpoints
- historical material that belongs in the archive

## Quality Bar

A good spec should be:

- clear enough to implement without guesswork
- testable
- explicit about edge cases and failure modes
- aligned with existing architecture and terminology

## Why This Rule Exists

LoreVault uses several layers of documentation. Specs should bridge architecture and code without collapsing into either one.

Primary source:
- `../development/current/SPEC_DOCUMENTATION_GUIDELINES.md`
