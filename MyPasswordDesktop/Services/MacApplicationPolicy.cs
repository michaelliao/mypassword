using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Threading;

namespace MyPasswordDesktop.Services
{
    /// <summary>
    /// Accessory apps have no application menu bar. Use Regular while a window
    /// is open, and Accessory only after all windows have been hidden or closed.
    /// </summary>
    internal sealed class MacApplicationPolicy : IDisposable
    {
        private readonly HashSet<Window> _windows = new();
        private readonly IDisposable _openedSubscription;
        private bool _pending;
        private bool _disposed;

        private MacApplicationPolicy(IClassicDesktopStyleApplicationLifetime desktop)
        {
            _openedSubscription = Window.WindowOpenedEvent.AddClassHandler(typeof(Window),
                (sender, _) => Track((Window)sender));
            foreach (var window in desktop.Windows)
                Track(window);
            desktop.Exit += (_, _) => Dispose();
        }

        public static void Attach(IClassicDesktopStyleApplicationLifetime desktop)
        {
            if (OperatingSystem.IsMacOS())
                _ = new MacApplicationPolicy(desktop);
        }

        private void Track(Window window)
        {
            if (!_windows.Add(window))
                return;
            window.Closed += OnClosed;
            window.PropertyChanged += OnPropertyChanged;
            ScheduleUpdate();
        }

        private void OnPropertyChanged(object sender, Avalonia.AvaloniaPropertyChangedEventArgs args)
        {
            if (args.Property == Window.IsVisibleProperty)
                ScheduleUpdate();
        }

        private void OnClosed(object sender, EventArgs args)
        {
            var window = (Window)sender;
            Untrack(window);
            _windows.Remove(window);
            ScheduleUpdate();
        }

        private void ScheduleUpdate()
        {
            if (_pending || _disposed)
                return;
            _pending = true;
            // Coalesce visibility changes when opening or closing owned dialogs.
            Dispatcher.UIThread.Post(Update, DispatcherPriority.Background);
        }

        private void Update()
        {
            _pending = false;
            if (_disposed)
                return;
            bool windowOpen = _windows.Any(window => window.IsVisible);
            IntPtr app = SendPointer(objc_getClass("NSApplication"), sel_registerName("sharedApplication"));
            nint desired = windowOpen ? 0 : 1; // Regular / Accessory
            nint current = SendInteger(app, sel_registerName("activationPolicy"));
            if (current == desired)
                return;
            SendPolicy(app, sel_registerName("setActivationPolicy:"), desired);
        }

        // Promote before Show/Activate, so AppKit performs a real foreground
        // transition with a menu bar instead of activating an accessory window.
        public static void PrepareToShowWindow()
        {
            if (!OperatingSystem.IsMacOS())
                return;
            IntPtr app = SendPointer(objc_getClass("NSApplication"), sel_registerName("sharedApplication"));
            if (SendInteger(app, sel_registerName("activationPolicy")) == 0)
                return;
            byte changed = SendPolicy(app, sel_registerName("setActivationPolicy:"), 0);
            if (changed != 0)
                SendVoid(app, sel_registerName("deactivate"));
        }

        private void Untrack(Window window)
        {
            window.Closed -= OnClosed;
            window.PropertyChanged -= OnPropertyChanged;
        }

        public void Dispose()
        {
            if (_disposed)
                return;
            _disposed = true;
            _openedSubscription.Dispose();
            foreach (var window in _windows)
            {
                Untrack(window);
            }
            _windows.Clear();
        }

        private const string ObjectiveC = "/usr/lib/libobjc.A.dylib";
        [DllImport(ObjectiveC)] private static extern IntPtr objc_getClass(string name);
        [DllImport(ObjectiveC)] private static extern IntPtr sel_registerName(string name);
        [DllImport(ObjectiveC, EntryPoint = "objc_msgSend")] private static extern IntPtr SendPointer(IntPtr obj, IntPtr selector);
        [DllImport(ObjectiveC, EntryPoint = "objc_msgSend")] private static extern nint SendInteger(IntPtr obj, IntPtr selector);
        [DllImport(ObjectiveC, EntryPoint = "objc_msgSend")] private static extern byte SendPolicy(IntPtr obj, IntPtr selector, nint policy);
        [DllImport(ObjectiveC, EntryPoint = "objc_msgSend")] private static extern void SendVoid(IntPtr obj, IntPtr selector);
    }
}
