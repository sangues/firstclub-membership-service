package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.MovementResponse;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SubscriptionHistoryTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void recordsSubscribeThenUpgrade() {
        User u = users.save(new User(null, "Olivia", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        service.changeTier(u.getId(), "GOLD");

        List<MovementResponse> history = service.history(u.getId());
        assertThat(history).extracting(MovementResponse::kind)
                .containsExactly("SUBSCRIBE", "UPGRADE");
    }
}
