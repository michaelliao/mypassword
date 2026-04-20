package org.puppylab.mypassword.util;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Small helpers for SWT {@link Shell} placement.
 */
public class ShellUtils {

    private ShellUtils() {
    }

    /**
     * Position {@code shell} at the center of the primary monitor. The shell must
     * already have its size set (via {@code setSize} or {@code pack}).
     */
    public static void setCenter(Shell shell) {
        Rectangle screen = shell.getDisplay().getPrimaryMonitor().getBounds();
        Point size = shell.getSize();
        shell.setLocation(screen.x + (screen.width - size.x) / 2, screen.y + (screen.height - size.y) / 2);
    }

    public static void activateApp() {
        Display display = Display.getDefault();
        display.asyncExec(() -> {
            Shell shell = display.getActiveShell();
            if (shell == null) {
                Shell[] shells = display.getShells();
                if (shells.length > 0) {
                    shell = shells[0];
                }
            }
            if (shell != null) {
                activateShell(shell);
            }
        });
    }

    public static void activateShell(Shell shell) {
        shell.setMinimized(false);
        shell.setActive();
        shell.forceActive();
    }
}
