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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TierEvaluationServiceTest {
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserRepository users;

    @Test
    void promotesToGoldAfterThreeOrders() {
        User u = users.save(new User(null, "Judy", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 3; i++)
            orders.ingest(new OrderEventRequest("j-" + i, u.getId(), new BigDecimal("100"), Instant.now()));

        evaluator.evaluate(u.getId(), false);

        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("GOLD");
        assertThat(subs.getCurrent(u.getId()).earnedTier()).isEqualTo("GOLD");
    }

    @Test
    void promotesToPlatinumWhenCountAndValueMet() {
        User u = users.save(new User(null, "Mallory", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 5; i++)
            orders.ingest(new OrderEventRequest("m-" + i, u.getId(), new BigDecimal("300"), Instant.now()));

        evaluator.evaluate(u.getId(), false);

        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("PLATINUM");
    }
}
