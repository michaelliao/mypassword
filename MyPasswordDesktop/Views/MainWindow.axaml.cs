using System;
using System.ComponentModel;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Avalonia.Platform;
using Avalonia.Threading;
using MyPasswordDesktop.Core;
using MyPasswordDesktop.Core.Data;
using MyPasswordDesktop.Core.Entities;
using MyPasswordDesktop.Services;
using MyPasswordDesktop.Util;
using MyPasswordDesktop.ViewModels;

namespace MyPasswordDesktop.Views
{
    public partial class MainWindow : Window
    {
        private HttpDaemon _daemon;
        private TrayIcon _trayIcon;
        private SettingsWindow _settingsWindow;
        private bool _bootstrapped;
        private bool _exiting;
        private bool _teardownDone;

        public MainWindow()
        {
            AvaloniaXamlLoader.Load(this);
            Opened += OnOpenedAsync;
            Closing += OnClosing;
        }

        private async void OnOpenedAsync(object sender, EventArgs e)
        {
            if (_bootstrapped)
            {
                return;
            }
            _bootstrapped = true;
            try
            {
                await BootstrapAsync();
            }
            catch (Exception ex)
            {
                Log.Error("bootstrap failed", ex);
                ExitApp();
            }
        }

        private async Task BootstrapAsync()
        {
            var vm = (MainWindowViewModel)DataContext;

            // ── locate the vault file ────────────────────────────────────────
            string dbFile = FileUtils.GetDbFile();
            if (dbFile == null || !FileUtils.IsValidVaultFile(dbFile))
            {
                bool located = await new VaultLocatorWindow().ShowDialog<bool>(this);
                if (!located)
                {
                    ExitApp();
                    return;
                }
            }

            // ── open the vault ───────────────────────────────────────────────
            dbFile = FileUtils.GetDbFile();
            var db = new DbManager(dbFile);
            var vault = new VaultManager(db);
            string lang = vault.GetSetting(SettingKey.LANGUAGE, "");
            I18n.I18n.Init(lang);
            ThemeManager.Apply(vault.GetSetting(SettingKey.THEME, ""));
            StringUtils.InitDateTimeLocale(lang);
            Session.Current.StartAutoLockThread();

            _daemon = new HttpDaemon();
            _daemon.InitDispatcher();

            // ── first-run vault initialization ───────────────────────────────
            if (!vault.IsInitialized())
            {
                bool initialized = await new InitVaultWindow().ShowDialog<bool>(this);
                if (!initialized)
                {
                    ExitApp();
                    return;
                }
            }

            _daemon.Start();

            // ── wire UI bridges ──────────────────────────────────────────────
            ClipboardService.Instance.Attach(Clipboard);
            // When the browser extension triggers activation, the browser still
            // owns focus at this instant — an immediate Activate() races with it
            // and the window may stay behind. Wait ~200 ms for the browser to
            // yield before pulling our window to the front.
            UiBridge.ActivateApp = () => Task.Delay(200)
                .ContinueWith(_ => Dispatcher.UIThread.Post(ActivateWindow));
            UiBridge.CopyPassword = pwd => ClipboardService.Instance.Copy(pwd);
            UiBridge.ShowPairRequest = ec => Dispatcher.UIThread.Post(() => ShowPairRequestAsync(ec));

            vm.ShowSettingsRequested += OpenSettings;
            // Close the settings dialog whenever the window flips back to the
            // unlock screen — so locking from the tray (or auto-lock) doesn't
            // leave a vault-relative dialog hanging on top.
            vm.PropertyChanged += (_, args) =>
            {
                if (args.PropertyName == nameof(MainWindowViewModel.Content)
                    && vm.Content is UnlockViewModel)
                {
                    _settingsWindow?.Close();
                }
            };
            vm.WireCallbacks();
            SetupTray();

            vm.ShowUnlock();
        }

        // ── tray icon ────────────────────────────────────────────────────────

