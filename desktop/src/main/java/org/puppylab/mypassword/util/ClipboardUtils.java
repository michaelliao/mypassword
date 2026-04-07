package org.puppylab.mypassword.util;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.puppylab.mypassword.core.ClearPasswordThread;

public class ClipboardUtils {

    public static void copyPassword(String password) {
        Display display = Display.getDefault();
        display.asyncExec(() -> {
            if (display.isDisposed())
                return;
            Clipboard cb = new Clipboard(display);
            cb.setContents(new Object[] { password }, new Transfer[] { TextTransfer.getInstance() });
            cb.dispose();
            ClearPasswordThread.scheduleClear(password);
        });
    }
}
