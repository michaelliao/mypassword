package org.puppylab.mypassword.rpc.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

    public static Path getUserHome() {
        return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    public static Path getAppDataDir() {
        Path userHome = getUserHome();
        Path appDataDir = userHome.resolve(".mypassword");
        if (Files.notExists(appDataDir)) {
            try {
                Files.createDirectories(appDataDir);
            } catch (IOException e) {
                throw new RuntimeException("Create dir failed: " + appDataDir);
            }
        }
        return appDataDir;
    }

    public static Path getSocketFile() {
        return getAppDataDir().resolve(".sock");
    }

    public static Path getLogFile() {
        return getAppDataDir().resolve("log.txt");
    }

    public static Path getDbFile() {
        return getAppDataDir().resolve("mypassword.db");
    }

}
