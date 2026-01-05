package com.devoxx.lsp4jmcp.tools;

import com.devoxx.lsp4jmcp.client.JdtlsClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JavaTools.
 * Tests cover symbol search, reference finding, and result formatting.
 */
@ExtendWith(MockitoExtension.class)
class JavaToolsTest {

    @Mock
    private JdtlsClient jdtlsClient;

    private JavaTools javaTools;
    private Gson gson;

    @BeforeEach
    void setUp() {
        javaTools = new JavaTools(jdtlsClient, Path.of("/test/workspace"));
        gson = new Gson();
    }

    // ============================================
    // findSymbols tests
    // ============================================

    @Test
    void findSymbols_returnsMatchingWorkspaceSymbols() throws Exception {
        // Given - workspace symbol returns a class
        SymbolInformation classSymbol = new SymbolInformation(
            "KeywordRepository",
            SymbolKind.Class,
            new Location("file:///test/Repo.java", new Range(new Position(0, 0), new Position(100, 0))),
            "com.test"
        );
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("KeywordRepository");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        doReturn(List.of()).when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.findSymbols("KeywordRepository");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("query").getAsString()).isEqualTo("KeywordRepository");
        assertThat(json.get("count").getAsInt()).isGreaterThanOrEqualTo(1);
        
