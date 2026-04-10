package org.puppylab.mypassword.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.puppylab.mypassword.util.IdUtils;

class IdUtilsTest {

    @Test
    void testNextId() throws Exception {
        final int threadCount = 100;
        final int iterations = 1000;
        final Map<Long, Boolean> ids = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneSignal = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    try {
                        latch.await();
                        for (int j = 0; j < iterations; j++) {
                            ids.put(IdUtils.nextId(), Boolean.TRUE);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        doneSignal.countDown();
                    }
                });
            }

            long start = System.currentTimeMillis();
            latch.countDown();
            doneSignal.await();
            long end = System.currentTimeMillis();

            int expectedSize = threadCount * iterations;
            System.out.println("Generated " + ids.size() + " in " + (end - start) + "ms");
            assertEquals(expectedSize, ids.size());
        }
    }

}
