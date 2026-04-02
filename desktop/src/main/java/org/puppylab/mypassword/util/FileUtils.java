package org.puppylab.mypassword.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

    public static Path getUserHome() {
        return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    public static Path getAppDataDir() {
        Path dir = getUserHome().resolve(".mypassword");
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return dir;
    }

    public static Path getDbFile() {
        return getAppDataDir().resolve("mypassword.db");
    }

    public static Path getLogFile() {
        return getAppDataDir().resolve("mypassword.log");
    }
}
