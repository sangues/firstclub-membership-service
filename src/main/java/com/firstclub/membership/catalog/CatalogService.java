package com.firstclub.membership.catalog;

import com.firstclub.membership.catalog.dto.PlanResponse;
import com.firstclub.membership.catalog.dto.TierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CatalogService {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;

    public CatalogService(MembershipPlanRepository plans, TierRepository tiers) {
        this.plans = plans;
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return plans.findAll().stream().map(PlanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listTiers() {
        return tiers.findAllByOrderByRankAsc().stream()
                .map(t -> new TierResponse(t.getId(), t.getName(), t.getRank(), List.of()))
                .toList();
    }
}
