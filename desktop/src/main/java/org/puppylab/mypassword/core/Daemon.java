package org.puppylab.mypassword.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.puppylab.mypassword.core.web.DispatcherService;
import org.puppylab.mypassword.core.web.RequestController;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.body.BodyReader;
import rawhttp.core.errors.InvalidHttpRequest;

/**
 * HTTP service on 127.0.0.1:27432 for the Chrome extension.
 *
 * Thread model: - start() blocks on accept() — run on a dedicated daemon
 * thread. - Each accepted connection is handed to a fixed thread pool. - If the
 * vault is locked and a UI prompt is needed, use display.syncExec() from the
 * handler thread.
 */
public class Daemon {

    public static final int PORT = 27432;

    private static final String CORS_HEADERS = "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Methods: POST, OPTIONS\r\n" + "Access-Control-Allow-Headers: Content-Type\r\n";

    private static final int POOL_SIZE = 4;

    final Logger       logger          = LoggerFactory.getLogger(getClass());
    final ObjectMapper mapper          = JsonUtils.getObjectMapper();
    final String       badRequestError = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Bad request.");

    private VaultManager            vaultManager;
    private final DispatcherService dispatcherService;

    private ServerSocket     serverSocket;
    private ExecutorService  pool;
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
        pool = Executors.newFixedThreadPool(POOL_SIZE, r -> {
            Thread t = new Thread(r, "http-handler");
            t.setDaemon(true);
            return t;
        });
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
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
        try {
            while (running) {
                @SuppressWarnings("resource")
                Socket conn = serverSocket.accept();
                pool.submit(() -> handleConnection(conn));
            }
        } catch (SocketException e) {
            if (running)
                logger.error("Socket error", e);
        } catch (Exception e) {
            logger.error("HTTP service error", e);
        }
    }

    /** Called by MainWindow when the SWT shell is disposed. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            // ignore — we're shutting down
        }
        if (pool != null)
            pool.shutdownNow();
        vaultManager.close();
    }

    // ── per-connection handler (runs on pool thread) ──────────────────────

    void handleConnection(Socket socket) {
        final RawHttp rawHttp = new RawHttp();
        try (socket; InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {

            while (running && !socket.isClosed()) {
                RawHttpRequest request;
                try {
                    request = rawHttp.parseRequest(input);
                } catch (InvalidHttpRequest e) {
                    writeRaw(output, badResponse(400, badRequestError));
                    break;
                } catch (IOException e) {
                    break; // client closed the connection
                }

                String method = request.getMethod();
                String path = request.getUri().getPath();

                // CORS preflight
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    writeRaw(output, preflightResponse());
                    continue;
                }

                Optional<? extends BodyReader> body = request.getBody();
                String jsonBody = body.isPresent() ? body.get().decodeBodyToString(StandardCharsets.UTF_8) : "{}";

                logger.info(">> {} {}", method, path);
                String responseJson = dispatcherService.process(path, jsonBody);
                logger.info("<< {}", responseJson);

                writeRaw(output, okResponse(responseJson));
            }
        } catch (Exception e) {
            logger.error("Connection error", e);
        }
    }

    // ── response builders ─────────────────────────────────────────────────

    private byte[] preflightResponse() {
        String header = "HTTP/1.1 204 No Content\r\n" + CORS_HEADERS + "Content-Length: 0\r\n\r\n";
        return header.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] okResponse(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" + "Content-Type: application/json\r\n" + "Content-Length: " + body.length
                + "\r\n" + CORS_HEADERS + "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(body, 0, result, headerBytes.length, body.length);
        return result;
    }

    private byte[] badResponse(int status, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + " Error\r\n" + "Content-Type: application/json\r\n" + "Content-Length: "
                + body.length + "\r\n" + CORS_HEADERS + "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(body, 0, result, headerBytes.length, body.length);
        return result;
    }

    private void writeRaw(OutputStream out, byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }
}
