# LoreVault Documentation

## Start Here

Read these first:

- **[Project Status](PROJECT-STATUS.md)** — current state, recent milestones, and next directions
- **[Architecture Overview](architecture/README.md)** — stakeholder-facing system viewpoints
- **[Architecture Decisions](adr/README.md)** — accepted decisions made at real architectural forks
- **[Pattern Library](patterns/README.md)** — present-state mechanism docs for important multi-file areas
- **[Development Workflow](rules/development-workflow.md)** — the default brainstorm → implement → promote loop for this repository
- **[Planning](planning/README.md)** — bounded future work, parked items, and ticket-like planning context

## Documentation Taxonomy

### Canonical Current Documentation

- **`architecture/`** — high-level system viewpoints and architectural communication
- **`adr/`** — accepted architectural decisions and their rationale
- **`patterns/`** — current implementation mechanisms that are hard to infer from code alone
- **`concepts/`** — durable conceptual models worth preserving even when implementation diverges
- **`rules/`** — coding, documentation, and hygiene guidance
- **`planning/`** — outstanding future work, parked investigations, and bounded scopes written in a ticket-like but solution-neutral style

### Exploratory / Future-Facing Documentation

- **`brainstorm/`** — proposals, sketches, and future-facing explorations that are valuable but not current truth

### Historical Documentation

- **`archive/`** — superseded plans, historical tickets, old milestone material, and archaeology

### API Documentation

- **`api/`** — REST/API specifications and Bruno collections

## Migration Status

Documentation migration is **in progress**.

The current repository still contains a mix of:

- canonical current docs
- older `development/` holdovers likely to be migrated or removed
- historical refactor and version material already archived
- conceptual work that still needs a better permanent home

The immediate goal of this migration is to make the directory meanings obvious and trustworthy before moving more content.

## How To Choose The Right Home

### Use `adr/` when

- a real architectural decision was made
- multiple viable options existed
- the document records **why** a path was chosen
- the document describes a past or accepted decision, not a future wish

### Use `patterns/` when

- the mechanism is implemented now
- it spans multiple files or layers
- it would take non-trivial code reading to reconstruct
- the goal is to explain **how this area works today**

### Use `concepts/` when

- the idea is foundational and worth preserving
- it may continue guiding design even if implementation changes
- it is too important to bury in the archive
- it is not honest to present it as current implementation truth

### Use `brainstorm/` when

- the material is future-facing or exploratory
- it is a proposal, sketch, or option space
- it may eventually turn into a concept, ADR, or pattern

### Use `planning/` when

- the work is worth tracking but not yet implemented
- the item should read like a lightweight ticket
- the document should capture product context, technical context, scope, and constraints
- the solution should remain open until the brainstorm and implementation cycle converges

### Use `rules/` when

- the document sets coding or documentation conventions
- it captures code hygiene rules or architectural preferences
- it tells contributors what to do repeatedly across the codebase

### Use `archive/` when

- the material is historical or superseded
- it is tied to an old milestone, ticket tree, or refactor phase
- it still has archaeology value but should not guide current work directly

## Navigation

- **Planning and parked work**: `planning/`
- **Exploration and proposals**: `brainstorm/`
- **Durable conceptual models**: `concepts/`
- **Contributor guidance**: `rules/`
- **Current implementation patterns**: `patterns/`
- **Historical archaeology**: `archive/`

## Rules

Put material in canonical `docs/` only if it is:

- useful to future readers
- intentionally curated
- honest about whether it describes the present, a concept, or a proposal
- placed in the folder whose meaning matches the document's purpose

Keep material in `docs/` when you want it to survive the current session.

Examples include:

- parked bug investigations
- lightweight work tracking
- review notes worth preserving
- exploratory analysis that may inform later implementation or canonical docs

Use out-of-repo scratch space for truly throwaway working material.
