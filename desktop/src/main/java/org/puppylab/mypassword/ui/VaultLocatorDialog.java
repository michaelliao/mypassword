package org.puppylab.mypassword.ui;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.util.FileUtils;

/**
 * Modal dialog shown when the vault file is missing or invalid. Lets the user
 * either point MyPassword at an existing vault file on disk or pick a folder
 * in which to create a fresh one. In either case, a pointer file
 * {@code ~/.mypassword/vault.path} is written with the chosen location so the
 * user can keep the real file anywhere they want (e.g. a OneDrive folder) and
 * have it sync on its own.
 */
public class VaultLocatorDialog {

    private static final String VAULT_FILE_NAME = "mypassword.db";

    private final Shell parent;

    public VaultLocatorDialog(Shell parent) {
        this.parent = parent;
    }

    /**
     * Open the dialog and block until the user chooses a vault or closes the
     * window. On success the pointer file has been written and
     * {@link FileUtils#getDbFile()} will resolve to the chosen path (which
     * may not yet exist — the caller still runs through {@code DbManager}
     * init to create and populate the schema).
     *
     * @return {@code true} if a vault has been located; {@code false} if the
     *         user closed the dialog and the app should exit.
     */
    public boolean open() {
        Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(i18n("locate.title"));
        shell.setSize(460, 240);
        shell.setLayout(new GridLayout(1, false));
        ShellUtil.setCenter(shell);

        Label title = new Label(shell, SWT.CENTER);
        title.setText(i18n("locate.heading"));
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Font boldFont = deriveBoldFont(title, 3);
        title.setFont(boldFont);
        title.addListener(SWT.Dispose, _ -> boldFont.dispose());

        Label hint = new Label(shell, SWT.CENTER | SWT.WRAP);
        hint.setText(i18n("locate.hint"));
        hint.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        GridData hintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        hintGd.widthHint = 420;
        hint.setLayoutData(hintGd);

        new Label(shell, SWT.NONE); // spacer

        Composite btnRow = new Composite(shell, SWT.NONE);
        btnRow.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        GridLayout btnGl = new GridLayout(2, true);
        btnGl.horizontalSpacing = 12;
        btnRow.setLayout(btnGl);

        Button openBtn = new Button(btnRow, SWT.PUSH);
        openBtn.setText(i18n("locate.btn.open"));
        GridData openGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        openGd.widthHint = 160;
        openGd.heightHint = 30;
        openBtn.setLayoutData(openGd);
        shell.setDefaultButton(openBtn);

        Button createBtn = new Button(btnRow, SWT.PUSH);
        createBtn.setText(i18n("locate.btn.create"));
        GridData createGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        createGd.widthHint = 160;
        createGd.heightHint = 30;
        createBtn.setLayoutData(createGd);

        Label errorLabel = new Label(shell, SWT.CENTER | SWT.WRAP);
        errorLabel.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_RED));
        errorLabel.setText("");
        GridData errGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        errGd.widthHint = 420;
        errorLabel.setLayoutData(errGd);

        // No cancel button — closing the window (title-bar X) leaves
        // result[0] = false and MainWindow exits.
        boolean[] result = { false };

        openBtn.addListener(SWT.Selection, _ -> {
            Path chosen = pickExistingVault(shell);
            if (chosen == null) {
                return;
            }
            if (!Files.isRegularFile(chosen)) {
                errorLabel.setText(i18n("locate.error.not_a_file"));
                shell.layout(true, true);
                return;
            }
            try {
                FileUtils.setVaultLocation(chosen);
                result[0] = true;
                shell.close();
            } catch (IOException ex) {
                errorLabel.setText(i18n("locate.error.pointer", ex.getMessage()));
                shell.layout(true, true);
            }
        });

        createBtn.addListener(SWT.Selection, _ -> {
            Path folder = pickNewVaultFolder(shell);
            if (folder == null) {
                return;
            }
            Path target = folder.resolve(VAULT_FILE_NAME);
            if (Files.exists(target)) {
                // Refuse silently — user should go back and pick "Open
                // existing vault..." instead of overwriting someone's vault.
                errorLabel.setText(i18n("locate.error.already_exists"));
                shell.layout(true, true);
                return;
            }
            try {
                // Leave the target non-existent; DbManager will create it via
                // SQLite JDBC and run the init schema once the pointer is in
                // place.
                FileUtils.setVaultLocation(target);
                result[0] = true;
                shell.close();
            } catch (IOException ex) {
                errorLabel.setText(i18n("locate.error.pointer", ex.getMessage()));
                shell.layout(true, true);
            }
        });

        shell.open();
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch()) {
                shell.getDisplay().sleep();
            }
        }

        return result[0];
    }

    private Path pickExistingVault(Shell shell) {
        FileDialog fd = new FileDialog(shell, SWT.OPEN);
        fd.setText(i18n("locate.open.title"));
        fd.setFilterNames(new String[] { i18n("locate.file.filter"), "All files (*.*)" });
        fd.setFilterExtensions(new String[] { "*.db", "*.*" });
        fd.setFileName(VAULT_FILE_NAME);
        String picked = fd.open();
        return picked == null ? null : Paths.get(picked).toAbsolutePath().normalize();
    }

    private Path pickNewVaultFolder(Shell shell) {
        DirectoryDialog dd = new DirectoryDialog(shell, SWT.OPEN);
        dd.setText(i18n("locate.create.title"));
        dd.setMessage(i18n("locate.create.message"));
        String picked = dd.open();
        return picked == null ? null : Paths.get(picked).toAbsolutePath().normalize();
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
