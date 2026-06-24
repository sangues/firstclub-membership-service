package com.firstclub.membership.subscription.dto;

import com.firstclub.membership.subscription.Subscription;
import java.time.Instant;

public record SubscriptionResponse(
        Long userId, String planType, String purchasedTier, String earnedTier,
        String effectiveTier, String status, Instant startAt, Instant expiresAt) {

    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getUser().getId(),
                s.getPlan().getPlanType().name(),
                s.getPurchasedTier().getName(),
                s.getEarnedTier() == null ? null : s.getEarnedTier().getName(),
                s.getEffectiveTier().getName(),
                s.getStatus().name(),
                s.getStartAt(), s.getExpiresAt());
    }
}
