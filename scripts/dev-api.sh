#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$ROOT_DIR/logs}"
LOG_FILE="${LOG_FILE:-$LOG_DIR/lorevault-api.log}"
PID_FILE="${PID_FILE:-$LOG_DIR/lorevault-api.pid}"
RUN_PROFILE="${SPRING_BOOT_RUN_PROFILE:-dev}"

usage() {
  cat <<'EOF'
Usage: ./scripts/dev-api.sh <command>

Commands:
  run      Run the API in the foreground with .env loaded
  start    Run the API in the background and write logs/pid files
  stop     Stop the background API process
  restart  Restart the background API process
  status   Show process and health status
  logs     Tail the API log file
EOF
}

load_env() {
  set -a
  if [ -f "$ROOT_DIR/.env" ]; then
    # shellcheck disable=SC1091
    source "$ROOT_DIR/.env"
  fi
  set +a

  APP_PORT="${SERVER_PORT:-18080}"
  HEALTH_URL="${HEALTH_URL:-http://localhost:${APP_PORT}/actuator/health}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

is_running() {
  [ -f "$PID_FILE" ] || return 1

  local pid
  pid="$(cat "$PID_FILE")"
  [ -n "$pid" ] || return 1

  kill -0 "$pid" >/dev/null 2>&1
}

start_background() {
  require_command mvn
  mkdir -p "$LOG_DIR"
  load_env

  if is_running; then
    printf 'API already running with PID %s\n' "$(cat "$PID_FILE")"
    printf 'Logs: %s\n' "$LOG_FILE"
    return 0
  fi

  printf 'Starting LoreVault API in background ...\n'
  printf 'Profile: %s\n' "$RUN_PROFILE"
  printf 'Health:  %s\n' "$HEALTH_URL"
  printf 'Logs:    %s\n' "$LOG_FILE"

  (
    cd "$ROOT_DIR"
    mvn -DskipTests install
    exec mvn -f lorevault-web/pom.xml spring-boot:run -Dspring-boot.run.profiles="$RUN_PROFILE"
  ) >>"$LOG_FILE" 2>&1 &

  echo "$!" >"$PID_FILE"
  wait_for_ready
}

run_foreground() {
  require_command mvn
  load_env

  cd "$ROOT_DIR"
  mvn -pl lorevault-web -am -DskipTests install
  exec mvn -pl lorevault-web spring-boot:run -Dspring-boot.run.profiles="$RUN_PROFILE"
}

wait_for_ready() {
  local pid
  pid="$(cat "$PID_FILE")"

  if ! command -v curl >/dev/null 2>&1; then
    printf 'Started with PID %s (curl not available, skipping health wait)\n' "$pid"
    return 0
  fi

  for _ in {1..60}; do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      printf 'API process exited before becoming healthy. Check %s\n' "$LOG_FILE" >&2
      rm -f "$PID_FILE"
      exit 1
    fi

    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
      printf 'API ready at %s\n' "$HEALTH_URL"
      return 0
    fi

    sleep 2
  done

  printf 'Timed out waiting for API health at %s\n' "$HEALTH_URL" >&2
  printf 'Process is still running; inspect logs with: ./scripts/dev-api.sh logs\n' >&2
}

stop_background() {
  if ! is_running; then
    printf 'API is not running.\n'
    rm -f "$PID_FILE"
    return 0
  fi

  local pid
  pid="$(cat "$PID_FILE")"
  printf 'Stopping API process %s ...\n' "$pid"
  kill "$pid"

  for _ in {1..30}; do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      rm -f "$PID_FILE"
      printf 'API stopped.\n'
      return 0
    fi
    sleep 1
  done

  printf 'API did not stop after 30 seconds.\n' >&2
  exit 1
}

show_status() {
  load_env

  if is_running; then
    printf 'Process: running (PID %s)\n' "$(cat "$PID_FILE")"
  else
    printf 'Process: stopped\n'
  fi

  printf 'Health URL: %s\n' "$HEALTH_URL"
  printf 'Log file:   %s\n' "$LOG_FILE"

  if command -v curl >/dev/null 2>&1 && curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    printf 'Health:     UP\n'
  else
    printf 'Health:     DOWN or unreachable\n'
  fi
}

tail_logs() {
  mkdir -p "$LOG_DIR"
  touch "$LOG_FILE"
  tail -F "$LOG_FILE"
}

main() {
  case "${1:-}" in
    run)
      run_foreground
      ;;
    start)
      start_background
      ;;
    stop)
      stop_background
      ;;
    restart)
      stop_background
      start_background
      ;;
    status)
      show_status
      ;;
    logs)
      tail_logs
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
