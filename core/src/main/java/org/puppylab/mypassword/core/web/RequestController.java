package org.puppylab.mypassword.core.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.Constants;
import org.puppylab.mypassword.core.EncryptUtils;
import org.puppylab.mypassword.core.ErrorUtils;
import org.puppylab.mypassword.core.IdUtils;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.data.ItemType;
import org.puppylab.mypassword.rpc.data.LoginFieldsData;
import org.puppylab.mypassword.rpc.data.LoginItemData;
import org.puppylab.mypassword.rpc.request.EmptyRequest;
import org.puppylab.mypassword.rpc.request.LoginItemRequest;
import org.puppylab.mypassword.rpc.request.VaultPasswordRequest;
import org.puppylab.mypassword.rpc.response.InfoResponse;
import org.puppylab.mypassword.rpc.response.LoginItemDataResponse;
import org.puppylab.mypassword.rpc.response.LoginItemsDataResponse;
import org.puppylab.mypassword.rpc.util.Base64Utils;
import org.puppylab.mypassword.rpc.util.FileUtils;
import org.puppylab.mypassword.rpc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestController {

    final Logger       logger = LoggerFactory.getLogger(getClass());
    final VaultManager vaultManager;

    public RequestController(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    /**
     * Spec:
     * 
     * Path is defined in @Post annotation;
     * 
     * The first parameter is BaseRequest or subclass;
     * 
     * The rest parameters are path variables;
     * 
     * Return BaseResponse or subclass.
     */
    @Post("/info")
    public BaseResponse info(EmptyRequest request) {
        return info();
    }

    @Post("/logins/list")
    public BaseResponse loginList(BaseRequest request) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        List<Item> items = vaultManager.getLoginItems();
        var response = new LoginItemsDataResponse();
        response.data = items.stream().map(li -> {
            LoginItemData lid = new LoginItemData();
            byte[] data = decryptItemData(key, li);
            JsonUtils.fillJson(data, lid);
            lid.id = li.id;
            lid.updated_at = li.updated_at;
            return lid;
        }).toList();
        return response;
    }

    @Post("/logins/create")
    public BaseResponse loginCreate(LoginItemRequest request) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        // check title:
        String title = normalize(request.title);
        if (title.isEmpty()) {
            return ErrorUtils.error(ErrorCode.BAD_FIELD, "Invalid field: title.");
        }
        // copy to lfd for json serialization:
        LoginFieldsData lfd = new LoginFieldsData();
        copy(request, lfd);
        Item item = new Item();
        encrypt(key, item, lfd);
        // prepare to insert into db:
        item.item_type = ItemType.LOGIN.value;
        item.id = IdUtils.nextId();
        item.deleted = false;
        item.updated_at = System.currentTimeMillis();
        this.vaultManager.createItem(item);
        // build response:
        LoginItemData lid = new LoginItemData();
        copy(lfd, lid);
        lid.id = item.id;
        lid.updated_at = item.updated_at;
        LoginItemDataResponse resp = new LoginItemDataResponse();
        resp.data = lid;
        return resp;
    }

    @Post("/logins/{id}/get")
    public BaseResponse loginGet(EmptyRequest request, String... args) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id;
        try {
            id = Long.parseLong(args[0]);
        } catch (Exception e) {
            return ErrorUtils.error(ErrorCode.DATA_NOT_FOUND, "Login item not found.");
        }
        Item item = this.vaultManager.getLoginItem(id);
        var lid = new LoginItemData();
        byte[] data = decryptItemData(key, item);
        JsonUtils.fillJson(data, lid);
        lid.id = item.id;
        lid.updated_at = item.updated_at;
        var resp = new LoginItemDataResponse();
        resp.data = lid;
        return resp;
    }

    @Post("/logins/{id}/update")
    public BaseResponse loginUpdate(LoginItemRequest request, String... args) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id;
        try {
            id = Long.parseLong(args[0]);
        } catch (Exception e) {
            return ErrorUtils.error(ErrorCode.DATA_NOT_FOUND, "Login item not found.");
        }
        LoginFieldsData lfd = new LoginFieldsData();
        copy(request, lfd);
        Item item = this.vaultManager.getLoginItem(id);
        encrypt(key, item, lfd);
        item.updated_at = System.currentTimeMillis();
        this.vaultManager.updateItem(item);
        // build response:
        LoginItemData lid = new LoginItemData();
        copy(lfd, lid);
        lid.id = item.id;
        lid.updated_at = item.updated_at;
        var resp = new LoginItemDataResponse();
        resp.data = lid;
        return resp;
    }

    @Post("/vault/init")
    public BaseResponse vaultInit(VaultPasswordRequest request) {
        if (vaultManager.isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault already initialized.");
        }
        if (request.password == null || request.password.length() < Constants.PASSWORD_MIN_LENGTH) {
            return ErrorUtils.error(ErrorCode.BAD_PASSWORD, "Bad password.");
        }
        SecretKey dek = vaultManager.initVault(request.password.toCharArray());
        Session.current().setKey(dek);
        logger.info("Vault initialized by provided password.");
        return info();
    }

    @Post("/vault/lock")
    public BaseResponse vaultLock(EmptyRequest request) {
        if (!vaultManager.isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault is not initialized.");
        }
        Session.current().setKey(null);
        return info();
    }

    @Post("/vault/unlock")
    public BaseResponse vaultUnlock(VaultPasswordRequest request) {
        if (!vaultManager.isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault is not initialized.");
        }
        if (request.password == null || request.password.length() < Constants.PASSWORD_MIN_LENGTH) {
            return ErrorUtils.error(ErrorCode.BAD_PASSWORD, "Bad password.");
        }
        SecretKey dek = vaultManager.unlockVault(request.password.toCharArray());
        if (dek == null) {
            return ErrorUtils.error(ErrorCode.BAD_PASSWORD, "Bad password.");
        }
        Session.current().setKey(dek);
        return info();
    }

    InfoResponse info() {
        var data = new InfoResponse.InfoData();
        data.initialized = this.vaultManager.isInitialized();
        data.locked = Session.current().isLocked();
        data.database = FileUtils.getDbFile().toString();
        var info = new InfoResponse();
        info.data = data;
        return info;
    }

    void copy(LoginFieldsData src, LoginFieldsData dest) {
        dest.title = normalize(src.title);
        dest.username = normalize(src.username);
        dest.password = normalize(src.password);
        dest.websites = normalize(src.websites);
        dest.ga = normalize(src.ga);
        dest.memo = notNull(src.memo);
    }

    String notNull(String str) {
        if (str == null) {
            return "";
        }
        return str;
    }

    List<String> normalize(List<String> strs) {
        if (strs == null || strs.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String str : strs) {
            String s = normalize(str);
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    String normalize(String str) {
        if (str == null) {
            return "";
        }
        return str.strip();
    }

    void encrypt(SecretKey key, Item item, Object fields) {
        String jsonData = JsonUtils.toJson(fields);
        byte[] data = jsonData.getBytes(StandardCharsets.UTF_8);
        byte[] iv = EncryptUtils.generateIV();
        byte[] encrypted = EncryptUtils.encrypt(data, key, iv);
        item.b64_encrypted_data = Base64Utils.b64(encrypted);
        item.b64_encrypted_data_iv = Base64Utils.b64(iv);
    }

    byte[] decryptItemData(SecretKey key, Item item) {
        byte[] encrypted = Base64Utils.b64(item.b64_encrypted_data);
        byte[] iv = Base64Utils.b64(item.b64_encrypted_data_iv);
        return EncryptUtils.decrypt(encrypted, key, iv);
    }
}
