# LLM Structured Output Pattern

**Status:** Transitional

LoreVault currently uses XML-based structured output for scene-analysis flows, but the intended durable pattern is typed JSON structured output.

Current direction:

- keep prompts explicit and constrained
- prefer typed output models over ad hoc parsing
- remove XML-specific cleanup once JSON schema mode is adopted

Primary references:
- `docs/development/research/spring-ai-keep-vs-drop-analysis.md`
- `docs/adr/005-move-structured-output-from-xml-to-json.md`
