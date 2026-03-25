package org.puppylab.mypassword.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.puppylab.mypassword.core.entity.LoginItem;
import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.core.entity.VaultVersion;
import org.puppylab.mypassword.rpc.util.Base64Utils;

public class DbManagerTest {

    Path      dbFile;
    DbManager dbManager;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = File.createTempFile("test-mypassword-", ".db").toPath();
        Files.delete(dbFile); // delete db file to force init schema
        dbManager = new DbManager(dbFile);
    }

    @AfterEach
    void tearDown() throws Exception {
        dbManager.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void testQueryVersion() {
        VaultVersion vv = dbManager.queryVaultVersion();
        assertEquals(1, vv.version);
    }

    @Test
    void testInitConfig() {
        VaultConfig vc = dbManager.queryVaultConfig();
        assertNull(vc);
        byte[] dek = EncryptUtils.generateKey();
        char[] password = "password".toCharArray();
        byte[] pbe_salt = EncryptUtils.generateSalt();
        byte[] pbe_key = EncryptUtils.derivePbeKey(password, pbe_salt, EncryptUtils.PBE_ITERATIONS);
        byte[] iv = EncryptUtils.generateIV();
        byte[] encrypted_dek = EncryptUtils.encrypt(dek, EncryptUtils.bytesToAesKey(pbe_key), iv);
        vc = new VaultConfig();
        vc.id = 1;
        vc.b64_encrypted_dek = Base64Utils.b64(encrypted_dek);
        vc.b64_encrypted_dek_iv = Base64Utils.b64(iv);
        vc.b64_pbe_salt = Base64Utils.b64(pbe_salt);
        vc.pbe_iterations = EncryptUtils.PBE_ITERATIONS;
        dbManager.insert(vc);
    }

    @Test
    void testInsertLoginItem() {
        List<LoginItem> items = dbManager.queryForList(LoginItem.class, "");
        assertTrue(items.isEmpty());

        for (int i = 1; i <= 10; i++) {
            LoginItem li = new LoginItem();
            li.id = IdUtils.nextId();
            li.deleted = false;
            li.b64_encrypted_data = "data-" + i;
            li.b64_encrypted_data_iv = "iv-" + i;
            li.updated_at = 1_000_000 + i;
            dbManager.insert(li);
        }

        items = dbManager.queryForList(LoginItem.class, "");
        assertEquals(10, items.size());
    }
}