        JsonArray symbols = json.getAsJsonArray("symbols");
        assertThat(symbols.size()).isGreaterThanOrEqualTo(1);
        JsonObject firstSymbol = symbols.get(0).getAsJsonObject();
        assertThat(firstSymbol.get("name").getAsString()).isEqualTo("KeywordRepository");
        assertThat(firstSymbol.get("kind").getAsString()).isEqualTo("Class");
        assertThat(firstSymbol.get("container").getAsString()).isEqualTo("com.test");
    }

    @Test
    void findSymbols_returnsMatchingDocumentSymbols() throws Exception {
        // Given - workspace symbol returns a class
        SymbolInformation classSymbol = new SymbolInformation(
            "KeywordRepository",
            SymbolKind.Class,
            new Location("file:///test/Repo.java", new Range(new Position(0, 0), new Position(100, 0))),
            "com.test"
        );
        doReturn(List.of()).when(jdtlsClient).findWorkspaceSymbols("findByName");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        
        // Document symbols include a method matching the query
        DocumentSymbol methodSymbol = new DocumentSymbol();
        methodSymbol.setName("findByName");
        methodSymbol.setKind(SymbolKind.Method);
        methodSymbol.setRange(new Range(new Position(10, 4), new Position(10, 20)));
        methodSymbol.setSelectionRange(new Range(new Position(10, 4), new Position(10, 20)));
        methodSymbol.setDetail("String");
        
        doReturn(List.of(methodSymbol)).when(jdtlsClient).getDocumentSymbols("file:///test/Repo.java");

        // When
        String result = javaTools.findSymbols("findByName");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("query").getAsString()).isEqualTo("findByName");
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
        
        JsonArray symbols = json.getAsJsonArray("symbols");
        JsonObject methodResult = symbols.get(0).getAsJsonObject();
        assertThat(methodResult.get("name").getAsString()).isEqualTo("findByName");
        assertThat(methodResult.get("kind").getAsString()).isEqualTo("Method");
        assertThat(methodResult.get("line").getAsInt()).isEqualTo(11); // 1-based
    }

    @Test
    void findSymbols_searchesNestedDocumentSymbols() throws Exception {
        // Given - class with nested method
        SymbolInformation classSymbol = new SymbolInformation(
            "OuterClass",
            SymbolKind.Class,
            new Location("file:///test/Outer.java", new Range(new Position(0, 0), new Position(100, 0))),
            "com.test"
        );
        doReturn(List.of()).when(jdtlsClient).findWorkspaceSymbols("nestedMethod");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        
        // Create nested structure: Class -> InnerClass -> nestedMethod
        DocumentSymbol nestedMethod = new DocumentSymbol();
        nestedMethod.setName("nestedMethod");
        nestedMethod.setKind(SymbolKind.Method);
        nestedMethod.setRange(new Range(new Position(20, 8), new Position(25, 8)));
        nestedMethod.setSelectionRange(new Range(new Position(20, 8), new Position(20, 20)));
        
        DocumentSymbol innerClass = new DocumentSymbol();
        innerClass.setName("InnerClass");
        innerClass.setKind(SymbolKind.Class);
        innerClass.setRange(new Range(new Position(15, 4), new Position(30, 4)));
        innerClass.setSelectionRange(new Range(new Position(15, 4), new Position(15, 15)));
        innerClass.setChildren(List.of(nestedMethod));
        
        DocumentSymbol outerClass = new DocumentSymbol();
        outerClass.setName("OuterClass");
        outerClass.setKind(SymbolKind.Class);
        outerClass.setRange(new Range(new Position(0, 0), new Position(100, 0)));
        outerClass.setSelectionRange(new Range(new Position(0, 0), new Position(0, 10)));
        outerClass.setChildren(List.of(innerClass));
        
        doReturn(List.of(outerClass)).when(jdtlsClient).getDocumentSymbols("file:///test/Outer.java");

        // When
        String result = javaTools.findSymbols("nestedMethod");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
        
        JsonObject foundMethod = json.getAsJsonArray("symbols").get(0).getAsJsonObject();
        assertThat(foundMethod.get("name").getAsString()).isEqualTo("nestedMethod");
    }

    @Test
    void findSymbols_performsCaseInsensitiveSearch() throws Exception {
        // Given
        SymbolInformation classSymbol = new SymbolInformation(
            "MyClass",
            SymbolKind.Class,
            new Location("file:///test/MyClass.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        doReturn(List.of()).when(jdtlsClient).findWorkspaceSymbols("MYMETHOD");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        
        DocumentSymbol method = new DocumentSymbol();
        method.setName("myMethod");
        method.setKind(SymbolKind.Method);
        method.setRange(new Range(new Position(10, 4), new Position(15, 4)));
        method.setSelectionRange(new Range(new Position(10, 4), new Position(10, 12)));
        
        doReturn(List.of(method)).when(jdtlsClient).getDocumentSymbols("file:///test/MyClass.java");

        // When - search with uppercase
        String result = javaTools.findSymbols("MYMETHOD");

        // Then - should find the lowercase method
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
    }

    @Test
    void findSymbols_returnsEmptyListWhenNoMatches() throws Exception {
        // Given - no workspace symbols and no document symbols match
        doReturn(List.of()).when(jdtlsClient).findWorkspaceSymbols("nonexistent");
        doReturn(List.of()).when(jdtlsClient).findWorkspaceSymbols("*");

        // When
        String result = javaTools.findSymbols("nonexistent");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(0);
        assertThat(json.getAsJsonArray("symbols")).isEmpty();
    }

    @Test
    void findSymbols_handlesExceptionGracefully() throws Exception {
        // Given
        doThrow(new RuntimeException("Connection failed"))
            .when(jdtlsClient).findWorkspaceSymbols(anyString());

        // When
        String result = javaTools.findSymbols("test");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").getAsString()).contains("Connection failed");
    }

    @Test
    void findSymbols_handlesDocumentSymbolsException() throws Exception {
        // Given - workspace symbols work but document symbols fail for one file
        SymbolInformation classSymbol = new SymbolInformation(
            "MyClass",
            SymbolKind.Class,
            new Location("file:///test/MyClass.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("test");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        doThrow(new RuntimeException("Document error"))
            .when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.findSymbols("test");

        // Then - should still return workspace symbols, not fail completely
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.has("error")).isFalse();
        assertThat(json.get("count").getAsInt()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void findSymbols_formatsLineNumbersAs1Based() throws Exception {
        // Given
        SymbolInformation classSymbol = new SymbolInformation(
            "TestClass",
            SymbolKind.Class,
            new Location("file:///test/Test.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("TestClass");
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        doReturn(List.of()).when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.findSymbols("TestClass");

        // Then - line should be 1 (0+1), column should be 1 (0+1)
        JsonObject json = gson.fromJson(result, JsonObject.class);
        JsonObject symbol = json.getAsJsonArray("symbols").get(0).getAsJsonObject();
        assertThat(symbol.get("line").getAsInt()).isEqualTo(1);
        assertThat(symbol.get("column").getAsInt()).isEqualTo(1);
    }

    // ============================================
    // findReferences tests
    // ============================================

    @Test
    void findReferences_returnsLocations() throws Exception {
        // Given
        Location ref1 = new Location("file:///test/A.java", new Range(new Position(5, 10), new Position(5, 20)));
        Location ref2 = new Location("file:///test/B.java", new Range(new Position(15, 5), new Position(15, 15)));
        doReturn(List.of(ref1, ref2))
            .when(jdtlsClient).findReferences("file:///test/workspace/Test.java", 10, 5);

        // When
        String result = javaTools.findReferences("Test.java", 10, 5);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(2);
        assertThat(json.getAsJsonArray("references")).hasSize(2);
    }

    @Test
    void findReferences_formatsLocationResultsCorrectly() throws Exception {
        // Given
        Location ref = new Location(
            "file:///test/Service.java",
            new Range(new Position(10, 5), new Position(10, 25))
        );
        doReturn(List.of(ref))
            .when(jdtlsClient).findReferences("file:///test/workspace/Test.java", 5, 10);

        // When
        String result = javaTools.findReferences("Test.java", 5, 10);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        JsonObject reference = json.getAsJsonArray("references").get(0).getAsJsonObject();
        
        // Verify file path has file:// prefix stripped
        assertThat(reference.get("file").getAsString()).isEqualTo("/test/Service.java");
        // Verify 1-based line numbers
        assertThat(reference.get("startLine").getAsInt()).isEqualTo(11);
        assertThat(reference.get("startColumn").getAsInt()).isEqualTo(6);
        assertThat(reference.get("endLine").getAsInt()).isEqualTo(11);
        assertThat(reference.get("endColumn").getAsInt()).isEqualTo(26);
    }

    @Test
    void findReferences_includesRequestLocationInResponse() throws Exception {
        // Given
        doReturn(List.of())
            .when(jdtlsClient).findReferences(anyString(), anyInt(), anyInt());

        // When
        String result = javaTools.findReferences("MyFile.java", 42, 15);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("file").getAsString()).isEqualTo("MyFile.java");
        assertThat(json.get("line").getAsInt()).isEqualTo(42);
        assertThat(json.get("character").getAsInt()).isEqualTo(15);
    }

    @Test
    void findReferences_returnsEmptyListWhenNoReferences() throws Exception {
        // Given
        doReturn(List.of())
            .when(jdtlsClient).findReferences(anyString(), anyInt(), anyInt());

        // When
        String result = javaTools.findReferences("Test.java", 10, 5);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(0);
        assertThat(json.getAsJsonArray("references")).isEmpty();
    }

    @Test
    void findReferences_handlesExceptionGracefully() throws Exception {
        // Given
        doThrow(new ExecutionException("Timeout", new TimeoutException()))
            .when(jdtlsClient).findReferences(anyString(), anyInt(), anyInt());

        // When
        String result = javaTools.findReferences("Test.java", 10, 5);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.has("error")).isTrue();
    }

    @Test
    void findReferences_resolvesRelativePathToAbsolute() throws Exception {
        // Given - relative path "Test.java" should be resolved against workspace root
        doReturn(List.of())
            .when(jdtlsClient).findReferences(eq("file:///test/workspace/Test.java"), anyInt(), anyInt());

        // When
        javaTools.findReferences("Test.java", 10, 5);

        // Then - verify the URI was correctly formed
        verify(jdtlsClient).findReferences(eq("file:///test/workspace/Test.java"), eq(10), eq(5));
    }

    @Test
    void findReferences_handlesAbsolutePath() throws Exception {
        // Given - absolute path should be used as-is
        doReturn(List.of())
            .when(jdtlsClient).findReferences(eq("file:///absolute/path/Test.java"), anyInt(), anyInt());

        // When
        javaTools.findReferences("/absolute/path/Test.java", 10, 5);

        // Then
        verify(jdtlsClient).findReferences(eq("file:///absolute/path/Test.java"), eq(10), eq(5));
    }

    // ============================================
    // findDefinition tests
    // ============================================

    @Test
    void findDefinition_returnsDefinitionLocation() throws Exception {
        // Given
        Location definition = new Location(
            "file:///test/Interface.java",
            new Range(new Position(20, 4), new Position(20, 30))
        );
        doReturn(List.of(definition))
            .when(jdtlsClient).findDefinition("file:///test/workspace/Impl.java", 50, 15);

        // When
        String result = javaTools.findDefinition("Impl.java", 50, 15);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.getAsJsonArray("definitions")).hasSize(1);
    }

    @Test
    void findDefinition_handlesMultipleDefinitions() throws Exception {
        // Given - sometimes there can be multiple definitions (e.g., overloaded methods)
        Location def1 = new Location("file:///test/A.java", new Range(new Position(10, 0), new Position(10, 20)));
        Location def2 = new Location("file:///test/B.java", new Range(new Position(20, 0), new Position(20, 20)));
        doReturn(List.of(def1, def2))
            .when(jdtlsClient).findDefinition(anyString(), anyInt(), anyInt());

        // When
        String result = javaTools.findDefinition("Test.java", 5, 10);

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.getAsJsonArray("definitions")).hasSize(2);
    }

    // ============================================
    // getDocumentSymbols tests
    // ============================================

    @Test
    void getDocumentSymbols_returnsSymbolsInDocument() throws Exception {
        // Given
        DocumentSymbol classSymbol = new DocumentSymbol();
        classSymbol.setName("MyClass");
        classSymbol.setKind(SymbolKind.Class);
        classSymbol.setRange(new Range(new Position(0, 0), new Position(100, 0)));
        classSymbol.setSelectionRange(new Range(new Position(0, 0), new Position(0, 10)));

        DocumentSymbol methodSymbol = new DocumentSymbol();
        methodSymbol.setName("myMethod");
        methodSymbol.setKind(SymbolKind.Method);
        methodSymbol.setRange(new Range(new Position(10, 0), new Position(20, 0)));
        methodSymbol.setSelectionRange(new Range(new Position(10, 0), new Position(10, 15)));

        doReturn(List.of(classSymbol, methodSymbol))
            .when(jdtlsClient).getDocumentSymbols("file:///test/workspace/MyClass.java");

        // When
        String result = javaTools.getDocumentSymbols("MyClass.java");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(2);
        assertThat(json.getAsJsonArray("symbols")).hasSize(2);
    }

    @Test
    void getDocumentSymbols_formatsDocumentSymbolResult() throws Exception {
        // Given
        DocumentSymbol symbol = new DocumentSymbol();
        symbol.setName("calculateTotal");
        symbol.setKind(SymbolKind.Method);
        symbol.setDetail("double");
        symbol.setRange(new Range(new Position(15, 4), new Position(25, 4)));
        symbol.setSelectionRange(new Range(new Position(15, 4), new Position(15, 18)));

        doReturn(List.of(symbol))
            .when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.getDocumentSymbols("Test.java");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        JsonObject symbolResult = json.getAsJsonArray("symbols").get(0).getAsJsonObject();
        assertThat(symbolResult.get("name").getAsString()).isEqualTo("calculateTotal");
        assertThat(symbolResult.get("kind").getAsString()).isEqualTo("Method");
        assertThat(symbolResult.get("detail").getAsString()).isEqualTo("double");
        assertThat(symbolResult.get("startLine").getAsInt()).isEqualTo(16); // 1-based
        assertThat(symbolResult.get("endLine").getAsInt()).isEqualTo(26); // 1-based
    }

    // ============================================
    // findInterfacesWithMethod tests
    // ============================================

    @Test
    void findInterfacesWithMethod_filtersToMethods() throws Exception {
        // Given - workspace symbols returns classes
        SymbolInformation repoClass = new SymbolInformation(
            "KeywordRepository",
            SymbolKind.Interface,
            new Location("file:///test/Repo.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        SymbolInformation helperClass = new SymbolInformation(
            "FindByNameHelper",
            SymbolKind.Class,
            new Location("file:///test/Helper.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        doReturn(List.of(repoClass, helperClass))
            .when(jdtlsClient).findWorkspaceSymbols("*");
        
        // Document symbols for Repo.java include a matching method
        DocumentSymbol findByNameMethod = new DocumentSymbol();
        findByNameMethod.setName("findByName");
        findByNameMethod.setKind(SymbolKind.Method);
        findByNameMethod.setRange(new Range(new Position(10, 4), new Position(10, 20)));
        findByNameMethod.setSelectionRange(new Range(new Position(10, 4), new Position(10, 20)));
        doReturn(List.of(findByNameMethod))
            .when(jdtlsClient).getDocumentSymbols("file:///test/Repo.java");
        
        // Document symbols for Helper.java has no matching method
        DocumentSymbol otherMethod = new DocumentSymbol();
        otherMethod.setName("helperMethod");
        otherMethod.setKind(SymbolKind.Method);
        otherMethod.setRange(new Range(new Position(5, 0), new Position(10, 0)));
        otherMethod.setSelectionRange(new Range(new Position(5, 0), new Position(5, 15)));
        doReturn(List.of(otherMethod))
            .when(jdtlsClient).getDocumentSymbols("file:///test/Helper.java");

        // When
        String result = javaTools.findInterfacesWithMethod("findByName");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("methodName").getAsString()).isEqualTo("findByName");
        // Only the method from KeywordRepository should be included
        assertThat(json.get("count").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonArray("methods").get(0).getAsJsonObject().get("name").getAsString())
            .isEqualTo("findByName");
    }

    @Test
    void findInterfacesWithMethod_excludesNonMethodSymbols() throws Exception {
        // Given
        SymbolInformation classSymbol = new SymbolInformation(
            "MyClass",
            SymbolKind.Class,
            new Location("file:///test/MyClass.java", new Range(new Position(0, 0), new Position(50, 0))),
            "com.test"
        );
        doReturn(List.of(classSymbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        
        // Document has a field named "test" (not a method)
        DocumentSymbol field = new DocumentSymbol();
        field.setName("testField");
        field.setKind(SymbolKind.Field);
        field.setRange(new Range(new Position(5, 4), new Position(5, 20)));
        field.setSelectionRange(new Range(new Position(5, 4), new Position(5, 14)));
        doReturn(List.of(field)).when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.findInterfacesWithMethod("test");

        // Then - should not include the field
        JsonObject json = gson.fromJson(result, JsonObject.class);
        assertThat(json.get("count").getAsInt()).isEqualTo(0);
    }

    // ============================================
    // URI/Path conversion tests
    // ============================================

    @Test
    void findSymbols_stripsFileProtocolFromUri() throws Exception {
        // Given
        SymbolInformation symbol = new SymbolInformation(
            "TestClass",
            SymbolKind.Class,
            new Location("file:///test/path/TestClass.java", new Range(new Position(0, 0), new Position(10, 0))),
            "com.test"
        );
        doReturn(List.of(symbol)).when(jdtlsClient).findWorkspaceSymbols("TestClass");
        doReturn(List.of(symbol)).when(jdtlsClient).findWorkspaceSymbols("*");
        doReturn(List.of()).when(jdtlsClient).getDocumentSymbols(anyString());

        // When
        String result = javaTools.findSymbols("TestClass");

        // Then
        JsonObject json = gson.fromJson(result, JsonObject.class);
        JsonObject symbolResult = json.getAsJsonArray("symbols").get(0).getAsJsonObject();
        assertThat(symbolResult.get("file").getAsString()).isEqualTo("/test/path/TestClass.java");
    }
}
