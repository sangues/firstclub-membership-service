package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.catalog.Benefit;
import java.util.Map;

public record BenefitResponse(Long id, String type, String description, Map<String, String> params) {
    public static BenefitResponse from(Benefit b) {
        return new BenefitResponse(b.getId(), b.getType().name(), b.getDescription(), b.getParams());
    }
}
