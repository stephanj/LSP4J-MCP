# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LSP4J-MCP is a Java MCP (Model Context Protocol) server that wraps JDTLS (Eclipse JDT Language Server) using LSP4J to provide Java IDE features to AI assistants. It exposes LSP capabilities (symbol search, find references, go-to-definition) as MCP tools.

## Build and Run Commands

```bash
# Build shaded JAR
mvn clean package

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=JavaToolsTest

# Run a single test method
mvn test -Dtest=JavaToolsTest#testFindSymbols

# Run the server (requires JDTLS installed)
./run.sh /path/to/java/project
# or directly:
java -jar target/lsp4j-mcp-1.0.0-SNAPSHOT.jar <workspace-path> jdtls
```

## Architecture

```
Claude Code ──MCP──▶ McpServerMain ──LSP4J──▶ JDTLS subprocess ──▶ Java Codebase
```

**Three-layer design:**
1. **McpServerMain** - Entry point, MCP server setup, tool registration
2. **JavaTools** - Translates MCP tool calls to LSP operations, formats JSON responses
3. **JdtlsClient/JdtlsLanguageClient** - LSP4J client managing JDTLS subprocess

**Key design decisions:**
- JDTLS runs as subprocess with JSON-RPC over stdio
- JDTLS workspace data stored in `/tmp/jdtls-data/<hash>/` (must be outside analyzed workspace)
- Stdout reserved for MCP protocol; all logging goes to `$LOG_FILE` (default: `/tmp/lsp4j-mcp.log`)
- Synchronous MCP server (`McpSyncServer`) for simpler request handling
- 120-second timeout for JDTLS initialization and operations

**MCP Tools exposed:**
- `find_symbols` - Two-step search: workspace symbols + document symbols per file
- `find_references` - Find all references to symbol at file:line:character
- `find_definition` - Go to definition of symbol
- `document_symbols` - List all symbols in a file
- `find_interfaces_with_method` - Find classes/interfaces containing a method

## Testing

Uses JUnit 5 with AssertJ and Mockito. Integration test `JdtlsClientIntegrationTest` requires JDTLS to be installed.
