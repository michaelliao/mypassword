package org.puppylab.mypassword.core;

import java.time.Duration;

import javax.crypto.SecretKey;

/**
 * Singleton session holding the in-memory Data Encryption Key.
 *
 * Thread-safe: all methods are synchronized. The desktop app has exactly one
 * vault and one user, so a single shared instance is correct. Any thread (SWT
 * main thread or HTTP handler pool) that unlocks/locks the vault immediately
 * affects all other threads.
 */
public class Session {

    public static enum UnlockType {
        PASSWORD, OAUTH;
    }

    private static final Session INSTANCE = new Session();

    private final long INACTIVE_TIME = Duration.ofMinutes(10).toMillis();

    private UnlockType unlockType     = null;
    private SecretKey  dek            = null;
    private long       lastActiveTime = 0;

    private Session() {
    }

    public static Session current() {
        return INSTANCE;
    }

    public synchronized void recordActivity() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public synchronized boolean isLocked() {
        if (dek == null) {
            return true;
        }
        if (System.currentTimeMillis() - lastActiveTime > INACTIVE_TIME) {
            lock();
            return true;
        }
        return false;
    }

    public synchronized void lock() {
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
}
