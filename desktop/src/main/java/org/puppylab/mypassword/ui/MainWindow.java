package org.puppylab.mypassword.ui;

import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.puppylab.mypassword.core.Daemon;
import org.puppylab.mypassword.core.DbManager;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.ui.controller.MainController;
import org.puppylab.mypassword.ui.view.EmptyView;
import org.puppylab.mypassword.ui.view.IdentityDetailView;
import org.puppylab.mypassword.ui.view.IdentityEditView;
import org.puppylab.mypassword.ui.view.ItemListView;
import org.puppylab.mypassword.ui.view.LoginDetailView;
import org.puppylab.mypassword.ui.view.LoginEditView;
import org.puppylab.mypassword.ui.view.NoteDetailView;
import org.puppylab.mypassword.ui.view.NoteEditView;
import org.puppylab.mypassword.ui.view.ToolbarView;
import org.puppylab.mypassword.ui.view.UnlockView;
import org.puppylab.mypassword.util.FileUtils;

public class MainWindow {

    public static void main(String[] args) {
        new MainWindow().open();
    }

    public void open() {
        // ── shared services ───────────────────────────────────────────────
        Daemon daemon = new Daemon();

        // ── bind port — exit if another instance is already running ──────────
        if (!daemon.listen()) {
            System.exit(1);
        }

        // ── main thread: owns the SWT Display ─────────────────────────────
        Display display = new Display();

        Path dbFile = FileUtils.getDbFile();
        DbManager dbManager = new DbManager(dbFile);
        VaultManager vaultManager = new VaultManager(dbManager);
        daemon.setVaultManager(vaultManager);

        if (!vaultManager.isInitialized()) {
            Shell initShell = new Shell(display);
            boolean initialized = new InitVaultDialog(initShell).open(vaultManager);
            initShell.dispose();
            if (!initialized) {
                display.dispose();
                System.exit(0);
            }
        }

        // ── start HTTP acceptor on a background daemon thread ─────────────
        Thread acceptor = new Thread(daemon::start, "http-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();

        // ── SWT shell ──────────────────────────────────────────────────────
        Shell shell = new Shell(display);
        shell.setText("MyPassword");
        shell.setSize(800, 600);
        shell.setLayout(new GridLayout(1, false));

        // Stop the HTTP service when the window closes
        shell.addListener(SWT.Dispose, e -> daemon.stop());

        // ── top-level stack: unlock vs. main content ───────────────────────
        Composite topContainer = new Composite(shell, SWT.NONE);
        topContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        StackLayout topStack = new StackLayout();
        topContainer.setLayout(topStack);

        UnlockView unlockView = new UnlockView(topContainer);

        // ── main content (shown after unlock) ─────────────────────────────
        Composite mainContent = new Composite(topContainer, SWT.NONE);
        mainContent.setLayout(new GridLayout(1, false));

        ToolbarView toolbar = new ToolbarView(mainContent);

        Composite body = new Composite(mainContent, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setLayout(new GridLayout(2, false));

        ItemListView listView = new ItemListView(body);

        Composite rightContainer = new Composite(body, SWT.NONE);
        rightContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        StackLayout rightStack = new StackLayout();
        rightContainer.setLayout(rightStack);

        EmptyView emptyView = new EmptyView(rightContainer);
        LoginDetailView loginDetailView = new LoginDetailView(rightContainer);
        NoteDetailView noteDetailView = new NoteDetailView(rightContainer);
        IdentityDetailView identityDetailView = new IdentityDetailView(rightContainer);
        LoginEditView loginEditView = new LoginEditView(rightContainer);
        NoteEditView noteEditView = new NoteEditView(rightContainer);
        IdentityEditView identityEditView = new IdentityEditView(rightContainer);

        MainController controller = new MainController(vaultManager, unlockView, topContainer, topStack, mainContent,
                toolbar, listView, emptyView, loginDetailView, noteDetailView, identityDetailView, loginEditView,
                noteEditView, identityEditView, rightContainer, rightStack);
        controller.init();

        // ── SWT event loop (main thread stays here) ────────────────────────
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        display.dispose();
    }
}
