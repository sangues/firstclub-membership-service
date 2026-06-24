package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.CatalogService;
import com.firstclub.membership.common.exception.SubscriptionNotFoundException;
import com.firstclub.membership.subscription.Subscription;
import com.firstclub.membership.subscription.SubscriptionRepository;
import com.firstclub.membership.subscription.SubscriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BenefitService {
    private final SubscriptionRepository subscriptions;
    private final CatalogService catalog;

    public BenefitService(SubscriptionRepository subscriptions, CatalogService catalog) {
        this.subscriptions = subscriptions; this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<ResolvedBenefit> effectiveBenefits(Long userId) {
        Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("no active subscription for user " + userId));
        return catalog.benefitsForTier(sub.getEffectiveTier().getId());
    }
}
