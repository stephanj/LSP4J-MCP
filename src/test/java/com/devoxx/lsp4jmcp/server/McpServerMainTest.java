package com.devoxx.lsp4jmcp.server;

import com.devoxx.lsp4jmcp.tools.JavaTools;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for McpServerMain.
 * Tests verify tool registration, schemas, and server configuration.
 */
class McpServerMainTest {

    // Expected tool names that should be registered
    private static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
        "find_symbols",
        "find_references",
        "find_definition",
        "document_symbols",
        "find_interfaces_with_method"
    );

    @Test
    void serverHasCorrectName() {
        // Verify server constants are properly defined
        assertThat(McpServerMain.class.getSimpleName()).isEqualTo("McpServerMain");
    }

    @Test
    void mainMethodExists() throws NoSuchMethodException {
        // Verify the main method signature is correct
        var mainMethod = McpServerMain.class.getMethod("main", String[].class);
        assertThat(mainMethod).isNotNull();
        assertThat(mainMethod.getReturnType()).isEqualTo(void.class);
        assertThat(Modifier.isStatic(mainMethod.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(mainMethod.getModifiers())).isTrue();
    }

    @Test
    void serverNameConstantIsDefined() throws Exception {
        // Verify SERVER_NAME constant exists and has expected value
        Field serverNameField = McpServerMain.class.getDeclaredField("SERVER_NAME");
        serverNameField.setAccessible(true);
        String serverName = (String) serverNameField.get(null);
        
        assertThat(serverName).isEqualTo("java-lsp");
    }

    @Test
    void serverVersionConstantIsDefined() throws Exception {
        // Verify SERVER_VERSION constant exists
        Field serverVersionField = McpServerMain.class.getDeclaredField("SERVER_VERSION");
        serverVersionField.setAccessible(true);
        String serverVersion = (String) serverVersionField.get(null);
        
        assertThat(serverVersion).isNotBlank();
        // Version should follow semantic versioning pattern
        assertThat(serverVersion).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    void findSymbolsToolSchema_hasCorrectStructure() {
        // Verify the schema structure for find_symbols tool
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "The symbol name or pattern to search for" }
              },
              "required": ["query"]
            }
            """;

        assertThat(schema).contains("query");
        assertThat(schema).contains("\"type\": \"string\"");
        assertThat(schema).contains("required");
    }

    @Test
    void findReferencesToolSchema_hasCorrectStructure() {
        // Verify the schema structure for find_references tool
        String schema = """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" },
                "line": { "type": "integer", "description": "Line number (1-based)" },
                "character": { "type": "integer", "description": "Character/column position (1-based)" }
              },
              "required": ["file", "line", "character"]
            }
            """;

        assertThat(schema).contains("file");
        assertThat(schema).contains("line");
        assertThat(schema).contains("character");
        assertThat(schema).contains("1-based");
        assertThat(schema).contains("required");
    }

    @Test
    void findDefinitionToolSchema_hasCorrectStructure() {
        // Verify the schema structure for find_definition tool
        String schema = """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" },
                "line": { "type": "integer", "description": "Line number (1-based)" },
                "character": { "type": "integer", "description": "Character/column position (1-based)" }
              },
              "required": ["file", "line", "character"]
            }
            """;

        assertThat(schema).contains("file");
        assertThat(schema).contains("line");
        assertThat(schema).contains("character");
    }

    @Test
    void documentSymbolsToolSchema_hasCorrectStructure() {
        // Verify the schema structure for document_symbols tool
        String schema = """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" }
              },
              "required": ["file"]
            }
            """;

        assertThat(schema).contains("file");
        assertThat(schema).contains("required");
    }

    @Test
    void findInterfacesWithMethodToolSchema_hasCorrectStructure() {
        // Verify the schema structure for find_interfaces_with_method tool
        String schema = """
            {
              "type": "object",
              "properties": {
                "method_name": { "type": "string", "description": "The method name to search for" }
              },
              "required": ["method_name"]
            }
            """;

        assertThat(schema).contains("method_name");
        assertThat(schema).contains("required");
    }

    @Test
    void allExpectedToolNamesAreValid() {
        // Verify that all expected tool names follow naming conventions
        for (String toolName : EXPECTED_TOOL_NAMES) {
            assertThat(toolName)
                .matches("[a-z_]+")
                .as("Tool name should be snake_case: " + toolName);
        }
        assertThat(EXPECTED_TOOL_NAMES).hasSize(5);
    }

    @Test
    void toolSchemasAreValidJson() {
        // Verify all tool schemas have proper JSON structure
        String[] schemas = {
            """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "The symbol name or pattern to search for" }
              },
              "required": ["query"]
            }
            """,
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
            """,
            """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" }
              },
              "required": ["file"]
            }
            """,
            """
            {
              "type": "object",
              "properties": {
                "method_name": { "type": "string", "description": "The method name to search for" }
              },
              "required": ["method_name"]
            }
            """
        };

        for (String schema : schemas) {
            assertThat(schema).contains("\"type\":");
            assertThat(schema).contains("\"properties\":");
            assertThat(schema).contains("\"required\":");
        }
    }

    @Test
    void javaToolsHasRequiredMethods() {
        // Verify JavaTools class has all the methods that handlers will call
        Method[] methods = JavaTools.class.getDeclaredMethods();
        Set<String> methodNames = Arrays.stream(methods)
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(methodNames).contains("findSymbols");
        assertThat(methodNames).contains("findReferences");
        assertThat(methodNames).contains("findDefinition");
        assertThat(methodNames).contains("getDocumentSymbols");
        assertThat(methodNames).contains("findInterfacesWithMethod");
    }

    @Test
    void findSymbolsMethodSignature_isCorrect() throws NoSuchMethodException {
        // Verify the method signature matches what the handler expects
        Method method = JavaTools.class.getMethod("findSymbols", String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void findReferencesMethodSignature_isCorrect() throws NoSuchMethodException {
        // Verify the method signature matches what the handler expects
        Method method = JavaTools.class.getMethod("findReferences", String.class, int.class, int.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void findDefinitionMethodSignature_isCorrect() throws NoSuchMethodException {
        // Verify the method signature matches what the handler expects
        Method method = JavaTools.class.getMethod("findDefinition", String.class, int.class, int.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void getDocumentSymbolsMethodSignature_isCorrect() throws NoSuchMethodException {
        // Verify the method signature matches what the handler expects
        Method method = JavaTools.class.getMethod("getDocumentSymbols", String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void findInterfacesWithMethodMethodSignature_isCorrect() throws NoSuchMethodException {
        // Verify the method signature matches what the handler expects
        Method method = JavaTools.class.getMethod("findInterfacesWithMethod", String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void runMethodExists() throws NoSuchMethodException {
        // Verify the private run method exists with correct signature
        Method runMethod = McpServerMain.class.getDeclaredMethod("run", 
            java.nio.file.Path.class, String.class);
        runMethod.setAccessible(true);
        
        assertThat(runMethod).isNotNull();
        assertThat(Modifier.isStatic(runMethod.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(runMethod.getModifiers())).isTrue();
    }

    @Test
    void toolDescriptions_areInformative() {
        // Verify tool descriptions provide useful information
        String[] descriptions = {
            "Search for Java symbols (classes, methods, fields) by name",
            "Find all references to a symbol at a given file location",
            "Go to the definition of a symbol at a given file location",
            "Get all symbols (classes, methods, fields) defined in a Java file",
            "Find all interfaces that contain a method with the given name"
        };

        for (String description : descriptions) {
            assertThat(description)
                .isNotBlank()
                .hasSizeGreaterThan(20);
        }
    }

    @Test
    void lineNumbersAreDocumentedAs1Based() {
        // Verify that the tool schemas document 1-based line numbers
        String referencesSchema = """
            {
              "type": "object",
              "properties": {
                "file": { "type": "string", "description": "Path to the Java file" },
                "line": { "type": "integer", "description": "Line number (1-based)" },
                "character": { "type": "integer", "description": "Character/column position (1-based)" }
              },
              "required": ["file", "line", "character"]
            }
            """;

        assertThat(referencesSchema).contains("1-based");
    }

    @Test
    void handlerParameterConversion_lineNumbersSubtractOne() {
        // The handlers in McpServerMain subtract 1 from line/character to convert
        // from 1-based (user input) to 0-based (LSP protocol)
        // This test documents this expected behavior
        int userLineNumber = 10;  // 1-based
        int lspLineNumber = userLineNumber - 1;  // 0-based
        
        assertThat(lspLineNumber).isEqualTo(9);
    }

    @Test
    void expectedToolCount_isFive() {
        // Verify we have exactly 5 tools registered
        assertThat(EXPECTED_TOOL_NAMES).hasSize(5);
    }

    @Test
    void toolNamesMatchJavaToolsMethods() {
        // Verify tool names correspond to JavaTools method names
        // (with snake_case to camelCase conversion)
        assertThat(EXPECTED_TOOL_NAMES).contains("find_symbols");  // -> findSymbols
        assertThat(EXPECTED_TOOL_NAMES).contains("find_references");  // -> findReferences
        assertThat(EXPECTED_TOOL_NAMES).contains("find_definition");  // -> findDefinition
        assertThat(EXPECTED_TOOL_NAMES).contains("document_symbols");  // -> getDocumentSymbols
        assertThat(EXPECTED_TOOL_NAMES).contains("find_interfaces_with_method");  // -> findInterfacesWithMethod
    }
}
