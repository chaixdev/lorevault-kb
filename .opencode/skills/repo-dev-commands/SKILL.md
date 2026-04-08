---
name: repo-dev-commands
description: Produces a repo-specific command and script guide for LoreVault — load when asked how to run the app, start Neo4j, load .env, seed local data, inspect logs, or choose an existing script
license: MIT
compatibility: opencode
metadata:
  audience: humans-and-agents
  workflow: developer-automation
---

## What I Do

This skill provides the repo-local operating guide for development commands, helper scripts, and local environment setup in LoreVault.
Load it before running or editing anything in `scripts/`, before starting the API locally, or whenever a task depends on existing dev workflow conventions.
It describes what already exists in the repo and how to use it safely.
It does not replace missing automation with new scripts unless the caller explicitly asks for implementation.

## Inputs Required

- Repository root available
- Current repo files readable, especially:
  - `AGENTS.md`
  - `README.md`
  - `docker-compose.yml`
  - `.env.example`
  - `scripts/reset-dev-db.sh`
  - `scripts/prepare-dev-environment.sh`
  - `docs/development/current/testing/developer-testing-workflow.md`
  - `lorevault-api/src/main/resources/application.yml`

## Methodology

1. Start from repo truth, not memory.
   - Use `application.yml`, scripts, and current docs as the primary sources.
   - Treat `README.md` as helpful but not authoritative when it conflicts with runtime config.

2. Route the request into one of five buckets.
   - **Run the app**
   - **Run tests / verification**
   - **Manage Neo4j**
   - **Seed local canonical data**
   - **Inspect logs / environment variables**

3. Prefer existing commands over inventing replacements.
   - If a repo script exists, recommend that script first.
   - If no repo script exists, provide the minimal direct command already consistent with this repo.

4. Handle environment variables explicitly.
   - The app consumes environment variables through Spring placeholders in `application.yml`.
   - Maven does not auto-load `.env`.
   - When the task is to run the app locally outside IDE automation, use shell-level env loading before `mvn`.

5. Keep app startup and data setup separate.
   - `scripts/dev-api.sh` starts the API only.
   - `scripts/prepare-dev-environment.sh` assumes the app and Neo4j are already running.
   - Do not claim it starts Docker or the API.
   - If the caller wants a one-command startup workflow for app + Neo4j + seeding, say that the repo still does not have that combined wrapper.

6. Use the documented Maven workflow tiers.
   - Fast loop: `mvn test`
   - Broader local verification: `mvn verify -P integration-tests`
   - Coverage: `mvn verify -P coverage-gate`
   - Architecture tests: `mvn test -P architecture-tests`
   - Mutation testing: `mvn test -P mutation-testing`

7. Be explicit about the real local port.
   - The real default app port is `18080` from `lorevault-api/src/main/resources/application.yml`.
   - If older docs mention 8080, call that out as stale and continue with 18080.

8. Recommend the committed local API helper first.

```bash
./scripts/dev-api.sh run
```

9. For background runs and logs, prefer the committed helper.

```bash
./scripts/dev-api.sh start
./scripts/dev-api.sh logs
```

10. When asked about scripts, answer with purpose + preconditions + command.
    - Purpose: what the script does
    - Preconditions: what must already be running / installed
    - Command: exact invocation
    - Constraints: destructive behavior, hardcoded assumptions, or missing automation

## Output Format

When answering command/script questions, produce exactly this shape:

### Recommended command
- One canonical command block

### Why this command
- 1–3 bullets tied to repo evidence

### Preconditions
- Explicit runtime or tooling assumptions

### Related scripts
- Bullet list of relevant repo scripts with one-line purpose summaries

### Gotchas
- Stale docs, hardcoded ports, missing wrappers, destructive reset behavior, or env-loading caveats

If the caller asked for a script inventory instead of a single command, use this shape instead:

### Script
- Path: `scripts/...`
- Purpose: ...
- Run with: `...`
- Preconditions: ...
- Notes: ...

## Edge Cases

### Edge case: README and runtime config disagree
- Prefer `lorevault-api/src/main/resources/application.yml` and current scripts over old prose.
- In this repo, app port `18080` wins over old `README.md` mentions of 8080.

### Edge case: Caller assumes `.env` is auto-loaded
- Correct the assumption directly.
- Explain that `.env` is loaded by IDE tooling in some workflows, but not by Maven itself.
- Provide a shell command that sources `.env` before `mvn`.

### Edge case: Caller expects one script to start everything
- State that the repo currently separates responsibilities:
  - `docker-compose` starts Neo4j
  - `scripts/dev-api.sh` starts the API
  - `scripts/prepare-dev-environment.sh` seeds data only after both are running
- Do not imply the combined startup wrapper already exists.

### Edge case: Caller wants to reset data safely
- Recommend `scripts/reset-dev-db.sh` only for local development.
- Warn that it destroys data in the local Neo4j container.

## Composability

- Upstream: `AGENTS.md` provides always-on project rules and command priorities.
- Downstream: this skill can be loaded before implementation skills that need correct local run commands, verification commands, or existing script awareness.
- Typical chain:
  1. Load `repo-dev-commands`
  2. Choose the correct existing command or script
  3. Only then implement or modify automation if explicitly requested
