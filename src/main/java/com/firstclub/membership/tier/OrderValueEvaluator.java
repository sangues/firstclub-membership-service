package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class OrderValueEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.ORDER_VALUE; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        BigDecimal min = new BigDecimal(params.getOrDefault("minMonthlyValue", "0"));
        return stats.getMonthlyOrderValue().compareTo(min) >= 0;
    }
}
