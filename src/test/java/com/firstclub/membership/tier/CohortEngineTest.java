package com.firstclub.membership.tier;

import com.firstclub.membership.catalog.EligibilityRule;
import com.firstclub.membership.catalog.EligibilityRuleRepository;
import com.firstclub.membership.catalog.Tier;
import com.firstclub.membership.catalog.TierRepository;
import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for the COHORT rule type.
 *
 * The entire test method runs inside a @Transactional that rolls back after
 * the method completes.  SubscriptionService and TierEvaluationService both use
 * TransactionTemplate with the default REQUIRED propagation, so they JOIN the
 * test transaction and their writes roll back with it — seeded data is unaffected.
 *
 * Note: OrderEventService also uses TransactionTemplate (REQUIRED), so its
 * commit happens inside the test transaction as well.  The @Async
 * @TransactionalEventListener(AFTER_COMMIT) listener will NOT fire here because
 * the outer test transaction never commits — that is intentional; we call
 * evaluate() directly in this test.
 */
@SpringBootTest
@Transactional
class CohortEngineTest {

    @Autowired TierRepository tiers;
    @Autowired EligibilityRuleRepository ruleRepo;
    @Autowired UserRepository users;
    @Autowired SubscriptionService subscriptionService;
    @Autowired OrderEventService orderEventService;
    @Autowired TierEvaluationService evaluationService;

    @Test
    void cohortRuleGatesPlatinum_vipUserPromotes_standardUserFallsToGold() {
        // Add a COHORT rule to PLATINUM so it now requires ORDER_COUNT≥5, ORDER_VALUE≥1000, AND cohort=VIP
        Tier platinum = tiers.findByName("PLATINUM").orElseThrow();
        EligibilityRule cohortRule = new EligibilityRule();
        cohortRule.setTier(platinum);
        cohortRule.setRuleType(RuleType.COHORT);
        cohortRule.setParams(Map.of("cohorts", "VIP"));
        ruleRepo.save(cohortRule);

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // --- VIP user: meets all PLATINUM rules including cohort ---
        User vipUser = users.save(new User(null, "VipUser-" + uniqueSuffix, "VIP"));
        subscriptionService.subscribe(vipUser.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 5; i++) {
            orderEventService.ingest(new OrderEventRequest(
                    "cohort-vip-" + uniqueSuffix + "-" + i,
                    vipUser.getId(),
                    new BigDecimal("300"),
                    Instant.now()));
        }
        evaluationService.evaluate(vipUser.getId(), false);
        assertThat(subscriptionService.getCurrent(vipUser.getId()).effectiveTier())
                .as("VIP user with 5×300 orders should reach PLATINUM")
                .isEqualTo("PLATINUM");

        // --- STANDARD user: meets ORDER_COUNT and ORDER_VALUE but fails COHORT → falls to GOLD ---
        User standardUser = users.save(new User(null, "StdUser-" + uniqueSuffix, "STANDARD"));
        subscriptionService.subscribe(standardUser.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 5; i++) {
            orderEventService.ingest(new OrderEventRequest(
                    "cohort-std-" + uniqueSuffix + "-" + i,
                    standardUser.getId(),
                    new BigDecimal("300"),
                    Instant.now()));
        }
        evaluationService.evaluate(standardUser.getId(), false);
        assertThat(subscriptionService.getCurrent(standardUser.getId()).effectiveTier())
                .as("STANDARD user fails COHORT rule and should fall back to GOLD")
                .isEqualTo("GOLD");
    }
}
