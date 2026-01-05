package com.devoxx.lsp4jmcp.client;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * LSP client that connects to JDTLS (Eclipse JDT Language Server).
 * Uses LSP4J to communicate via the Language Server Protocol.
 */
public class JdtlsClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(JdtlsClient.class);
    private static final int TIMEOUT_SECONDS = 120; // JDTLS needs time to index
    private static final int INIT_TIMEOUT_SECONDS = 180;

    private final Process jdtlsProcess;
    private final LanguageServer languageServer;
    private final JdtlsLanguageClient languageClient;
    private final Path workspaceRoot;
    private final Thread stderrThread;
    private boolean initialized = false;

    public JdtlsClient(Path workspaceRoot, String jdtlsCommand) throws IOException {
        this.workspaceRoot = workspaceRoot;
        this.languageClient = new JdtlsLanguageClient();

        // Create data directory for JDTLS workspace data
        // IMPORTANT: Must be OUTSIDE the workspace, otherwise JDTLS fails with "overlaps" error
        String workspaceHash = Integer.toHexString(workspaceRoot.toString().hashCode());
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "jdtls-data", workspaceHash);
        Files.createDirectories(dataDir);

        LOG.info("Starting JDTLS process: {} with workspace: {}", jdtlsCommand, workspaceRoot);
        LOG.info("JDTLS data directory: {}", dataDir);

        // Build command with data directory
        List<String> command = new ArrayList<>();
        for (String part : jdtlsCommand.split("\\s+")) {
            command.add(part);
        }
        command.add("-data");
        command.add(dataDir.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        pb.directory(workspaceRoot.toFile());

        this.jdtlsProcess = pb.start();

        // Capture stderr in a separate thread for debugging
        this.stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(jdtlsProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.debug("JDTLS stderr: {}", line);
                }
            } catch (IOException e) {
                LOG.warn("Error reading JDTLS stderr", e);
            }
        }, "jdtls-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();

        // Check if process started
        try {
            Thread.sleep(500); // Give it a moment to fail if it's going to
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!jdtlsProcess.isAlive()) {
            int exitCode = jdtlsProcess.exitValue();
            throw new IOException("JDTLS process exited immediately with code: " + exitCode);
        }

        InputStream input = jdtlsProcess.getInputStream();
        OutputStream output = jdtlsProcess.getOutputStream();

        Launcher<LanguageServer> launcher = Launcher.createLauncher(
            languageClient,
            LanguageServer.class,
            input,
            output
        );

        this.languageServer = launcher.getRemoteProxy();

        // Start listening in background
        Future<?> listening = launcher.startListening();

        LOG.info("JDTLS process started (PID: {}), waiting for initialization...",
            jdtlsProcess.pid());
    }

    public void initialize() throws ExecutionException, InterruptedException, TimeoutException {
        if (initialized) {
            return;
        }

        LOG.info("Sending initialize request to JDTLS...");

        InitializeParams params = new InitializeParams();
        params.setRootUri(workspaceRoot.toUri().toString());
        params.setCapabilities(createClientCapabilities());
        params.setProcessId((int) ProcessHandle.current().pid());

        // Set workspace folders
        WorkspaceFolder folder = new WorkspaceFolder(
            workspaceRoot.toUri().toString(),
            workspaceRoot.getFileName().toString()
        );
        params.setWorkspaceFolders(List.of(folder));

        try {
            InitializeResult result = languageServer.initialize(params)
                .get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            languageServer.initialized(new InitializedParams());
            initialized = true;

            ServerCapabilities caps = result.getCapabilities();
            LOG.info("JDTLS initialized successfully!");
            LOG.info("  - Workspace symbol provider: {}", caps.getWorkspaceSymbolProvider());
            LOG.info("  - Definition provider: {}", caps.getDefinitionProvider());
            LOG.info("  - References provider: {}", caps.getReferencesProvider());

            // Give JDTLS time to index the workspace
            LOG.info("Waiting for workspace indexing...");
            Thread.sleep(5000); // Wait 5 seconds for initial indexing

        } catch (TimeoutException e) {
            LOG.error("JDTLS initialization timed out after {} seconds", INIT_TIMEOUT_SECONDS);
            throw e;
        } catch (ExecutionException e) {
            LOG.error("JDTLS initialization failed: {}", e.getCause().getMessage());
            throw e;
        }
    }

    private ClientCapabilities createClientCapabilities() {
        ClientCapabilities capabilities = new ClientCapabilities();

        WorkspaceClientCapabilities workspace = new WorkspaceClientCapabilities();
        
        // Symbol capabilities with dynamic registration
        SymbolCapabilities symbolCaps = new SymbolCapabilities();
        symbolCaps.setDynamicRegistration(true);
        workspace.setSymbol(symbolCaps);
        
        workspace.setWorkspaceFolders(true);
        capabilities.setWorkspace(workspace);

        TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
        
        // Definition with dynamic registration
        DefinitionCapabilities defCaps = new DefinitionCapabilities();
        defCaps.setDynamicRegistration(true);
        textDocument.setDefinition(defCaps);
        
        // References with dynamic registration
        ReferencesCapabilities refCaps = new ReferencesCapabilities();
        refCaps.setDynamicRegistration(true);
        textDocument.setReferences(refCaps);
        
        // Document symbol with dynamic registration
        DocumentSymbolCapabilities docSymbolCaps = new DocumentSymbolCapabilities();
        docSymbolCaps.setDynamicRegistration(true);
        textDocument.setDocumentSymbol(docSymbolCaps);
        
        capabilities.setTextDocument(textDocument);

        return capabilities;
    }

    /**
     * Search for symbols matching a query across the workspace.
     */
    public List<? extends SymbolInformation> findWorkspaceSymbols(String query)
            throws ExecutionException, InterruptedException, TimeoutException {
        ensureInitialized();

        LOG.debug("Searching workspace symbols for: '{}'", query);
        WorkspaceSymbolParams params = new WorkspaceSymbolParams(query);

        var result = languageServer.getWorkspaceService()
            .symbol(params)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (result == null) {
            LOG.warn("JDTLS returned null for symbol search");
            return List.of();
        }

        // Handle Either<List<SymbolInformation>, List<WorkspaceSymbol>>
        List<? extends SymbolInformation> symbols;
        if (result.isLeft()) {
            symbols = result.getLeft() != null ? result.getLeft() : List.of();
        } else {
            var right = result.getRight();
            if (right != null) {
                symbols = right.stream()
                    .map(this::toSymbolInformation)
                    .toList();
            } else {
                symbols = List.of();
            }
        }

        LOG.debug("Found {} symbols for query '{}'", symbols.size(), query);
        return symbols;
    }

    private SymbolInformation toSymbolInformation(WorkspaceSymbol ws) {
        SymbolInformation si = new SymbolInformation();
        si.setName(ws.getName());
        si.setKind(ws.getKind());
        si.setContainerName(ws.getContainerName());
        if (ws.getLocation().isLeft()) {
            si.setLocation(ws.getLocation().getLeft());
        }
        return si;
    }

    /**
     * Find all references to a symbol at a given location.
     */
    public List<? extends Location> findReferences(String uri, int line, int character)
            throws ExecutionException, InterruptedException, TimeoutException {
        ensureInitialized();

        ReferenceParams params = new ReferenceParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, character));
        params.setContext(new ReferenceContext(true));

        var result = languageServer.getTextDocumentService()
            .references(params)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return result != null ? result : List.of();
    }

    /**
     * Go to definition of a symbol at a given location.
     */
    public List<? extends Location> findDefinition(String uri, int line, int character)
            throws ExecutionException, InterruptedException, TimeoutException {
        ensureInitialized();

        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, character));

        var result = languageServer.getTextDocumentService()
            .definition(params)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (result == null) {
            return List.of();
        }

        if (result.isLeft()) {
            return result.getLeft();
        } else {
            return result.getRight().stream()
                .map(link -> new Location(link.getTargetUri(), link.getTargetRange()))
                .toList();
        }
    }

    /**
     * Get all symbols in a document.
     */
    public List<? extends DocumentSymbol> getDocumentSymbols(String uri)
            throws ExecutionException, InterruptedException, TimeoutException {
        ensureInitialized();

        DocumentSymbolParams params = new DocumentSymbolParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));

        var result = languageServer.getTextDocumentService()
            .documentSymbol(params)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (result == null || result.isEmpty()) {
            return List.of();
        }

        var first = result.get(0);
        if (first.isRight()) {
            return result.stream()
                .map(either -> either.getRight())
                .toList();
        } else {
            return result.stream()
                .map(either -> toDocumentSymbol(either.getLeft()))
                .toList();
        }
    }

    private DocumentSymbol toDocumentSymbol(SymbolInformation si) {
        DocumentSymbol ds = new DocumentSymbol();
        ds.setName(si.getName());
        ds.setKind(si.getKind());
        ds.setRange(si.getLocation().getRange());
        ds.setSelectionRange(si.getLocation().getRange());
        return ds;
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("JDTLS client not initialized. Call initialize() first.");
        }
        if (!jdtlsProcess.isAlive()) {
            throw new IllegalStateException("JDTLS process is no longer running.");
        }
    }

    public boolean isRunning() {
        return jdtlsProcess.isAlive();
    }
    
    /**
     * Get the language client for advanced operations.
     */
    public JdtlsLanguageClient getLanguageClient() {
        return languageClient;
    }

    @Override
    public void close() {
        LOG.info("Shutting down JDTLS client");
        try {
            languageServer.shutdown().get(5, TimeUnit.SECONDS);
            languageServer.exit();
        } catch (Exception e) {
            LOG.warn("Error during graceful shutdown", e);
        }
        jdtlsProcess.destroyForcibly();
        stderrThread.interrupt();
    }
}
