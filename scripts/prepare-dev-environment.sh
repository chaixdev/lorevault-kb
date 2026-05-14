#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:18080}"
NEO4J_CONTAINER="${NEO4J_CONTAINER:-lorevault-neo4j}"

UNIVERSE_NAME="JENKINSVERSE"
SERIES_NAME="Deathworlders"
BOOK_TITLE="The Kevin Jenkins Experience"
BOOK_NUMBER=0

CHAPTER_FILES=(
  "$ROOT_DIR/lorevault-web/src/test/resources/sample-chapters/000_deathworlders - The Kevin Jenkins Experience.txt"
  "$ROOT_DIR/lorevault-web/src/test/resources/sample-chapters/005_reddit-Hambone3110 - Run, little monster.txt"
  "$ROOT_DIR/lorevault-web/src/test/resources/sample-chapters/007_reddit-Hambone3110 - Aftermath.txt"
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

require_file() {
  if [ ! -f "$1" ]; then
    printf 'Missing required file: %s\n' "$1" >&2
    exit 1
  fi
}

require_json_field() {
  local json="$1"
  local field="$2"
  local value
  value="$(jq -r "$field // empty" <<<"$json")"

  if [ -z "$value" ] || [ "$value" = "null" ]; then
    printf 'Expected response field %s but got:\n%s\n' "$field" "$json" >&2
    exit 1
  fi

  printf '%s' "$value"
}

require_neo4j_running() {
  if docker ps --format '{{.Names}}' | grep -qx "$NEO4J_CONTAINER"; then
    return 0
  fi

  printf 'Neo4j container %s is not running\n' "$NEO4J_CONTAINER" >&2
  exit 1
}

wait_for_app() {
  printf 'Waiting for app at %s ...\n' "$BASE_URL"

  for _ in {1..30}; do
    if curl -fsS "$BASE_URL/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  printf 'App did not become ready at %s\n' "$BASE_URL" >&2
  exit 1
}

reset_neo4j() {
  printf 'Resetting Neo4j ...\n'
  "$ROOT_DIR/scripts/reset-dev-db.sh"
}

create_universe() {
  printf 'Creating universe %s ...\n' "$UNIVERSE_NAME"

  local response
  response="$(curl -fsS -X POST "$BASE_URL/api/command/library/create-universe" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"$UNIVERSE_NAME\"}")"

  UNIVERSE_ID="$(require_json_field "$response" '.universeId')"
}

create_series() {
  printf 'Creating series %s ...\n' "$SERIES_NAME"

  local response
  response="$(curl -fsS -X POST "$BASE_URL/api/command/library/create-series" \
    -H 'Content-Type: application/json' \
    -d "{\"universeId\":\"$UNIVERSE_ID\",\"name\":\"$SERIES_NAME\"}")"

  SERIES_ID="$(require_json_field "$response" '.seriesId')"
}

create_book() {
  printf 'Creating book %s ...\n' "$BOOK_TITLE"

  local response
  response="$(curl -fsS -X POST "$BASE_URL/api/command/library/create-book" \
    -H 'Content-Type: application/json' \
    -d "{\"universeId\":\"$UNIVERSE_ID\",\"seriesId\":\"$SERIES_ID\",\"title\":\"$BOOK_TITLE\",\"bookNumber\":$BOOK_NUMBER}")"

  BOOK_ID="$(require_json_field "$response" '.bookId')"
}

wait_for_job() {
  local job_id="$1"

  printf 'Waiting for job %s ...\n' "$job_id"

  for _ in {1..60}; do
    local response status
    response="$(curl -fsS "$BASE_URL/api/query/jobs/$job_id")"
    status="$(jq -r '.currentStatus' <<<"$response")"

    case "$status" in
      COMPLETE)
        printf 'Job %s complete.\n' "$job_id"
        return 0
        ;;
      FAILED|ERROR)
        printf 'Job %s failed:\n%s\n' "$job_id" "$response" >&2
        exit 1
        ;;
    esac

    sleep 2
  done

  printf 'Timed out waiting for job %s\n' "$job_id" >&2
  exit 1
}

upload_chapter() {
  local chapter_number="$1"
  local file_path="$2"
  local escaped_file_path

  require_file "$file_path"
  printf 'Uploading chapter %s from %s ...\n' "$chapter_number" "$file_path"

  escaped_file_path="${file_path//\"/\\\"}"

  local response job_id
  response="$(curl -fsS -X POST \
    "$BASE_URL/api/command/ingest?bookId=$BOOK_ID&chapterNumber=$chapter_number" \
    --form "file=@\"${escaped_file_path}\";type=text/plain")"

  job_id="$(require_json_field "$response" '.jobId')"
  printf 'Chapter %s job: %s\n' "$chapter_number" "$job_id"
  wait_for_job "$job_id"
  printf 'Chapter %s complete.\n' "$chapter_number"
}

main() {
  require_command curl
  require_command jq
  require_command docker

  for chapter_file in "${CHAPTER_FILES[@]}"; do
    require_file "$chapter_file"
  done

  require_neo4j_running
  wait_for_app
  reset_neo4j
  wait_for_app

  create_universe
  create_series
  create_book

  upload_chapter 1 "${CHAPTER_FILES[0]}"
  upload_chapter 2 "${CHAPTER_FILES[1]}"
  upload_chapter 3 "${CHAPTER_FILES[2]}"

  printf '\nEnvironment ready.\n'
  printf 'Universe: %s (%s)\n' "$UNIVERSE_NAME" "$UNIVERSE_ID"
  printf 'Series:   %s (%s)\n' "$SERIES_NAME" "$SERIES_ID"
  printf 'Book:     %s (%s)\n' "$BOOK_TITLE" "$BOOK_ID"
}

main "$@"
