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
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.IdUtils;

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
    void testQueryAppVersion() {
        int v = dbManager.queryAppVersion();
        assertEquals(1, v);
    }

    @Test
    void testQueryDataVersion() {
        int v1 = dbManager.queryDataVersion();
        assertEquals(0, v1);
        dbManager.incDataVersion();
        int v2 = dbManager.queryDataVersion();
        assertEquals(1, v2);
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
    void testInsertItems() {
        List<Item> items = dbManager.queryForList(Item.class, "");
        assertTrue(items.isEmpty());

        for (int i = 1; i <= 10; i++) {
            Item item = new Item();
            item.item_type = ItemType.LOGIN;
            item.id = IdUtils.nextId();
            item.deleted = false;
            item.b64_encrypted_data = "{ \"title\": \"data-" + i + "\" }";
            item.b64_encrypted_data_iv = "iv-" + i;
            item.updated_at = 1_000_000 + i;
            dbManager.insert(item);
        }

        items = dbManager.queryForList(Item.class, "");
        assertEquals(10, items.size());
    }
}
