package com.devoxx.lsp4jmcp.client;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for JdtlsLanguageClient to ensure all required LSP methods are implemented
 * and don't throw UnsupportedOperationException.
 */
class JdtlsLanguageClientTest {

    private JdtlsLanguageClient client;

    @BeforeEach
    void setUp() {
        client = new JdtlsLanguageClient();
    }

    @Test
    void registerCapability_doesNotThrow() throws ExecutionException, InterruptedException {
        // Given - JDTLS sends capability registrations during startup
        Registration registration = new Registration("1", "textDocument/completion", null);
        RegistrationParams params = new RegistrationParams(List.of(registration));

        // When/Then - should not throw UnsupportedOperationException
        assertThatCode(() -> client.registerCapability(params).get())
            .doesNotThrowAnyException();
    }

    @Test
    void registerCapability_returnsCompletedFuture() throws ExecutionException, InterruptedException {
        // Given
        Registration registration = new Registration("test-id", "workspace/symbol", null);
        RegistrationParams params = new RegistrationParams(List.of(registration));

        // When
        var result = client.registerCapability(params);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isDone()).isTrue();
        assertThat(result.get()).isNull(); // Void future
    }

    @Test
    void unregisterCapability_doesNotThrow() throws ExecutionException, InterruptedException {
        // Given
        Unregistration unregistration = new Unregistration("1", "textDocument/completion");
        UnregistrationParams params = new UnregistrationParams(List.of(unregistration));

        // When/Then
        assertThatCode(() -> client.unregisterCapability(params).get())
            .doesNotThrowAnyException();
    }

    @Test
    void workspaceFolders_returnsEmptyList() throws ExecutionException, InterruptedException {
        // When
        var result = client.workspaceFolders().get();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void configuration_returnsNullForEachItem() throws ExecutionException, InterruptedException {
        // Given - JDTLS requests configuration for various settings
        ConfigurationItem item1 = new ConfigurationItem();
        item1.setSection("java.configuration");
        ConfigurationItem item2 = new ConfigurationItem();
        item2.setSection("java.format");
        ConfigurationParams params = new ConfigurationParams(List.of(item1, item2));

        // When
        var result = client.configuration(params).get();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isNull();
        assertThat(result.get(1)).isNull();
    }

    @Test
    void applyEdit_returnsFalseResponse() throws ExecutionException, InterruptedException {
        // Given
        ApplyWorkspaceEditParams params = new ApplyWorkspaceEditParams(new WorkspaceEdit());

        // When
        var result = client.applyEdit(params).get();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isApplied()).isFalse();
    }

    @Test
    void createProgress_doesNotThrow() throws ExecutionException, InterruptedException {
        // Given
        WorkDoneProgressCreateParams params = new WorkDoneProgressCreateParams();
        params.setToken("progress-token");

        // When/Then
        assertThatCode(() -> client.createProgress(params).get())
            .doesNotThrowAnyException();
    }

    @Test
    void notifyProgress_doesNotThrow() {
        // Given
        ProgressParams params = new ProgressParams();
        params.setToken("progress-token");
        params.setValue(Either.forLeft(new WorkDoneProgressBegin()));

        // When/Then
        assertThatCode(() -> client.notifyProgress(params))
            .doesNotThrowAnyException();
    }

    @Test
    void showDocument_returnsFalseResult() throws ExecutionException, InterruptedException {
        // Given
        ShowDocumentParams params = new ShowDocumentParams("file:///test.java");

        // When
        var result = client.showDocument(params).get();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void refreshSemanticTokens_doesNotThrow() throws ExecutionException, InterruptedException {
        assertThatCode(() -> client.refreshSemanticTokens().get())
            .doesNotThrowAnyException();
    }

    @Test
    void refreshCodeLenses_doesNotThrow() throws ExecutionException, InterruptedException {
        assertThatCode(() -> client.refreshCodeLenses().get())
            .doesNotThrowAnyException();
    }

    @Test
    void refreshInlayHints_doesNotThrow() throws ExecutionException, InterruptedException {
        assertThatCode(() -> client.refreshInlayHints().get())
            .doesNotThrowAnyException();
    }

    @Test
    void refreshInlineValues_doesNotThrow() throws ExecutionException, InterruptedException {
        assertThatCode(() -> client.refreshInlineValues().get())
            .doesNotThrowAnyException();
    }

    @Test
    void refreshDiagnostics_doesNotThrow() throws ExecutionException, InterruptedException {
        assertThatCode(() -> client.refreshDiagnostics().get())
            .doesNotThrowAnyException();
    }

    @Test
    void publishDiagnostics_doesNotThrow() {
        // Given
        PublishDiagnosticsParams params = new PublishDiagnosticsParams(
            "file:///test.java",
            List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 10)), "Test error"))
        );

        // When/Then
        assertThatCode(() -> client.publishDiagnostics(params))
            .doesNotThrowAnyException();
    }

    @Test
    void showMessage_doesNotThrow() {
        // Given
        MessageParams params = new MessageParams(MessageType.Info, "Test message");

        // When/Then
        assertThatCode(() -> client.showMessage(params))
            .doesNotThrowAnyException();
    }

    @Test
    void showMessageRequest_returnsNull() throws ExecutionException, InterruptedException {
        // Given
        ShowMessageRequestParams params = new ShowMessageRequestParams();
        params.setType(MessageType.Info);
        params.setMessage("Test request");

        // When
        var result = client.showMessageRequest(params).get();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void logMessage_handlesAllMessageTypes() {
        // When/Then - verify all message types are handled without throwing
        assertThatCode(() -> client.logMessage(new MessageParams(MessageType.Error, "Error")))
            .doesNotThrowAnyException();
        assertThatCode(() -> client.logMessage(new MessageParams(MessageType.Warning, "Warning")))
            .doesNotThrowAnyException();
        assertThatCode(() -> client.logMessage(new MessageParams(MessageType.Info, "Info")))
            .doesNotThrowAnyException();
        assertThatCode(() -> client.logMessage(new MessageParams(MessageType.Log, "Log")))
            .doesNotThrowAnyException();
    }

    @Test
    void telemetryEvent_doesNotThrow() {
        // When/Then
        assertThatCode(() -> client.telemetryEvent("test event"))
            .doesNotThrowAnyException();
    }
}
