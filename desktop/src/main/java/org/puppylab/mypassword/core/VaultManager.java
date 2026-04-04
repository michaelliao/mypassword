package org.puppylab.mypassword.core;

import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.core.exception.EncryptException;
import org.puppylab.mypassword.rpc.BadRequestException;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.ConvertUtils;

public class VaultManager {

    private final DbManager dbManager;

    private VaultConfig vaultConfig = null;

    public VaultManager(DbManager dbManager) {
        this.dbManager = dbManager;
        this.vaultConfig = dbManager.queryVaultConfig();
    }

    public boolean isInitialized() {
        return this.vaultConfig != null;
    }

    // nullable:
    Item getItem(long id) {
        return this.dbManager.queryFirst(Item.class, "where id = ?", id);
    }

    public AbstractItemData getItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new BadRequestException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        return ConvertUtils.toItemData(key, item);
    }

    public List<AbstractItemData> getItems(SecretKey key) {
        return getRawItems().stream().map(it -> ConvertUtils.toItemData(key, it)).toList();
    }

    public List<Item> getRawItems() {
        return this.dbManager.queryForList(Item.class, "");
    }

    /**
     * Create item by provided data. The id of the data is ignored. The return data
     * has a generated valid id.
     */
    public AbstractItemData createItem(SecretKey key, AbstractItemData data) {
        // check:
        if (data.fields() == null) {
            throw new BadRequestException(ErrorCode.BAD_FIELD, "Missing fields.");
        }
        String errField = data.fields().check();
        if (errField != null) {
            throw new BadRequestException(ErrorCode.BAD_FIELD, "Invalid field: " + errField);
        }
        // prepare entity:
        Item item = new Item();
        item.id = IdUtils.nextId();
        item.item_type = data.item_type;
        item.deleted = false;
        item.favorite = false;
        item.updated_at = System.currentTimeMillis();
        // encrypt fields and set to item:
        ConvertUtils.encrypt(key, item, data.fields());
        this.dbManager.insert(item);
        return ConvertUtils.toItemData(key, item);
    }

    /**
     * Soft delete an item. Return deleted item data.
     */
    public AbstractItemData deleteItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new BadRequestException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        if (!item.deleted) {
            item.deleted = true;
            item.updated_at = System.currentTimeMillis();
            this.dbManager.update(item, "deleted", "updated_at");
        }
        return ConvertUtils.toItemData(key, item);
    }

    /**
     * Set deleted flag to false. Return undeleted item data.
     */
    public AbstractItemData restoreItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new BadRequestException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        if (item.deleted) {
            item.deleted = false;
            item.updated_at = System.currentTimeMillis();
            this.dbManager.update(item, "deleted", "updated_at");
        }
        return ConvertUtils.toItemData(key, item);
    }

    /**
     * Update an exist item. The argument data must have a valid id. Return updated
     * item data.
     */
    public AbstractItemData updateItem(SecretKey key, AbstractItemData data) {
        String errField = data.fields().check();
        if (errField != null) {
            throw new BadRequestException(ErrorCode.BAD_FIELD, "Invalid field: " + errField);
        }
        Item item = getItem(data.id);
        if (item == null) {
            throw new BadRequestException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + data.id);
        }
        if (item.item_type != data.item_type) {
            throw new BadRequestException(ErrorCode.BAD_FIELD, "Item type not match: " + data.item_type);
        }
        ConvertUtils.encrypt(key, item, data.fields());
        item.updated_at = System.currentTimeMillis();
        this.dbManager.tx(() -> {
            this.dbManager.execute(
                    "INSERT INTO ItemHistory (hid, rid, b64_encrypted_data, b64_encrypted_data_iv, updated_at) SELECT "
                            + IdUtils.nextId()
                            + ", id, b64_encrypted_data, b64_encrypted_data_iv, updated_at FROM Item where id = ?",
                    item.id);
            this.dbManager.update(item, "b64_encrypted_data", "b64_encrypted_data_iv", "updated_at");
        });
        return ConvertUtils.toItemData(key, item);
    }

    public SecretKey initVault(String password) {
        if (isInitialized()) {
            throw new IllegalStateException("Vault already initialized.");
        }
        // generate pbe key by password:
        final int pbe_iterations = 1_000_000;
        byte[] pbe_salt = EncryptUtils.generateSalt();
        byte[] pbe_key = EncryptUtils.derivePbeKey(password.toCharArray(), pbe_salt, pbe_iterations);
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
