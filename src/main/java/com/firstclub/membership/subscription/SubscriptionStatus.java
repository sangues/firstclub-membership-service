package com.firstclub.membership.subscription;

import java.util.*;

public enum SubscriptionStatus {
    PENDING, ACTIVE, EXPIRED, CANCELLED;

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED = Map.of(
        PENDING, EnumSet.of(ACTIVE, CANCELLED),
        ACTIVE, EnumSet.of(EXPIRED, CANCELLED),
        EXPIRED, EnumSet.noneOf(SubscriptionStatus.class),
        CANCELLED, EnumSet.noneOf(SubscriptionStatus.class)
    );

    public boolean canTransitionTo(SubscriptionStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
