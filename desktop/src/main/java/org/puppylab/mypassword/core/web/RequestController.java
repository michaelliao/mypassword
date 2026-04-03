package org.puppylab.mypassword.core.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.Constants;
import org.puppylab.mypassword.core.EncryptUtils;
import org.puppylab.mypassword.core.ErrorUtils;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.AbstractFields;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.core.entity.Item;
import org.puppylab.mypassword.rpc.BadRequestException;
import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.request.ItemRequest;
import org.puppylab.mypassword.rpc.request.VaultPasswordRequest;
import org.puppylab.mypassword.rpc.response.InfoResponse;
import org.puppylab.mypassword.rpc.response.ItemResponse;
import org.puppylab.mypassword.rpc.response.ItemsResponse;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.FileUtils;
import org.puppylab.mypassword.util.JsonUtils;
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
    public BaseResponse info(BaseRequest request) {
        return info();
    }

    /**
     * Get all items.
     */
    @Post("/items/list")
    public BaseResponse list(BaseRequest request) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        List<Item> items = vaultManager.getRawItems();
        var response = new ItemsResponse();
        response.items = items.stream().map(item -> toItemData(key, item)).toList();
        return response;
    }

    @Post("/items/create")
    public BaseResponse create(ItemRequest request) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            throw new BadRequestException(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id = this.vaultManager.createItem(key, request.item);
        // build response:
        var response = new ItemResponse();
        response.item = this.vaultManager.getItem(key, id);
        return response;
    }

    @Post("/items/{id}/get")
    public BaseResponse itemGet(BaseRequest request, String... args) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            throw new BadRequestException(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id = getIdFromPath(args[0]);
        // build response:
        var response = new ItemResponse();
        response.item = this.vaultManager.getItem(key, id);
        return response;
    }

    @Post("/items/{id}/update")
    public BaseResponse itemUpdate(ItemRequest request, String... args) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            return ErrorUtils.error(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id = getIdFromPath(args[0]);
        this.vaultManager.updateItem(key, request.item);
        // build response:
        var response = new ItemResponse();
        response.item = this.vaultManager.getItem(key, id);
        return response;
    }

    @Post("/items/{id}/delete")
    public BaseResponse itemDelete(BaseRequest request, String... args) {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            throw new BadRequestException(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        long id = getIdFromPath(args[0]);
        this.vaultManager.deleteItem(id);
        var response = new BaseResponse();
        return response;
    }

    long getIdFromPath(String s) {
        long id;
        try {
            id = Long.parseLong(s);
        } catch (Exception e) {
            throw new BadRequestException(ErrorCode.DATA_NOT_FOUND, "Item not found.");
        }
        return id;
    }

    @Post("/vault/lock")
    public BaseResponse vaultLock(BaseRequest request) {
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

    /**
     * Convert db entity Item to LoginItemData, NoteItemData or IdentityItemData by
     * item_type.
     */
    AbstractItemData toItemData(SecretKey key, Item item) {
        byte[] d_data = decryptItemData(key, item);
        AbstractItemData itemData = switch (item.item_type) {
        case ItemType.LOGIN -> {
            var data = new LoginItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, LoginFieldsData.class);
            yield data;
        }
        case ItemType.NOTE -> {
            var data = new NoteItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, NoteFieldsData.class);
            yield data;
        }
        case ItemType.IDENTITY -> {
            var data = new IdentityItemData();
            // decrypt crypto fields:
            data.data = JsonUtils.fromJson(d_data, IdentityFieldsData.class);
            yield data;
        }
        default -> {
            throw new IllegalArgumentException("Invalid item type: " + item.item_type);
        }
        };
        // copy plain fields:
        itemData.id = item.id;
        itemData.item_type = item.item_type;
        itemData.deleted = item.deleted;
        itemData.favorite = item.favorite;
        itemData.updated_at = item.updated_at;
        return itemData;
    }

    void encrypt(SecretKey key, Item item, AbstractFields fields) {
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
