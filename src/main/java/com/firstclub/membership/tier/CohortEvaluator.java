package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Map;

@Component
public class CohortEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.COHORT; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        String allowed = params.getOrDefault("cohorts", "");
        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .anyMatch(c -> c.equalsIgnoreCase(user.getCohort()));
    }
}
