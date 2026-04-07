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

Documentation migration is complete. The repository uses:

- small canonical docs in `development/current/`
- explicit architectural decisions in `adr/`
- reusable pattern docs in `patterns/`
- a clear archive for history in `archive/`

Version directories under `development/versions/` and `development/refactor/` retain only README redirect stubs pointing to `archive/`.

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
