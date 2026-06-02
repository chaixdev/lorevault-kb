# Development Workflow

This document describes LoreVault's default development workflow.

It is intentionally lightweight.

The goal is to preserve a clear loop for exploring, implementing, validating, and documenting work without forcing a heavyweight ticket-driven SDLC on every task.

## Core Loop

The default loop is:

1. identify a problem, opportunity, or missing capability
2. discuss cause, constraints, and solution space
3. write an exploratory solution design proposal document
4. implement and iterate with verification and UAT
5. append implementation notes and deviations to proposal document where useful
6. promote accepted truth into canonical top-level docs
7. keep not-yet-done work in planning documentation

## Folder Roles In This Workflow

### Exploratory proposal docs

Use exploratory proposal docs for:

- exploratory proposals
- solution-space analysis
- design alternatives
- unresolved questions
- implementation notes attached to an evolving proposal

Exploratory proposal docs are the right place to explore *how* something might be solved.

They are not canonical truth.

### Top-level canonical docs

Use top-level docs for accepted and implemented truth:

- `../adr/` for accepted architectural decisions
- `../patterns/` for present-state implementation mechanisms
- `../rules/` for durable contributor guidance and conventions
- `../concepts/` for durable abstractions that outlive specific implementations

Once something is implemented and accepted, the important as-is truth should be promoted into the correct canonical home, and the planning item should be removed.

### Planning docs

Use planning docs for work worth tracking that is not yet implemented.

Planning items should read like lightweight tickets:

- enough product context to explain why the work matters
- enough technical context to make the work resumable later
- enough scope framing to keep the item bounded

Planning items should **not** be overly opinionated about the implementation approach.

That belongs in the proposal → implementation loop.

Once something is implemented and accepted, the important as-is truth should be promoted into the correct canonical home, and the planning item should be removed.

## What This Workflow Is Optimized For

This workflow is optimized for:

- exploratory product and technical work
- iterative implementation with fast feedback
- agent-assisted execution
- promoting only accepted truth into canonical docs
- keeping future work visible without prematurely hardening the solution

## What This Workflow Is Not

This is not a heavyweight ticket-first SDLC.

Earlier versions of the repo used smaller ticket trees more aggressively to help AI agents stay bounded.

That role is now largely superseded by:

- better todo tracking in agentic tools
- parent/subagent orchestration
- tighter proposal → implementation → promotion loops

So planning docs should exist to preserve future work and context, not to artificially decompose every implementation step.

## Practical Rules

### When starting work

- If the main need is solution exploration, start in exploratory proposal docs
- If the work is already known but not active yet, record it in planning docs
- If the truth is already accepted and implemented, update the top-level canonical docs instead

### During implementation

- iterate in code and UAT until the behavior is accepted
- append implementation notes only where they add lasting value
- record important deviations from the original proposal

### After acceptance

- extract and promote the durable truth
- avoid leaving accepted truth stranded only in brainstorm docs
- update the right canonical home rather than creating duplicate summaries in multiple places

## Planning vs exploratory proposal docs

Use this distinction consistently:

- **Planning** = this matters and should be done later
- **Exploratory proposal docs** = this is the explored solution space for how it might be done

Planning items are ticket-like and solution-neutral.

Exploratory proposal docs are proposal-oriented and can be opinionated, comparative, or exploratory.

## Outcome

This workflow aims to keep the repository legible:

- future work is visible in planning docs
- exploration lives in proposal docs
- accepted truth lives in top-level canonical docs

That separation is the default standard for new work in this repository.

## Local Development Data Policy

The local Neo4j and PostgreSQL development databases are treated as **disposable and rebuildable** at all stages of current development.

There is no migration obligation for local development data.

When the graph schema, extraction pipeline, catalog schema, or entity model changes, the correct response is to wipe the local databases and re-ingest from source content. Re-ingestion is the migration path.

This policy applies until LoreVault explicitly adopts a schema versioning and migration mechanism and documents it in a canonical rule or ADR. Until then:

- Do not write planning or design docs that treat existing local database data as a constraint
- Do not design schema changes around in-place local data migration
- Do not block implementation work on backfill concerns for local data

### Flyway migrations during wipe-state development

Flyway is used to create schemas consistently, not to preserve local data history.

While LoreVault remains a demo/development project with wipe-reset-test cycles and no durable shipped database schema, module-local Flyway migrations should stay collapsed into a single evolving `V1__...sql` per schema area.

Practical rule:

- If a schema has not been shipped as durable user data, rewrite its existing `V1__...sql` migration instead of adding `V2`, `V3`, etc.
- Use Git history as the version record for development-stage schema evolution.
- If editing `V1` causes local Flyway checksum mismatches, reset the local database/schema history; do not preserve the mismatch with repair commits or compatibility migrations.
- Start incremental migrations only after the project explicitly declares a durable schema boundary in a canonical rule or ADR.

Current application:

- The relation catalog PostgreSQL schema is development-only and lives in one evolving migration: `lorevault-catalog/src/main/resources/db/migration/catalog/V1__catalog_definition.sql`.

Scripts that support this policy:

- `scripts/reset-dev-db.sh` — wipes the local Neo4j dev database and local PostgreSQL catalog data; pass `--catalog-schema` after editing an evolving catalog `V1` migration to also drop catalog tables and Flyway schema history before the next API start
- `scripts/prepare-dev-environment.sh` — wipes and re-seeds canonical sample data (requires app and Neo4j already running)
