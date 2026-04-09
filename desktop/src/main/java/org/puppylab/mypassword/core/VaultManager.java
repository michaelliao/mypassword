package org.puppylab.mypassword.core;

import java.util.HexFormat;
import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.entity.ExtensionConfig;
import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.core.entity.RecoveryConfig;
import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.core.entity.VaultSetting;
import org.puppylab.mypassword.core.exception.EncryptException;
import org.puppylab.mypassword.core.web.pkce.OAuthUser;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.VaultException;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.ConvertUtils;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.HashUtils;
import org.puppylab.mypassword.util.IdUtils;
import org.puppylab.mypassword.util.PasswordUtils;
import org.puppylab.mypassword.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VaultManager {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private DbManager dbManager;

    private VaultConfig       vaultConfig = null;
    private volatile Runnable onOAuthChanged;
    private volatile Runnable onVaultUnlocked;

    private static VaultManager instance = null;

    public VaultManager(DbManager dbManager) {
        this.dbManager = dbManager;
        this.vaultConfig = dbManager.queryVaultConfig();
        instance = this;
    }

    public static VaultManager getCurrent() {
        return instance;
    }

    public boolean isInitialized() {
        return this.vaultConfig != null;
    }

    public String getSetting(String key, String defaultValue) {
        VaultSetting vs = this.dbManager.queryFirst(VaultSetting.class, "where setting_key = ?", key);
        if (vs == null) {
            return defaultValue;
        }
        return vs.setting_value;
    }

    public long getSetting(String key, long defaultValue) {
        String s = getSetting(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getSetting(String key, int defaultValue) {
        String s = getSetting(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setSetting(String key, String value) {
        logger.info("set setting {} = {}", key, value);
        VaultSetting vs = this.dbManager.queryFirst(VaultSetting.class, "where setting_key = ?", key);
        if (vs == null) {
            vs = new VaultSetting();
            vs.setting_key = key;
            vs.setting_value = value;
            this.dbManager.insert(vs);
        } else {
            vs.setting_value = value;
            this.dbManager.update(vs, "setting_value");
        }
    }

    public void setSetting(String key, long value) {
        setSetting(key, String.valueOf(value));
    }

    public void setSetting(String key, int value) {
        setSetting(key, String.valueOf(value));
    }

    private int appVersion = 0;

    public int getAppVersion() {
        if (appVersion == 0) {
            appVersion = this.dbManager.queryAppVersion();
        }
        return appVersion;
    }

    public int getDataVersion() {
        return this.dbManager.queryDataVersion();
    }

    public void setOnOAuthChanged(Runnable onOAuthChanged) {
        this.onOAuthChanged = onOAuthChanged;
    }

    public void setOnVaultUnlocked(Runnable onVaultUnlocked) {
        this.onVaultUnlocked = onVaultUnlocked;
    }

    void fireVaultUnlocked() {
        Runnable callback = this.onVaultUnlocked;
        if (callback != null) {
            callback.run();
        }
    }

    public ExtensionConfig getExtension(long id) {
        return dbManager.queryFirst(ExtensionConfig.class, "WHERE id = ?", id);
    }

    public List<ExtensionConfig> getExtensions() {
        return dbManager.queryForList(ExtensionConfig.class, "");
    }

    public ExtensionConfig saveExtensionRequest(String name, String device) {
        ExtensionConfig ec = new ExtensionConfig();
        ec.id = IdUtils.nextId();
        ec.approve = false;
        ec.name = StringUtils.checkNotEmpty("name", name);
        ec.device = StringUtils.checkNotEmpty("device", device);
        ec.seed = PasswordUtils.generatePassword(16, PasswordUtils.STYLE_ALPHABET_NUMBER);
        dbManager.insert(ec);
        return ec;
    }

    public void approveExtension(long id, boolean approve) {
        ExtensionConfig ec = dbManager.queryFirst(ExtensionConfig.class, "WHERE id = ?", id);
        if (ec == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Extension request not found.");
        }
        if (approve) {
            ec.approve = approve;
            dbManager.update(ec, "approve");
        } else {
            dbManager.delete(ec);
        }
    }

    public List<RecoveryConfig> getRecoveryConfigs() {
        return dbManager.queryForList(RecoveryConfig.class, "");
    }

    public RecoveryConfig getRecoveryConfig(String provider) {
        return dbManager.queryFirst(RecoveryConfig.class, "WHERE oauth_provider = ?", provider);
    }

    // nullable:
    Item getItem(long id) {
        return this.dbManager.queryFirst(Item.class, "WHERE id = ?", id);
    }

    public AbstractItemData getItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        return ConvertUtils.toItemData(key, item);
    }

    public List<AbstractItemData> getItems(SecretKey key) {
        return getRawItems().stream().map(it -> ConvertUtils.toItemData(key, it)).toList();
    }

    public List<AbstractItemData> getItems(SecretKey key, int type) {
        return getRawItems(type).stream().map(it -> ConvertUtils.toItemData(key, it)).toList();
    }

    public List<Item> getRawItems() {
        return this.dbManager.queryForList(Item.class, "");
    }

    public List<Item> getRawItems(int type) {
        return this.dbManager.queryForList(Item.class, "WHERE item_type = ?", type);
    }

    /**
     * Create item by provided data. The id of the data is ignored. The return data
     * has a generated valid id.
     */
    public AbstractItemData createItem(SecretKey key, AbstractItemData data) {
        // check:
        if (data.fields() == null) {
            throw new VaultException(ErrorCode.BAD_FIELD, "Missing fields.");
        }
        String errField = data.fields().check();
        if (errField != null) {
            throw new VaultException(ErrorCode.BAD_FIELD, "Invalid field: " + errField);
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
        this.dbManager.incDataVersion();
        return ConvertUtils.toItemData(key, item);
    }

    /**
     * Soft delete an item. Return deleted item data.
     */
    public AbstractItemData deleteItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        if (!item.deleted) {
            item.deleted = true;
            item.updated_at = System.currentTimeMillis();
            this.dbManager.update(item, "deleted", "updated_at");
            this.dbManager.incDataVersion();
        }
        return ConvertUtils.toItemData(key, item);
    }

    /**
     * Set deleted flag to false. Return undeleted item data.
     */
    public AbstractItemData restoreItem(SecretKey key, long id) {
        Item item = getItem(id);
        if (item == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + id);
        }
        if (item.deleted) {
            item.deleted = false;
            item.updated_at = System.currentTimeMillis();
            this.dbManager.update(item, "deleted", "updated_at");
            this.dbManager.incDataVersion();
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
            throw new VaultException(ErrorCode.BAD_FIELD, "Invalid field: " + errField);
        }
        Item item = getItem(data.id);
        if (item == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Item not found: " + data.id);
        }
        if (item.item_type != data.item_type) {
            throw new VaultException(ErrorCode.BAD_FIELD, "Item type not match: " + data.item_type);
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
            this.dbManager.incDataVersion();
        });
        return ConvertUtils.toItemData(key, item);
    }

    public SecretKey initVault(String password) {
        if (isInitialized()) {
            throw new IllegalStateException("Vault already initialized.");
        }
        byte[] dek = EncryptUtils.generateKey();
        encryptDEK(password, dek);
        return EncryptUtils.bytesToAesKey(dek);
    }

    /**
     * Derive a fresh PBE key from {@code password} and wrap {@code dek} into a new
     * {@link VaultConfig}. Since VaultConfig always has a single row with id=1, the
     * existing row (if any) is deleted and a fresh row is inserted.
     */
    private void encryptDEK(String password, byte[] dek) {
        final int pbe_iterations = 1_000_000;
        byte[] pbe_salt = EncryptUtils.generateSalt();
        byte[] pbe_key = EncryptUtils.derivePbeKey(password.toCharArray(), pbe_salt, pbe_iterations);
        byte[] encrypted_dek_iv = EncryptUtils.generateIV();
        byte[] encrypted_dek = EncryptUtils.encrypt(dek, EncryptUtils.bytesToAesKey(pbe_key), encrypted_dek_iv);
        VaultConfig vc = new VaultConfig();
        vc.id = 1;
        vc.pbe_iterations = pbe_iterations;
        vc.b64_pbe_salt = Base64Utils.b64(pbe_salt);
        vc.b64_encrypted_dek = Base64Utils.b64(encrypted_dek);
        vc.b64_encrypted_dek_iv = Base64Utils.b64(encrypted_dek_iv);
        this.dbManager.tx(() -> {
            this.dbManager.execute("DELETE FROM VaultConfig WHERE id = 1");
            this.dbManager.insert(vc);
        });
        this.vaultConfig = vc;
    }

    public void saveOAuthRecovery(String provider, String name, String email, String oauthId, SecretKey dek) {
        RecoveryConfig rc = dbManager.queryFirst(RecoveryConfig.class, "where oauth_provider = ?", provider);
        if (rc == null) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "OAuth provider not found: " + provider);
        }
        // generate random HMAC key:
        byte[] hmacKey = EncryptUtils.generateKey();
        byte[] uidHash = HashUtils.sha256(oauthId);
        // derive AES key = pbe(pbeKey, salt, iterations):
        int pbe_iterations = 1_000_000;
        byte[] pbe_salt = EncryptUtils.generateSalt();
        String password = HexFormat.of().formatHex(HashUtils.hmacSha256(oauthId, hmacKey));
        byte[] pbe_key = EncryptUtils.derivePbeKey(password.toCharArray(), pbe_salt, pbe_iterations);
        byte[] encrypted_dek_iv = EncryptUtils.generateIV();
        // encrypt DEK:
        byte[] encryptedDek = EncryptUtils.encrypt(dek.getEncoded(), EncryptUtils.bytesToAesKey(pbe_key),
                encrypted_dek_iv);
        // update RecoveryConfig:
        rc.oauth_name = name != null ? name : "";
        rc.oauth_email = email != null ? email : "";
        rc.b64_uid_hash = Base64Utils.b64(uidHash);
        rc.b64_uid_hash_hmac = Base64Utils.b64(hmacKey);
        rc.pbe_iterations = pbe_iterations;
        rc.b64_pbe_salt = Base64Utils.b64(pbe_salt);
        rc.b64_encrypted_dek = Base64Utils.b64(encryptedDek);
        rc.b64_encrypted_dek_iv = Base64Utils.b64(encrypted_dek_iv);
        rc.updated_at = System.currentTimeMillis();
        dbManager.update(rc, "oauth_name", "oauth_email", "b64_uid_hash", "b64_uid_hash_hmac", "pbe_iterations",
                "b64_pbe_salt", "b64_encrypted_dek", "b64_encrypted_dek_iv", "updated_at");
        Runnable callback = this.onOAuthChanged;
        if (callback != null) {
            callback.run();
        }
    }

    public void disconnectOAuth(String provider) {
        RecoveryConfig rc = dbManager.queryFirst(RecoveryConfig.class, "where oauth_provider = ?", provider);
        if (rc != null) {
            rc.oauth_name = "";
            rc.oauth_email = "";
            rc.b64_uid_hash = "";
            rc.b64_uid_hash_hmac = "";
            rc.b64_encrypted_dek = "";
            rc.b64_encrypted_dek_iv = "";
            rc.updated_at = 0;
            dbManager.update(rc, "oauth_name", "oauth_email", "b64_uid_hash", "b64_uid_hash_hmac", "b64_encrypted_dek",
                    "b64_encrypted_dek_iv", "updated_at");
        }
    }

    /**
     * Return null if bad oauth.
     */
    public SecretKey unlockVaultByOAuth(OAuthUser oauthUser) {
        if (!isInitialized()) {
            throw new IllegalStateException("Vault not initialized.");
        }
        RecoveryConfig rc = dbManager.queryFirst(RecoveryConfig.class, "where oauth_provider = ?", oauthUser.provider);
        if (rc == null || rc.b64_uid_hash.isEmpty() || rc.b64_uid_hash_hmac.isEmpty()) {
            return null;
        }
        // check user id:
        final String oauthId = oauthUser.oauthId;
        byte[] uidHash = HashUtils.sha256(oauthId);
        if (!Base64Utils.b64(uidHash).equals(rc.b64_uid_hash)) {
            logger.warn("oauth id not match.");
            return null;
        }
        byte[] hmacKey = Base64Utils.b64(rc.b64_uid_hash_hmac);
        // derive AES key = pbe(pbeKey, salt, iterations):
        int pbe_iterations = rc.pbe_iterations;
        byte[] pbe_salt = Base64Utils.b64(rc.b64_pbe_salt);
        String password = HexFormat.of().formatHex(HashUtils.hmacSha256(oauthId, hmacKey));
        byte[] pbe_key = EncryptUtils.derivePbeKey(password.toCharArray(), pbe_salt, pbe_iterations);
        byte[] encrypted_dek_iv = Base64Utils.b64(rc.b64_encrypted_dek_iv);
        byte[] encrypted_dek = Base64Utils.b64(rc.b64_encrypted_dek);
        byte[] dek = null;
        try {
            dek = EncryptUtils.decrypt(encrypted_dek, EncryptUtils.bytesToAesKey(pbe_key), encrypted_dek_iv);
        } catch (EncryptException e) {
            return null;
        }
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

    /**
     * Change the master password. Verifies {@code oldPassword} first; on success
     * re-wraps the existing DEK with a new PBE key derived from {@code newPassword}
     * and updates the stored vault config. Returns {@code false} if the old
     * password is incorrect.
     */
    public boolean changeMasterPassword(String oldPassword, String newPassword) {
        if (!isInitialized()) {
            throw new IllegalStateException("Vault not initialized.");
        }
        SecretKey dekKey = unlockVault(oldPassword.toCharArray());
        if (dekKey == null) {
            return false;
        }
        encryptDEK(newPassword, dekKey.getEncoded());
        return true;
    }

    public void resetMasterPassword(String newPassword, SecretKey dek) {
        if (!isInitialized()) {
            throw new IllegalStateException("Vault not initialized.");
        }
        encryptDEK(newPassword, dek.getEncoded());
    }

    public void close() {
        this.dbManager.close();
        this.dbManager = null;
    }

}
