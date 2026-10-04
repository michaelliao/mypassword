using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using MyPasswordDesktop.Services;
using MyPasswordDesktop.ViewModels;
using MyPasswordDesktop.Views;

namespace MyPasswordDesktop
{
    public partial class App : Application
    {
        public override void Initialize()
        {
            AvaloniaXamlLoader.Load(this);
        }

        public override void OnFrameworkInitializationCompleted()
        {
            if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
            {
                // Do not auto-shutdown when transient bootstrap windows close;
                // the app only exits when the main window is explicitly closed.
                desktop.ShutdownMode = Avalonia.Controls.ShutdownMode.OnExplicitShutdown;

                // Load the system-default language so the locator/init dialogs
                // are localized; re-loaded with the saved setting once the
                // vault is open.
                I18n.I18n.Init("");

                var vm = new MainWindowViewModel();
                var window = new MainWindow { DataContext = vm };
                desktop.MainWindow = window;
                MacApplicationPolicy.Attach(desktop);
            }

            base.OnFrameworkInitializationCompleted();
        }
    }
}
