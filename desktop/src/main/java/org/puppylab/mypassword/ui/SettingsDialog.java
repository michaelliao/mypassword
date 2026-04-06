package org.puppylab.mypassword.ui;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.Daemon;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.SettingKey;
import org.puppylab.mypassword.core.entity.RecoveryConfig;

/**
 * Tab-based settings dialog opened from the tray menu.
 *
 * <ul>
 *   <li><b>General</b> — keep tray icon, language.</li>
 *   <li><b>Security</b> — auto-lock, clipboard clearing.</li>
 *   <li><b>Password</b> — change master password.</li>
 * </ul>
 */
public class SettingsDialog {

    private static final int MIN_PASSWORD_LEN = 8;
    private static final int MAX_PASSWORD_LEN = 50;

    private static final String[] LANGUAGES = { "", "en", "zh" };

    /** Auto-lock options in minutes; 0 = Never. */
    private static final int[] AUTO_LOCK_MINUTES = { 1, 5, 10, 30, 60, 0 };

    /** Clear-clipboard options in seconds; 0 = Never. */
    private static final int[] CLEAR_CLIPBOARD_SECONDS = { 60, 300, 600, 0 };

    private final Shell parent;

    public SettingsDialog(Shell parent) {
        this.parent = parent;
    }

    public void open(VaultManager vaultManager) {
        Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(i18n("settings.title"));
        shell.setSize(500, 520);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 12;
        gl.marginHeight = 12;
        shell.setLayout(gl);

        TabFolder tabs = new TabFolder(shell, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        buildGeneralTab(tabs, vaultManager);
        buildSecurityTab(tabs, vaultManager);
        buildPasswordTab(tabs, vaultManager);

        shell.open();
        shell.addListener(SWT.Dispose, _ -> vaultManager.setOnOAuthChanged(null));
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch())
                shell.getDisplay().sleep();
        }
    }

    // ── General tab ──────────────────────────────────────────────────────
    private void buildGeneralTab(TabFolder tabs, VaultManager vaultManager) {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText(i18n("settings.tab.general"));

        Composite c = new Composite(tabs, SWT.NONE);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 16;
        gl.marginHeight = 16;
        gl.horizontalSpacing = 12;
        gl.verticalSpacing = 12;
        c.setLayout(gl);
        item.setControl(c);

        // keep tray icon:
        Button trayCheck = new Button(c, SWT.CHECK);
        trayCheck.setText(i18n("settings.keep_tray_icon"));
        trayCheck.setSelection(vaultManager.getSetting(SettingKey.KEEP_TRAY_ICON, 1) != 0);
        GridData trayGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        trayGd.horizontalSpan = 2;
        trayCheck.setLayoutData(trayGd);

        // language:
        Label langLabel = new Label(c, SWT.NONE);
        langLabel.setText(i18n("settings.language"));
        langLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo langCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        langCombo.add(i18n("settings.language.system"));
        langCombo.add("English");
        langCombo.add("\u4e2d\u6587");
        String curLang = vaultManager.getSetting(SettingKey.LANGUAGE, "");
        int langIdx = 0;
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(curLang)) {
                langIdx = i;
                break;
            }
        }
        langCombo.select(langIdx);
        GridData langGd = new GridData(SWT.END, SWT.CENTER, false, false);
        langGd.widthHint = 180;
        langCombo.setLayoutData(langGd);

        // persist immediately on change:
        trayCheck.addListener(SWT.Selection,
                _ -> vaultManager.setSetting(SettingKey.KEEP_TRAY_ICON, trayCheck.getSelection() ? 1 : 0));
        langCombo.addListener(SWT.Selection,
                _ -> vaultManager.setSetting(SettingKey.LANGUAGE, LANGUAGES[langCombo.getSelectionIndex()]));
    }

    // ── Security tab ─────────────────────────────────────────────────────
    private void buildSecurityTab(TabFolder tabs, VaultManager vaultManager) {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText(i18n("settings.tab.security"));

        Composite c = new Composite(tabs, SWT.NONE);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 16;
        gl.marginHeight = 16;
        gl.horizontalSpacing = 12;
        gl.verticalSpacing = 12;
        c.setLayout(gl);
        item.setControl(c);

        // auto lock:
        Label autoLockLabel = new Label(c, SWT.NONE);
        autoLockLabel.setText(i18n("settings.auto_lock"));
        autoLockLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo autoLockCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (int m : AUTO_LOCK_MINUTES) {
            autoLockCombo.add(m == 0 ? i18n("settings.never") : i18n("settings.minutes", m));
        }
        autoLockCombo.select(indexOf(AUTO_LOCK_MINUTES, vaultManager.getSetting(SettingKey.AUTO_LOCK, 10)));
        GridData alGd = new GridData(SWT.END, SWT.CENTER, false, false);
        alGd.widthHint = 140;
        autoLockCombo.setLayoutData(alGd);

        // clear clipboard:
        Label clearLabel = new Label(c, SWT.NONE);
        clearLabel.setText(i18n("settings.clear_clipboard"));
        clearLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo clearCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (int s : CLEAR_CLIPBOARD_SECONDS) {
            clearCombo.add(s == 0 ? i18n("settings.never") : i18n("settings.minutes", s / 60));
        }
        clearCombo.select(indexOf(CLEAR_CLIPBOARD_SECONDS, vaultManager.getSetting(SettingKey.CLEAR_CLIPBOARD, 60)));
        GridData ccGd = new GridData(SWT.END, SWT.CENTER, false, false);
        ccGd.widthHint = 140;
        clearCombo.setLayoutData(ccGd);

        // persist immediately on change:
        autoLockCombo.addListener(SWT.Selection, _ -> vaultManager.setSetting(SettingKey.AUTO_LOCK,
                AUTO_LOCK_MINUTES[autoLockCombo.getSelectionIndex()]));
        clearCombo.addListener(SWT.Selection, _ -> vaultManager.setSetting(SettingKey.CLEAR_CLIPBOARD,
                CLEAR_CLIPBOARD_SECONDS[clearCombo.getSelectionIndex()]));
    }

    /** Return index of {@code value} in {@code values}, or 0 if not found. */
    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value)
                return i;
        }
        return 0;
    }

    // ── Password tab ─────────────────────────────────────────────────────
    private void buildPasswordTab(TabFolder tabs, VaultManager vaultManager) {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText(i18n("settings.tab.password"));

        Composite c = new Composite(tabs, SWT.NONE);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 16;
        gl.marginHeight = 16;
        gl.horizontalSpacing = 12;
        gl.verticalSpacing = 12;
        c.setLayout(gl);
        item.setControl(c);

        boolean oauthUnlocked = Session.current().getUnlockType() == Session.UnlockType.OAUTH;

        Text curText = null;
        if (!oauthUnlocked) {
            Label curLabel = new Label(c, SWT.NONE);
            curLabel.setText(i18n("settings.password.current"));
            curLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            curText = new Text(c, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
            curText.setTextLimit(MAX_PASSWORD_LEN);
            GridData curGd = new GridData(SWT.END, SWT.CENTER, false, false);
            curGd.widthHint = 220;
            curText.setLayoutData(curGd);
        }

        Label newLabel = new Label(c, SWT.NONE);
        newLabel.setText(i18n("settings.password.new"));
        newLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Text newText = new Text(c, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        newText.setTextLimit(MAX_PASSWORD_LEN);
        GridData newGd = new GridData(SWT.END, SWT.CENTER, false, false);
        newGd.widthHint = 220;
        newText.setLayoutData(newGd);

        Label cfmLabel = new Label(c, SWT.NONE);
        cfmLabel.setText(i18n("settings.password.confirm"));
        cfmLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Text cfmText = new Text(c, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        cfmText.setTextLimit(MAX_PASSWORD_LEN);
        GridData cfmGd = new GridData(SWT.END, SWT.CENTER, false, false);
        cfmGd.widthHint = 220;
        cfmText.setLayoutData(cfmGd);

        Label msgLabel = new Label(c, SWT.NONE);
        GridData msgGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        msgGd.horizontalSpan = 2;
        msgLabel.setLayoutData(msgGd);

        Button changeBtn = new Button(c, SWT.PUSH);
        changeBtn.setText(i18n("settings.password.btn.change"));
        GridData changeGd = new GridData(SWT.END, SWT.CENTER, true, false);
        changeGd.horizontalSpan = 2;
        changeGd.widthHint = 160;
        changeGd.heightHint = 30;
        changeBtn.setLayoutData(changeGd);

        final Text curTextRef = curText;
        changeBtn.addListener(SWT.Selection, _ -> {
            String nw = newText.getText();
            String cfm = cfmText.getText();
            if (nw.length() < MIN_PASSWORD_LEN) {
                msgLabel.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_RED));
                msgLabel.setText(i18n("settings.password.error.too_short", MIN_PASSWORD_LEN));
                c.layout(true, true);
                return;
            }
            if (!nw.equals(cfm)) {
                msgLabel.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_RED));
                msgLabel.setText(i18n("settings.password.error.mismatch"));
                c.layout(true, true);
                return;
            }
            if (oauthUnlocked) {
                // no old password needed — use DEK from session directly:
                vaultManager.resetMasterPassword(nw, Session.current().getKey());
            } else {
                String cur = curTextRef.getText();
                boolean ok = vaultManager.changeMasterPassword(cur, nw);
                if (!ok) {
                    msgLabel.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_RED));
                    msgLabel.setText(i18n("settings.password.error.wrong"));
                    c.layout(true, true);
                    return;
                }
                curTextRef.setText("");
            }
            newText.setText("");
            cfmText.setText("");
            msgLabel.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
            msgLabel.setText(i18n("settings.password.success"));
            c.layout(true, true);
        });

        // ── OAuth list ──────────────────────────────────────────────────
        Label separator = new Label(c, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData sepGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        sepGd.horizontalSpan = 2;
        sepGd.verticalIndent = 8;
        separator.setLayoutData(sepGd);

        Label oauthHint = new Label(c, SWT.WRAP);
        oauthHint.setText(i18n("settings.oauth.hint"));
        GridData hintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hintGd.horizontalSpan = 2;
        oauthHint.setLayoutData(hintGd);

        // container for OAuth rows (rebuilt on refresh):
        Composite oauthContainer = new Composite(c, SWT.NONE);
        GridLayout containerLayout = new GridLayout(1, false);
        containerLayout.marginWidth = 0;
        containerLayout.marginHeight = 0;
        oauthContainer.setLayout(containerLayout);
        GridData containerGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        containerGd.horizontalSpan = 2;
        oauthContainer.setLayoutData(containerGd);

        buildOAuthRows(oauthContainer, vaultManager);

        // register callback so HTTP-thread OAuth completion refreshes the UI:
        vaultManager.setOnOAuthChanged(() -> {
            c.getDisplay().asyncExec(() -> {
                if (c.isDisposed()) return;
                // dispose old rows and rebuild:
                for (var child : oauthContainer.getChildren()) {
                    child.dispose();
                }
                buildOAuthRows(oauthContainer, vaultManager);
                c.layout(true, true);
            });
        });
    }

    private void buildOAuthRows(Composite container, VaultManager vaultManager) {
        List<RecoveryConfig> configs = vaultManager.getRecoveryConfigs();
        for (RecoveryConfig rc : configs) {
            Composite row = new Composite(container, SWT.NONE);
            GridLayout rowLayout = new GridLayout(3, false);
            rowLayout.marginWidth = 0;
            rowLayout.marginHeight = 2;
            rowLayout.horizontalSpacing = 12;
            row.setLayout(rowLayout);
            row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            boolean loggedIn = rc.b64_uid_hash != null && !rc.b64_uid_hash.isEmpty();

            // provider name (capitalized):
            Label providerLabel = new Label(row, SWT.NONE);
            String displayName = Character.toUpperCase(rc.oauth_provider.charAt(0)) + rc.oauth_provider.substring(1);
            providerLabel.setText(displayName);
            providerLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            // logged-in-as status:
            Label statusLabel = new Label(row, SWT.NONE);
            if (loggedIn) {
                statusLabel.setText(formatOAuthStatus(rc));
            } else {
                statusLabel.setText(i18n("settings.oauth.not_logged_in"));
                statusLabel.setForeground(container.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            }
            statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            // action button:
            Button actionBtn = new Button(row, SWT.PUSH);
            GridData btnGd = new GridData(SWT.END, SWT.CENTER, false, false);
            btnGd.widthHint = 100;
            actionBtn.setLayoutData(btnGd);

            if (loggedIn) {
                actionBtn.setText(i18n("settings.oauth.btn.disconnect"));
                actionBtn.addListener(SWT.Selection, _ -> {
                    MessageBox mb = new MessageBox(container.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
                    mb.setText(i18n("confirm.title"));
                    mb.setMessage(i18n("settings.oauth.confirm.disconnect", displayName));
                    if (mb.open() != SWT.OK) return;
                    vaultManager.disconnectOAuth(rc.oauth_provider);
                    // dispose and rebuild:
                    for (var child : container.getChildren()) {
                        child.dispose();
                    }
                    buildOAuthRows(container, vaultManager);
                    container.getParent().layout(true, true);
                });
            } else {
                actionBtn.setText(i18n("settings.oauth.btn.login"));
                actionBtn.addListener(SWT.Selection, _ -> {
                    Program.launch("http://127.0.0.1:" + Daemon.PORT + "/oauth/" + rc.oauth_provider + "/start");
                });
            }
        }
    }

    private String formatOAuthStatus(RecoveryConfig rc) {
        String name = rc.oauth_name != null && !rc.oauth_name.isEmpty() ? rc.oauth_name : "";
        String email = rc.oauth_email != null && !rc.oauth_email.isEmpty() ? rc.oauth_email : "";
        if (!name.isEmpty() && !email.isEmpty()) {
            return name + " <" + email + ">";
        } else if (!email.isEmpty()) {
            return email;
        } else if (!name.isEmpty()) {
            return name;
        }
        return "Connected";
    }
}
