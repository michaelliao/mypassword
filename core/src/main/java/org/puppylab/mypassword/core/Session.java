package org.puppylab.mypassword.core;

import java.time.Duration;

import javax.crypto.SecretKey;

/**
 * ThreadLocal based session for holding data encryption key.
 */
public class Session {

    private static ThreadLocal<Session> threadlocal = new ThreadLocal<>();

    private final long INACTIVE_TIME;

    private SecretKey dek            = null;
    private long      lastActiveTime = 0;

    public static void init() {
        var session = new Session();
        threadlocal.set(session);
    }

    public static Session current() {
        return threadlocal.get();
    }

    public static void remove() {
        threadlocal.remove();
    }

    private Session() {
        INACTIVE_TIME = Duration.ofMinutes(10).toMillis();
    }

    public void recordActivity() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public boolean isLocked() {
        if (dek == null) {
            return true;
        }
        if (System.currentTimeMillis() - lastActiveTime > INACTIVE_TIME) {
            lock();
            return true;
        }
        return false;
    }

    public void lock() {
        this.dek = null;
    }

    public void setKey(SecretKey key) {
        this.dek = key;
        recordActivity();
    }

    public SecretKey getKey() {
        if (isLocked()) {
            return null;
        }
        recordActivity();
        return this.dek;
    }
}
