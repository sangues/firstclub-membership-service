package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import java.util.Map;

public interface EligibilityEvaluator {
    RuleType ruleType();
    boolean matches(UserOrderStats stats, User user, Map<String, String> params);
}
