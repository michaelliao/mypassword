package org.puppylab.mypassword.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.puppylab.mypassword.core.web.DispatcherService;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP service on 127.0.0.1:27432 for the Chrome extension.
 *
 * Thread model: - start() blocks on accept() — run on a dedicated daemon
 * thread. - Each accepted connection is handed to a fixed thread pool. - If the
 * vault is locked and a UI prompt is needed, use display.syncExec() from the
 * handler thread.
 */
public class Daemon implements HttpHandler {

    public static final int PORT = 27432;

    final Logger       logger          = LoggerFactory.getLogger(getClass());
    final ObjectMapper mapper          = JsonUtils.getObjectMapper();
    final String       badRequestError = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Bad request.");

    private VaultManager            vaultManager;
    private final DispatcherService dispatcherService;

    private HttpServer       httpServer;
    private volatile boolean running = true;

    public Daemon() {
        this.dispatcherService = new DispatcherService(new RequestController(vaultManager));
    }

    public void setVaultManager(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    /**
     * Binds the server socket. Call on the main thread before {@link #start()}.
     * Returns {@code false} if the port is already in use (duplicate instance).
     */
    public boolean listen() {
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            logger.info("HTTP service listening on 127.0.0.1:{}", PORT);
            return true;
        } catch (IOException e) {
            logger.error("Failed to bind port {} — another instance may be running", PORT, e);
            return false;
        }
    }

    /**
     * Blocking accept loop — call from a dedicated background thread after
     * {@link #listen()}.
     */
    public void start() {
        this.httpServer.start();
    }

    /** Called by MainWindow when the SWT shell is disposed. */
    public void stop() {
        this.httpServer.stop(0);
        vaultManager.close();
    }

    // -------- http handler --------

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String body = null;
        if ("POST".equals(method) && contentType != null
                && (contentType.equals("application/json") || contentType.startsWith("application/json;"))) {
            body = readJsonBody(exchange);
        }
        if ("OPTIONS".equals(method)) {
            sendCors(exchange);
        } else {
            Object resp = processHttp(exchange);
            exchange.sendResponseHeaders(200, -1);
        }
        exchange.close();
    }

    private String readJsonBody(HttpExchange exchange) throws IOException {
        try (var reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.readAllAsString();
        }
    }

    // -------- cors response --------

    private void sendCors(HttpExchange exchange) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    private Object processHttp(HttpExchange exchange) {
        return "Hello";
    }
}
