package org.puppylab.mypassword.ui;

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
import java.util.List;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.IdentityFieldsData;
import org.puppylab.mypassword.core.data.IdentityItemData;
import org.puppylab.mypassword.core.data.ItemType;
import org.puppylab.mypassword.core.data.LoginFieldsData;
import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.NoteFieldsData;
import org.puppylab.mypassword.core.data.NoteItemData;

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
    public boolean open(VaultManager vaultManager) {
        Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText("Set Master Password");
        shell.setSize(400, 320);
        shell.setLayout(new GridLayout(1, false));

        // ── title ────────────────────────────────────────────────────────
        Label title = new Label(shell, SWT.CENTER);
        title.setText("Create Master Password");
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Font boldFont = deriveBoldFont(title, 3);
        title.setFont(boldFont);
        title.addListener(SWT.Dispose, _ -> boldFont.dispose());

        // ── hint ─────────────────────────────────────────────────────────
        Label hint = new Label(shell, SWT.CENTER | SWT.WRAP);
        hint.setText("This password protects your vault. It cannot be recovered if lost.");
        hint.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hintGd.widthHint = 360;
        hint.setLayoutData(hintGd);

        new Label(shell, SWT.NONE); // spacer

        // ── password field ───────────────────────────────────────────────
        Text passwordText = new Text(shell, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        passwordText.setMessage("Password (" + MIN_PASSWORD_LEN + "–" + MAX_PASSWORD_LEN + " chars)");
        passwordText.setTextLimit(MAX_PASSWORD_LEN);
        GridData pwGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pwGd.heightHint = 28;
        passwordText.setLayoutData(pwGd);

        // ── confirm field ─────────────────────────────────────────────────
        Text confirmText = new Text(shell, SWT.BORDER | SWT.PASSWORD | SWT.SINGLE);
        confirmText.setMessage("Confirm password");
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
        createBtn.setText("Create Vault");
        GridData createGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        createGd.widthHint = 120;
        createGd.heightHint = 30;
        createBtn.setLayoutData(createGd);
        createBtn.setEnabled(false);
        shell.setDefaultButton(createBtn);

        Button cancelBtn = new Button(btnRow, SWT.PUSH);
        cancelBtn.setText("Cancel");
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
                errorLabel.setText("Password must be at least " + MIN_PASSWORD_LEN + " characters.");
                shell.layout(true, true);
                return;
            }
            if (!pw.equals(cfm)) {
                errorLabel.setText("Passwords do not match.");
                confirmText.setText("");
                confirmText.setFocus();
                shell.layout(true, true);
                return;
            }
            SecretKey key = vaultManager.initVault(pw);
            insertSampleData(vaultManager, key);
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

    private static void insertSampleData(VaultManager vm, SecretKey key) {
        // logins:
        vm.createItem(key, newLogin("Google", "michael@gmail.com", "secret-google", List.of("https://google.com", "https://gmail.com"), true));
        vm.createItem(key, newLogin("GitHub", "michael-liao", "secret-github", List.of("https://github.com"), false));
        vm.createItem(key, newLogin("Amazon", "michael@gmail.com", "secret-amazon", List.of("https://amazon.com"), false));
        vm.createItem(key, newLogin("Netflix", "michael@gmail.com", "secret-netflix", List.of("https://netflix.com"), false));
        vm.createItem(key, newLogin("Twitter / X", "michael_liao", "secret-twitter", List.of("https://x.com"), false));
        vm.createItem(key, newLogin("LinkedIn", "michael.liao@work.com", "secret-linkedin", List.of("https://linkedin.com"), true));
        vm.createItem(key, newLogin("Dropbox", "michael@gmail.com", "secret-dropbox", List.of("https://dropbox.com"), false));
        vm.createItem(key, newLogin("Apple ID", "michael@icloud.com", "secret-apple", List.of("https://apple.com"), false));
        vm.createItem(key, newLogin("Microsoft", "michael@outlook.com", "secret-ms", List.of("https://microsoft.com"), false));
        vm.createItem(key, newLogin("Steam", "michael_games", "secret-steam", List.of("https://store.steampowered.com"), false));
        vm.createItem(key, newLogin("Spotify", "michael@gmail.com", "secret-spotify", List.of("https://spotify.com"), true));
        vm.createItem(key, newLogin("PayPal", "michael@gmail.com", "secret-paypal", List.of("https://paypal.com"), false));
        vm.createItem(key, newLogin("Slack", "michael.liao@work.com", "secret-slack", List.of("https://slack.com"), false));
        vm.createItem(key, newLogin("Notion", "michael.liao@work.com", "secret-notion", List.of("https://notion.so"), false));
        vm.createItem(key, newLogin("Adobe", "michael@gmail.com", "secret-adobe", List.of("https://adobe.com"), false));
        vm.createItem(key, newLogin("Figma", "michael.liao@work.com", "secret-figma", List.of("https://figma.com"), false));
        vm.createItem(key, newLogin("Cloudflare", "michael@gmail.com", "secret-cf", List.of("https://cloudflare.com"), false));
        vm.createItem(key, newLogin("Digital Ocean", "michael@gmail.com", "secret-do", List.of("https://digitalocean.com"), false));
        // notes:
        vm.createItem(key, newNote("Wi-Fi Password", "Router: TP-Link AX3000\nSSID: Home-5G\nPassword: xK9#mP2$vL", true));
        vm.createItem(key, newNote("Server SSH Keys", "prod-01: ssh michael@10.0.0.1\nprod-02: ssh michael@10.0.0.2", false));
        vm.createItem(key, newNote("Recovery Codes — Gmail", "1. 4829-3810\n2. 9271-5028\n3. 1847-6392", false));
        vm.createItem(key, newNote("API Keys", "Stripe live key: sk_live_abc123\nStripe test key: sk_test_xyz789", false));
        vm.createItem(key, newNote("Software Licenses", "JetBrains: ABCD-EFGH-IJKL\nSublime: 1234-5678-9012", false));
        vm.createItem(key, newNote("Home Alarm Code", "Front door: 8432\nGarage: 1597", true));
        vm.createItem(key, newNote("Bank Account Details", "IBAN: GB29 NWBK 6016 1331 9268 19", false));
        vm.createItem(key, newNote("Emergency Contacts", "Police: 110, Fire: 119, Ambulance: 120", false));
        // identities:
        vm.createItem(key, newIdentity("Personal Passport", "Michael Liao", "E12345678", null, List.of("+86 138-0000-1234"), true));
        vm.createItem(key, newIdentity("Work ID", "Michael Liao", null, null, List.of("+86 138-0000-1234"), false));
        vm.createItem(key, newIdentity("Driver License", "Michael Liao", null, null, null, false));
        vm.createItem(key, newIdentity("National ID", "Michael Liao", null, "110101199001011234", null, false));
    }

    private static LoginItemData newLogin(String title, String username, String password, List<String> websites, boolean fav) {
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

    private static NoteItemData newNote(String title, String content, boolean fav) {
        NoteItemData d = new NoteItemData();
        d.item_type = ItemType.NOTE;
        d.favorite = fav;
        d.data = new NoteFieldsData();
        d.data.title = title;
        d.data.content = content;
        return d;
    }

    private static IdentityItemData newIdentity(String name, String fullName, String passport, String idNumber, List<String> mobiles, boolean fav) {
        IdentityItemData d = new IdentityItemData();
        d.item_type = ItemType.IDENTITY;
        d.favorite = fav;
        d.data = new IdentityFieldsData();
        d.data.name = name;
        d.data.passport_number = passport;
        d.data.identity_number = idNumber;
        d.data.mobiles = mobiles;
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
