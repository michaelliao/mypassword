package org.puppylab.mypassword.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class VaultManagerTest {

    Path         dbFile;
    VaultManager vaultManager;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = File.createTempFile("test-mypassword-", ".db").toPath();
        Files.delete(dbFile); // delete db file to force init schema
        this.vaultManager = new VaultManager(new DbManager(dbFile));
        this.vaultManager.initVault("password");
    }

    @AfterEach
    void tearDown() throws Exception {
        vaultManager.close();
        Files.deleteIfExists(dbFile);
    }

}
