package org.puppylab.mypassword.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.core.data.AbstractItemData;
import org.puppylab.mypassword.core.entity.RecoveryConfig;
import org.puppylab.mypassword.core.web.GetMapping;
import org.puppylab.mypassword.core.web.PathVariable;
import org.puppylab.mypassword.core.web.PostMapping;
import org.puppylab.mypassword.core.web.RequestBody;
import org.puppylab.mypassword.core.web.RequestParam;
import org.puppylab.mypassword.core.web.pkce.OAuthAuthenticator;
import org.puppylab.mypassword.core.web.pkce.OAuthUser;
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

    final Logger logger = LoggerFactory.getLogger(getClass());

    final Map<String, OAuthAuthenticator> authenticators = new HashMap<>();

    public RequestController() {
        // load recovery config:
        List<RecoveryConfig> rcs = VaultManager.getCurrent().getRecoveryConfigs();
        for (RecoveryConfig rc : rcs) {
            String provider = rc.oauth_provider;
            // find provider class: "google" -> "GoogleAuthenticator"
            String className = OAuthAuthenticator.class.getPackageName() + "."
                    + Character.toUpperCase(provider.charAt(0)) + provider.substring(1) + "Authenticator";
            try {
                Class<?> clazz = Class.forName(className);
                OAuthAuthenticator auth = (OAuthAuthenticator) clazz.getConstructor().newInstance();
                logger.info("add provider {}: {}", provider, clazz.getSimpleName());
                authenticators.put(provider, auth);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot find class '" + className + "' by provider: " + provider);
            }
        }

    }

    SecretKey getKey() {
        SecretKey key = Session.getCurrent().getKey();
        if (key == null) {
            throw new BadRequestException(ErrorCode.VAULT_LOCKED, "Vault is locked.");
        }
        return key;
    }

    @PostMapping("/info")
    public InfoResponse info() {
        var data = new InfoResponse.InfoData();
        data.initialized = VaultManager.getCurrent().isInitialized();
        data.locked = Session.getCurrent().isLocked();
        data.database = FileUtils.getDbFile().toString();
        var info = new InfoResponse();
        info.data = data;
        return info;
    }

    @GetMapping("/oauth/{provider}/start")
    public String oauthStart(@PathVariable("provider") String provider, @RequestParam("recover") boolean isRecover) {
        OAuthAuthenticator auth = authenticators.get(provider);
        if (auth == null) {
            return "<html><body>OAuth provider not found.</body></html>";
        }
        return "redirect:" + auth.startOAuth(isRecover);
    }

    @GetMapping("/oauth/{provider}/callback")
    public String oauthCallback(@PathVariable("provider") String provider, @RequestParam("code") String code) {
        VaultManager vaultManager = VaultManager.getCurrent();
        OAuthAuthenticator auth = authenticators.get(provider);
        if (auth == null) {
            return htmlPage("OAuth provider not found.");
        }
        logger.info("exchange code: {}", code);
        OAuthUser user = auth.exchangeOAuthId(code);
        if (user == null) {
            return htmlPage("OAuth login failed.");
        }
        String displayProvider = Character.toUpperCase(provider.charAt(0)) + provider.substring(1);
        // must check if oauth is used to recover key or encrypt key:
        if (auth.isRecoverMode()) {
            logger.info("unlock vault by oauth...");
            // decrypt dek:
            SecretKey dek = vaultManager.unlockVaultByOAuth(user);
            if (dek == null) {
                return htmlPage("Unlock vault by OAuth failed. Make sure you logged in with correct user account.");
            }
            Session.getCurrent().setKey(Session.UnlockType.OAUTH, dek);
            vaultManager.fireVaultUnlocked();
            return htmlPage("<p>You have successfully logged in " + displayProvider
                    + " and unlocked your vault.</p><p>Please reset your master password in Settings - Password.</p>");
        } else {
            logger.info("add oauth recovery...");
            // check if vault is unlocked (DEK available):
            SecretKey dek = Session.getCurrent().getKey();
            if (dek == null) {
                return htmlPage("Vault is locked. Please unlock your vault first.");
            }
            // save OAuth recovery:
            vaultManager.saveOAuthRecovery(provider, user.name, user.email, user.oauthId, dek);
            // display name:
            String displayUser = user.name != null && !user.name.isEmpty() ? user.name : "";
            String displayEmail = user.email != null && !user.email.isEmpty() ? " &lt;" + user.email + "&gt;" : "";
            return htmlPage("<p>You have successfully logged in " + displayProvider + " account " + displayUser
                    + displayEmail + ".</p><p>You can use your " + displayProvider
                    + " account to unlock your vault for emergency.</p>");
        }
    }

    /**
     * Get all items.
     */
    @PostMapping("/items/list")
    public BaseResponse list(@RequestBody BaseRequest request) {
        SecretKey key = getKey();
        List<AbstractItemData> items = VaultManager.getCurrent().getItems(key);
        var response = new ItemsResponse();
        response.items = items;
        return response;
    }

    @PostMapping("/items/create")
    public BaseResponse create(@RequestBody ItemRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = VaultManager.getCurrent().createItem(key, request.item);
        return response;
    }

    @PostMapping("/items/{id}/get")
    public BaseResponse itemGet(@PathVariable("id") long id, @RequestBody BaseRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = VaultManager.getCurrent().getItem(key, id);
        return response;
    }

    @PostMapping("/items/{id}/update")
    public BaseResponse itemUpdate(@PathVariable("id") long id, @RequestBody ItemRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        request.item.id = id;
        response.item = VaultManager.getCurrent().updateItem(key, request.item);
        return response;
    }

    @PostMapping("/items/{id}/delete")
    public BaseResponse itemDelete(@PathVariable("id") long id, @RequestBody BaseRequest request) {
        SecretKey key = getKey();
        var response = new ItemResponse();
        response.item = VaultManager.getCurrent().deleteItem(key, id);
        return response;
    }

    @PostMapping("/vault/lock")
    public BaseResponse vaultLock() {
        if (!VaultManager.getCurrent().isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault is not initialized.");
        }
        Session.getCurrent().lock();
        return info();
    }

    @PostMapping("/vault/unlock")
    public BaseResponse vaultUnlock(@RequestBody VaultPasswordRequest request) {
        if (!VaultManager.getCurrent().isInitialized()) {
            return ErrorUtils.error(ErrorCode.BAD_REQUEST, "Vault is not initialized.");
        }
        if (request.password == null || request.password.length() < Constants.PASSWORD_MIN_LENGTH) {
            return ErrorUtils.error(ErrorCode.BAD_PASSWORD, "Bad password.");
        }
        SecretKey dek = VaultManager.getCurrent().unlockVault(request.password.toCharArray());
        if (dek == null) {
            return ErrorUtils.error(ErrorCode.BAD_PASSWORD, "Bad password.");
        }
        Session.getCurrent().setKey(Session.UnlockType.PASSWORD, dek);
        VaultManager.getCurrent().fireVaultUnlocked();
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

    static String htmlPage(String body) {
        return "<html><body>" + body + "<p>You can now close this page.</p></body></html>";
    }
}
