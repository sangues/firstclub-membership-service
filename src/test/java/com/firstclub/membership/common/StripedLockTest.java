package com.firstclub.membership.common;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class StripedLockTest {
    @Test
    void serializesMutationsForSameKey() throws Exception {
        StripedLock locks = new StripedLock(16);
        int[] shared = {0};
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger key = new AtomicInteger(7);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                locks.withLock(key.get(), () -> { shared[0] = shared[0] + 1; });
                done.countDown();
            });
        }
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(shared[0]).isEqualTo(threads); // no lost updates
    }
}
