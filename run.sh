#!/bin/bash
# Run the LSP4J MCP Server
#
# Usage: ./run.sh <workspace-path>
# Example: ./run.sh /Users/stephan/IdeaProjects/callforpapers

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/target/lsp4j-mcp-1.0.0-SNAPSHOT.jar"

# Default JDTLS command - adjust for your system
JDTLS_CMD="${JDTLS_CMD:-jdtls}"

if [ ! -f "$JAR_FILE" ]; then
    echo "Building project..."
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests
fi

if [ -z "$1" ]; then
    echo "Usage: $0 <workspace-path>"
    echo "Example: $0 /Users/stephan/IdeaProjects/callforpapers"
    exit 1
fi

WORKSPACE_PATH="$1"

exec java -jar "$JAR_FILE" "$WORKSPACE_PATH" "$JDTLS_CMD"
