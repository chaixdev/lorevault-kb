# LLM Structured Output

LoreVault prefers typed structured output over ad hoc parsing or schema-light free text when working with LLM responses.

## Why this pattern exists

Structured output reduces downstream cleanup, makes retries more predictable, and gives the codebase a clearer contract between prompt design and the code that consumes model responses.

This pattern favors:

- explicit prompts
- typed response models
- direct binding into typed records where practical
- fewer bespoke parser layers

## Present-state shape

The current direction is to keep prompts explicit and constrained while letting the application bind structured responses directly into typed models where practical.

The important present-state behavior is:

- LoreVault prefers typed output models over schema-light free text
- prompt structure and output contracts should stay aligned with retry and observability boundaries
- provider usage should stay compatible with the current Spring AI direction rather than reintroducing custom parsing layers unnecessarily

## Boundaries

This pattern covers how LoreVault structures LLM responses.

It does **not** cover:

- the full orchestration story around triad analysis or scene detection
- status emission and ingestion observability behavior
- provider-selection policy beyond what is needed to preserve typed structured output

## Related Documentation

- `../ingestion/triad-analysis.md`
- `../../adr/002-keep-and-upgrade-spring-ai.md`
- `../../adr/003-prefer-direct-services-over-ports-and-mappers.md`
