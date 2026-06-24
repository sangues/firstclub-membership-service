package com.firstclub.membership.tier;

import com.firstclub.membership.order.OrderEventService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the @Async @TransactionalEventListener(AFTER_COMMIT) chain
 * (OrderIngestedEvent → TierEvaluationListener → TierEvaluationService.evaluate)
 * actually promotes a user without a direct evaluate() call.
 *
 * No @Transactional here: we need real commits so the AFTER_COMMIT listener fires.
 * A fresh user + unique order IDs ensure no collision with seeded/other-test data.
 */
@SpringBootTest
class AsyncTierProgressionTest {

    @Autowired UserRepository users;
    @Autowired SubscriptionService subscriptionService;
    @Autowired OrderEventService orderEventService;

    @Test
    void asyncListenerPromotesUserToGoldAfterThreeOrders() throws InterruptedException {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(null, "AsyncUser-" + uid, "STANDARD"));
        subscriptionService.subscribe(user.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);

        // Ingest 3 orders — each real commit fires the AFTER_COMMIT async listener
        for (int i = 0; i < 3; i++) {
            orderEventService.ingest(new OrderEventRequest(
                    "async-" + uid + "-" + i,
                    user.getId(),
                    new BigDecimal("100"),
                    Instant.now()));
        }

        // Poll up to 5 s (50 × 100 ms) for the async chain to promote the user
        String effectiveTier = "SILVER";
        for (int attempt = 0; attempt < 50; attempt++) {
            effectiveTier = subscriptionService.getCurrent(user.getId()).effectiveTier();
            if ("GOLD".equals(effectiveTier)) break;
            Thread.sleep(100);
        }

        assertThat(effectiveTier)
                .as("Async AFTER_COMMIT listener should have promoted user to GOLD after 3 orders")
                .isEqualTo("GOLD");
    }
}
