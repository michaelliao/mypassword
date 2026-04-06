package org.puppylab.mypassword.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.data.SettingKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton session holding the in-memory Data Encryption Key.
 *
 * Thread-safe: all methods are synchronized. The desktop app has exactly one
 * vault and one user, so a single shared instance is correct. Any thread (SWT
 * main thread or HTTP handler pool) that unlocks/locks the vault immediately
 * affects all other threads.
 *
 * Auto-lock is based on OS-level idle time (keyboard/mouse inactivity) queried
 * via Win32 GetLastInputInfo. The timeout is read from VaultManager settings.
 */
public class Session {

    public static enum UnlockType {
        PASSWORD, OAUTH;
    }

    static final Logger logger = LoggerFactory.getLogger(Session.class);

    private static final Session INSTANCE = new Session();

    private static final long AUTO_LOCK_POLL_INTERVAL = 30_000L; // 30 seconds

    // Win32 GetLastInputInfo handle (null on non-Windows)
    private static final MethodHandle getLastInputInfo;
    private static final MethodHandle getTickCount;

    // LASTINPUTINFO struct: cbSize (UINT=4 bytes) + dwTime (DWORD=4 bytes) = 8
    // bytes
    private static final MemoryLayout LASTINPUTINFO_LAYOUT = MemoryLayout
            .structLayout(ValueLayout.JAVA_INT.withName("cbSize"), ValueLayout.JAVA_INT.withName("dwTime"));

    static {
        MethodHandle glii = null;
        MethodHandle gtc = null;
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Linker linker = Linker.nativeLinker();
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
                glii = linker.downcallHandle(user32.find("GetLastInputInfo").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                gtc = linker.downcallHandle(kernel32.find("GetTickCount").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT));
            }
        } catch (Exception e) {
            logger.warn("Failed to load Win32 idle API, falling back to app-level tracking", e);
        }
        getLastInputInfo = glii;
        getTickCount = gtc;
    }

    private VaultManager      vaultManager;
    private volatile Runnable onAutoLocked;
    private UnlockType        unlockType     = null;
    private SecretKey         dek            = null;
    private long              lastActiveTime = 0;

    private Session() {
    }

    public static Session current() {
        return INSTANCE;
    }

    public void setVaultManager(VaultManager vaultManager) {
        this.vaultManager = vaultManager;
    }

    public void setOnAutoLocked(Runnable onAutoLocked) {
        this.onAutoLocked = onAutoLocked;
    }

    /**
     * Start the auto-lock polling thread. Call once after setVaultManager.
     */
    public void startAutoLockThread() {
        Thread thread = new Thread(this::autoLockLoop, "auto-lock");
        thread.setDaemon(true);
        thread.start();
    }

    private void autoLockLoop() {
        while (true) {
            try {
                Thread.sleep(AUTO_LOCK_POLL_INTERVAL);
            } catch (InterruptedException e) {
                continue;
            }
            if (isLocked()) {
                Runnable callback = this.onAutoLocked;
                if (callback != null) {
                    callback.run();
                }
            }
        }
    }

    public synchronized void recordActivity() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public synchronized boolean isLocked() {
        if (dek == null) {
            return true;
        }
        long autoLockMinutes = vaultManager != null ? vaultManager.getSetting(SettingKey.AUTO_LOCK, 10) : 10;
        if (autoLockMinutes <= 0) {
            return false;
        }
        long autoLockMs = autoLockMinutes * 60_000L;
        long idleMs = getSystemIdleTimeMillis();
        logger.info("auto lock in {} minutes: get system idle time millis: {}", autoLockMinutes, idleMs);
        if (idleMs >= autoLockMs) {
            lock();
            return true;
        }
        return false;
    }

    public synchronized void lock() {
        this.unlockType = null;
        this.dek = null;
    }

    public synchronized void setKey(UnlockType unlockType, SecretKey key) {
        this.unlockType = unlockType;
        this.dek = key;
        if (key != null) {
            recordActivity();
        }
    }

    public UnlockType getUnlockType() {
        return this.unlockType;
    }

    public synchronized SecretKey getKey() {
        if (isLocked()) {
            return null;
        }
        recordActivity();
        return this.dek;
    }

    /**
     * Returns OS-level idle time in milliseconds. On Windows, uses
     * GetLastInputInfo/GetTickCount. On other platforms, falls back to app-level
     * tracking (time since last recordActivity call).
     */
    long getSystemIdleTimeMillis() {
        if (getLastInputInfo != null && getTickCount != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = arena.allocate(LASTINPUTINFO_LAYOUT);
                info.set(ValueLayout.JAVA_INT, 0, 8); // cbSize = 8
                int ok = (int) getLastInputInfo.invokeExact(info);
                if (ok != 0) {
                    int lastInput = info.get(ValueLayout.JAVA_INT, 4); // dwTime
                    int tickCount = (int) getTickCount.invokeExact();
                    return Integer.toUnsignedLong(tickCount - lastInput);
                }
            } catch (Throwable e) {
                logger.warn("GetLastInputInfo failed", e);
            }
        }
        // fallback: app-level tracking
        return System.currentTimeMillis() - lastActiveTime;
    }
}
