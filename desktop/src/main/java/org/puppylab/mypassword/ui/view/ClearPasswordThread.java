package org.puppylab.mypassword.ui.view;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Display;
import org.puppylab.mypassword.core.EncryptUtils;
import org.puppylab.mypassword.core.VaultManager;
import org.puppylab.mypassword.util.HashUtils;

public class ClearPasswordThread {

    private static Display      display;
    private static VaultManager vaultManager;

    private static final AtomicInteger version = new AtomicInteger(0);

    public static void init(Display display, VaultManager vaultManager) {
        ClearPasswordThread.display = display;
        ClearPasswordThread.vaultManager = vaultManager;
    }

    public static void schedule(String password) {
        int ver = version.incrementAndGet();
        int seconds = vaultManager.getSetting("clear_pwd_after", 60);
        byte[] hmacKey = EncryptUtils.generateKey();
        byte[] expectedHash = HashUtils.hmacSha256(password, hmacKey);
        Thread cleaner = new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            if (version.get() != ver) {
                return;
            }
            display.asyncExec(() -> {
                if (display.isDisposed())
                    return;
                Clipboard cb = new Clipboard(display);
                String current = (String) cb.getContents(TextTransfer.getInstance());
                if (current != null && Arrays.equals(expectedHash, HashUtils.hmacSha256(current, hmacKey))) {
                    cb.clearContents();
                }
                cb.dispose();
            });
        }, "clipboard-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }
}
