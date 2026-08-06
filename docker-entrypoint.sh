#!/bin/sh
set -eu

workspace="${1:-/workspace}"

if [ ! -d "$workspace" ]; then
    echo "Workspace directory does not exist: $workspace" >&2
    exit 64
fi

mkdir -p "$HOME"

exec java \
    -XX:InitialRAMPercentage=5.0 \
    -XX:MaxRAMPercentage=25.0 \
    -XX:+ExitOnOutOfMemoryError \
    -jar /opt/lsp4j-mcp/lsp4j-mcp.jar \
    "$workspace" \
    "$JDTLS_CMD"
