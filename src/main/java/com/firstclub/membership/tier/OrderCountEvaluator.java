package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class OrderCountEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.ORDER_COUNT; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        long min = Long.parseLong(params.getOrDefault("minOrders", "0"));
        return stats.getTotalOrders() >= min;
    }
}
