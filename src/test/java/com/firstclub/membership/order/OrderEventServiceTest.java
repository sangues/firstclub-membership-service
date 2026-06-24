package com.firstclub.membership.order;

import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderEventServiceTest {
    @Autowired OrderEventService service;
    @Autowired UserOrderStatsRepository stats;
    @Autowired UserRepository users;

    @Test
    void aggregatesOrdersAndIsIdempotent() {
        User u = users.save(new User(null, "Ivan", "STANDARD"));
        service.ingest(new OrderEventRequest("o-1", u.getId(), new BigDecimal("100"), Instant.now()));
        service.ingest(new OrderEventRequest("o-2", u.getId(), new BigDecimal("250"), Instant.now()));
        service.ingest(new OrderEventRequest("o-2", u.getId(), new BigDecimal("250"), Instant.now())); // duplicate

        UserOrderStats s = stats.findByUserId(u.getId()).orElseThrow();
        assertThat(s.getTotalOrders()).isEqualTo(2);
        assertThat(s.getMonthlyOrderValue()).isEqualByComparingTo("350");
    }
}
