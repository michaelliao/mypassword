package org.puppylab.mypassword.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.puppylab.mypassword.core.web.DispatcherService;
import org.puppylab.mypassword.core.web.RequestController;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.util.FileUtils;
import org.puppylab.mypassword.rpc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.body.BodyReader;
import rawhttp.core.errors.InvalidHttpRequest;

/**
 * Core service.
 */
public class Daemon {

    final Logger       logger = LoggerFactory.getLogger(getClass());
    final ObjectMapper mapper = JsonUtils.getObjectMapper();

    String              badRequestError   = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Bad request.");
    ServerSocketChannel serverChannel     = null;
    VaultManager        vaultManager      = null;
    DispatcherService   dispatcherService = null;
    RequestController   requestController = null;

    volatile boolean running = true;

    public static void main(String[] args) {
        new Daemon().start();
    }

    void start() {
        Path sock = FileUtils.getSocketFile();
        if (Files.exists(sock)) {
            try (var _ = SocketChannel.open(UnixDomainSocketAddress.of(sock))) {
                logger.error("Daemon is already running.");
                System.exit(1);
                return;
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(sock);
                } catch (IOException ioe) {
                    logger.error("Try remove sock failed.");
                    System.exit(1);
                    return;
                }
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup, "Shutdown-Hook"));

        logger.info("try start daemon...");
        try {
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(sock));
            logger.info("daemon started at: {}", sock);

            // open db:
            this.vaultManager = new VaultManager();
            this.requestController = new RequestController(this.vaultManager);
            this.dispatcherService = new DispatcherService(this.requestController);

            while (running) {
                final SocketChannel clientChannel = serverChannel.accept();
                Thread t = new Thread(() -> {
                    Session.init();
                    try (clientChannel) {
                        handleConnection(clientChannel);
                    } catch (InvalidHttpRequest e) {
                        logger.warn("Could not read request.");
                    } catch (Exception e) {
                        logger.error("Connection error", e);
                    } finally {
                        Session.remove();
                    }
                    logger.info("Connection closed.");
                });
                t.start();
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    void handleConnection(SocketChannel channel) throws IOException {
        final RawHttp rawHttp = new RawHttp();
        channel.configureBlocking(true);
        try (InputStream input = Channels.newInputStream(channel);
                OutputStream output = Channels.newOutputStream(channel)) {
            while (running && channel.isOpen()) {
                RawHttpRequest request = rawHttp.parseRequest(input);

                // 2. 提取 Header 中的关键信息
                String path = request.getUri().getPath();
                String userAgent = request.getHeaders().getFirst("User-Agent").orElse("Unknown");

                // 3. 根据 Content-Length 精准读取 Body
                Optional<? extends BodyReader> body = request.getBody();
                String jsonBody = "{}";
                if (body.isPresent()) {
                    jsonBody = body.get().decodeBodyToString(StandardCharsets.UTF_8);
                }
                logger.info("request: {}: {}", path, jsonBody);
                // 4. 处理业务逻辑
                String responseJson = dispatcherService.process(path, jsonBody);
                logger.info("response: {}", responseJson);

                // 5. 构造 HTTP 格式的响应回写
                byte[] responseBody = responseJson.getBytes(StandardCharsets.UTF_8);
                String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + responseBody.length + "\r\n\r\n";
                output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
                output.write(responseBody);
                output.flush();
            }
        }
    }

    String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.strip().toLowerCase();
    }

    void cleanup() {
        logger.info("cleanup: closing database and deleting socket...");
        running = false;
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
                serverChannel = null;
            }
        } catch (IOException e) {
        }
        if (vaultManager != null) {
            vaultManager.close();
            vaultManager = null;
        }
    }
}
