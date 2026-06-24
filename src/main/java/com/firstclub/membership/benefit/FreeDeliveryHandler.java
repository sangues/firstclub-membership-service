package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class FreeDeliveryHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.FREE_DELIVERY; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Free delivery on eligible orders", params);
    }
}
