package com.firstclub.membership.catalog;

import com.firstclub.membership.benefit.BenefitHandlerRegistry;
import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.dto.BenefitResponse;
import com.firstclub.membership.catalog.dto.PlanResponse;
import com.firstclub.membership.catalog.dto.TierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CatalogService {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;
    private final BenefitRepository benefits;
    private final TierBenefitRepository tierBenefits;
    private final BenefitHandlerRegistry benefitRegistry;

    public CatalogService(MembershipPlanRepository plans, TierRepository tiers,
                          BenefitRepository benefits, TierBenefitRepository tierBenefits,
                          BenefitHandlerRegistry benefitRegistry) {
        this.plans = plans;
        this.tiers = tiers;
        this.benefits = benefits;
        this.tierBenefits = tierBenefits;
        this.benefitRegistry = benefitRegistry;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return plans.findAll().stream().map(PlanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listTiers() {
        return tiers.findAllByOrderByRankAsc().stream()
                .map(t -> new TierResponse(t.getId(), t.getName(), t.getRank(), benefitsForTier(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BenefitResponse> listBenefits() {
        return benefits.findAll().stream()
                .map(BenefitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ResolvedBenefit> benefitsForTier(Long tierId) {
        return tierBenefits.findByTierId(tierId).stream()
                .map(tb -> benefitRegistry.resolve(tb.getBenefit(), tb.getParamOverrides()))
                .toList();
    }
}
