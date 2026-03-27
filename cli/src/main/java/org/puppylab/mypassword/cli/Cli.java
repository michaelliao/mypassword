package org.puppylab.mypassword.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpClient;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.data.LoginFieldsData;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.rpc.request.ItemRequest;
import org.puppylab.mypassword.rpc.request.OAuth;
import org.puppylab.mypassword.rpc.request.VaultPasswordRequest;
import org.puppylab.mypassword.rpc.response.InfoResponse;
import org.puppylab.mypassword.rpc.response.ItemResponse;
import org.puppylab.mypassword.rpc.response.ItemsResponse;
import org.puppylab.mypassword.rpc.util.FileUtils;
import org.puppylab.mypassword.rpc.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import rawhttp.core.RawHttp;

public class Cli {

    HttpClient   client     = HttpClient.newBuilder().build();
    Path         socketPath = FileUtils.getSocketFile();
    ObjectMapper mapper     = JsonUtils.getObjectMapper();
    RawHttp      rawHttp    = new RawHttp();

    SocketChannel socketChannel;
    InputStream   inputStream;
    OutputStream  outputStream;

    static String version = "N/A";
    static String commit  = "N/A";

    void interactive() {
        // try connect:
        try {
            var address = UnixDomainSocketAddress.of(this.socketPath);
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(address);
            info("Connected at " + address);
            this.socketChannel = channel;
            this.inputStream = Channels.newInputStream(channel);
            this.outputStream = Channels.newOutputStream(channel);
        } catch (IOException e) {
            error("Failed to connect.", e);
            return;
        }
        // command mode:
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String prompt = ">>> ";
            while (true) {
                String line;
                try {
                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    // handle Ctrl+C
                    continue;
                } catch (EndOfFileException e) {
                    // handle Ctrl+D
                    break;
                }
                if (line == null) {
                    break;
                }
                List<String> args = Parser.parse(line);
                String command = next(args, true);
                if (command == null) {
                    continue;
                }

                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("q")
                        || command.equalsIgnoreCase("quit")) {
                    break;
                }
                processCommand(command, args);
            }
        } catch (Exception e) {
            System.err.println("REPL Error: " + e.getMessage());
        }
        cleanup();
    }

    void processCommand(String command, List<String> args) {
        switch (command) {
        case "h", "help" -> help();
        case "q", "quit", "exit" -> System.exit(0);
        case "i", "info" -> request("/info", null, InfoResponse.class);
        case "vault" -> vault(args);
        case "oauth" -> oauth(args);
        case "item" -> item(args);
        default -> error("Unrecognized command: " + command);
        }
    }

    void item(List<String> args) {
        String action = next(args, true);
        if (action == null) {
            error("Invalid command: item <list|get|create|update|delete> <args>");
            return;
        }
        switch (action) {
        case "list" -> {
            request("/items/list", null, ItemsResponse.class);
        }
        case "get" -> {
            String id = next(args);
            if (id == null) {
                error("Invalid command: item get <id>");
                return;
            }
            request("/items/" + id + "/get", null, ItemResponse.class);
        }
        case "create" -> {
            ItemRequest request = parse(args);
            if (request == null) {
                return;
            }
            request("/items/create", request, ItemResponse.class);
        }
        default -> error("Invalid command: item <list|get|create|update|delete> <args>");
        }
    }

    void vault(List<String> args) {
        String action = next(args, true);
        if (action == null) {
            error("Invalid command: vault <lock|unlock|init> <args>");
            return;
        }
        switch (action) {
        case "lock" -> request("/vault/lock", null, InfoResponse.class);
        case "unlock", "init" -> {
            String password = next(args);
            if (password == null) {
                error("Invalid command: vault " + action + " <password>");
                return;
            }
            var req = new VaultPasswordRequest();
            req.password = password;
            request("/vault/" + action, req, InfoResponse.class);
        }
        default -> error("Invalid command: vault <lock|unlock|init> <args>");
        }
    }

    void oauth(List<String> args) {
        String action = next(args, true);
        if (action == null) {
            error("Invalid command: oauth <add|remove> <google>");
            return;
        }
        String provider = next(args, true);
        if (provider == null || !Set.of("google").contains(provider.toLowerCase())) {
            error("Invalid command: oauth <add|remove> <google>");
            return;
        }

        var req = new OAuth();
        req.action = action.toLowerCase();
        req.provider = provider.toLowerCase();
        switch (req.action) {
        case "remove" -> {
            return;
        }
        case "add" -> {
            info("Please login in browser...");
            while (true) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        default -> {
            error("Invalid command: oauth <add|remove> <google>");
            return;
        }
        }
    }

    void help() {
        info("MyPassword Command Line Interface, version %s, commit %s.", version, commit);
        info("""
                Command:
                  h, help                             Print help.
                  q, quit, exit                       Exit command line.
                  i, info                             Display vault info.
                  vault init <password>               Initialize vault by provide a password.
                  vault unlock <password>             Unlock vault.
                  vault lock                          Lock vault.
                  vault password <old-pwd> <new-pwd>  Change the vault password.
                  item list                           List login items.
                  item get <id>                       Get login item by id.
                  item create title=x username=y ...  Create login item.
                  item
                """);
    }

    <T extends BaseResponse> T request(String path, BaseRequest req, Class<T> respClass) {
        try {
            if (req == null) {
                req = new BaseRequest();
            }
            String json = this.mapper.writeValueAsString(req);
            byte[] jsonRequest = json.getBytes(StandardCharsets.UTF_8);
            // hidden password if any:
            hiddenPassword(req);
            info("Request: " + path + "\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(req));
            String header = "POST " + path
                    + " HTTP/1.1\r\nHost: localhost\r\nUser-Agent: MyPassword-Cli\r\nConnection: Keep-Alive\r\nContent-Length: "
                    + jsonRequest.length + "\r\n\r\n";
            this.outputStream.write(header.getBytes(StandardCharsets.UTF_8));
            this.outputStream.write(jsonRequest);
            this.outputStream.flush();
            // now read response:
            var response = rawHttp.parseResponse(this.inputStream);
            var optBody = response.getBody();
            if (optBody.isPresent()) {
                String jsonResponse = optBody.get().asRawString(StandardCharsets.UTF_8);
                T resp = mapper.readValue(jsonResponse, respClass);
                info("Response:\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resp));
                return resp;
            } else {
                error("Empty response.");
            }
        } catch (IOException e) {
            error("IO error.", e);
        }
        return null;
    }

    void disconnect() {
        if (this.socketChannel != null) {
            try {
                this.socketChannel.close();
            } catch (IOException e) {
                error("Error when disconnect: " + e.getMessage());
            }
            this.socketChannel = null;
            this.inputStream = null;
            this.outputStream = null;
        }
    }

    void cleanup() {
        disconnect();
    }

    void info(String msg) {
        System.out.println(msg);
    }

    void info(String msg, Object... args) {
        System.out.printf(msg, args);
        System.out.println();
    }

    void warn(String msg) {
        System.out.println(msg);
    }

    void warn(String msg, Object... args) {
        System.out.printf(msg, args);
        System.out.println();
    }

    void error(String msg) {
        System.err.println(msg);
    }

    void error(String msg, Throwable t) {
        System.err.println(msg);
        t.printStackTrace();
    }

    ItemRequest parse(List<String> args) {
        var fs = new LoginFieldsData();
        fs.title = "Unnamed";
        for (String arg : args) {
            // key=value
            int pos = arg.indexOf('=');
            if (pos <= 0) {
                error("Invalid key-value: " + arg);
                return null;
            }
            String key = arg.substring(0, pos);
            String value = arg.substring(pos + 1);
            switch (key.toLowerCase()) {
            case "ga" -> fs.ga = value;
            case "memo" -> fs.memo = value;
            case "password" -> fs.password = value;
            case "title" -> fs.title = value;
            case "username" -> fs.username = value;
            case "websites" -> fs.websites = List.of(value.split(","));
            default -> {
                error("Invalid key-value: " + arg);
                return null;
            }
            }
        }
        var itemData = new LoginItemData();
        itemData.data = fs;
        var lid = new ItemRequest();
        lid.item = itemData;
        return lid;
    }

    /**
     * Next arg or null if no more arg.
     */
    String next(List<String> args) {
        return next(args, false);
    }

    /**
     * Next arg or null if no more arg.
     */
    String next(List<String> args, boolean toLowercase) {
        if (args.isEmpty()) {
            return null;
        }
        String s = args.removeFirst();
        return toLowercase ? s.toLowerCase() : s;
    }

    void hiddenPassword(BaseRequest request) {
        Class<?> clazz = request.getClass();
        try {
            Field f = clazz.getField("password");
            f.set(request, "********");
        } catch (ReflectiveOperationException e) {
            // ignore
        }
    }

    public static void main(String[] args) {
        Cli main = new Cli();
        main.interactive();
    }
}
