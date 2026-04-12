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
import org.puppylab.mypassword.core.HttpDaemon;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.SettingKey;
import org.puppylab.mypassword.core.entity.ExtensionConfig;
import org.puppylab.mypassword.core.entity.RecoveryConfig;

/**
 * Tab-based settings dialog opened from the tray menu.
 *
 * <ul>
 * <li><b>General</b> — keep tray icon, language.</li>
 * <li><b>Security</b> — auto-lock, clipboard clearing.</li>
 * <li><b>Password</b> — change master password.</li>
 * </ul>
 */
public class SettingsDialog {

    private static final int MIN_PASSWORD_LEN = 8;
    private static final int MAX_PASSWORD_LEN = 50;

    private static final String[] LANGUAGES = { "", "en", "zh" };

    /** Auto-lock options in minutes; 0 = Never. */
    private static final int[] AUTO_LOCK_MINUTES = { 1, 2, 5, 10, 15, 30, 60, 0 };

    /** Clear-clipboard options in minutes; 0 = Never. */
    private static final int[] CLEAR_CLIPBOARD_MINUTES = { 1, 2, 5, 0 };

    /** Delete-trashed-items options in days; 0 = Never. */
    private static final int[] DELETE_AFTER_DAYS = { 7, 14, 30, 90, 180, 0 };

    private final Shell parent;

    public SettingsDialog(Shell parent) {
        this.parent = parent;
    }

