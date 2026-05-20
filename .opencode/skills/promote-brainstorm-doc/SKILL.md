---
name: promote-brainstorm-doc
description: Produces canonical LoreVault docs updates from a brainstorm proposal plus implementation notes — load when asked to canonicalize a proposal, run an ADR/pattern pass, or extract current docs from implemented work
license: MIT
compatibility: opencode
metadata:
  audience: humans-and-agents
  workflow: documentation-promotion
---

## What I Do

This skill codifies LoreVault's repeatable documentation-promotion pass: start from a brainstorm proposal, append implementation notes, then extract only stabilized knowledge into the correct canonical docs.
Load it when the task is to refresh current docs after implementation, derive ADRs or pattern docs from shipped work, or reconcile a proposal with landed code.
It produces edits in [docs](../../../docs/) such as [PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md), roadmap docs, ADRs, pattern docs, and proposal implementation notes.
It does not treat brainstorm material as truth or promote speculative future design into canonical docs.

## Inputs Required

- Repository root available
- Read access to these sources of truth:
  - `AGENTS.md`
  - [docs/README.md](../../../docs/README.md)
  - [docs/brainstorm/README.md](../../../docs/brainstorm/README.md)
  - the active brainstorm proposal file
  - the proposal's implementation notes section (append one first if missing)
  - the implemented code and tests relevant to the proposal
  - current canonical docs likely affected ([PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md), roadmap, [adr/](../../../docs/adr/), [patterns/](../../../docs/patterns/), [concepts/](../../../docs/concepts/))

## Methodology

1. Start from repo taxonomy, not personal preference.
   - Read [docs/README.md](../../../docs/README.md) and [docs/brainstorm/README.md](../../../docs/brainstorm/README.md) first.
   - Treat folder meaning as a hard routing constraint.
   - The governing principle is truthful docs over completeness.
   - **Naming convention:** All new files in `docs/brainstorm/` and `docs/planning/` must use an ISO datetime prefix (`YYYY-MM-DDTHHMM_topic-slug.md`), not a month/year suffix. Generate the timestamp with `date +%Y-%m-%dT%H%M`.

2. Establish the documentation basis before promoting anything.
   - Identify the active brainstorm proposal.
   - Check whether it already has an implementation-notes section.
   - If implementation has advanced since the last proposal update, append implementation notes before extracting canonical docs.
   - Use implementation notes to record deviations, learned constraints, and details that may later deserve promotion.

3. Reconcile proposal, code, and tests.
   - The proposal explains intent, not guaranteed truth.
   - Verify each candidate insight against implemented code, tests, and accepted runtime behavior.
   - When proposal and code diverge, record the divergence in implementation notes and promote only the implemented truth.

4. Extract documentation candidates, then classify them by purpose.
   - Ask of each candidate fact: is this a current mechanism, an accepted decision, a durable abstraction, current continuity, or still just exploration?
   - Route with this table:
     - **ADR** when a real architectural fork was chosen and the durable value is mostly the why.
     - **Pattern** when the mechanism is implemented now and spans multiple files/layers.
     - **Concept** when the abstraction is durable but not honest as present-state implementation truth.
     - **development/current** when the material is current continuity/spec detail but not yet a smaller durable canonical artifact.
     - **brainstorm** when it is still future-facing, optional, unresolved, or speculative.

5. Refresh continuity docs after major implemented slices.
   - Update [PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md) to reflect what has actually shipped and what is next.
   - Update [refactor-roadmap.md](../../../docs/planning/README.md) when the near-term sequence changes.
   - Keep these docs iterative and truthful; do not rewrite them into a fake long-term certainty.

6. Promote mechanism docs conservatively.
   - A pattern doc should explain how the area works today, not replay the whole brainstorm.
   - Distill the minimum current behaviors, trigger points, graph shapes, lifecycle rules, and file boundaries a future reader would struggle to reconstruct from code alone.
   - Prefer one good mechanism doc over many thin overlapping docs.

7. Promote ADRs only when the decision is real.
   - Do not create an ADR just because a feature exists.
   - Create one when multiple viable paths existed and the team actually committed to one.
   - Capture the chosen path, the rejected alternatives at a high level, and why the chosen path won.

