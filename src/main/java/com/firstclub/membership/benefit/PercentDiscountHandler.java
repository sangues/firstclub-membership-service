package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PercentDiscountHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.PERCENT_DISCOUNT; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        String pct = params.getOrDefault("discountPercent", "0");
        return new ResolvedBenefit(b.getType().name(),
                "Extra " + pct + "% discount on selected items", params);
    }
}
