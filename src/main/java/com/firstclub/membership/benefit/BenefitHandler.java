package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import java.util.Map;

public interface BenefitHandler {
    BenefitType type();
    ResolvedBenefit resolve(Benefit benefit, Map<String, String> effectiveParams);
}
