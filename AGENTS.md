# LoreVault Agent Guide

## Source of truth

1. `docs/PROJECT-STATUS.md` — current progress snapshot
2. `docs/planning/README.md` — bounded future work and parked planning context
3. `docs/rules/development-workflow.md` — default repo workflow
4. `docs/archive/` — archaeology only

When status and roadmap disagree, treat `docs/PROJECT-STATUS.md` as the progress snapshot.

## Build and test commands

```bash
mvn test                                               # fast local loop
mvn verify -P integration-tests
mvn verify -P coverage-gate
mvn test -P architecture-tests
mvn test -P mutation-testing
mvn clean compile
mvn clean compile -DskipTests
mvn package -DskipTests
```

No Maven wrapper (`./mvnw`) — use `mvn` directly.

## Run commands

```bash
docker-compose up -d neo4j          # start Neo4j (container: lorevault-neo4j)
./scripts/dev-api.sh run            # foreground API (sources .env automatically)
./scripts/dev-api.sh start          # background
./scripts/dev-api.sh logs           # tail background log
./scripts/dev-api.sh stop|restart|status
```

- API port: **18080** (prefer `application.yml` over README for port truth)
- Health: `http://localhost:18080/actuator/health`
- `dev-api.sh` starts the API only — it does not start Neo4j

## Repo-local scripts

| Script | What it does |
|---|---|
| `scripts/dev-api.sh` | Run/manage API with `.env` pre-loaded |
| `scripts/reset-dev-db.sh` | Reset local Neo4j dev DB (destructive; auth `neo4j/neosecret`) |
| `scripts/prepare-dev-environment.sh` | Reset DB + seed canonical sample data (requires app + Neo4j already running) |

## Environment variables

Source: `.env` / `lorevault-web/src/main/resources/application.yml` (prefer `application.yml` for keys the app actually consumes).

Key vars: `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `GEMINI_AI_API_KEY`, `GROQ_API_KEY`

## Coding standards — mandatory skill

When delegating **any implementation, refactoring, or review task** via `task()`, always include
`lorevault-coding-style` in `load_skills`:

```
task(category="...", load_skills=["lorevault-coding-style"], ...)
```

When working directly on code (not delegating), load the skill via the `skill` tool before writing any code.

## Agent workflow expectations

- Prefer existing scripts over ad hoc replacements
- Prefer `mvn` commands already documented in `docs/rules/developer-testing-workflow.md`
- Prefer small, focused changes over broad refactors unless explicitly asked
- Verify behavior against code/config, not README prose

## Doc routing

| Content type | Location |
|---|---|
| Durable architecture/rules | `docs/adr/`, `docs/patterns/`, `docs/rules/`, `docs/concepts/` |
| Exploratory proposals | `docs/brainstorm/` |
| Bounded future work / parked items | `docs/planning/` |
| Historical / superseded | `docs/archive/` |
| Throwaway scratch | Outside the repo (system temp) |

**Naming convention for brainstorm and planning files:** Use an ISO datetime prefix (`YYYY-MM-DDTHHMM_topic-slug.md`), not a month/year suffix. Example: `2026-05-11T0930_orchestration-domain-separation.md`, not `orchestration-domain-separation-may-2026.md`.
