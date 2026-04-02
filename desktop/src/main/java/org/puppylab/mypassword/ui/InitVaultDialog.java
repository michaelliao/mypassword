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
import org.puppylab.mypassword.core.VaultManager;

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
            vaultManager.initVault(pw.toCharArray());
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

    private static Font deriveBoldFont(Label base, int extraPoints) {
        FontData[] fds = base.getFont().getFontData();
        for (FontData fd : fds) {
            fd.setHeight(fd.getHeight() + extraPoints);
            fd.setStyle(SWT.BOLD);
        }
        return new Font(base.getDisplay(), fds);
    }
}