        private void SetupTray()
        {
            WindowIcon icon = TryLoadIcon();
            _trayIcon = new TrayIcon
            {
                Icon = icon,
                ToolTipText = I18n.I18n.T("app.name"),
                IsVisible = true,
            };

            var menu = new NativeMenu();
            var open = new NativeMenuItem(I18n.I18n.T("tray.open"));
            open.Click += (_, _) => ActivateWindow();
            var lockItem = new NativeMenuItem(I18n.I18n.T("tray.lock"));
            lockItem.Click += (_, _) => Session.Current.Lock();
            var settings = new NativeMenuItem(I18n.I18n.T("tray.settings"));
            settings.Click += (_, _) => OpenSettings();
            var exit = new NativeMenuItem(I18n.I18n.T("tray.exit"));
            exit.Click += (_, _) => ExitApp();

            menu.Add(open);
            menu.Add(lockItem);
            menu.Add(new NativeMenuItemSeparator());
            menu.Add(settings);
            menu.Add(new NativeMenuItemSeparator());
            menu.Add(exit);
            _trayIcon.Menu = menu;
            _trayIcon.Clicked += (_, _) => ActivateWindow();

            // register the tray icon with the application so it is displayed
            TrayIcon.SetIcons(Application.Current, new TrayIcons { _trayIcon });

            if (icon != null)
            {
                Icon = icon;
            }
        }

        private static WindowIcon TryLoadIcon()
        {
            try
            {
                return new WindowIcon(AssetLoader.Open(new Uri("avares://MyPassword/Assets/logo.ico")));
            }
            catch (Exception e)
            {
                Log.Warn("failed to load tray icon", e);
                return null;
            }
        }

        private void ActivateWindow()
        {
            Show();
            WindowState = WindowState.Normal;
            Activate();
        }

        // ── extension pairing prompt ─────────────────────────────────────────

        private async void ShowPairRequestAsync(ExtensionConfig ec)
        {
            ActivateWindow();
            bool approve = await Dialogs.ConfirmAsync(
                I18n.I18n.T("pair.title"), I18n.I18n.T("pair.message", ec.name, ec.device));
            VaultManager.Current.ApproveExtension(ec.id, approve);
            Log.Info($"extension pair request {ec.id}: {(approve ? "approved" : "rejected")}");
        }

        private void OpenSettings()
        {
            // a settings dialog is already open — activate it instead of
            // opening a duplicate.
            if (_settingsWindow != null)
            {
                if (_settingsWindow.WindowState == WindowState.Minimized)
                {
                    _settingsWindow.WindowState = WindowState.Normal;
                }
                _settingsWindow.Activate();
                return;
            }
            _settingsWindow = new SettingsWindow();
            _settingsWindow.Closed += (_, _) => _settingsWindow = null;
            _settingsWindow.Show(this);
        }

        // ── lifecycle ────────────────────────────────────────────────────────

        private void OnClosing(object sender, WindowClosingEventArgs e)
        {
            // Only macOS application quit bypasses the close-to-tray behavior.
            bool shuttingDown = OperatingSystem.IsMacOS()
                && (e.CloseReason is WindowCloseReason.ApplicationShutdown
                    or WindowCloseReason.OSShutdown);
            // desktop.Shutdown() below closes this window again, re-entering
            // OnClosing — let that pass straight through to avoid recursion.
            if (_teardownDone)
            {
                return;
            }
            if (!_exiting && !shuttingDown)
            {
                // a plain window close keeps the app alive in the tray unless
                // the user disabled that setting.
                bool keepInTray = VaultManager.Current != null
                                  && VaultManager.Current.GetSetting(SettingKey.KEEP_TRAY_ICON, 1) != 0;
                if (keepInTray)
                {
                    e.Cancel = true;
                    Hide();
                    return;
                }
                _exiting = true;
            }
            // really exiting — tear down the daemon (closes the vault) then
            // shut the application down.
            _teardownDone = true;
            _daemon?.Stop();
            // Native application quit is already inside the shutdown loop.
            if (!shuttingDown && Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
            {
                desktop.Shutdown();
            }
        }

        /// <summary>
        /// Exit the whole application. Routes through <see cref="OnClosing"/> so
        /// teardown happens exactly once, in one place.
        /// </summary>
        private void ExitApp()
        {
            _exiting = true;
            Close();
        }
    }
}
