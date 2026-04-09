# Documentation Migration Tracker

**Status:** Active  
**Started:** 2026-04-09  
**Purpose:** Persistent working tracker for the documentation taxonomy migration. This file is intentionally outside `docs/` so the migration can keep continuity across compaction without becoming canonical documentation.

## Target Taxonomy

- `docs/adr/` — accepted architectural decisions documenting past forks in the road
- `docs/patterns/` — present-state mechanism docs for complex multi-file areas
- `docs/concepts/` — durable conceptual models worth preserving even when implementation diverges
- `docs/brainstorm/` — future-facing proposals, sketches, and exploratory specs
- `docs/rules/` — coding and documentation guidance, hygiene rules, and conventions
- `docs/archive/` — historical and superseded material only

## Principles

1. Update README and index guidance first so the tree explains itself before deeper moves.
2. Do not promote speculative material into `patterns/` unless it matches the current codebase.
3. Preserve foundational conceptual work even if it is only partially implemented.
4. Prefer synthesis into smaller canonical docs over wholesale promotion of large ticket/planning artifacts.
5. Keep `docs/archive/` for archaeology, not as a shadow source of truth.

## Current Work Queue

### Phase 1 — Grounding

- [x] Update `docs/README.md`
- [x] Update `docs/adr/README.md`
- [x] Update `docs/patterns/README.md`
- [x] Create `docs/concepts/README.md`
- [x] Create `docs/brainstorm/README.md`
- [x] Create `docs/rules/README.md`

### Phase 2 — Migration Inventory

- [x] Identify strongest promotion candidates from `docs/archive/` and `docs/development/`
- [x] Separate move-vs-synthesize decisions
- [x] Identify stale README/index references after taxonomy changes

### Phase 3 — Content Promotion

- [x] Promote foundational conceptual material into `docs/concepts/`
- [x] Promote current-state mechanism docs into `docs/patterns/`
- [x] Move exploratory future-facing material into `docs/brainstorm/`
- [x] Move durable guidance into `docs/rules/`

### Phase 4 — Cleanup

- [x] Repair links and navigation
- [x] Remove misleading "migration complete" messaging
- [x] Summarize remaining archive-only material

## Initial Candidate Notes

- `event DAG` and `entity-claims` should land in `docs/concepts/`, not `docs/patterns/`.
- Process specs that describe implemented behavior may remain under current docs temporarily, but should eventually either become `patterns/` or a smaller dedicated spec/data-model area.
- Existing ADRs are directionally correct, but the ADR README should clarify that ADRs record accepted decisions rather than future guidance.

## Progress Log

- 2026-04-09: Updated root taxonomy READMEs (`docs/README.md`, `docs/adr/README.md`, `docs/patterns/README.md`).
- 2026-04-09: Created `docs/concepts/`, `docs/brainstorm/`, and `docs/rules/` with initial guidance READMEs.
- 2026-04-09: Added first concept promotions: `docs/concepts/entity-claim-model.md` and `docs/concepts/event-dag.md`.
- 2026-04-09: Updated top-level navigation docs to acknowledge the new taxonomy and removed misleading "migration complete" messaging from `docs/PROJECT-STATUS.md`.
- 2026-04-09: Expanded canonical patterns with richer current-state docs for ingestion, retrieval, persistence, testing, and LLM observability.
- 2026-04-09: Added first durable rules docs for service design, documentation guidance, testing workflow, and CI profiles.
- 2026-04-09: Preserved future-facing architectural proposals under `docs/brainstorm/`.

## Remaining Archive-Only Material

The following still primarily belong in `docs/archive/` until a later migration wave chooses to synthesize them:

- detailed version-scoped ticket trees
- historical refactor execution logs
- milestone-specific planning/checklist documents
- older research artifacts that have not yet been distilled into concepts or patterns

The next likely promotion candidates, if more migration work is desired later, are:

- selective summaries from the content hierarchy integration docs
- selective summaries from archived testing research
- additional future-facing proposal summaries beyond the two brainstorm docs already created

## Background Sessions

- `ses_2905d55efffePybGI0B3Kopg7n` — promotion candidate mapping
- `ses_2905d55cfffeL12bq5Q1VP3ZPJ` — README/reference impact scan
