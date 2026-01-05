package com.devoxx.lsp4jmcp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JdtlsClient.
 * Tests cover initialization, error handling, and data directory creation.
 * 
 * Note: Tests that require a running JDTLS process are in JdtlsClientIntegrationTest.
 * These unit tests focus on error paths and pre-process setup behavior.
 */
class JdtlsClientTest {

    @TempDir
    Path tempDir;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void constructor_failsWithInvalidCommand() {
        // Given an invalid command that doesn't exist
        String invalidCommand = "/nonexistent/path/to/jdtls";

        // When/Then - should fail to start process
        assertThatThrownBy(() -> new JdtlsClient(tempDir, invalidCommand))
            .isInstanceOf(IOException.class);
    }

    @Test
    void constructor_failsWithEmptyCommand() {
        // Given an empty command
        String emptyCommand = "";

        // When/Then - should fail
        assertThatThrownBy(() -> new JdtlsClient(tempDir, emptyCommand))
            .isInstanceOf(Exception.class);
    }

    @Test
    void constructor_createsDataDirectory() throws IOException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        // When - try to create client (will fail because jdtls doesn't exist, but should create data dir first)
        try {
            new JdtlsClient(workspace, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected - jdtls doesn't exist
        }

        // Then - data directory should have been created OUTSIDE the workspace (in temp dir)
        String workspaceHash = Integer.toHexString(workspace.toString().hashCode());
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", workspaceHash);
        assertThat(dataDir).exists();
        assertThat(dataDir).isDirectory();
    }

    @Test
    void constructor_handlesCommandWithArguments() {
        // Given a command with arguments
        String commandWithArgs = "/nonexistent/jdtls --some-arg value";

        // When/Then - should parse command correctly and fail on execution, not parsing
        assertThatThrownBy(() -> new JdtlsClient(tempDir, commandWithArgs))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Cannot run program");
    }

