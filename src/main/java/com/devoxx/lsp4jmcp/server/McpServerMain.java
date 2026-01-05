package com.devoxx.lsp4jmcp.server;

import com.devoxx.lsp4jmcp.client.JdtlsClient;
import com.devoxx.lsp4jmcp.tools.JavaTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * MCP Server that provides Java IDE features via JDTLS.
 *
 * Usage: java -jar lsp4j-mcp.jar <workspace-path> <jdtls-command>
 */
public class McpServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(McpServerMain.class);

    private static final String SERVER_NAME = "java-lsp";
    private static final String SERVER_VERSION = "1.0.0";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar lsp4j-mcp.jar <workspace-path> <jdtls-command>");
            System.err.println("Example: java -jar lsp4j-mcp.jar /path/to/project jdtls");
            System.exit(1);
        }

        Path workspacePath = Path.of(args[0]).toAbsolutePath();
        String jdtlsCommand = args[1];

        LOG.info("Starting Java LSP MCP Server");
        LOG.info("Workspace: {}", workspacePath);
        LOG.info("JDTLS command: {}", jdtlsCommand);

        try {
            run(workspacePath, jdtlsCommand);
        } catch (Exception e) {
            LOG.error("Fatal error", e);
            System.exit(1);
        }
    }

    private static void run(Path workspacePath, String jdtlsCommand) throws Exception {
        // Start JDTLS client
        JdtlsClient jdtlsClient = new JdtlsClient(workspacePath, jdtlsCommand);
        jdtlsClient.initialize();

        // Create tools wrapper
        JavaTools javaTools = new JavaTools(jdtlsClient, workspacePath);

        // Create transport provider
        var transportProvider = new StdioServerTransportProvider(new ObjectMapper());

        // Define tools
        Tool findSymbolsTool = new Tool("find_symbols",
            "Search for Java symbols (classes, methods, fields) by name",
            """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "The symbol name or pattern to search for" }
              },
              "required": ["query"]
            }
            """);

        Tool findReferencesTool = new Tool("find_references",
            "Find all references to a symbol at a given file location",
            """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" },
                "line": { "type": "integer", "description": "Line number (1-based)" },
                "character": { "type": "integer", "description": "Character/column position (1-based)" }
              },
              "required": ["file", "line", "character"]
            }
            """);

        Tool findDefinitionTool = new Tool("find_definition",
            "Go to the definition of a symbol at a given file location",
            """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" },
                "line": { "type": "integer", "description": "Line number (1-based)" },
                "character": { "type": "integer", "description": "Character/column position (1-based)" }
              },
              "required": ["file", "line", "character"]
            }
            """);

        Tool documentSymbolsTool = new Tool("document_symbols",
            "Get all symbols (classes, methods, fields) defined in a Java file",
            """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" }
              },
              "required": ["file"]
            }
            """);

        Tool findInterfacesWithMethodTool = new Tool("find_interfaces_with_method",
            "Find all interfaces that contain a method with the given name",
            """
            {
              "type": "object",
              "properties": {
                "method_name": { "type": "string", "description": "The method name to search for" }
              },
              "required": ["method_name"]
            }
            """);

        // Create and configure server with tools and handlers
        McpSyncServer server = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(ServerCapabilities.builder()
                .tools(true)
                .build())
            .tool(findSymbolsTool, (exchange, args) ->
                CallToolResult.builder()
                    .addTextContent(javaTools.findSymbols((String) args.get("query")))
                    .build())
            .tool(findReferencesTool, (exchange, args) -> {
                String file = (String) args.get("file");
                int line = ((Number) args.get("line")).intValue() - 1;
                int character = ((Number) args.get("character")).intValue() - 1;
                return CallToolResult.builder()
                    .addTextContent(javaTools.findReferences(file, line, character))
                    .build();
            })
            .tool(findDefinitionTool, (exchange, args) -> {
                String file = (String) args.get("file");
                int line = ((Number) args.get("line")).intValue() - 1;
                int character = ((Number) args.get("character")).intValue() - 1;
                return CallToolResult.builder()
                    .addTextContent(javaTools.findDefinition(file, line, character))
                    .build();
            })
            .tool(documentSymbolsTool, (exchange, args) ->
                CallToolResult.builder()
                    .addTextContent(javaTools.getDocumentSymbols((String) args.get("file")))
                    .build())
            .tool(findInterfacesWithMethodTool, (exchange, args) ->
                CallToolResult.builder()
                    .addTextContent(javaTools.findInterfacesWithMethod((String) args.get("method_name")))
                    .build())
            .build();

        LOG.info("MCP Server started with 5 tools");

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            jdtlsClient.close();
            try {
                server.close();
            } catch (Exception e) {
                LOG.warn("Error closing server", e);
            }
        }));

        // Block until interrupted
        Thread.currentThread().join();
    }
}
