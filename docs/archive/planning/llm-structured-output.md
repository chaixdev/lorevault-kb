# LLM Structured Output

**Status:** deferred

LoreVault prefers typed structured output over ad hoc parsing or schema-light free text.

## Current Direction

- keep prompts explicit and constrained
- prefer typed output models over ad hoc parsing
- let the application bind structured responses directly into typed records where practical
- keep prompts and structured output contracts aligned with observability and retry boundaries

## Why This Pattern Exists

Structured output reduces downstream cleanup, makes retries more predictable, and gives the codebase a clearer contract between prompt design and persistence logic.

## Current Reality

LoreVault has moved away from the older XML-heavy direction in its active architecture work.

The durable pattern is:

- explicit prompts
- typed response binding
- fewer bespoke parser layers
- provider usage that stays compatible with the current Spring AI direction

## Pattern Boundaries

This pattern is about how LoreVault structures LLM responses.

It is not itself the full orchestration story; triad orchestration, status emission, and persistence observability are related but separate mechanisms.
