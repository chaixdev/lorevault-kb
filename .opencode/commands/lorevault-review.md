---
description: >
  Deep code review of the current branch against main. Loads the
  lorevault-deep-reviewer and lorevault-coding-style skills, injects branch
  context, and executes a four-phase parallel review (context acquisition →
  five simultaneous analysis tracks → aggregation → structured report).
---

Load the `lorevault-deep-reviewer` skill and the `lorevault-coding-style` skill,
then conduct a thorough review following the operating procedure defined in those
skills exactly.

---

## Branch Context

Current branch:
!git branch --show-current

Merge base with main:
!git merge-base HEAD main

Commits on this branch:
!git log --oneline $(git merge-base HEAD main)..HEAD

Changed files (with line counts):
!git diff --stat $(git merge-base HEAD main)..HEAD

---

## Ticket / Task Context

$ARGUMENTS

---

## Review Instructions

Follow the four-phase operating procedure from the `lorevault-deep-reviewer` skill:

**Phase 1 — Context Acquisition**
Read all changed files in parallel (parallel tool calls, not sequential).
Read their associated test files in parallel.
Read relevant supporting context files (callers, event definitions, configs) in parallel.
Build the structural inventory before proceeding.

**Phase 2 — Parallel Track Analysis**
Launch all five analysis track sub-tasks simultaneously as background tasks.
Do not wait for one to finish before starting the next.

For each track sub-task:
- Pass the changed file list (paths), branch context, and ticket intent.
- Extract only the relevant sections from `docs/rules/coding-standards.md`
  (use the track-to-sections mapping in the `lorevault-coding-style` skill).
- Pass the relevant cross-reference file paths for that track.
- Instruct the sub-task to read all changed files independently.
- Instruct the sub-task to return findings in the structured format from the reviewer skill.

The five tracks are defined in full in the `lorevault-deep-reviewer` skill:
- Track A — Logic & Correctness
- Track B — Data & Persistence
- Track C — Async & Events
- Track D — Security & Observability
- Track E — Structure & Quality

**Phase 3 — Aggregation**
Collect all track findings. Identify cross-track hits (same file, nearby line) and
elevate their severity. Deduplicate. Run test gap analysis.

**Phase 4 — Synthesis**
Write the structured review document following the output format in the reviewer skill:
summary + verdict, findings with IDs, priority action table, test gaps, positive notes.

---

## Output

Write the completed review to:
`docs/reviews/<YYYY-MM-DD>_review_<branch-name>.md`

where `<branch-name>` is the current branch name with slashes replaced by dashes,
and `<YYYY-MM-DD>` is today's date.
