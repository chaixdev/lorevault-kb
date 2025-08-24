# LV-085-10R — Migration job to reconcile edges [migration]

Problem

- Existing edges must be updated to the triad model with canonical relations and statuses.

Proposal

- Implement a one-off migration that:
  - Normalizes relations to canonical 7
  - Sets default status based on existing metadata (e.g., CONFIRMED for ordered edges)
  - Backfills evidence fields when available; otherwise leaves null

Scope

- Migration runner: Spring Boot command or Flyway-like approach
- Idempotency: track progress and allow re-run safely
- Tests: unit tests around normalization and status setting

Acceptance criteria

- [ ] Migration runs without errors on sample data
- [ ] Rerunning migration is a no-op

Quality gates

- [ ] Build and unit tests pass; ArchUnit rules preserved.
