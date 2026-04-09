# LoreVault Documentation

## Start Here

Read these first:

- **[Project Status](PROJECT-STATUS.md)** — current state, recent milestones, and next directions
- **[Architecture Overview](architecture/README.md)** — stakeholder-facing system viewpoints
- **[Architecture Decisions](adr/README.md)** — accepted decisions made at real architectural forks
- **[Pattern Library](patterns/README.md)** — present-state mechanism docs for important multi-file areas
- **[Refactor Roadmap](development/current/refactor-roadmap.md)** — active continuity for ongoing structural work

## Documentation Taxonomy

### Canonical Current Documentation

- **`architecture/`** — high-level system viewpoints and architectural communication
- **`adr/`** — accepted architectural decisions and their rationale
- **`patterns/`** — current implementation mechanisms that are hard to infer from code alone
- **`concepts/`** — durable conceptual models worth preserving even when implementation diverges
- **`rules/`** — coding, documentation, and hygiene guidance
- **`development/current/`** — current detailed specs, data-model docs, and active continuity docs that have not yet been promoted into a smaller permanent home

### Exploratory / Future-Facing Documentation

- **`brainstorm/`** — proposals, sketches, and future-facing explorations that are valuable but not current truth
- **`development/research/`** — active research that still informs current decisions and may later become ADRs, patterns, or concepts

### Historical Documentation

- **`archive/`** — superseded plans, historical tickets, old milestone material, and archaeology

### API Documentation

- **`api/`** — REST/API specifications and Bruno collections

## Migration Status

Documentation migration is **in progress**.

The current repository still contains a mix of:

- canonical current docs
- older `development/` holdovers
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

### Use `rules/` when

- the document sets coding or documentation conventions
- it captures code hygiene rules or architectural preferences
- it tells contributors what to do repeatedly across the codebase

### Use `archive/` when

- the material is historical or superseded
- it is tied to an old milestone, ticket tree, or refactor phase
- it still has archaeology value but should not guide current work directly

## Navigation

- **Current system understanding**: `development/current/`
- **Data-model specifics**: `development/current/data-model/`
- **Current process specs**: `development/current/processes/`
- **Current configuration docs**: `development/current/configuration/`
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

Use `.agent-notes/` for:

- exploratory analysis
- ticket-specific investigation
- review artifacts
- temporary migration trackers and working memory

`.agent-notes/` is not canonical documentation.
