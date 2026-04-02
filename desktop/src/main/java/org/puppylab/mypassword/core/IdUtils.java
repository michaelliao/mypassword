package org.puppylab.mypassword.core;

import java.util.concurrent.atomic.AtomicLong;

public class IdUtils {

    private static final AtomicLong prevId = new AtomicLong(0);

    public static long nextId() {
        for (;;) {
            long currentMax = prevId.get();
            long ts = System.currentTimeMillis();
            long next = (ts <= currentMax) ? currentMax + 1 : ts;
            if (prevId.compareAndSet(currentMax, next)) {
                return next;
            }
        }
    }

}
