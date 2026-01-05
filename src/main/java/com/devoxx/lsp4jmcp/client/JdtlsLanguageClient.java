package com.devoxx.lsp4jmcp.client;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Language client implementation that receives callbacks from JDTLS.
 * Implements all required methods to avoid UnsupportedOperationExceptions.
 */
public class JdtlsLanguageClient implements LanguageClient {
    private static final Logger LOG = LoggerFactory.getLogger(JdtlsLanguageClient.class);
    
    // Latch to wait for JDTLS to become ready
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private volatile String currentStatus = "Starting";
    
    /**
     * JDTLS-specific status notification.
     * This is called by JDTLS to report its status (e.g., "Starting", "Ready").
     */
    @JsonNotification("language/status")
    public void languageStatus(StatusReport status) {
        LOG.info("JDTLS status [{}]: {}", status.getType(), status.getMessage());
        currentStatus = status.getMessage();
        
        // JDTLS reports "Ready" or "ServiceReady" when it's done indexing
        if ("ServiceReady".equals(status.getType()) || 
            (status.getMessage() != null && status.getMessage().contains("Ready"))) {
            LOG.info("JDTLS is ready!");
            readyLatch.countDown();
        }
    }
    
    /**
     * Wait for JDTLS to report ready status.
     * @param timeout timeout value
     * @param unit timeout unit
     * @return true if JDTLS became ready, false if timeout occurred
     */
    public boolean waitForReady(long timeout, TimeUnit unit) throws InterruptedException {
        return readyLatch.await(timeout, unit);
    }
    
    /**
     * Get the current status message.
     */
    public String getCurrentStatus() {
        return currentStatus;
    }
    
    /**
     * JDTLS status report object.
     */
    public static class StatusReport {
        private String type;
        private String message;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    @Override
    public void telemetryEvent(Object object) {
        LOG.debug("Telemetry event: {}", object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        LOG.debug("Diagnostics for {}: {} issues",
            diagnostics.getUri(),
            diagnostics.getDiagnostics().size());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        LOG.info("JDTLS message [{}]: {}",
            messageParams.getType(),
            messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        LOG.info("JDTLS message request: {}", requestParams.getMessage());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        switch (message.getType()) {
            case Error -> LOG.error("JDTLS: {}", message.getMessage());
            case Warning -> LOG.warn("JDTLS: {}", message.getMessage());
            case Info -> LOG.info("JDTLS: {}", message.getMessage());
            case Log -> LOG.debug("JDTLS: {}", message.getMessage());
        }
    }

    /**
     * Handle capability registration requests from the server.
     * JDTLS uses dynamic capability registration for features like workspace symbols.
     */
    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        LOG.debug("Register capability request: {}", params.getRegistrations().size());
        for (Registration registration : params.getRegistrations()) {
            LOG.debug("  - Registered: {} (method: {})",
                registration.getId(),
                registration.getMethod());
        }
        // Accept all capability registrations
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle capability unregistration requests from the server.
     */
    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        LOG.debug("Unregister capability request: {}", params.getUnregisterations().size());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle workspace folder requests from the server.
     */
    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        LOG.debug("Workspace folders requested");
        // Return empty list - the folders are set during initialization
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Handle configuration requests from the server.
     */
    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
        LOG.debug("Configuration requested for {} items", params.getItems().size());
        // Return empty config for each item
        return CompletableFuture.completedFuture(
            params.getItems().stream()
                .map(item -> (Object) null)
                .toList()
        );
    }

    /**
     * Handle apply edit requests from the server.
     */
    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        LOG.debug("Apply edit requested");
        // We don't support editing, but acknowledge the request
        return CompletableFuture.completedFuture(
            new ApplyWorkspaceEditResponse(false)
        );
    }

    /**
     * Handle semantic tokens refresh requests.
     */
    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        LOG.debug("Refresh semantic tokens requested");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle code lens refresh requests.
     */
    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        LOG.debug("Refresh code lenses requested");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle inlay hint refresh requests.
     */
    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        LOG.debug("Refresh inlay hints requested");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle inline value refresh requests.
     */
    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        LOG.debug("Refresh inline values requested");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle diagnostic refresh requests.
     */
    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        LOG.debug("Refresh diagnostics requested");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle create files requests.
     */
    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        LOG.debug("Create progress: {}", params.getToken());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle progress notifications.
     */
    @Override
    public void notifyProgress(ProgressParams params) {
        LOG.debug("Progress notification: {}", params.getToken());
    }

    /**
     * Handle showDocument requests.
     */
    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        LOG.debug("Show document: {}", params.getUri());
        return CompletableFuture.completedFuture(new ShowDocumentResult(false));
    }
}
