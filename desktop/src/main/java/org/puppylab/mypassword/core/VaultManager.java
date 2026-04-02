package org.puppylab.mypassword.core;

import java.nio.file.Path;
import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.core.exception.EncryptException;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.FileUtils;

public class VaultManager {

    private final Path      dbFile;
    private final DbManager dbManager;

    private VaultConfig vaultConfig = null;

    public VaultManager() {
        this.dbFile = FileUtils.getDbFile();
        this.dbManager = new DbManager(this.dbFile);
        this.vaultConfig = dbManager.queryVaultConfig();
    }

    public boolean isInitialized() {
        return this.vaultConfig != null;
    }

    // nullable:
    public Item getItem(long id) {
        return this.dbManager.queryFirst(Item.class, "where id = ?", id);
    }

    public List<Item> getItems() {
        return this.dbManager.queryForList(Item.class, "");
    }

    public void createItem(Item item) {
        this.dbManager.insert(item);
    }

    public boolean deleteItem(long id) {
        Item item = getItem(id);
        if (item == null || item.deleted) {
            return false;
        }
        item.deleted = true;
        item.updated_at = System.currentTimeMillis();
        this.dbManager.update(item, "deleted", "updated_at");
        return true;
    }

    public void updateItem(Item item) {
        this.dbManager.tx(() -> {
            this.dbManager.execute(
                    "INSERT INTO ItemHistory (hid, rid, b64_encrypted_data, b64_encrypted_data_iv, updated_at) SELECT "
                            + IdUtils.nextId()
                            + ", id, b64_encrypted_data, b64_encrypted_data_iv, updated_at FROM Item where id = ?",
                    item.id);
            this.dbManager.update(item, "b64_encrypted_data", "b64_encrypted_data_iv", "updated_at");
        });
    }

    public SecretKey initVault(char[] password) {
        if (isInitialized()) {
            throw new IllegalStateException("Vault already initialized.");
        }
        // generate pbe key by password:
        final int pbe_iterations = 1_000_000;
        byte[] pbe_salt = EncryptUtils.generateSalt();
        byte[] pbe_key = EncryptUtils.derivePbeKey(password, pbe_salt, pbe_iterations);
        // encrypt dek by pbe key:
        byte[] dek = EncryptUtils.generateKey();
        byte[] encrypted_dek_iv = EncryptUtils.generateIV();
        byte[] encrypted_dek = EncryptUtils.encrypt(dek, EncryptUtils.bytesToAesKey(pbe_key), encrypted_dek_iv);
        VaultConfig vc = new VaultConfig();
        vc.id = 1;
        vc.pbe_iterations = pbe_iterations;
        vc.b64_pbe_salt = Base64Utils.b64(pbe_salt);
        vc.b64_encrypted_dek = Base64Utils.b64(encrypted_dek);
        vc.b64_encrypted_dek_iv = Base64Utils.b64(encrypted_dek_iv);
        dbManager.insert(vc);
        this.vaultConfig = vc;
        return EncryptUtils.bytesToAesKey(dek);
    }

    /**
     * Return null if bad password.
     */
    public SecretKey unlockVault(char[] password) {
        if (!isInitialized()) {
            throw new IllegalStateException("Vault not initialized.");
        }
        byte[] pbe_key = EncryptUtils.derivePbeKey(password, Base64Utils.b64(this.vaultConfig.b64_pbe_salt),
                this.vaultConfig.pbe_iterations);
        byte[] encrypted_dek = Base64Utils.b64(this.vaultConfig.b64_encrypted_dek);
        byte[] encrypted_dek_iv = Base64Utils.b64(this.vaultConfig.b64_encrypted_dek_iv);
        byte[] dek = null;
        try {
            dek = EncryptUtils.decrypt(encrypted_dek, EncryptUtils.bytesToAesKey(pbe_key), encrypted_dek_iv);
        } catch (EncryptException e) {
            return null;
        }
        return EncryptUtils.bytesToAesKey(dek);
    }

    public void close() {
        this.dbManager.close();
    }
}