8. Preserve proposal-first continuity.
   - The brainstorm file remains the record of exploratory thinking and evolution.
   - Canonical docs should not erase the proposal; they should extract stabilized truth from it.
   - The correct sequence is:
     1. update proposal implementation notes
     2. extract ADR/pattern/current docs
     3. refresh status/roadmap links if needed

9. Avoid canonical-doc inflation.
   - Do not promote every implementation detail.
   - Promote only material that is useful to future readers, intentionally curated, and in the folder whose meaning matches the document's purpose.
   - Leave ticket-by-ticket exploration, raw analysis, and temporary migration notes out of canonical docs.

10. Make routing decisions explicit when ambiguous.
    - If a topic feels like both ADR and pattern, split it by question:
      - **Why this path was chosen** → ADR
      - **How the chosen path works now** → Pattern
    - If no durable decision exists, do not force an ADR.

## Output Format

When running this skill, produce work in this order:

### 1. Proposal update
- Append or update an `Implementation Notes Since This Proposal` section in the active brainstorm proposal.
- Required contents:
  - shipped or implemented status
  - what stayed aligned with the proposal
  - important deviations from the proposal
  - implementation details worth preserving later
  - what remains intentionally unimplemented

### 2. Promotion map
- Before editing canonical docs, build a short routing map in working notes or reasoning with this exact shape:

```text
Candidate insight -> Target home -> Why
```

- Example homes:
  - [docs/adr/...](../../../docs/adr/)
  - [docs/patterns/...](../../../docs/patterns/)
  - [docs/concepts/...](../../../docs/concepts/)
  - [docs/planning/...](../../../docs/planning/)
  - [PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md)
  - [Planning README](../../../docs/planning/README.md)

### 3. Canonical docs edits
- Create or update only the docs justified by implemented truth.
- Preferred outputs for this repo are:
  - [PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md)
  - [Planning README](../../../docs/planning/README.md)
  - one or more [docs/adr/*.md](../../../docs/adr/) files when decision-worthy
  - one or more [docs/patterns/*.md](../../../docs/patterns/) files when mechanism-worthy

### 4. Final documentation summary
- Report exactly:
  - proposal file updated
  - canonical docs created/updated
  - ADRs added or deliberately not added
  - patterns added or deliberately not added
  - any unresolved ambiguities left in brainstorm rather than promoted

## Edge Cases

### Edge case: Proposal diverges from code
- The code wins for canonical docs.
- Append the divergence to the proposal's implementation notes.
- Do not silently rewrite canonical docs to match obsolete proposal intent.

### Edge case: Pattern vs ADR ambiguity
- Split the material by question.
- If the enduring value is rationale, create/update an ADR.
- If the enduring value is present mechanism, create/update a pattern doc.
- If both matter, create both and keep each focused.

### Edge case: No ADR-worthy decision exists
- Do not manufacture an ADR for completeness.
- Record the implemented mechanism in a pattern doc or continuity doc if that is the honest fit.

### Edge case: Speculative future work is tempting to promote
- Keep speculative work in `brainstorm/` or roadmap continuity only.
- Canonical docs must not present future wishes as current truth.

### Edge case: Status/roadmap and canonical mechanism docs drift apart
- Treat [PROJECT-STATUS.md](../../../docs/PROJECT-STATUS.md) as the current progress snapshot.
- Align roadmap wording with implemented reality and immediate next slices.
- Do not leave a new pattern or ADR undocumented in status if it materially changes current state.

## Composability

- Upstream: `AGENTS.md` and the docs READMEs provide the repo's always-on documentation taxonomy and source-of-truth rules.
- Downstream: this skill can precede implementation follow-up, release notes work, roadmap updates, or commit preparation for docs-only passes.
- Typical chain:
  1. load `promote-brainstorm-doc`
  2. update proposal implementation notes
  3. classify insights into ADR/pattern/concept/current-doc buckets
  4. edit canonical docs
  5. summarize what was promoted and what intentionally stayed in brainstorm
