package com.firstclub.membership.tier;

import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.UserOrderStatsRepository;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTest {
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserOrderStatsRepository stats;
    @Autowired UserRepository users;

    @Test
    void parallelOrdersAndUpgradeStayConsistent() throws Exception {
        User u = users.save(new User(null, "Peggy", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);

        int n = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch done = new CountDownLatch(n + 1);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    orders.ingest(new OrderEventRequest("p-" + idx, u.getId(), new BigDecimal("100"), Instant.now()));
                    if (idx % 5 == 0) // 10 duplicate re-submits
                        orders.ingest(new OrderEventRequest("p-" + idx, u.getId(), new BigDecimal("100"), Instant.now()));
                } finally { done.countDown(); }
            });
        }
        pool.submit(() -> { try { subs.changeTier(u.getId(), "GOLD"); } finally { done.countDown(); } });

        done.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        evaluator.evaluate(u.getId(), false);

        assertThat(stats.findByUserId(u.getId()).orElseThrow().getTotalOrders()).isEqualTo(n); // no lost updates / no dup
        // 50 orders -> PLATINUM earned; purchased GOLD -> effective = MAX = PLATINUM
        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("PLATINUM");
    }
}
