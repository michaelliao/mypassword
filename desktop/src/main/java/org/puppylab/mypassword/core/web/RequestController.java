package org.puppylab.mypassword.core.web;

import java.util.List;

import javax.crypto.SecretKey;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.core.Constants;
import org.puppylab.mypassword.core.ErrorUtils;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.rpc.BadRequestException;
import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.request.ItemRequest;
import org.puppylab.mypassword.rpc.request.VaultPasswordRequest;
import org.puppylab.mypassword.rpc.response.InfoResponse;
import org.puppylab.mypassword.rpc.response.ItemResponse;
import org.puppylab.mypassword.rpc.response.ItemsResponse;
import org.puppylab.mypassword.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestController {

    final Logger       logger = LoggerFactory.getLogger(getClass());
    final VaultManager vaultManager;

    public RequestController(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    SecretKey getKey() {
        SecretKey key = Session.current().getKey();
        if (key == null) {
            throw new BadRequestException(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        return key;
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
        SecretKey key = getKey();
        List<AbstractItemData> items = vaultManager.getItems(key);
        var response = new ItemsResponse();
        response.items = items;
        return response;
    }

    @Post("/items/create")
    public BaseResponse create(ItemRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = this.vaultManager.createItem(key, request.item);
        return response;
    }

    @Post("/items/{id}/get")
    public BaseResponse itemGet(BaseRequest request, String... args) {
        SecretKey key = getKey();
        long id = getIdFromPath(args[0]);
        var response = new ItemResponse();
        response.item = this.vaultManager.getItem(key, id);
        return response;
    }

    @Post("/items/{id}/update")
    public BaseResponse itemUpdate(ItemRequest request, String... args) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = this.vaultManager.updateItem(key, request.item);
        return response;
    }

    @Post("/items/{id}/delete")
    public BaseResponse itemDelete(BaseRequest request, String... args) {
        SecretKey key = getKey();
        long id = getIdFromPath(args[0]);
        var response = new ItemResponse();
        response.item = this.vaultManager.deleteItem(key, id);
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

    @Post("/activate")
    public BaseResponse activate(BaseRequest request) {
        Display display = Display.getDefault();
        display.asyncExec(() -> {
            Shell shell = display.getActiveShell();
            if (shell == null) {
                Shell[] shells = display.getShells();
                if (shells.length > 0) {
                    shell = shells[0];
                }
            }
            if (shell != null) {
                shell.setMinimized(false);
                shell.setActive();
                shell.forceActive();
            }
        });
        return new BaseResponse();
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
}