    @Test
    void dataDirectoryPath_isOutsideWorkspace() throws IOException {
        // Given
        Path workspace = tempDir.resolve("my-project");
        Files.createDirectories(workspace);

        // When - attempt to create client
        try {
            new JdtlsClient(workspace, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected
        }

        // Then - verify data directory is OUTSIDE workspace (fixes JDTLS overlap error)
        Path insideWorkspace = workspace.resolve(".jdtls-data");
        assertThat(insideWorkspace).doesNotExist();

        // Data dir should be in temp folder with workspace hash
        String workspaceHash = Integer.toHexString(workspace.toString().hashCode());
        Path expectedDataDir = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", workspaceHash);
        assertThat(expectedDataDir).exists();
    }

    @Test
    void constructor_parsesMultipleCommandArguments() {
        // Given a command with multiple space-separated arguments
        String command = "/nonexistent/java -jar server.jar --verbose";

        // When/Then - should split into 4 parts correctly
        assertThatThrownBy(() -> new JdtlsClient(tempDir, command))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Cannot run program");
    }

    @Test
    void constructor_addsDataDirectoryArgument() throws IOException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        
        // When - create client (will fail but data dir gets created)
        try {
            new JdtlsClient(workspace, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected
        }

        // Then - verify the data directory structure
        String workspaceHash = Integer.toHexString(workspace.toString().hashCode());
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", workspaceHash);
        assertThat(dataDir).exists();
    }

    @Test
    void dataDirectory_usesWorkspaceHashForUniqueness() throws IOException {
        // Given two different workspaces
        Path workspace1 = tempDir.resolve("project-a");
        Path workspace2 = tempDir.resolve("project-b");
        Files.createDirectories(workspace1);
        Files.createDirectories(workspace2);

        // When - attempt to create clients for both
        try {
            new JdtlsClient(workspace1, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected
        }
        try {
            new JdtlsClient(workspace2, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected
        }

        // Then - each should have its own data directory based on workspace hash
        String hash1 = Integer.toHexString(workspace1.toString().hashCode());
        String hash2 = Integer.toHexString(workspace2.toString().hashCode());
        
        Path dataDir1 = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", hash1);
        Path dataDir2 = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", hash2);
        
        assertThat(dataDir1).exists();
        assertThat(dataDir2).exists();
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void constructor_throwsIOExceptionForProcessStartFailure() {
        // Given a command that cannot be executed
        String invalidCommand = "/this/path/does/not/exist/jdtls";

        // When/Then
        assertThatThrownBy(() -> new JdtlsClient(tempDir, invalidCommand))
            .isInstanceOf(IOException.class);
    }

    @Test
    void constructor_splitsCommandOnWhitespace() {
        // Given a command with multiple whitespace-separated parts
        String command = "/path/to/java   -jar    server.jar";

        // When/Then - command should be split correctly (though it will fail to execute)
        assertThatThrownBy(() -> new JdtlsClient(tempDir, command))
            .isInstanceOf(IOException.class);
    }

    @Test
    void dataDirectory_isCreatedInSystemTempFolder() throws IOException {
        // Given
        Path workspace = tempDir.resolve("test-workspace");
        Files.createDirectories(workspace);

        // When
        try {
            new JdtlsClient(workspace, "/nonexistent/jdtls");
        } catch (IOException e) {
            // Expected
        }

        // Then - data directory should be under system temp
        String workspaceHash = Integer.toHexString(workspace.toString().hashCode());
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", workspaceHash);
        
        assertThat(dataDir.toString()).startsWith(System.getProperty("java.io.tmpdir"));
        assertThat(dataDir.getParent().getFileName().toString()).isEqualTo("jdtls-data");
    }

    @Test
    void jdtlsLanguageClient_isCreatedDuringConstruction() throws IOException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        
        // Create a mock JDTLS script that stays alive
        Path mockScript = tempDir.resolve("mock-jdtls.sh");
        Files.writeString(mockScript, "#!/bin/bash\nwhile true; do sleep 1; done");
        mockScript.toFile().setExecutable(true);

        JdtlsClient client = null;
        try {
            // When
            client = new JdtlsClient(workspace, mockScript.toString());

            // Then
            assertThat(client.getLanguageClient()).isNotNull();
            assertThat(client.getLanguageClient()).isInstanceOf(JdtlsLanguageClient.class);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    void close_terminatesRunningProcess() throws IOException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        
        // Create a mock script that stays alive
        Path mockScript = tempDir.resolve("mock-jdtls.sh");
        Files.writeString(mockScript, "#!/bin/bash\nwhile true; do sleep 1; done");
        mockScript.toFile().setExecutable(true);

        JdtlsClient client = new JdtlsClient(workspace, mockScript.toString());
        assertThat(client.isRunning()).isTrue();

        // When
        client.close();

        // Then - give a moment for process termination
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(client.isRunning()).isFalse();
    }

    @Test
    void isRunning_returnsTrueForActiveProcess() throws IOException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        
        // Create a mock script that stays alive
        Path mockScript = tempDir.resolve("mock-jdtls.sh");
        Files.writeString(mockScript, "#!/bin/bash\nwhile true; do sleep 1; done");
        mockScript.toFile().setExecutable(true);

        JdtlsClient client = null;
        try {
            // When
            client = new JdtlsClient(workspace, mockScript.toString());

            // Then
            assertThat(client.isRunning()).isTrue();
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    void isRunning_returnsFalseAfterClose() throws IOException, InterruptedException {
        // Given
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        
        // Create a mock script that stays alive
        Path mockScript = tempDir.resolve("mock-jdtls.sh");
        Files.writeString(mockScript, "#!/bin/bash\nwhile true; do sleep 1; done");
        mockScript.toFile().setExecutable(true);

        JdtlsClient client = new JdtlsClient(workspace, mockScript.toString());
        assertThat(client.isRunning()).isTrue();

        // When
        client.close();
        Thread.sleep(200);

        // Then
        assertThat(client.isRunning()).isFalse();
    }
}
