# LSP4J-MCP Server

A Java MCP (Model Context Protocol) server that wraps JDTLS (Eclipse JDT Language Server) using LSP4J to provide Java IDE features to AI assistants like Claude.

## Features

This MCP server exposes the following tools:

| Tool | Description |
|------|-------------|
| `find_symbols` | Search for Java symbols (classes, methods, fields) by name |
| `find_references` | Find all references to a symbol at a given file location |
| `find_definition` | Go to the definition of a symbol |
| `document_symbols` | Get all symbols defined in a Java file |
| `find_interfaces_with_method` | Find all interfaces containing a method with a given name |

## Prerequisites

- Java 21+
- Maven 3.8+
- JDTLS installed (e.g., via Homebrew: `brew install jdtls`)

## Build

```bash
mvn clean package
```

This creates a shaded JAR at `target/lsp4j-mcp-1.0.0-SNAPSHOT.jar`.

## Usage

### Option 1: Run Script

```bash
./run.sh /path/to/your/java/project
```

### Option 2: Direct Java Command

```bash
java -jar target/lsp4j-mcp-1.0.0-SNAPSHOT.jar <workspace-path> <jdtls-command>
```

Example:
```bash
java -jar target/lsp4j-mcp-1.0.0-SNAPSHOT.jar /Users/me/projects/myapp jdtls
```

### Option 3: Claude Code MCP Configuration

Add to your `.mcp.json`:

```json
{
  "mcpServers": {
    "java-lsp": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/LSP4J-MCP/target/lsp4j-mcp-1.0.0-SNAPSHOT.jar",
        "/path/to/your/java/project",
        "jdtls"
      ],
      "env": {
        "LOG_FILE": "/tmp/lsp4j-mcp.log"
      }
    }
  }
}
```

## Project Structure

```
LSP4J-MCP/
├── pom.xml                              # Maven build configuration
├── run.sh                               # Startup script
├── mcp-config.json                      # Example MCP configuration
├── src/
│   ├── main/
│   │   ├── java/com/devoxx/lsp4jmcp/
│   │   │   ├── client/
│   │   │   │   ├── JdtlsClient.java         # LSP client for JDTLS
│   │   │   │   └── JdtlsLanguageClient.java # LSP callback handler
│   │   │   ├── tools/
│   │   │   │   └── JavaTools.java           # Tool implementations
│   │   │   └── server/
│   │   │       └── McpServerMain.java       # MCP server entry point
│   │   └── resources/
│   │       └── logback.xml                  # Logging configuration
│   └── test/
│       └── java/com/devoxx/lsp4jmcp/
│           ├── client/JdtlsClientTest.java
│           ├── tools/JavaToolsTest.java
│           └── server/McpServerMainTest.java
└── README.md
```

## Testing

Run all tests:
```bash
mvn test
```

## Dependencies

- [LSP4J](https://github.com/eclipse-lsp4j/lsp4j) - Eclipse Language Server Protocol for Java
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) - Model Context Protocol SDK
- [JDTLS](https://github.com/eclipse-jdtls/eclipse.jdt.ls) - Eclipse JDT Language Server

## Example Prompts

Once the MCP server is configured, you can ask your AI assistant questions that will trigger the LSP tools. Here are some examples:

### Finding Symbols

> "Find all classes named Repository in this project"

> "Search for methods containing 'save' in their name"

> "Where is the UserService class defined?"

> "List all interfaces in this codebase"

### Finding References

> "Find all usages of the `processOrder` method at line 45 in OrderService.java"

> "Where is the `CustomerRepository` interface used throughout the codebase?"

> "Show me all places that call the constructor at line 12 of PaymentGateway.java"

### Go to Definition

> "What is the definition of the method being called at line 78, column 15 in CheckoutController.java?"

> "Take me to the implementation of the interface method at line 23 in MyService.java"

### Document Symbols

> "List all methods and fields in the Customer.java file"

> "What symbols are defined in src/main/java/com/example/service/OrderService.java?"

> "Give me an overview of the structure of ConfigurationManager.java"

### Finding Interface Methods

> "Which interfaces define a method called 'findById'?"

> "Find all classes that have a method named 'process'"

> "Show me all interfaces containing the 'validate' method"

### Complex Queries

> "I need to understand how the `authenticate` method works. First find its definition, then show me all places it's called."

> "Refactor help: Find all usages of the deprecated `legacyProcess` method so I can update them"

> "I want to implement a new Repository interface. Show me all existing Repository interfaces and their methods."

## Logging

Logs are written to the file specified by the `LOG_FILE` environment variable (default: `/tmp/lsp4j-mcp.log`).

Note: stdout is reserved for MCP protocol communication, so all logging goes to file.

## Architecture

```
Claude Code ──MCP──▶ McpServerMain ──LSP4J──▶ JDTLS ──▶ Java Codebase
                         │
                         ▼
                    JavaTools
                    (find_symbols, find_references, etc.)
```

The server:
1. Starts JDTLS as a subprocess
2. Connects via LSP4J (JSON-RPC over stdio)
3. Exposes LSP features as MCP tools
4. Communicates with Claude Code via MCP protocol

## License

MIT
