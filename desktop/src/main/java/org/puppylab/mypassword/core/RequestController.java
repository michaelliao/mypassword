package org.puppylab.mypassword.core;

import java.util.List;

import javax.crypto.SecretKey;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.web.GetMapping;
import org.puppylab.mypassword.core.web.PathVariable;
import org.puppylab.mypassword.core.web.PostMapping;
import org.puppylab.mypassword.core.web.RequestBody;
import org.puppylab.mypassword.core.web.RequestParam;
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

/**
 * Spring-annotation compatible controller for processing http request.
 */
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

    @PostMapping("/info")
    public InfoResponse info() {
        var data = new InfoResponse.InfoData();
        data.initialized = this.vaultManager.isInitialized();
        data.locked = Session.current().isLocked();
        data.database = FileUtils.getDbFile().toString();
        var info = new InfoResponse();
        info.data = data;
        return info;
    }

    @GetMapping("/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code) {
        return "<html><body>OAuth OK:</body></html>";
    }

    /**
     * Get all items.
     */
    @PostMapping("/items/list")
    public BaseResponse list(@RequestBody BaseRequest request) {
        SecretKey key = getKey();
        List<AbstractItemData> items = vaultManager.getItems(key);
        var response = new ItemsResponse();
        response.items = items;
        return response;
    }

    @PostMapping("/items/create")
    public BaseResponse create(@RequestBody ItemRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = this.vaultManager.createItem(key, request.item);
        return response;
    }

    @PostMapping("/items/{id}/get")
    public BaseResponse itemGet(@PathVariable("id") long id, @RequestBody BaseRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = this.vaultManager.getItem(key, id);
        return response;
    }

    @PostMapping("/items/{id}/update")
    public BaseResponse itemUpdate(@PathVariable("id") long id, @RequestBody ItemRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        request.item.id = id;
        response.item = this.vaultManager.updateItem(key, request.item);
        return response;
    }

    @PostMapping("/items/{id}/delete")
    public BaseResponse itemDelete(@PathVariable("id") long id, @RequestBody BaseRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = this.vaultManager.deleteItem(key, id);
        return response;
    }

    @PostMapping("/vault/lock")
    public BaseResponse vaultLock() {
        if (!vaultManager.isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault is not initialized.");
        }
        Session.current().setKey(null);
        return info();
    }

    @PostMapping("/vault/unlock")
    public BaseResponse vaultUnlock(@RequestBody VaultPasswordRequest request) {
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

    @PostMapping("/activate")
    public BaseResponse activate() {
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
}
