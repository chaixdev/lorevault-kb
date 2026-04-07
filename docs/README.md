# LoreVault Documentation

## Start Here

Read these first:

- **[Project Status](PROJECT-STATUS.md)** — current state, next steps, and active decisions
- **[Architecture Overview](architecture/README.md)** — system architecture and viewpoints
- **[Pattern Library](patterns/README.md)** — durable implementation patterns
- **[Architecture Decisions](adr/README.md)** — current architectural decisions
- **[Refactor Roadmap](development/current/refactor-roadmap.md)** — current structural roadmap and next refactor steps

## Structure

### Canonical Documentation

- **`architecture/`** — system architecture viewpoints
- **`adr/`** — active architectural decisions
- **`patterns/`** — stable implementation patterns
- **`development/current/`** — living system documentation
- **`development/research/`** — current deep-dive research worth keeping active

### Historical Documentation

- **`archive/`** — historical, superseded, completed, or exploratory documents

### API Documentation

- **`api/`** — REST API specifications and collections

## Migration Status

LoreVault is currently moving away from a version-heavy documentation structure toward:

- small canonical docs
- explicit architectural decisions
- reusable pattern docs
- a clear archive for history

Some historical material still remains under `development/refactor/` and `development/versions/` while the migration is in progress.

## Navigation

- **Current system understanding**: `development/current/`
- **Refactor continuity**: `development/current/refactor-roadmap.md`
- **Data model**: `development/current/data-model/`
- **Current processes**: `development/current/processes/`
- **Configuration**: `development/current/configuration/`
- **Historical archaeology**: `archive/` and remaining version/refactor docs during migration

## Documentation Rules

### Put material in `docs/` only if it is:

- currently true
- useful to future readers
- not tied to one abandoned ticket or obsolete milestone
- descriptive rather than speculative

### Use the archive for:

- completed refactor plans
- superseded milestone docs
- historical tickets
- abandoned or deferred proposals that still have archaeology value

### Use `.agent-notes/` for:

- exploratory analysis
- ticket-specific investigation
- code review notes
- temporary working memory

`.agent-notes/` is not canonical documentation.
