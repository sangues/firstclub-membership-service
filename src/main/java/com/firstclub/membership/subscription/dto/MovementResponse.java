package com.firstclub.membership.subscription.dto;

import com.firstclub.membership.subscription.TierMovementLog;
import java.time.Instant;

public record MovementResponse(String fromTier, String toTier, String kind, Instant at) {
    public static MovementResponse from(TierMovementLog log) {
        return new MovementResponse(log.getFromTier(), log.getToTier(), log.getKind().name(), log.getAt());
    }
}
