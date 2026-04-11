package org.puppylab.mypassword.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

    /** Name of the pointer file inside {@link #getAppDataDir()}. */
    private static final String VAULT_POINTER = "vault.path";

    public static Path getUserHome() {
        return Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    /**
     * Return {@code ~/.mypassword}, creating it if missing. Throws if the path
     * exists but is not a directory (e.g. a file named {@code .mypassword}).
     */
    public static Path getAppDataDir() {
        Path dir = getUserHome().resolve(".mypassword");
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dir)) {
            throw new IllegalStateException(dir + " exists but is not a directory. Remove it and restart MyPassword.");
        }
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return dir;
    }

    /**
     * Resolve the vault database path.
     *
     * <p>
     * If {@code ~/.mypassword/vault.path} exists and contains an absolute path,
     * that path is returned so users can keep the real file anywhere they want
     * (e.g. inside a OneDrive folder). Otherwise null is returned.
     *
     * <p>
     * This is a redirect file, not a symlink — creating symlinks on Windows
     * requires Developer Mode / admin, which we don't want to force on users.
     */
    public static Path getDbFile() {
        Path pointer = getAppDataDir().resolve(VAULT_POINTER);
        if (Files.isRegularFile(pointer)) {
            try {
                String content = Files.readString(pointer, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return Paths.get(content).toAbsolutePath().normalize();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    public static Path getLogFile() {
        return getAppDataDir().resolve("mypassword.log");
    }

    /**
     * Returns true when the vault database path resolves to an existing regular
     * file. A missing file or a pointer file referencing a non-existent target
     * returns {@code false}, in which case the startup code should prompt the user
     * via {@code VaultLocatorDialog}.
     */
    public static boolean isValidVaultFile(Path p) {
        return Files.isRegularFile(p);
    }

    /**
     * Write the pointer file so future calls to {@link #getDbFile()} resolve to
     * {@code target}. Passing the default location (or {@code null}) removes the
     * pointer file.
     */
    public static void setVaultLocation(Path target) throws IOException {
        Path pointer = getAppDataDir().resolve(VAULT_POINTER);
        if (target == null) {
            Files.deleteIfExists(pointer);
            return;
        }
        Files.writeString(pointer, target.toAbsolutePath().normalize().toString(), StandardCharsets.UTF_8);
    }
}
