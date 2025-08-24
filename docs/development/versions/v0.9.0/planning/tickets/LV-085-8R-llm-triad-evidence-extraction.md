# LV-085-8R — LLM triad evidence extraction [feature]

Problem

- Need localized, auditable quotes supporting triad relations.

Proposal

- Create prompt and service that, given triad (E, A, B) with contexts, extracts short verbatim quotes and offsets indicating the evidence for each relation.

Scope

- Prompt: triad-evidence.txt with explicit instructions and format
- Service: TriadEvidenceService to call LLM (behind Feature toggle if needed in dev); parse and validate
- Tests: parser unit tests; optional integration stub

Acceptance criteria

- [ ] Parser extracts evidence fields and offsets reliably on samples
- [ ] Service returns structured evidence suitable for scoring

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
