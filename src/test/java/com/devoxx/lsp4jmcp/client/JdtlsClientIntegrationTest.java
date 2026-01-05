package com.devoxx.lsp4jmcp.client;

import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JdtlsClient that require a running JDTLS instance.
 *
 * These tests verify that the LSP client can find symbols in the actual codebase.
 *
 * To run these tests, set the JDTLS_PATH environment variable to the jdtls command.
 * Example: JDTLS_PATH=jdtls mvn test -Dtest=JdtlsClientIntegrationTest
 */
@EnabledIfEnvironmentVariable(named = "JDTLS_PATH", matches = ".+")
class JdtlsClientIntegrationTest {

    private static JdtlsClient client;
    private static Path workspaceRoot;

    @BeforeAll
    static void setUp() throws Exception {
        String jdtlsCommand = System.getenv("JDTLS_PATH");
        workspaceRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        System.out.println("Starting JDTLS integration test");
        System.out.println("Workspace: " + workspaceRoot);
        System.out.println("JDTLS command: " + jdtlsCommand);

        client = new JdtlsClient(workspaceRoot, jdtlsCommand);
        client.initialize();

        // Wait for JDTLS to report ready status
        System.out.println("Waiting for JDTLS to report ready status...");
        boolean ready = client.getLanguageClient().waitForReady(60, TimeUnit.SECONDS);
        System.out.println("JDTLS ready: " + ready);
        System.out.println("Current status: " + client.getLanguageClient().getCurrentStatus());
        
        // Give a bit more time for symbol indexing after ready
        if (ready) {
            System.out.println("Waiting additional time for symbol indexing...");
            Thread.sleep(2000); // Wait 2 seconds after ready
        } else {
            System.out.println("JDTLS did not report ready in time, waiting anyway...");
            Thread.sleep(15000); // Wait 15 seconds if not ready
        }
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void findSymbols_shouldFindJdtlsClientClass() throws Exception {
        // JDTLS workspace symbol search returns CLASS-level symbols, not methods
        // So we search for the JdtlsClient class instead of methods
        
        // When - search for the JdtlsClient class
        List<? extends SymbolInformation> symbols = client.findWorkspaceSymbols("JdtlsClient");
        
        System.out.println("Found " + symbols.size() + " symbols for 'JdtlsClient'");
        for (SymbolInformation symbol : symbols) {
            System.out.println("  - " + symbol.getName() + 
                " (kind: " + symbol.getKind() + 
                ", container: " + symbol.getContainerName() + ")");
        }

        // Then - should find the JdtlsClient class
        assertThat(symbols)
            .isNotEmpty()
            .as("Should find JdtlsClient class");

        // Verify we found a class named "JdtlsClient"
        boolean foundJdtlsClient = symbols.stream()
            .anyMatch(s -> s.getName().equals("JdtlsClient") && s.getKind() == SymbolKind.Class);

        assertThat(foundJdtlsClient)
            .isTrue()
            .as("Should find a class named 'JdtlsClient'");
    }

    @Test
    void findSymbols_shouldFindJdtlsClientInCorrectLocation() throws Exception {
        // JDTLS workspace symbol search returns CLASS-level symbols
        // We verify that the JdtlsClient class is found with correct location
        
        // When - search for the JdtlsClient class
        List<? extends SymbolInformation> symbols = client.findWorkspaceSymbols("JdtlsClient");

        // Then - should find the JdtlsClient class with correct location
        SymbolInformation jdtlsClientClass = symbols.stream()
            .filter(s -> s.getName().equals("JdtlsClient"))
            .filter(s -> s.getKind() == SymbolKind.Class)
            .findFirst()
            .orElse(null);

        assertThat(jdtlsClientClass)
            .isNotNull()
            .as("Should find 'JdtlsClient' class");

        assertThat(jdtlsClientClass.getLocation().getUri())
            .contains("JdtlsClient.java")
            .as("The JdtlsClient class should be located in JdtlsClient.java");
    }

    @Test
    void findSymbols_shouldReturnCorrectSymbolKind() throws Exception {
        // When - search for classes using wildcard
        List<? extends SymbolInformation> symbols = client.findWorkspaceSymbols("*");

        // Then - filter to only classes
        List<? extends SymbolInformation> classes = symbols.stream()
            .filter(s -> s.getKind() == SymbolKind.Class)
            .toList();

        System.out.println("Found " + classes.size() + " classes");

        assertThat(classes)
            .isNotEmpty()
            .as("Should find at least one class symbol");

        // All filtered results should be classes
        assertThat(classes)
            .allMatch(s -> s.getKind() == SymbolKind.Class);
    }
}
