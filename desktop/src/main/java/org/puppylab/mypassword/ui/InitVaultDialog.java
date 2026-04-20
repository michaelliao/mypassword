package org.puppylab.mypassword.ui;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.util.List;

import javax.crypto.SecretKey;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;
import org.puppylab.mypassword.util.ShellUtils;

/**
 * Modal dialog shown on first launch to set the master password and initialize
 * the vault. Blocks until the user completes setup or cancels (which exits the
 * app).
 */
public class InitVaultDialog {

    private static final int MIN_PASSWORD_LEN = 8;
    private static final int MAX_PASSWORD_LEN = 50;

    private final Shell parent;

    public InitVaultDialog(Shell parent) {
        this.parent = parent;
    }

    /**
     * Opens the dialog and initializes the vault on success. Returns {@code false}
     * if the user cancelled (caller should exit).
     */
    public boolean open() {
        Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(i18n("init.title"));
        shell.setSize(400, 320);
        shell.setLayout(new GridLayout(1, false));
        ShellUtils.setCenter(shell);

        // ── title ────────────────────────────────────────────────────────
        Label title = new Label(shell, SWT.CENTER);
        title.setText(i18n("init.heading"));
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Font boldFont = deriveBoldFont(title, 3);
        title.setFont(boldFont);
        title.addListener(SWT.Dispose, _ -> boldFont.dispose());

        // ── hint ─────────────────────────────────────────────────────────
        Label hint = new Label(shell, SWT.CENTER | SWT.WRAP);
        hint.setText(i18n("init.hint"));
        hint.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hintGd.widthHint = 360;
        hint.setLayoutData(hintGd);

        new Label(shell, SWT.NONE); // spacer

        // ── password field ───────────────────────────────────────────────
        Text passwordText = new Text(shell, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        passwordText.setMessage(i18n("init.password.placeholder", MIN_PASSWORD_LEN, MAX_PASSWORD_LEN));
        passwordText.setTextLimit(MAX_PASSWORD_LEN);
        GridData pwGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pwGd.heightHint = 28;
        passwordText.setLayoutData(pwGd);

        // ── confirm field ─────────────────────────────────────────────────
        Text confirmText = new Text(shell, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        confirmText.setMessage(i18n("init.confirm.placeholder"));
        confirmText.setTextLimit(MAX_PASSWORD_LEN);
        GridData cfGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        cfGd.heightHint = 28;
        confirmText.setLayoutData(cfGd);

        // ── error label ───────────────────────────────────────────────────
        Label errorLabel = new Label(shell, SWT.CENTER);
        errorLabel.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_RED));
        errorLabel.setText("");
        errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // ── buttons ───────────────────────────────────────────────────────
        Composite btnRow = new Composite(shell, SWT.NONE);
        btnRow.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        GridLayout btnGl = new GridLayout(2, true);
        btnGl.horizontalSpacing = 12;
        btnRow.setLayout(btnGl);

        Button createBtn = new Button(btnRow, SWT.PUSH);
        createBtn.setText(i18n("init.btn.create"));
        GridData createGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        createGd.widthHint = 120;
        createGd.heightHint = 30;
        createBtn.setLayoutData(createGd);
        createBtn.setEnabled(false);
        shell.setDefaultButton(createBtn);

        Button cancelBtn = new Button(btnRow, SWT.PUSH);
        cancelBtn.setText(i18n("init.btn.cancel"));
        GridData cancelGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        cancelGd.widthHint = 120;
        cancelGd.heightHint = 30;
        cancelBtn.setLayoutData(cancelGd);

        // ── result ────────────────────────────────────────────────────────
        boolean[] result = { false };

        // ── enable button only when password length is sufficient ─────────
        Runnable updateBtn = () -> createBtn.setEnabled(passwordText.getText().length() >= MIN_PASSWORD_LEN);
        passwordText.addListener(SWT.Modify, _ -> updateBtn.run());

        // ── submit logic ──────────────────────────────────────────────────
        Runnable submit = () -> {
            String pw = passwordText.getText();
            String cfm = confirmText.getText();
            if (pw.length() < MIN_PASSWORD_LEN) {
                errorLabel.setText(i18n("init.error.too_short", MIN_PASSWORD_LEN));
                shell.layout(true, true);
                return;
            }
            if (!pw.equals(cfm)) {
                errorLabel.setText(i18n("init.error.mismatch"));
                confirmText.setText("");
                confirmText.setFocus();
                shell.layout(true, true);
                return;
            }
            SecretKey key = VaultManager.getCurrent().initVault(pw);
            insertSampleData(key);
            result[0] = true;
            shell.close();
        };

        passwordText.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN && createBtn.isEnabled())
                submit.run();
        });
        confirmText.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN && createBtn.isEnabled())
                submit.run();
        });
        createBtn.addListener(SWT.Selection, _ -> submit.run());
        cancelBtn.addListener(SWT.Selection, _ -> shell.close());

        // ── run modal loop ────────────────────────────────────────────────
        shell.open();
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch())
                shell.getDisplay().sleep();
        }

        return result[0];
    }

    private static void insertSampleData(SecretKey key) {
        VaultManager vm = VaultManager.getCurrent();
        // logins:
        vm.createItem(key, newLogin("MyPassword", "example@puppylab.org", "my-password-for-test-1",
                List.of("https://mypassword.puppylab.org/login.html"), true));
        vm.createItem(key, newLogin("MyPassword", "test@puppylab.org", "my-password-for-test-2",
                List.of("https://mypassword.puppylab.org/login.html"), false));
        // notes:
        vm.createItem(key, newNote("Wi-Fi Password", "SSID: Home-5G\nPassword: 12345678"));
        vm.createItem(key, newNote("Software License",
                "MyPassword is a free, open source desktop password manager.\nSource: https://github.com/michaelliao/mypassword\nLicense: GPLv3"));
        // identities:
        vm.createItem(key,
                newIdentity("Simpson", "ChunkyLover53@aol.com", "E-1234567890", "ID-1234567890",
                        List.of("+1 123456789"), "742 Evergreen Terrace, Springfield", "58008",
                        "an overweight, lazy, and often ignorant, yet deeply devoted man."));
    }

    private static LoginItemData newLogin(String title, String username, String password, List<String> websites,
            boolean fav) {
        LoginItemData d = new LoginItemData();
        d.item_type = ItemType.LOGIN;
        d.favorite = fav;
        d.data = new LoginFieldsData();
        d.data.title = title;
        d.data.username = username;
        d.data.password = password;
        d.data.websites = websites;
        return d;
    }

    private static NoteItemData newNote(String title, String content) {
        NoteItemData d = new NoteItemData();
        d.item_type = ItemType.NOTE;
        d.data = new NoteFieldsData();
        d.data.title = title;
        d.data.content = content;
        return d;
    }

    private static IdentityItemData newIdentity(String name, String email, String passport, String idNumber,
            List<String> mobiles, String address, String zipCode, String memo) {
        IdentityItemData d = new IdentityItemData();
        d.item_type = ItemType.IDENTITY;
        d.data = new IdentityFieldsData();
        d.data.name = name;
        d.data.email = email;
        d.data.passport_number = passport;
        d.data.identity_number = idNumber;
        d.data.mobiles = mobiles;
        d.data.address = address;
        d.data.zip_code = zipCode;
        d.data.memo = memo;
        return d;
    }

    private static Font deriveBoldFont(Label base, int extraPoints) {
        FontData[] fds = base.getFont().getFontData();
        for (FontData fd : fds) {
            fd.setHeight(fd.getHeight() + extraPoints);
            fd.setStyle(SWT.BOLD);
        }
        return new Font(base.getDisplay(), fds);
    }
}
