package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.catalog.MembershipPlan;
import java.math.BigDecimal;

public record PlanResponse(Long id, String planType, BigDecimal price, int durationDays) {
    public static PlanResponse from(MembershipPlan p) {
        return new PlanResponse(p.getId(), p.getPlanType().name(), p.getPrice(), p.getDurationDays());
    }
}