    public void open() {
        Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(i18n("settings.title"));
        shell.setSize(580, 520);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 12;
        gl.marginHeight = 12;
        shell.setLayout(gl);

        TabFolder tabs = new TabFolder(shell, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        buildGeneralTab(tabs);
        buildSecurityTab(tabs);
        buildPasswordTab(tabs);
        buildExtensionTab(tabs);

        shell.open();
        shell.addListener(SWT.Dispose, _ -> VaultManager.getCurrent().setOnOAuthChanged(null));
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch())
                shell.getDisplay().sleep();
        }
    }

    // ── General tab ──────────────────────────────────────────────────────
    private void buildGeneralTab(TabFolder tabs) {
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
        trayCheck.setSelection(VaultManager.getCurrent().getSetting(SettingKey.KEEP_TRAY_ICON, 1) != 0);
        GridData trayGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        trayGd.horizontalSpan = 2;
        trayCheck.setLayoutData(trayGd);

        // language:
        Label langLabel = new Label(c, SWT.NONE);
        langLabel.setText(i18n("settings.language"));
        langLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo langCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        String curLang = VaultManager.getCurrent().getSetting(SettingKey.LANGUAGE, "");
        int langIdx = 0;
        for (int i = 0; i < LANGUAGES.length; i++) {
            String code = LANGUAGES[i];
            langCombo.add(code.isEmpty() ? i18n("settings.language.system") : i18n("settings.language.name." + code));
            if (code.equals(curLang)) {
                langIdx = i;
            }
        }
        langCombo.select(langIdx);
        GridData langGd = new GridData(SWT.END, SWT.CENTER, false, false);
        langGd.widthHint = 180;
        langCombo.setLayoutData(langGd);

        // delete items in trash after:
        Label deleteAfterLabel = new Label(c, SWT.NONE);
        deleteAfterLabel.setText(i18n("settings.delete_after"));
        deleteAfterLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo deleteAfterCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (int d : DELETE_AFTER_DAYS) {
            deleteAfterCombo.add(d == 0 ? i18n("settings.never") : i18n("settings.days", d));
        }
        deleteAfterCombo
                .select(indexOf(DELETE_AFTER_DAYS, VaultManager.getCurrent().getSetting(SettingKey.DELETE_AFTER, 90)));
        GridData daGd = new GridData(SWT.END, SWT.CENTER, false, false);
        daGd.widthHint = 180;
        deleteAfterCombo.setLayoutData(daGd);

        // persist immediately on change:
        trayCheck.addListener(SWT.Selection,
                _ -> VaultManager.getCurrent().setSetting(SettingKey.KEEP_TRAY_ICON, trayCheck.getSelection() ? 1 : 0));
        langCombo.addListener(SWT.Selection, _ -> VaultManager.getCurrent().setSetting(SettingKey.LANGUAGE,
                LANGUAGES[langCombo.getSelectionIndex()]));
        deleteAfterCombo.addListener(SWT.Selection, _ -> VaultManager.getCurrent().setSetting(SettingKey.DELETE_AFTER,
                DELETE_AFTER_DAYS[deleteAfterCombo.getSelectionIndex()]));
    }

    // ── Security tab ─────────────────────────────────────────────────────
    private void buildSecurityTab(TabFolder tabs) {
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
        autoLockCombo
                .select(indexOf(AUTO_LOCK_MINUTES, VaultManager.getCurrent().getSetting(SettingKey.AUTO_LOCK, 10)));
        GridData alGd = new GridData(SWT.END, SWT.CENTER, false, false);
        alGd.widthHint = 140;
        autoLockCombo.setLayoutData(alGd);

        // clear clipboard:
        Label clearLabel = new Label(c, SWT.NONE);
        clearLabel.setText(i18n("settings.clear_clipboard"));
        clearLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Combo clearCombo = new Combo(c, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (int s : CLEAR_CLIPBOARD_MINUTES) {
            clearCombo.add(s == 0 ? i18n("settings.never") : i18n("settings.minutes", s));
        }
        clearCombo.select(
                indexOf(CLEAR_CLIPBOARD_MINUTES, VaultManager.getCurrent().getSetting(SettingKey.CLEAR_CLIPBOARD, 1)));
        GridData ccGd = new GridData(SWT.END, SWT.CENTER, false, false);
        ccGd.widthHint = 140;
        clearCombo.setLayoutData(ccGd);

        // persist immediately on change:
        autoLockCombo.addListener(SWT.Selection, _ -> VaultManager.getCurrent().setSetting(SettingKey.AUTO_LOCK,
                AUTO_LOCK_MINUTES[autoLockCombo.getSelectionIndex()]));
        clearCombo.addListener(SWT.Selection, _ -> VaultManager.getCurrent().setSetting(SettingKey.CLEAR_CLIPBOARD,
                CLEAR_CLIPBOARD_MINUTES[clearCombo.getSelectionIndex()]));
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
    private void buildPasswordTab(TabFolder tabs) {
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

        boolean oauthUnlocked = Session.getCurrent().getUnlockType() == Session.UnlockType.OAUTH;

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
                VaultManager.getCurrent().resetMasterPassword(nw, Session.getCurrent().getKey());
            } else {
                String cur = curTextRef.getText();
                boolean ok = VaultManager.getCurrent().changeMasterPassword(cur, nw);
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

        buildOAuthRows(oauthContainer);

        // register callback so HTTP-thread OAuth completion refreshes the UI:
        VaultManager.getCurrent().setOnOAuthChanged(() -> {
            c.getDisplay().asyncExec(() -> {
                if (c.isDisposed())
                    return;
                // dispose old rows and rebuild:
                for (var child : oauthContainer.getChildren()) {
                    child.dispose();
                }
                buildOAuthRows(oauthContainer);
                c.layout(true, true);
            });
        });
    }

    // ── Extension tab ─────────────────────────────────────────────────
    private void buildExtensionTab(TabFolder tabs) {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText("Extension");

        Composite c = new Composite(tabs, SWT.NONE);
        GridLayout gl = new GridLayout(3, false);
        gl.marginWidth = 16;
        gl.marginHeight = 16;
        gl.horizontalSpacing = 12;
        gl.verticalSpacing = 6;
        c.setLayout(gl);
        item.setControl(c);

        // header row:
        Label nameHeader = new Label(c, SWT.NONE);
        nameHeader.setText("Name");
        GridData nameHGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        nameHGd.widthHint = 200;
        nameHeader.setLayoutData(nameHGd);

        Label deviceHeader = new Label(c, SWT.NONE);
        deviceHeader.setText("Device");
        deviceHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // placeholder for action column:
        Label actionHeader = new Label(c, SWT.NONE);
        actionHeader.setText("");
        GridData actionGd = new GridData(SWT.END, SWT.CENTER, false, false);
        actionGd.widthHint = 160;
        actionHeader.setLayoutData(actionGd);

        // separator:
        Label sep = new Label(c, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData sepGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        sepGd.horizontalSpan = 3;
        sep.setLayoutData(sepGd);

        buildExtensionRows(c);

        // description with clickable link:
        Label desc = new Label(c, SWT.NONE);
        desc.setText("To get extensions please visit:");
        desc.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData descGd = new GridData(SWT.LEFT, SWT.CENTER, true, false);
        descGd.horizontalSpan = 3;
        descGd.verticalIndent = 8;
        desc.setLayoutData(descGd);

        Label link = new Label(c, SWT.NONE);
        link.setText("https://mypassword.puppylab.org/extension/");
        link.setForeground(c.getDisplay().getSystemColor(SWT.COLOR_LINK_FOREGROUND));
        link.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        GridData linkGd = new GridData(SWT.LEFT, SWT.TOP, true, false);
        linkGd.horizontalSpan = 3;
        link.setLayoutData(linkGd);
        link.addListener(SWT.MouseUp, _ -> Program.launch("https://mypassword.puppylab.org/extension/"));
    }

    private void buildExtensionRows(Composite container) {
        List<ExtensionConfig> extensions = VaultManager.getCurrent().getExtensions();
        if (extensions.isEmpty()) {
            Label empty = new Label(container, SWT.NONE);
            empty.setText("No extensions paired.");
            empty.setForeground(container.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            GridData emptyGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
            emptyGd.horizontalSpan = 3;
            empty.setLayoutData(emptyGd);
            return;
        }
        for (ExtensionConfig ec : extensions) {
            Label nameLabel = new Label(container, SWT.NONE);
            nameLabel.setText(ec.name);
            GridData nameGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            nameGd.widthHint = 200;
            nameLabel.setLayoutData(nameGd);

            Label deviceLabel = new Label(container, SWT.NONE);
            deviceLabel.setText(ec.device);
            deviceLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            Composite btnComposite = new Composite(container, SWT.NONE);
            GridLayout btnLayout = new GridLayout(2, false);
            btnLayout.marginWidth = 0;
            btnLayout.marginHeight = 0;
            btnLayout.horizontalSpacing = 4;
            btnComposite.setLayout(btnLayout);
            GridData btnContainerGd = new GridData(SWT.END, SWT.CENTER, false, false);
            btnContainerGd.widthHint = 160;
            btnComposite.setLayoutData(btnContainerGd);

            if (ec.approve) {
                Button unpairBtn = new Button(btnComposite, SWT.PUSH);
                unpairBtn.setText("Unpair");
                GridData unpairGd = new GridData(SWT.END, SWT.CENTER, true, false);
                unpairGd.widthHint = 75;
                unpairBtn.setLayoutData(unpairGd);
                unpairBtn.addListener(SWT.Selection, _ -> {
                    MessageBox mb = new MessageBox(container.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
                    mb.setText(i18n("confirm.title"));
                    mb.setMessage("Unpair extension \"" + ec.name + "\"?");
                    if (mb.open() != SWT.OK)
                        return;
                    VaultManager.getCurrent().approveExtension(ec.id, false);
                    rebuildExtensionRows(container);
                });
            } else {
                Button approveBtn = new Button(btnComposite, SWT.PUSH);
                approveBtn.setText("Approve");
                GridData approveGd = new GridData(SWT.END, SWT.CENTER, false, false);
                approveGd.widthHint = 75;
                approveBtn.setLayoutData(approveGd);

                Button rejectBtn = new Button(btnComposite, SWT.PUSH);
                rejectBtn.setText("Reject");
                GridData rejectGd = new GridData(SWT.END, SWT.CENTER, false, false);
                rejectGd.widthHint = 75;
                rejectBtn.setLayoutData(rejectGd);

                approveBtn.addListener(SWT.Selection, _ -> {
                    VaultManager.getCurrent().approveExtension(ec.id, true);
                    rebuildExtensionRows(container);
                });
                rejectBtn.addListener(SWT.Selection, _ -> {
                    VaultManager.getCurrent().approveExtension(ec.id, false);
                    rebuildExtensionRows(container);
                });
            }
        }
    }

    private void rebuildExtensionRows(Composite container) {
        // dispose only data rows (skip header labels and separator = first 4 children)
        var children = container.getChildren();
        for (int i = 4; i < children.length; i++) {
            children[i].dispose();
        }
        buildExtensionRows(container);
        container.layout(true, true);
    }

    private void buildOAuthRows(Composite container) {
        List<RecoveryConfig> configs = VaultManager.getCurrent().getRecoveryConfigs();
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
                    if (mb.open() != SWT.OK)
                        return;
                    VaultManager.getCurrent().disconnectOAuth(rc.oauth_provider);
                    // dispose and rebuild:
                    for (var child : container.getChildren()) {
                        child.dispose();
                    }
                    buildOAuthRows(container);
                    container.getParent().layout(true, true);
                });
            } else {
                actionBtn.setText(i18n("settings.oauth.btn.login"));
                actionBtn.addListener(SWT.Selection, _ -> {
                    Program.launch("http://127.0.0.1:" + HttpDaemon.PORT + "/oauth/" + rc.oauth_provider + "/start");
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
