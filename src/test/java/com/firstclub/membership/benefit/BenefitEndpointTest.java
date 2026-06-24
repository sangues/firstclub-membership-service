package com.firstclub.membership.benefit;

import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.tier.TierEvaluationService;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BenefitEndpointTest {
    @Autowired MockMvc mvc;
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserRepository users;

    @Test
    void goldUserSeesTenPercentDiscount() throws Exception {
        User u = users.save(new User(null, "Niaj", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 3; i++)
            orders.ingest(new OrderEventRequest("n-" + i, u.getId(), new BigDecimal("100"), Instant.now()));
        evaluator.evaluate(u.getId(), false);

        mvc.perform(get("/api/users/{id}/benefits", u.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.type=='PERCENT_DISCOUNT')].params.discountPercent").value(hasItem("10")));
    }
}
