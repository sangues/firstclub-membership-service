package com.firstclub.membership.config;

import com.firstclub.membership.catalog.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DataSeeder implements ApplicationRunner {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;

    public DataSeeder(MembershipPlanRepository plans, TierRepository tiers) {
        this.plans = plans;
        this.tiers = tiers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (plans.count() == 0) {
            plans.save(new MembershipPlan(null, PlanType.MONTHLY, new BigDecimal("199"), PlanType.MONTHLY.durationDays(), true));
            plans.save(new MembershipPlan(null, PlanType.QUARTERLY, new BigDecimal("499"), PlanType.QUARTERLY.durationDays(), true));
            plans.save(new MembershipPlan(null, PlanType.YEARLY, new BigDecimal("1499"), PlanType.YEARLY.durationDays(), true));
        }
        if (tiers.count() == 0) {
            tiers.save(new Tier(null, "SILVER", 1, true));
            tiers.save(new Tier(null, "GOLD", 2, true));
            tiers.save(new Tier(null, "PLATINUM", 3, true));
        }
    }
}
