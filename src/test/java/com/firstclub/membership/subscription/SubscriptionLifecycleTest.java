package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SubscriptionLifecycleTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void upgradeThenDowngradeMovesPurchasedTier() {
        User u = users.save(new User(null, "Grace", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        assertThat(service.changeTier(u.getId(), "GOLD").effectiveTier()).isEqualTo("GOLD");
        assertThat(service.changeTier(u.getId(), "SILVER").effectiveTier()).isEqualTo("SILVER");
    }

    @Test
    void cancelThenCancelAgainRejected() {
        User u = users.save(new User(null, "Heidi", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        service.cancel(u.getId());
        assertThatThrownBy(() -> service.cancel(u.getId()))
            .isInstanceOf(com.firstclub.membership.common.exception.SubscriptionNotFoundException.class);
    }
}
