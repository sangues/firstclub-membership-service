package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SubscriptionServiceTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void subscribeActivatesWithChosenTier() {
        User u = users.save(new User(null, "Carol", "STANDARD"));
        SubscriptionResponse r = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "GOLD"), null);
        assertThat(r.status()).isEqualTo("ACTIVE");
        assertThat(r.purchasedTier()).isEqualTo("GOLD");
        assertThat(r.effectiveTier()).isEqualTo("GOLD");
        assertThat(r.expiresAt()).isAfter(r.startAt());
    }

    @Test
    void subscribeDefaultsToLowestTierWhenNoneGiven() {
        User u = users.save(new User(null, "Dave", "STANDARD"));
        SubscriptionResponse r = service.subscribe(u.getId(), new SubscribeRequest("YEARLY", null), null);
        assertThat(r.purchasedTier()).isEqualTo("SILVER");
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameSubscription() {
        User u = users.save(new User(null, "Erin", "STANDARD"));
        SubscriptionResponse first = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), "key-1");
        SubscriptionResponse second = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), "key-1");
        assertThat(second.status()).isEqualTo(first.status());
    }

    @Test
    void secondActiveSubscriptionRejected() {
        User u = users.save(new User(null, "Frank", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        assertThatThrownBy(() -> service.subscribe(u.getId(), new SubscribeRequest("YEARLY", "GOLD"), null))
            .isInstanceOf(com.firstclub.membership.common.exception.AlreadySubscribedException.class);
    }
}
