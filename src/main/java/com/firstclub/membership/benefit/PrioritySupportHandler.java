package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PrioritySupportHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.PRIORITY_SUPPORT; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Priority customer support", params);
    }
}
