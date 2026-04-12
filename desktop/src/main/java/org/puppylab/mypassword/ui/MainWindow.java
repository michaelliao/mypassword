package org.puppylab.mypassword.ui;

import static org.puppylab.mypassword.util.I18nUtils.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.puppylab.mypassword.core.ClearPasswordThread;
import org.puppylab.mypassword.core.DbManager;
import org.puppylab.mypassword.core.HttpDaemon;
import org.puppylab.mypassword.core.Session;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.core.data.SettingKey;
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
import org.puppylab.mypassword.util.I18nUtils;

public class MainWindow {

    public static void main(String[] args) {
        new MainWindow().open();
    }

    public void open() {
        // ── shared services ───────────────────────────────────────────────
        HttpDaemon daemon = new HttpDaemon();

        // ── bind port — exit if another instance is already running ──────────
        if (!daemon.listen()) {
            // activate the existing instance's window before exiting:
            try (var client = HttpClient.newHttpClient()) {
                client.send(
                        HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + HttpDaemon.PORT + "/activate"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                // ignore — existing instance may not be reachable
            }
            System.exit(1);
        }

        // ── locate vault ──────────────────────────────────────────────────
        // ~/.mypassword is either created or validated (throws if the path
        // exists but isn't a directory). The db path is resolved via the
        // pointer file (~/.mypassword/vault.path) if present, otherwise
        // ask user to choose.

        // ── main thread: owns the SWT Display ─────────────────────────────
        // Display is created before touching DbManager so the locator dialog
        // (shown when the vault file is missing / unlocated) has a Display to
        // attach to. i18n is initialised with the system locale here — the
        // user's saved preference is applied later, once the vault is open.
        Display display = new Display();
        I18nUtils.init("");

        Path configuredDbFile = FileUtils.getDbFile();
        if (configuredDbFile == null || !FileUtils.isValidVaultFile(configuredDbFile)) {
            Shell locatorShell = new Shell(display);
            boolean located = new VaultLocatorDialog(locatorShell).open();
            locatorShell.dispose();
            if (!located) {
                display.dispose();
                System.exit(0);
            }
        }

        // getDbFile() is re-resolved here so it picks up the pointer file
        // written by the locator dialog (if the user went through it).
        Path dbFile = FileUtils.getDbFile();
        DbManager dbManager = new DbManager(dbFile);
        VaultManager vaultManager = new VaultManager(dbManager);
        // Re-initialise i18n with the saved language setting so the rest of
        // the UI (InitVaultDialog, MainWindow shell, views) honours it.
        I18nUtils.init(vaultManager.getSetting(SettingKey.LANGUAGE, ""));
        Session.getCurrent().startAutoLockThread();
        daemon.initDispatcher();

        if (!vaultManager.isInitialized()) {
            Shell initShell = new Shell(display);
            boolean initialized = new InitVaultDialog(initShell).open();
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
        shell.setText(i18n("app.name"));
        shell.setSize(800, 600);
        ShellUtil.setCenter(shell);
        shell.setLayout(new GridLayout(1, false));
        Image appIcon = loadIcon(display);
        shell.setImage(appIcon);

        // Stop the HTTP service when the window closes
        shell.addListener(SWT.Dispose, _ -> daemon.stop());

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
        ClearPasswordThread.init();
        LoginDetailView loginDetailView = new LoginDetailView(rightContainer);
        NoteDetailView noteDetailView = new NoteDetailView(rightContainer);
        IdentityDetailView identityDetailView = new IdentityDetailView(rightContainer);
        LoginEditView loginEditView = new LoginEditView(rightContainer);
        NoteEditView noteEditView = new NoteEditView(rightContainer);
        IdentityEditView identityEditView = new IdentityEditView(rightContainer);

        MainController controller = new MainController(unlockView, topContainer, topStack, mainContent, toolbar,
                listView, emptyView, loginDetailView, noteDetailView, identityDetailView, loginEditView, noteEditView,
                identityEditView, rightContainer, rightStack);
        controller.init();

        // ── activate window action (shared by tray and /activate) ───────
        Runnable activate = () -> {
            shell.setVisible(true);
            shell.setMinimized(false);
            shell.setActive();
            shell.forceActive();
        };

        // ── system tray ──────────────────────────────────────────────────
        Tray tray = display.getSystemTray();
        if (tray != null) {
            TrayItem trayItem = new TrayItem(tray, SWT.NONE);
            trayItem.setToolTipText(i18n("app.name"));
            trayItem.setImage(appIcon);

            Menu trayMenu = new Menu(shell, SWT.POP_UP);
            MenuItem openItem = new MenuItem(trayMenu, SWT.PUSH);
            openItem.setText(i18n("tray.open"));
            openItem.addListener(SWT.Selection, _ -> activate.run());
            MenuItem lockItem = new MenuItem(trayMenu, SWT.PUSH);
            lockItem.setText(i18n("tray.lock"));
            lockItem.addListener(SWT.Selection, _ -> controller.lock());
            new MenuItem(trayMenu, SWT.SEPARATOR);
            MenuItem settingsItem = new MenuItem(trayMenu, SWT.PUSH);
            settingsItem.setText(i18n("tray.settings"));
            settingsItem.addListener(SWT.Selection, _ -> new SettingsDialog(shell).open());
            new MenuItem(trayMenu, SWT.SEPARATOR);
            MenuItem exitItem = new MenuItem(trayMenu, SWT.PUSH);
            exitItem.setText(i18n("tray.exit"));
            exitItem.addListener(SWT.Selection, _ -> shell.dispose());

            trayItem.addListener(SWT.Selection, _ -> activate.run());
            trayItem.addListener(SWT.MenuDetect, _ -> {
                boolean unlocked = !Session.getCurrent().isLocked();
                lockItem.setEnabled(unlocked);
                settingsItem.setEnabled(unlocked);
                trayMenu.setVisible(true);
            });

            // minimize to tray instead of closing, unless the user has
            // disabled the "keep tray icon" setting (default: on).
            shell.addListener(SWT.Close, e -> {
                boolean keepInTray = vaultManager.getSetting(SettingKey.KEEP_TRAY_ICON, 1) != 0;
                if (keepInTray) {
                    e.doit = false;
                    shell.setVisible(false);
                }
            });
        }

        // ── SWT event loop (main thread stays here) ────────────────────────
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch())
                display.sleep();
        }
        appIcon.dispose();
        display.dispose();
    }

    private static Image loadIcon(Display display) {
        try (InputStream input = MainWindow.class.getResourceAsStream("/logo.ico")) {
            return new Image(display, input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
