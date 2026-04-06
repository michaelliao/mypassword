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
 * via platform-specific APIs. The timeout is read from VaultManager settings.
 */
public class Session {

    public static enum UnlockType {
        PASSWORD, OAUTH;
    }

    static final Logger logger = LoggerFactory.getLogger(Session.class);

    private static final Session INSTANCE = new Session();

    private static final long AUTO_LOCK_POLL_INTERVAL = 30_000L; // 30 seconds

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    private static final boolean IS_LINUX = OS_NAME.contains("linux");

    // ── Windows: GetLastInputInfo / GetTickCount ──────────────────────
    private static final MethodHandle winGetLastInputInfo;
    private static final MethodHandle winGetTickCount;
    private static final MemoryLayout WIN_LASTINPUTINFO = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("cbSize"),
            ValueLayout.JAVA_INT.withName("dwTime"));

    // ── macOS: CGEventSourceSecondsSinceLastEventType ─────────────────
    private static final MethodHandle macIdleTime;

    // ── Linux: XScreenSaver ──────────────────────────────────────────
    private static final MethodHandle linuxXOpenDisplay;
    private static final MethodHandle linuxXDefaultRootWindow;
    private static final MethodHandle linuxXScreenSaverAllocInfo;
    private static final MethodHandle linuxXScreenSaverQueryInfo;
    private static final MethodHandle linuxXFree;
    private static final MethodHandle linuxXCloseDisplay;

    static {
        // Windows
        MethodHandle glii = null, gtc = null;
        // macOS
        MethodHandle mit = null;
        // Linux
        MethodHandle lxod = null, lxdrw = null, lxssai = null, lxssqi = null, lxf = null, lxcd = null;

        Linker linker = Linker.nativeLinker();
        try {
            if (IS_WINDOWS) {
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
                glii = linker.downcallHandle(user32.find("GetLastInputInfo").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                gtc = linker.downcallHandle(kernel32.find("GetTickCount").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT));
            } else if (IS_MAC) {
                // CGEventSourceSecondsSinceLastEventType(int32_t stateID, int32_t eventType) -> double
                SymbolLookup cg = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
                mit = linker.downcallHandle(cg.find("CGEventSourceSecondsSinceLastEventType").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            } else if (IS_LINUX) {
                SymbolLookup x11 = SymbolLookup.libraryLookup("libX11.so.6", Arena.global());
                SymbolLookup xss = SymbolLookup.libraryLookup("libXss.so.1", Arena.global());
                // Display* XOpenDisplay(char*)
                lxod = linker.downcallHandle(x11.find("XOpenDisplay").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                // Window XDefaultRootWindow(Display*) — actually a macro, use XRootWindow(display, 0)
                lxdrw = linker.downcallHandle(x11.find("XRootWindow").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                // XScreenSaverInfo* XScreenSaverAllocInfo()
                lxssai = linker.downcallHandle(xss.find("XScreenSaverAllocInfo").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS));
                // Status XScreenSaverQueryInfo(Display*, Drawable, XScreenSaverInfo*)
                lxssqi = linker.downcallHandle(xss.find("XScreenSaverQueryInfo").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                                ValueLayout.ADDRESS));
                // int XFree(void*)
                lxf = linker.downcallHandle(x11.find("XFree").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                // int XCloseDisplay(Display*)
                lxcd = linker.downcallHandle(x11.find("XCloseDisplay").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            }
        } catch (Exception e) {
            logger.warn("Failed to load native idle API, falling back to app-level tracking", e);
        }
        winGetLastInputInfo = glii;
        winGetTickCount = gtc;
        macIdleTime = mit;
        linuxXOpenDisplay = lxod;
        linuxXDefaultRootWindow = lxdrw;
        linuxXScreenSaverAllocInfo = lxssai;
        linuxXScreenSaverQueryInfo = lxssqi;
        linuxXFree = lxf;
        linuxXCloseDisplay = lxcd;
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
        if (idleMs >= autoLockMs) {
            logger.info("auto-lock triggered: idle {}ms >= {}ms", idleMs, autoLockMs);
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

    // ── platform idle time detection ─────────────────────────────────

    long getSystemIdleTimeMillis() {
        if (IS_WINDOWS) {
            return getSystemIdleTimeMillisForWin();
        } else if (IS_MAC) {
            return getSystemIdleTimeMillisForMac();
        } else if (IS_LINUX) {
            return getSystemIdleTimeMillisForLinux();
        }
        return System.currentTimeMillis() - lastActiveTime;
    }

    /**
     * Windows: GetLastInputInfo / GetTickCount.
     */
    long getSystemIdleTimeMillisForWin() {
        if (winGetLastInputInfo != null && winGetTickCount != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = arena.allocate(WIN_LASTINPUTINFO);
                info.set(ValueLayout.JAVA_INT, 0, 8); // cbSize = 8
                int ok = (int) winGetLastInputInfo.invokeExact(info);
                if (ok != 0) {
                    int lastInput = info.get(ValueLayout.JAVA_INT, 4); // dwTime
                    int tickCount = (int) winGetTickCount.invokeExact();
                    return Integer.toUnsignedLong(tickCount - lastInput);
                }
            } catch (Throwable e) {
                logger.warn("GetLastInputInfo failed", e);
            }
        }
        return System.currentTimeMillis() - lastActiveTime;
    }

    /**
     * macOS: CGEventSourceSecondsSinceLastEventType.
     * kCGEventSourceStateCombinedSessionState = 0, kCGAnyInputEventType = ~0.
     */
    long getSystemIdleTimeMillisForMac() {
        if (macIdleTime != null) {
            try {
                double seconds = (double) macIdleTime.invokeExact(0, ~0);
                return (long) (seconds * 1000);
            } catch (Throwable e) {
                logger.warn("CGEventSourceSecondsSinceLastEventType failed", e);
            }
        }
        return System.currentTimeMillis() - lastActiveTime;
    }

    /**
     * Linux/X11: XScreenSaverQueryInfo. The idle field in XScreenSaverInfo
     * is at offset 32 bytes (Window=8 + int=4 + int=4 + ulong=8 + idle=8)
     * on 64-bit systems. Returns idle time in milliseconds.
     */
    long getSystemIdleTimeMillisForLinux() {
        if (linuxXOpenDisplay != null && linuxXScreenSaverQueryInfo != null) {
            MemorySegment display = MemorySegment.NULL;
            MemorySegment ssInfo = MemorySegment.NULL;
            try {
                display = (MemorySegment) linuxXOpenDisplay.invokeExact(MemorySegment.NULL);
                if (display.equals(MemorySegment.NULL)) {
                    return System.currentTimeMillis() - lastActiveTime;
                }
                long rootWindow = (long) linuxXDefaultRootWindow.invokeExact(display, 0);
                ssInfo = (MemorySegment) linuxXScreenSaverAllocInfo.invokeExact();
                if (ssInfo.equals(MemorySegment.NULL)) {
                    return System.currentTimeMillis() - lastActiveTime;
                }
                int status = (int) linuxXScreenSaverQueryInfo.invokeExact(display, rootWindow, ssInfo);
                if (status != 0) {
                    // XScreenSaverInfo.idle is at offset 32 on 64-bit:
                    // Window(8) + state(4) + kind(4) + til_or_since(8) + idle(8)
                    // but with padding: Window(8) + state(4) + kind(4) + til_or_since(8) = 24, idle at 24
                    // Actually: struct { Window window; int state; int kind; unsigned long til_or_since; unsigned long idle; unsigned long eventMask; }
                    // On LP64: Window=ulong=8, int=4, padding to 8, ulong=8... let me compute:
                    // window: offset 0, size 8
                    // state: offset 8, size 4
                    // kind: offset 12, size 4
                    // til_or_since: offset 16, size 8
                    // idle: offset 24, size 8
                    MemorySegment reinterpreted = ssInfo.reinterpret(40);
                    long idleMs = reinterpreted.get(ValueLayout.JAVA_LONG, 24);
                    return idleMs;
                }
            } catch (Throwable e) {
                logger.warn("XScreenSaverQueryInfo failed", e);
            } finally {
                try {
                    if (!ssInfo.equals(MemorySegment.NULL)) {
                        linuxXFree.invokeExact(ssInfo);
                    }
                    if (!display.equals(MemorySegment.NULL)) {
                        linuxXCloseDisplay.invokeExact(display);
                    }
                } catch (Throwable e) {
                    logger.warn("X11 cleanup failed", e);
                }
            }
        }
        return System.currentTimeMillis() - lastActiveTime;
    }
}
