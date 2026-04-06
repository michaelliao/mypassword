package org.puppylab.mypassword.core;

import java.util.Arrays;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.puppylab.mypassword.core.data.SettingKey;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearPasswordThread extends Thread {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private static VaultManager vaultManager;

    private static volatile int    countdown = 0;
    private static volatile byte[] hmacKey;
    private static volatile byte[] expectedHash;

    private static ClearPasswordThread instance;

    public static void init(VaultManager vaultManager) {
        ClearPasswordThread.vaultManager = vaultManager;
        instance = new ClearPasswordThread();
        instance.setDaemon(true);
        instance.setName("clipboard-cleaner");
        instance.start();
    }

    public static void scheduleClear(String password) {
        int minutes = vaultManager.getSetting(SettingKey.CLEAR_CLIPBOARD, 1);
        if (minutes <= 0) {
            return;
        }
        byte[] key = EncryptUtils.generateKey();
        hmacKey = key;
        expectedHash = HashUtils.hmacSha256(password, key);
        countdown = minutes * 60;
        instance.interrupt();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // scheduleClear() called, restart countdown loop
                continue;
            }
            if (countdown <= 0) {
                continue;
            }
            countdown--;
            if (countdown == 0) {
                final byte[] key = hmacKey;
                final byte[] hash = expectedHash;
                Display.getDefault().asyncExec(() -> {
                    if (Display.getCurrent().isDisposed())
                        return;
                    Clipboard cb = new Clipboard(Display.getCurrent());
                    String current = (String) cb.getContents(TextTransfer.getInstance());
                    if (current != null && Arrays.equals(hash, HashUtils.hmacSha256(current, key))) {
                        cb.setContents(new Object[] { " " }, new Transfer[] { TextTransfer.getInstance() });
                        cb.clearContents(DND.CLIPBOARD);
                        logger.info("password cleared from clipboard.");
                    }
                    cb.dispose();
                });
            }
        }
    }
}
