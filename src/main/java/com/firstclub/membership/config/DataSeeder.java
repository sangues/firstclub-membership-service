package com.firstclub.membership.config;

import com.firstclub.membership.catalog.*;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;
    private final BenefitRepository benefits;
    private final TierBenefitRepository tierBenefits;
    private final UserRepository users;

    public DataSeeder(MembershipPlanRepository plans, TierRepository tiers,
                      BenefitRepository benefits, TierBenefitRepository tierBenefits,
                      UserRepository users) {
        this.plans = plans;
        this.tiers = tiers;
        this.benefits = benefits;
        this.tierBenefits = tierBenefits;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            users.save(new User(null, "Alice", "STANDARD"));
            users.save(new User(null, "Bob", "VIP"));
        }
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
        if (benefits.count() == 0) {
            Benefit freeDelivery = benefits.save(new Benefit(null, BenefitType.FREE_DELIVERY, "Free delivery", new HashMap<>()));
            Benefit discount = benefits.save(new Benefit(null, BenefitType.PERCENT_DISCOUNT, "Discount", new HashMap<>(Map.of("discountPercent", "5"))));
            Benefit deals = benefits.save(new Benefit(null, BenefitType.EXCLUSIVE_DEALS, "Exclusive deals", new HashMap<>()));
            Benefit early = benefits.save(new Benefit(null, BenefitType.EARLY_ACCESS, "Early access", new HashMap<>()));
            Benefit priority = benefits.save(new Benefit(null, BenefitType.PRIORITY_SUPPORT, "Priority support", new HashMap<>()));

            Tier silver = tiers.findByName("SILVER").orElseThrow();
            Tier gold = tiers.findByName("GOLD").orElseThrow();
            Tier platinum = tiers.findByName("PLATINUM").orElseThrow();

            tierBenefits.save(new TierBenefit(null, silver, freeDelivery, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, gold, freeDelivery, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, gold, discount, new HashMap<>(Map.of("discountPercent", "10"))));
            tierBenefits.save(new TierBenefit(null, gold, deals, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, platinum, freeDelivery, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, platinum, discount, new HashMap<>(Map.of("discountPercent", "15"))));
            tierBenefits.save(new TierBenefit(null, platinum, deals, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, platinum, early, new HashMap<>()));
            tierBenefits.save(new TierBenefit(null, platinum, priority, new HashMap<>()));
        }
    }
}
