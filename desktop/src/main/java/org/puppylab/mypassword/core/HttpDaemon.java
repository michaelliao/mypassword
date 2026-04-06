package org.puppylab.mypassword.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.puppylab.mypassword.core.web.DispatcherService;
import org.puppylab.mypassword.rpc.BadRequestException;
import org.puppylab.mypassword.rpc.BaseResponse;
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
public class HttpDaemon implements HttpHandler {

    public static final int PORT = 27432;

    final Logger       logger          = LoggerFactory.getLogger(getClass());
    final ObjectMapper mapper          = JsonUtils.getObjectMapper();
    final String       badRequestError = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Bad request.");

    private VaultManager      vaultManager;
    private DispatcherService dispatcherService;

    private HttpServer httpServer;

    public HttpDaemon() {
    }

    public void setVaultManager(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
        this.dispatcherService = new DispatcherService(new RequestController(vaultManager));
    }

    /**
     * Binds the server socket. Call on the main thread before {@link #start()}.
     * Returns {@code false} if the port is already in use (duplicate instance).
     */
    public boolean listen() {
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            this.httpServer.createContext("/", this);
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
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String query = uri.getRawQuery();
        String body = null;
        logger.info("http {}: {}", method, path);
        if ("POST".equals(method)) {
            body = readRequestBody(exchange);
        }
        if ("OPTIONS".equals(method)) {
            sendCors(exchange);
        } else {
            processHttp(exchange, method, path, query, body);
        }
        exchange.close();
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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

    private void processHttp(HttpExchange exchange, String method, String path, String query, String body)
            throws IOException {
        Object resp = null;
        try {
            resp = this.dispatcherService.processHttpRequest(exchange, method, path, query, body);
        } catch (BadRequestException e) {
            logger.warn("http handle error {}: {}", e.errorCode, e.getMessage());
            BaseResponse errorResp = new BaseResponse();
            errorResp.error = e.errorCode;
            errorResp.errorMessage = e.getMessage();
            String errorJson = JsonUtils.toJson(errorResp);
            sendResponse(exchange, "application/json", errorJson);
            return;
        } catch (Exception e) {
            logger.error("http handle exception: " + e.getMessage(), e);
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        if (resp == null) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        if (resp == DispatcherService.NOT_PROCESSED) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        if (resp instanceof String s) {
            logger.info("string response: {}", s);
            if (s.startsWith("redirect:")) {
                // redirect to url:
                exchange.getResponseHeaders().set("Location", s.substring(9));
                exchange.sendResponseHeaders(302, -1);
            } else if (s.startsWith("<html>")) {
                sendResponse(exchange, "text/html", s);
            } else {
                // send as json:
                sendResponse(exchange, "application/json", s);
            }
            return;
        }
        // serialize to json:
        String json = JsonUtils.toJson(resp);
        logger.info("json response: {}", json);
        sendResponse(exchange, "application/json", json);
    }

    private void sendResponse(HttpExchange exchange, String contentType, String content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
