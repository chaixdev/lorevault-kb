# LoreVault Agent Guide

## Purpose

This file gives agents the always-on project rules for working in this repository.
Use it for command selection, documentation routing, and local development workflow.

## Repo shape

- Java 21 + Maven multi-module project
- Main runnable module: `lorevault-api`
- Graph database: Neo4j via `docker-compose.yml`
- Living documentation: `docs/`
- Temporary agent working memory: `.agent-notes/`

## Source of truth

When checking status or roadmap, prefer these files in this order:

1. `docs/PROJECT-STATUS.md` — current progress snapshot
2. `docs/development/current/refactor-roadmap.md` — broad roadmap
3. `docs/development/current/` — current system docs
4. `docs/archive/` — archaeology only

Do not treat `.agent-notes/` as canonical documentation.

## Build and test commands

### Fast local loop

- `mvn test`

### Broader verification

- `mvn verify -P integration-tests`
- `mvn verify -P coverage-gate`
- `mvn test -P architecture-tests`
- `mvn test -P mutation-testing`

### Compile / package

- `mvn clean compile`
- `mvn -pl lorevault-api clean compile -DskipTests`
- `mvn -pl lorevault-api package -DskipTests`

## Run commands

### Start Neo4j

- `docker-compose up -d neo4j`

Neo4j container name is expected to be `lorevault-neo4j`.

### Run the API manually

Preferred dev command:

```bash
./scripts/dev-api.sh run
```

Why:

- the script sources `.env` before invoking Maven
- the script keeps the canonical module/profile command in one place
- the script provides `start`, `stop`, `restart`, `status`, and `logs` subcommands for local development

Background workflow:

```bash
./scripts/dev-api.sh start
./scripts/dev-api.sh logs
```

### Health endpoints

Use port **18080** as the real default.

- `http://localhost:18080/actuator/health`
- `http://localhost:18080/api/status`

`README.md` still mentions 8080 in one spot. Prefer `application.yml` over the README for port truth.

## Repo-local scripts

### `scripts/reset-dev-db.sh`

- Resets the local Neo4j dev database inside the `lorevault-neo4j` container
- Destructive for local dev data
- Assumes container auth `neo4j/neosecret`

Run with:

- `./scripts/reset-dev-db.sh`

### `scripts/dev-api.sh`

- Runs the API with `.env` loaded before Maven starts
- Supports foreground and background workflows
- Writes logs to `logs/lorevault-api.log` in background mode
- Writes PID state to `logs/lorevault-api.pid` in background mode

Run with:

- `./scripts/dev-api.sh run`
- `./scripts/dev-api.sh start`
- `./scripts/dev-api.sh logs`
- `./scripts/dev-api.sh status`
- `./scripts/dev-api.sh stop`
- `./scripts/dev-api.sh restart`

### `scripts/prepare-dev-environment.sh`

- Resets Neo4j
- Creates the canonical universe / series / book
- Uploads 3 fixed sample chapters
- Waits for ingestion jobs to finish

Preconditions:

- app already running at `http://localhost:18080`
- Neo4j container already running
- `curl`, `jq`, and `docker` installed

Run with:

- `./scripts/prepare-dev-environment.sh`

Do **not** assume this script starts Docker or the app. It does not.

## Environment variables

Relevant local files:

- `.env`
- `.env.example`
- `lorevault-api/src/main/resources/application.yml`

Important keys used by the app include:

- `NEO4J_USERNAME`
- `NEO4J_PASSWORD`
- `GEMINI_AI_API_KEY`
- `GROQ_API_KEY`

Do not assume `.env.example` is perfectly current; prefer `application.yml` for the keys the running app actually consumes.

## Logs and manual debugging

- Use `./scripts/dev-api.sh logs` for the committed log tail helper
- Background mode writes the API log to `logs/lorevault-api.log`
- Foreground mode still writes directly to stdout/stderr

Example:

```bash
./scripts/dev-api.sh start
./scripts/dev-api.sh logs
```

## Agent workflow expectations

- Prefer existing scripts over ad hoc replacements
- Prefer `mvn` commands already documented in `docs/development/current/testing/developer-testing-workflow.md`
- Prefer small, focused changes over broad refactors unless explicitly asked
- When documenting current behavior, verify against code/config, not only README prose
- When status and roadmap disagree, treat `docs/PROJECT-STATUS.md` as the progress snapshot

## When adding new docs

- Put current, durable documentation under `docs/development/current/`, `docs/patterns/`, or `docs/adr/`
- Put temporary exploration or ticket notes under `.agent-notes/`
- Put historical or superseded material in `docs/archive/`

## Do not assume

- there is a Maven wrapper (`./mvnw`) — there is not
- `scripts/dev-api.sh` starts the API only; it does not start Neo4j
- README port numbers are always current — verify against `application.yml`
