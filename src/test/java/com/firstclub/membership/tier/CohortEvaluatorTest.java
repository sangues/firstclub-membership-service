package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CohortEvaluatorTest {

    private final CohortEvaluator evaluator = new CohortEvaluator();

    // UserOrderStats requires a User in its constructor; pass a minimal user and null-safe stats.
    private UserOrderStats statsFor(User user) {
        return new UserOrderStats(user);
    }

    @Test
    void exactMatchReturnsTrue() {
        User user = new User(null, "Alice", "VIP");
        assertThat(evaluator.matches(statsFor(user), user, Map.of("cohorts", "VIP"))).isTrue();
    }

    @Test
    void nonMatchReturnsFalse() {
        User user = new User(null, "Bob", "STANDARD");
        assertThat(evaluator.matches(statsFor(user), user, Map.of("cohorts", "VIP"))).isFalse();
    }

    @Test
    void caseInsensitiveMatchReturnsTrue() {
        User user = new User(null, "Carol", "vip");
        assertThat(evaluator.matches(statsFor(user), user, Map.of("cohorts", "VIP"))).isTrue();
    }

    @Test
    void commaSeparatedListMatchesAny() {
        User user = new User(null, "Dave", "GOLD_TIER");
        assertThat(evaluator.matches(statsFor(user), user, Map.of("cohorts", "VIP,GOLD_TIER"))).isTrue();
    }
}
