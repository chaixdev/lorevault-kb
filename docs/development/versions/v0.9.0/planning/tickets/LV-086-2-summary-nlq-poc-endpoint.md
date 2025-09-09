# LV-086-2 — Summary NLQ POC endpoint [research]

Status: NOT IMPLEMENTED

Context

- Provide a proof-of-concept endpoint to summarize "what happened so far" up to a chapter boundary.
- See planning: ../v0.9.0-scene-to-event-entity-plan.md
- See research: ../../research/graphrag-endpoint-proposal.md

Problem

- We need to validate GraphRAG-aligned summarization over ordered Events before formalizing APIs.

Proposal

- Add a POC endpoint that retrieves ordered Events, summarizes them, and returns a concise narrative; treat as research (non-stable).

Scope

- Controller (POC), service using existing summarization capability; minimal DTOs.

Out of scope

- Production-grade prompt design and evaluation

Technical notes

- Include citations/evidence when requested; log token usage and latency for evaluation.

Acceptance criteria

- [ ] Endpoint returns a coherent summary consistent with Event order on fixture data
- [ ] Optional evidence sections included when requested

Quality gates

- [ ] Smoke tests; opt-out of strict coverage if needed (documented)

Links

- Planning: ../v0.9.0-scene-to-event-entity-plan.md#086—timeline-query-+-graphrag-nlq-research
- Research: ../../research/graphrag-endpoint-proposal.md
