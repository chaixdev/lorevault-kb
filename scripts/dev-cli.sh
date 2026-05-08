#!/usr/bin/env bash
# dev-cli.sh — Run the LoreVault CLI module
#
# Usage:
#   ./scripts/dev-cli.sh run <command> [options]
#
# Commands:
#   library create  -u "Universe" [-s "Series"] -b "Book Title" [-n bookNumber]
#   prepare         -b <bookUuid> -n <chapterNumber> [-t "Title"] <chapter-file|->
#   step run        SCENE_DETECTION --job <uuid> --chapter <uuid>
#   step list
#   status          <jobId>
#   jobs list       [--universe U] [--status S] [--limit N] [--offset O]
#
# Environment:
#   Sources .env automatically (same as dev-api.sh)

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source environment variables
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a; source "$PROJECT_ROOT/.env"; set +a
fi

# Build the CLI module if needed
CLI_JAR="$PROJECT_ROOT/lorevault-cli/target/lorevault-cli-*-exec.jar"

run_cli() {
    # Check if jar exists, build if not
    if ! compgen -G "$CLI_JAR" > /dev/null 2>&1; then
        echo "CLI jar not found. Building..."
        (cd "$PROJECT_ROOT" && mvn -pl lorevault-cli -am package -DskipTests -q)
    fi

    # Find the actual jar file
    local jar_path
    jar_path=$(ls "$PROJECT_ROOT/lorevault-cli/target/lorevault-cli-"*-exec.jar 2>/dev/null | head -1)

    if [[ -z "$jar_path" ]]; then
        echo "Error: CLI jar not found after build"
        exit 1
    fi

    exec java -jar "$jar_path" "$@"
}

case "${1:-help}" in
    run)
        shift
        run_cli "$@"
        ;;
    build)
        echo "Building CLI module..."
        (cd "$PROJECT_ROOT" && mvn -pl lorevault-cli -am package -DskipTests)
        ;;
    help|*)
        echo "LoreVault CLI"
        echo ""
        echo "Usage:"
        echo "  $0 run <command> [options]    Run a CLI command"
        echo "  $0 build                       Build the CLI module"
        echo ""
        echo "Commands:"
        echo "  library create  -u \"Universe\" [-s \"Series\"] -b \"Book Title\" [-n bookNumber]"
        echo "  prepare         -b <bookUuid> -n <chapterNumber> [-t \"Title\"] <chapter-file|->"
        echo "  step run        SCENE_DETECTION --job <uuid> --chapter <uuid>"
        echo "  step list"
        echo "  status          <jobId>"
        echo "  jobs list       [--universe U] [--status S] [--limit N] [--offset O]"
        ;;
esac