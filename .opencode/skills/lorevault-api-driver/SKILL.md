---
name: lorevault-api-driver
description: Drives the LoreVault ingestion pipeline via curl commands — load when asked to run ingestion steps, drive the API, test endpoints, or execute a step-by-step pipeline
---

## What I Do

Produces executable curl command sequences that drive the LoreVault ingestion pipeline step by step. Use this skill when an agent needs to create library structure, prepare chapters, run individual pipeline steps, inspect results, or test endpoints via the REST API. Does not produce code changes — only curl commands and their expected responses.

## Inputs Required

- LoreVault API must be running at `localhost:18080` (see `repo-dev-commands` skill for startup)
- Neo4j must be running (see `repo-dev-commands` skill)
- The caller must provide: chapter text content, and optionally book/series/universe names

## Methodology

### 1. Establish prerequisites

Before any pipeline commands, verify the API is healthy:

```bash
curl -s localhost:18080/actuator/health | jq .
```

If this fails, the API is not running. Refer to `repo-dev-commands` for startup instructions.

### 2. Build library structure top-down

Always create entities in dependency order: universe → series → book. Capture IDs from each response for use in subsequent commands.

```bash
UNIVERSE_ID=$(curl -s -X POST localhost:18080/api/command/library/universe \
  -H 'Content-Type: application/json' \
  -d '{"name":"My Universe"}' | jq -r '.id')

SERIES_ID=$(curl -s -X POST localhost:18080/api/command/library/series \
  -H 'Content-Type: application/json' \
  -d "{\"universeId\":\"$UNIVERSE_ID\",\"name\":\"My Series\"}" | jq -r '.id')

BOOK_ID=$(curl -s -X POST localhost:18080/api/command/library/book \
  -H 'Content-Type: application/json' \
  -d "{\"universeId\":\"$UNIVERSE_ID\",\"seriesId\":\"$SERIES_ID\",\"title\":\"My Book\",\"bookNumber\":1}" | jq -r '.id')
```

**Framework:** Always use `jq -r '.id'` to extract IDs. Never hardcode IDs — they change between runs.

### 3. Prepare chapter (no cascade)

Use the `/prepare` endpoint to create a chapter and job without triggering the async pipeline. This is the correct starting point for step-by-step execution.

```bash
PREPARE_RESULT=$(curl -s -X POST localhost:18080/api/command/ingest/prepare \
  -H 'Content-Type: application/json' \
  -d "{\"bookId\":\"$BOOK_ID\",\"chapterNumber\":1,\"chapterTitle\":\"Chapter 1\",\"chapterText\":\"It was a dark and stormy night...\"}")
JOB_ID=$(echo "$PREPARE_RESULT" | jq -r '.jobId')
CHAPTER_ID=$(echo "$PREPARE_RESULT" | jq -r '.chapterId')
```

**Framework:** Always use `/prepare` for step-by-step workflows. Never use `/ingest` (full pipeline) unless the goal is to trigger the entire async cascade.

### 4. Discover available steps

```bash
curl -s localhost:18080/api/query/ingestion/stages | jq '.stages[] | {key, scope, prerequisites}'
```

Use this to verify the step surface and check prerequisites before running steps.

### 5. Run steps in prerequisite order

The dependency graph is:

```
detect-scenes ──┬── chunk ──── embed
                 ├── resolve-individuals ──┐
                 ├── resolve-collectives ───┤
                 ├── resolve-locations ──────┼── (book reduce steps)
                 └── resolve-events ────────┘
```

Run each step as a POST to its endpoint. Default behavior: `fireEvents=false` (isolated, no cascade).

```bash
# Step-by-step (isolated, no cascade):
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/detect-scenes" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/chunk" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/embed" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-individuals" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-collectives" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-locations" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-objects" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/resolve-events" | jq .

# Book-level reduction (after chapter resolution):
curl -s -X POST "localhost:18080/api/command/ingest/books/$BOOK_ID/reduce-individuals" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/books/$BOOK_ID/reduce-collectives" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/books/$BOOK_ID/reduce-locations" | jq .
curl -s -X POST "localhost:18080/api/command/ingest/books/$BOOK_ID/reduce-objects" | jq .
```

**Framework:** Always check `success` in the response before proceeding to the next step. If `success` is `false`, check `retryable` — if `true`, the step may be retried; if `false`, the failure is permanent.

### 6. Use fireEvents for cascade mode

Add `?fireEvents=true&jobId=$JOB_ID` to trigger downstream pipeline events after a step completes:

```bash
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/detect-scenes?fireEvents=true&jobId=$JOB_ID" | jq .
```

**Framework:** Use `fireEvents=true` only when intentionally triggering the async cascade. For inspection and debugging, use the default `fireEvents=false`.

### 7. Check job status

```bash
curl -s "localhost:18080/api/query/jobs/$JOB_ID" | jq .
curl -s "localhost:18080/api/query/jobs?limit=20&offset=0" | jq .
```

### 8. Handle errors correctly

| Response | Meaning | Action |
|---|---|---|
| `200` with `success: true` | Step completed | Proceed to next step |
| `200` with `success: false, retryable: true` | Transient failure (LLM timeout, rate limit) | Retry the same step |
| `200` with `success: false, retryable: false` | Permanent failure (bad input, not found) | Investigate, do not retry |
| `400` | Invalid UUID or validation error | Fix the request |
| `404` | Chapter or book not found | Verify the ID |
| `307` | Redirect from old `resolve-*` URL to `reduce-*` | Use the new URL directly |

## Output Format

Produce a bash script with variable capture and `jq` parsing. Every command must be copy-pasteable into a terminal. Structure:

```bash
# 1. Prerequisites
curl -s localhost:18080/actuator/health | jq .

# 2. Create library
UNIVERSE_ID=$(...)
SERIES_ID=$(...)
BOOK_ID=$(...)

# 3. Prepare chapter
PREPARE_RESULT=$(...)
JOB_ID=$(...)
CHAPTER_ID=$(...)

# 4. Run steps
curl -s -X POST "localhost:18080/api/command/ingest/chapters/$CHAPTER_ID/detect-scenes" | jq .
# ... subsequent steps
```

## Edge Cases

### Edge case: Step returns `success: false, retryable: true`
This is a transient failure (LLM timeout, rate limit, claim contention). Wait briefly and retry the same curl command. Do not proceed to the next step until the current step succeeds.

### Edge case: Chapter or book not found (404)
The UUID was likely not captured correctly. Re-run the library creation or prepare step and capture the ID with `jq -r '.id'`. Never hardcode UUIDs.

### Edge case: Multiple chapters in one book
Prepare each chapter separately with `/prepare`, then run chapter-scoped steps for each. Book-level reduction steps (`reduce-*`) run once per book after all chapters are processed.

### Edge case: Need to reset data
Use `scripts/reset-dev-db.sh` to wipe the local Neo4j database, then `scripts/prepare-dev-environment.sh` to re-seed canonical data. Both require the API and Neo4j to be running first.

## Composability

- **Upstream:** Load `repo-dev-commands` first to ensure the API and Neo4j are running.
- **Downstream:** The curl output can be piped to `jq` for structured inspection, or the IDs can be used in Neo4j Cypher queries for verification.
- **Reference:** Full endpoint documentation lives in `docs/curl-catalog.md`.