#!/usr/bin/env bash

# Java Code Formatter using jbang-fmt with Eclipse formatter
# https://github.com/jbangdev/jbang-fmt

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"

# Default to eclipse style, can be overridden: eclipse, google, java, quarkus, spring
STYLE="${FORMAT_STYLE:-eclipse}"

usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  format    Format all Java source files (default)"
    echo "  check     Check formatting without modifying files (for CI)"
    echo "  help      Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  FORMAT_STYLE  Formatter style: eclipse, google, java, quarkus, spring (default: eclipse)"
}

format() {
    echo "Formatting Java files with '$STYLE' style..."
    jbang jbang-fmt@jbangdev/jbang-fmt --style="$STYLE" --indent-with space --indent-size 4 --line-length 120 "$SRC_DIR"
    echo "Done."
}

check() {
    echo "Checking Java formatting with '$STYLE' style..."
    if jbang jbang-fmt@jbangdev/jbang-fmt --style="$STYLE" --indent-with space --indent-size 4 --line-length 120 --check "$SRC_DIR"; then
        echo "All files are properly formatted."
        exit 0
    else
        echo "Some files need formatting. Run './format.sh' to fix."
        exit 1
    fi
}

case "${1:-format}" in
    format)
        format
        ;;
    check)
        check
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        echo "Unknown command: $1"
        usage
        exit 1
        ;;
esac
